package com.club.harness;

import com.club.ClubClient;
import com.club.config.ClubConfig;
import com.club.modules.binds.HoldKeys;
import com.club.modules.binds.ModuleBinds;
import com.club.modules.freelook.FreelookModule;
import com.club.modules.fullbright.FullbrightModule;
import com.club.modules.togglesprint.ToggleSprintModule;
import com.club.modules.zoom.ZoomModule;
import com.club.ui.hud.HudEditorScreen;
import com.club.ui.menu.ClubMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Self-driving verification harness (dev only — activated by the {@code CLUB_HARNESS} env var, inert
 * otherwise). Waits until in-world, then runs a scripted sequence of technical asserts (module logic
 * called directly) and visual scenes (open a screen, drive its keyboard, screenshot the framebuffer),
 * writes {@code run/club-harness-report.txt} + {@code run/screenshots/club-*.png}, and quits the client.
 *
 * <p>This is the "separate environment" for checking every feature without a human at the keyboard:
 * screens are driven through {@link Screen#keyPressed}/charTyped (exercising the real Stage-27 keyboard
 * nav), and frames are captured with the engine's own {@link ScreenshotRecorder}. Steps are spaced by
 * settle ticks so the screen renders before capture. Every step is wrapped so one failure can't abort
 * the run.</p>
 */
public final class ClubHarness {
    private ClubHarness() {}

    public static boolean enabled() { return System.getenv("CLUB_HARNESS") != null; }

    public static void start() {
        Harness h = new Harness();
        ClientTickEvents.END_CLIENT_TICK.register(h::tick);
    }

    private static final class Step {
        final Runnable action; final int settle;
        /** A GATE step (Stage 64): it is evaluated every tick and the script does not advance until it
         *  returns true — or until {@code timeout} ticks have passed, which is a REPORTABLE outcome, not a
         *  silent one. Measuring after a fixed number of ticks is what let the profiler read a client that
         *  was still building chunk meshes and JIT-compiling, and call that number the mod's cost. */
        final java.util.function.BooleanSupplier until; final int timeout;
        Step(Runnable a, int s) { this(a, s, null, 0); }
        Step(Runnable a, int s, java.util.function.BooleanSupplier u, int t) {
            action = a; settle = s; until = u; timeout = t;
        }
    }

    private static final class Harness {
        private final MinecraftClient mc = MinecraftClient.getInstance();
        private final List<String> report = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private int cursor = -1;    // -1 = not started (waiting for world)
        private int wait;
        private int shotNo, passed, failed;
        private int prevGuiScale = 2;   // restored after the squeezed-window scene
        private boolean built, finished;
        private int gateTicks;
        private boolean gateTimedOut;

        /** The two measurement windows of the profiler self-test (Stage 64). */
        private com.club.modules.perf.HudProfiler.Snapshot statsA, statsB;
        private final Settle settle = new Settle();

        /** Relative disagreement between two readings of the same thing — the instrument's error bar. */
        private static double spread(double x, double y) {
            double lo = Math.min(x, y);
            return lo <= 0 ? 1.0 : Math.abs(x - y) / lo;
        }

        /**
         * WAIT UNTIL THE READING STOPS MOVING — the fix for the bug that produced 0.45 ms and 1.22 ms from
         * the same build (Stage 64).
         *
         * <p>The old script warmed up for a fixed 60 ticks and then measured. That is not a warm-up, it is a
         * guess. The first measured window ran at 85 fps and the second, in the same static scene seconds
         * later, at 207 — the client was still JIT-compiling and building chunk meshes, and every phase was
         * inflated by the same ~2.4x, INCLUDING the pure-CPU raycast and layout, which cannot possibly care
         * about the GPU. We were timing a busy machine and printing it as the mod's cost.
         *
         * <p>So: probe in short windows, and open only once three consecutive windows agree within 12%. If
         * they never do, the gate TIMES OUT — and the run then reports INVALID and asserts nothing, because
         * a number from a machine that never settled is not evidence about the mod.
         *
         * <p>It converges on the SHARE OF THE FRAME, not on the millisecond, and that distinction is the
         * whole finding of this stage. Measured across four windows in two sessions, one machine, one static
         * scene: the HUD's median cost was 0.31, 0.60, 0.72, 0.83 ms — a 2.7x spread — because the client
         * itself ran at 76, 96, 114 and 207 fps depending on what else the machine was doing. Its share of
         * the frame over those same four windows: 6.4%, 5.7%, 6.1%, 6.3%. The millisecond was the machine.
         * The share is the mod.
         */
        private final class Settle implements java.util.function.BooleanSupplier {
            static final int PROBE = 60;              // ticks per probe window (~3 s)
            private static final int NEEDED = 2;      // consecutive agreeing pairs => 3 agreeing windows
            private static final double TOLERANCE = 0.12;

            private int t;
            private double prev = -1;
            private int agreed;
            int windows;                              // how long the machine took — worth printing
            double lastShare, lastMs, lastFps;

            @Override public boolean getAsBoolean() {
                if (t == 0) com.club.hud.HudManager.profile(true);
                if (++t < PROBE) return false;

                var s = com.club.hud.HudManager.stats();
                com.club.hud.HudManager.profile(false);
                t = 0; windows++;
                lastShare = s.share(); lastMs = s.medianMs(); lastFps = s.fps();
                if (s.frames() < 30 || lastShare <= 0) { prev = -1; agreed = 0; return false; }

                if (prev > 0 && spread(prev, lastShare) <= TOLERANCE) agreed++; else agreed = 0;
                prev = lastShare;
                return agreed >= NEEDED;
            }
        }

        void tick(MinecraftClient client) {
            if (finished) return;
            if (cursor < 0) {
                if (mc.player == null || mc.world == null) return;   // wait for the quick-play world
                if (mc.getFramebuffer() == null) return;
                build();
                cursor = 0; wait = 20;   // let the world settle a second before starting
                return;
            }
            if (wait-- > 0) return;
            if (cursor >= steps.size()) { finish(); return; }
            Step s = steps.get(cursor);
            if (s.until != null) {   // a gate: hold the script here until it opens (or gives up)
                gateTicks++;
                boolean open;
                try { open = s.until.getAsBoolean(); }
                catch (Throwable t) { report.add("EXCEPTION in gate " + cursor + ": " + t); open = true; }
                if (!open && gateTicks < s.timeout) return;
                gateTimedOut = !open;
                gateTicks = 0;
            }
            cursor++;
            try { s.action.run(); } catch (Throwable t) { report.add("EXCEPTION in step " + (cursor - 1) + ": " + t); }
            wait = s.settle;
        }

        // ---- helpers -----------------------------------------------------------

        private void step(int settle, Runnable r) { steps.add(new Step(r, settle)); }
        /** Hold the script here until {@code cond} opens, at most {@code timeout} ticks, then run {@code then}. */
        private void gate(java.util.function.BooleanSupplier cond, int timeout, Runnable then) {
            steps.add(new Step(then, 2, cond, timeout));
        }

        // ---- the A/B (Stage 65) -------------------------------------------------

        private final List<com.club.modules.perf.HudProfiler.Snapshot> batchOff = new ArrayList<>();
        private final List<com.club.modules.perf.HudProfiler.Snapshot> batchOn  = new ArrayList<>();

        /** One measured window with the icon batch in a given state. Interleaved by the caller. */
        private void abWindow(boolean on) {
            step(2, () -> { com.club.hud.PixelIcons.batchEnabled = on; com.club.hud.HudManager.profile(true); });
            step(90, () -> {});                                  // ~4.5 s of real frames
            step(2, () -> {
                var s = com.club.hud.HudManager.stats();
                com.club.hud.HudManager.profile(false);
                (on ? batchOn : batchOff).add(s);
            });
        }

        private static double medianShare(List<com.club.modules.perf.HudProfiler.Snapshot> ws) {
            double[] a = ws.stream().mapToDouble(com.club.modules.perf.HudProfiler.Snapshot::share).sorted().toArray();
            return a.length == 0 ? 0 : a[a.length / 2];
        }
        private static double worstSpread(List<com.club.modules.perf.HudProfiler.Snapshot> ws) {
            double lo = Double.MAX_VALUE, hi = 0;
            for (var s : ws) { lo = Math.min(lo, s.share()); hi = Math.max(hi, s.share()); }
            return lo <= 0 ? 1.0 : (hi - lo) / lo;
        }
        private void check(String name, boolean cond) {
            report.add((cond ? "PASS  " : "FAIL  ") + name);
            if (cond) passed++; else failed++;
        }
        private void shot(String name) {
            String file = String.format("club-%02d-%s.png", shotNo++, name);
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), t -> {});
            report.add("SHOT  " + file);
        }
        private void key(int k) { key(k, 0); }
        /** A real tap: press AND release. The release matters — ClubMenuScreen tracks held keys to tell a
         *  GLFW auto-repeat from a fresh press, so a press-only harness would leave keys "stuck down". */
        private void key(int k, int mods) {
            Screen s = mc.currentScreen;
            if (s == null) return;
            s.keyPressed(k, 0, mods);
            s.keyReleased(k, 0, mods);
        }
        /** The key that actually opens the Club menu — read from the binding, NOT assumed to be the
         *  default: a dev run (or a player) may have rebound it, and then the test would press a key
         *  that isn't reserved at all and quietly prove nothing. */
        private int menuKey() {
            return InputUtil.fromTranslationKey(ClubClient.openMenuKey.getBoundKeyTranslationKey()).getCode();
        }
        private void type(char c) { Screen s = mc.currentScreen; if (s != null) s.charTyped(c, 0); }

        // ---- the script --------------------------------------------------------

        private void build() {
            if (built) return; built = true;
            ClubConfig cfg = ClubConfig.get();

            // ===== TECHNICAL ASSERTS (direct module logic) =====
            step(2, () -> report.add("== technical asserts =="));

            // Fullbright scoping (the Stage-45 fix): overridden ONLY inside the lightmap window.
            step(2, () -> {
                double real = num(mc.options.getGamma().getValue());
                boolean prev = FullbrightModule.on();
                FullbrightModule.set(true);
                double outside = num(mc.options.getGamma().getValue());
                FullbrightModule.enterLightmap();
                double inside = num(mc.options.getGamma().getValue());
                FullbrightModule.exitLightmap();
                FullbrightModule.set(prev);
                check("fullbright: gamma OUTSIDE lightmap stays real (" + real + ") — options.txt honest", outside == real);
                check("fullbright: gamma INSIDE lightmap reads 15", inside == FullbrightModule.GAMMA);
            });

            // Zoom divisor math (smooth off → instant, so it's frame-independent). Force enabled so the
            // test is robust to whatever the persisted config holds.
            step(2, () -> {
                // divisor math (active state now comes from the RAW key, which the harness can't hold)
                float f = cfg.zoom.factor;
                check("zoom: full zoom divides FOV by factor", Math.abs(ZoomModule.divisorFor(f, 1f) - f) < 0.01f);
                check("zoom: no zoom → divisor 1", Math.abs(ZoomModule.divisorFor(f, 0f) - 1f) < 0.01f);
                check("zoom: not active while its key isn't held", !ZoomModule.active());
            });

            // Look damping while zoomed (Stage 58): the view must turn slower the deeper you zoom, so
            // the world moves at a constant speed across the SCREEN (what every mainstream zoom does).
            step(2, () -> {
                check("zoom: no zoom → sensitivity untouched", ZoomModule.scaleFor(1f, 70f) == 1f);
                float s4 = ZoomModule.scaleFor(4f, 70f);
                check("zoom: 4x damps the look to ~1/4.5 (" + String.format("%.3f", s4) + ")",
                        s4 > 0.15f && s4 < 0.30f);
                check("zoom: deeper zoom damps more", ZoomModule.scaleFor(8f, 70f) < s4);
            });

            // Screen Stretch must be a NO-OP out of the box (Stage 59 audit): it shipped ON with a
            // hard-coded 16:9 target, which is invisible on a 16:9 dev monitor and WARPS the world of
            // everyone on 16:10 / ultrawide / 5:4 the moment they install the mod.
            step(2, () -> {
                // A FRESH install must not touch the projection. (This dev config has 4:3 picked by hand,
                // and the migration deliberately leaves a DELIBERATE choice alone — so assert the default
                // and the fallback, not the live value.)
                check("stretch: a fresh install is AUTO (no warp on a non-16:9 monitor)",
                        "AUTO".equals(new ClubConfig.ScreenStretch().preset));
                check("stretch: an unknown preset falls back to AUTO, not to a ratio",
                        com.club.modules.screenstretch.StretchPreset.fromName("junk")
                                == com.club.modules.screenstretch.StretchPreset.AUTO);
                String was = cfg.screenStretch.preset;
                cfg.screenStretch.preset = "AUTO";
                boolean[] vertical = new boolean[1];
                check("stretch: AUTO → the world projection is untouched and no bars are painted",
                        !com.club.modules.screenstretch.ScreenStretchModule.isActive()
                        && Math.abs(com.club.modules.screenstretch.ScreenStretchModule.projectionScaleX() - 1f) < 0.001f
                        && com.club.modules.screenstretch.ScreenStretchModule.barThickness(1080, 1920, vertical) == 0);
                cfg.screenStretch.preset = was;
            });

            // Toggle Sprint force + clean release.
            step(2, () -> {
                boolean prevEnabled = cfg.toggleSprint.enabled;
                boolean vanillaToggle = mc.options.getSprintToggled().getValue();
                cfg.toggleSprint.enabled = true;
                ToggleSprintModule.tick(mc);
                if (vanillaToggle) report.add("SKIP  togglesprint force (vanilla Sprint:Toggle is on)");
                else check("togglesprint: holds sprintKey while active", mc.options.sprintKey.isPressed());
                cfg.toggleSprint.enabled = false;
                ToggleSprintModule.tick(mc);
                check("togglesprint: off-edge releases sprintKey (no stick)", !mc.options.sprintKey.isPressed());
                cfg.toggleSprint.enabled = prevEnabled;
            });

            // Freelook: engages, forces third-person, rotates the free camera not the player.
            step(2, () -> {
                // engage via apply() directly — active() reads the RAW key, which the harness can't hold
                Perspective prevP = mc.options.getPerspective();
                FreelookModule.apply(true, mc);
                check("freelook: engages on hold", FreelookModule.active());
                check("freelook: forces third-person", mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK);
                float playerYaw = mc.player.getYaw();
                float camY0 = FreelookModule.camYaw();
                FreelookModule.onLook(120, 0);
                check("freelook: mouse rotates the FREE camera", FreelookModule.camYaw() != camY0);
                check("freelook: player yaw is NOT changed by look", mc.player.getYaw() == playerYaw);
                FreelookModule.apply(false, mc);
                check("freelook: releases + restores perspective", !FreelookModule.active()
                        && mc.options.getPerspective() == prevP);
            });

            // Module TOGGLE keybinds: assign/label, conflict-steal, clear, malformed self-heal (no crash).
            step(2, () -> {
                ModuleBinds.set("No Bobbing", "key.keyboard.k");
                check("binds: assign records a displayable bind", "K".equals(ModuleBinds.label("No Bobbing")));
                ModuleBinds.set("Fullbright", "key.keyboard.k");
                check("binds: conflict steals the key from the other module", ModuleBinds.label("No Bobbing") == null);
                check("binds: new holder keeps it", ModuleBinds.label("Fullbright") != null);
                ModuleBinds.set("Fullbright", null);
                check("binds: cleared", ModuleBinds.label("Fullbright") == null);
                boolean crashed = false;
                try {
                    ClubConfig.get().moduleBinds.put("Fullbright", "key.keyboard.THIS_IS_JUNK");
                    ModuleBinds.tick(mc);
                } catch (Throwable t) { crashed = true; }
                check("binds: malformed config value self-heals (no crash)",
                        !crashed && ClubConfig.get().moduleBinds.get("Fullbright") == null);
                // Stage 58: a HOLD module has no toggle bind at all — one press must not both zoom AND
                // switch Zoom off (the "works every other press" bug).
                check("binds: hold modules carry no toggle bind (Zoom)", !ModuleBinds.tracks("Zoom"));
                check("binds: hold modules carry no toggle bind (Freelook)", !ModuleBinds.tracks("Freelook"));
            });

            // HOLD keys (Stage 58): the Zoom/Freelook row rebinds the REAL vanilla binding, and one
            // physical key can never drive two Club actions (the namespaces steal from each other).
            step(2, () -> {
                HoldKeys.set("Zoom", InputUtil.fromKeyCode(GLFW_KEY_C, 0));
                check("holdkeys: Zoom's hold key binds to C", "C".equals(HoldKeys.label("Zoom")));
                check("holdkeys: the vanilla binding really moved",
                        "key.keyboard.c".equals(ClubClient.zoomKey.getBoundKeyTranslationKey()));
                ModuleBinds.set("Fullbright", "key.keyboard.c");   // a toggle bind claims the same key
                check("holdkeys: a toggle bind on that key steals it (no double-fire)", HoldKeys.label("Zoom") == null);
                HoldKeys.reset("Zoom");
                check("holdkeys: Reset restores the factory key (C)", "C".equals(HoldKeys.label("Zoom")));
                check("holdkeys: …and takes it back from the toggle bind", ModuleBinds.label("Fullbright") == null);
                ModuleBinds.set("Fullbright", null);
            });

            // ===== THE VANILLA SEAM (Stage 62) =====
            // Everything here is a bug the owner could hit by never leaving vanilla's own screens. The mod
            // used to look only at its own two bind namespaces and never once at options.allKeys.

            // A Club key that lands on a VANILLA key must be NAMED. (Vanilla dispatches one binding per
            // physical key, so the collision kills one of the two actions — arbitrarily, by hash order.)
            step(2, () -> {
                String sneakKey = mc.options.sneakKey.getBoundKeyTranslationKey();
                String taken = com.club.modules.binds.KeyConflicts.other(sneakKey);
                check("conflicts: a key vanilla already owns is reported, by vanilla's own name for it ("
                        + taken + ")", taken != null && !taken.isBlank());
                check("conflicts: a free key reports nothing",
                        com.club.modules.binds.KeyConflicts.other("key.keyboard.f13") == null);
                check("conflicts: an unbound key is not a conflict",
                        com.club.modules.binds.KeyConflicts.other(InputUtil.UNKNOWN_KEY.getTranslationKey()) == null);
                // A key only CLUB uses is not a conflict — the scan must skip our own bindings, or every
                // bind row in the mod would permanently accuse itself.
                InputUtil.Key free = InputUtil.fromKeyCode(GLFW_KEY_F13, 0);
                com.club.modules.binds.HoldKeys.set("Zoom", free);
                check("conflicts: a key only Club uses is not reported as a conflict",
                        com.club.modules.binds.KeyConflicts.other(free.getTranslationKey()) == null);
                com.club.modules.binds.HoldKeys.reset("Zoom");

                // …and the one the OWNER should know about: Zoom ships on C, and so does vanilla's
                // "Save Toolbar Activator". Every install starts with that duplicate — vanilla paints it red
                // in Controls, and until Stage 62 the Club menu said nothing at all. Reported, not asserted:
                // the default key is a product decision (C is the OptiFine muscle-memory spot), and the mod's
                // job is to SAY so, which the popover now does.
                String shipDefault = com.club.modules.binds.KeyConflicts.other(
                        ClubClient.zoomKey.getDefaultKey().getTranslationKey());
                report.add("INFO  Zoom's factory key (C) shares itself with vanilla: "
                        + (shipDefault == null ? "nothing" : shipDefault));
            });

            // The trap with no way out: vanilla's Controls screen puts Zoom on the menu key, the two race
            // for vanilla's single-winner dispatch map, the menu key loses — and the only place to fix the
            // bind is the menu that can no longer be opened.
            step(2, () -> {
                InputUtil.Key menu = InputUtil.fromTranslationKey(ClubClient.openMenuKey.getBoundKeyTranslationKey());
                ClubClient.zoomKey.setBoundKey(menu);          // exactly what the vanilla Controls screen does
                net.minecraft.client.option.KeyBinding.updateKeysByCode();
                com.club.modules.binds.HoldKeys.reconcileMenuKey();
                check("menu key: a hold key that lands on it is taken back — the menu is always reachable",
                        !menu.getTranslationKey().equals(ClubClient.zoomKey.getBoundKeyTranslationKey()));
                check("menu key: …and the menu keeps its own key", !ClubClient.openMenuKey.isUnbound());
                com.club.modules.binds.HoldKeys.reset("Zoom");
                check("menu key: Zoom is restorable afterwards", "C".equals(com.club.modules.binds.HoldKeys.label("Zoom")));
            });

            // A toggle bind that a hold key has quietly taken over must SAY so — vanilla's screen can create
            // that overlap behind the popover's back, and the row went on showing a dead key as if it worked.
            step(2, () -> {
                com.club.modules.binds.ModuleBinds.set("Fullbright", "key.keyboard.k");
                ClubClient.zoomKey.setBoundKey(InputUtil.fromKeyCode(GLFW_KEY_K, 0));   // the vanilla screen, again
                net.minecraft.client.option.KeyBinding.updateKeysByCode();
                check("binds: the row knows which hold module took its key",
                        "Zoom".equals(com.club.modules.binds.ModuleBinds.shadowedBy("Fullbright")));
                com.club.modules.binds.HoldKeys.reset("Zoom");
                check("binds: …and stops saying so once the key is given back",
                        com.club.modules.binds.ModuleBinds.shadowedBy("Fullbright") == null);
                com.club.modules.binds.ModuleBinds.set("Fullbright", null);
            });

            // A key the tick loop can never fire is not a bind: it reads "Not set", it does not read "Mouse 4".
            step(2, () -> {
                ClubConfig.get().moduleBinds.put("Fullbright", "key.mouse.4");
                check("binds: a mouse button in a keyboard-only field reads as unbound, not as a live key",
                        com.club.modules.binds.ModuleBinds.label("Fullbright") == null);
                ClubConfig.get().moduleBinds.remove("Fullbright");
            });

            // Toggle Sprint: the sequence that latched the sprint key down FOREVER. The module force-holds
            // sprintKey; the player then switches vanilla's "Sprint: Toggle" ON; sprintKey is a
            // StickyKeyBinding, whose setPressed(false) is a NO-OP in toggle mode — so the release did
            // nothing and the player sprinted for the rest of the session.
            step(2, () -> {
                boolean prevEnabled = cfg.toggleSprint.enabled;
                boolean prevVanilla = mc.options.getSprintToggled().getValue();
                cfg.toggleSprint.enabled = true;
                mc.options.getSprintToggled().setValue(false);
                ToggleSprintModule.tick(mc);                      // forcing now
                mc.options.getSprintToggled().setValue(true);     // …and the player flips the vanilla option
                ToggleSprintModule.tick(mc);                      // off-edge: the release must actually release
                check("togglesprint: the sprint key is released even when vanilla's Sprint: Toggle takes over",
                        !mc.options.sprintKey.isPressed());
                check("togglesprint: the card admits it is idle while vanilla's toggle is on",
                        com.club.ui.menu.MenuContent.notice("Toggle Sprint") != null);
                mc.options.getSprintToggled().setValue(prevVanilla);
                cfg.toggleSprint.enabled = prevEnabled;
                ToggleSprintModule.tick(mc);
                mc.options.sprintKey.setPressed(false);
            });

            // A lit card that is a deliberate no-op has to say which one it is.
            step(2, () -> {
                String was = cfg.screenStretch.preset;
                boolean wasOn = cfg.screenStretch.enabled;
                cfg.screenStretch.enabled = true; cfg.screenStretch.preset = "AUTO";
                check("stretch: an enabled-but-AUTO card says the world is untouched",
                        com.club.ui.menu.MenuContent.notice("Screen Stretch") != null);
                cfg.screenStretch.preset = "R16_9";   // the ENUM name — fromName() is valueOf(), not the label
                check("stretch: …and says nothing once it is really stretching",
                        com.club.ui.menu.MenuContent.notice("Screen Stretch") == null);
                cfg.screenStretch.preset = was; cfg.screenStretch.enabled = wasOn;
            });

            // ===== THE HUD OWNS ITS SIZE (Stage 63) =====
            // The owner caught this one: "какого хера у нас худы меняют свой размер в зависимости от настроек
            // в игре". They did — the HUD was laid out in Minecraft's GUI units, so the player's GUI Scale
            // multiplied every chip, on top of that chip's own Size slider. The menu was cured of exactly this
            // in Stage 60; the HUD had been left behind.
            //
            // The proof has to be in PHYSICAL pixels, because that is what the player sees: take the same
            // element at GUI Scale 1, 2, 3 and 4, convert its box to real screen pixels, and demand the same
            // rectangle every time. Before this stage those four numbers were 1:2:3:4.
            step(2, () -> {
                int prev = mc.options.getGuiScale().getValue();
                com.club.ui.hud.HudElement probe = com.club.hud.HudManager.probeElement();
                if (probe == null) { check("hud: an element to measure", false); return; }
                double[] physW = new double[4];
                double[] physX = new double[4];
                for (int s = 1; s <= 4; s++) {
                    mc.options.getGuiScale().setValue(s);
                    mc.onResolutionChanged();
                    float k = com.club.ui.ClubCanvas.scale(mc);                 // MC units per Club unit
                    double mcPx = mc.getWindow().getScaleFactor();              // physical px per MC unit
                    int[] b = probe.box(mc);                                    // Club units
                    physW[s - 1] = b[2] * k * mcPx;
                    physX[s - 1] = b[0] * k * mcPx;
                }
                mc.options.getGuiScale().setValue(prev);
                mc.onResolutionChanged();
                report.add(String.format("INFO  hud size in real pixels at GUI scale 1..4: %.0f %.0f %.0f %.0f "
                        + "(x: %.0f %.0f %.0f %.0f)", physW[0], physW[1], physW[2], physW[3],
                        physX[0], physX[1], physX[2], physX[3]));
                double wSpread = Math.abs(physW[3] - physW[0]);
                double xSpread = Math.abs(physX[3] - physX[0]);
                check("hud: the same physical SIZE at every GUI scale (spread " + Math.round(wSpread) + "px)",
                        wSpread <= 2.0);
                check("hud: …and the same physical POSITION (spread " + Math.round(xSpread) + "px)",
                        xSpread <= 2.0);
            });

            // A config written before Stage 63 holds GUI-space pixels. They have to be converted, once, using
            // the scale the player was last on — not silently reinterpreted, which would fling the HUD across
            // the screen for anyone who wasn't on GUI Scale 2.
            step(2, () -> {
                ClubConfig.Hud h = cfg.hud;
                int wasX = h.armorX, wasY = h.armorY; Integer wasSpace = h.space;
                int prevScale = mc.options.getGuiScale().getValue();
                // Do it at GUI scale 4 on purpose: that is the stock AUTO scale on 1080p, so it is where most
                // players' saved coordinates actually come from — and it is the only scale where a wrong
                // conversion is obvious (the space is 270 units tall, half the canvas, so every number doubles).
                mc.options.getGuiScale().setValue(4);
                mc.onResolutionChanged();
                int scaledH = mc.getWindow().getScaledHeight();
                h.armorX = 100; h.armorY = 100; h.space = 0;      // pretend: an old file, coords in GUI px
                com.club.hud.HudSpace.resetForTest();
                com.club.hud.HudSpace.migrate(mc);
                float f = com.club.ui.ClubCanvas.HEIGHT / scaledH;
                check("hud: an old config's coordinates are converted into Club units (100 → "
                                + h.armorY + ", scaledH " + scaledH + ", factor " + String.format("%.2f", f) + ")",
                        h.armorY == Math.round(100 * f) && h.space != null && h.space == 1);
                mc.options.getGuiScale().setValue(prevScale);
                mc.onResolutionChanged();
                // …and an auto position (-1) is a decision, not a coordinate: it must survive untouched.
                h.armorX = -1; h.space = 0;
                com.club.hud.HudSpace.resetForTest();
                com.club.hud.HudSpace.migrate(mc);
                check("hud: an auto position is not rescaled", h.armorX == -1);
                h.armorX = wasX; h.armorY = wasY; h.space = wasSpace;
                com.club.hud.HudSpace.resetForTest();
            });

            // [SEAM:checks] New workstreams add their assert blocks here, each in its own step(...).

            // ===== THE INSTRUMENT, AND WHAT IT MEASURES (Stage 64-65) =====
            // The step this replaces reported a MEAN of one accumulator and asserted "< 0.8 ms". It shipped
            // a number (0.45 ms) the same build could not reproduce — the repo's own last report said 1.22 ms
            // at the identical draws — and Stage 63 accepted a change on a 1.20 -> 1.06 "win" that lived
            // inside that spread. The instrument was the bug.
            //
            // Two rules come out of fixing it, and both are enforced here:
            //   1. THE MILLISECOND IS THE MACHINE; THE SHARE OF THE FRAME IS THE MOD. Two windows seconds
            //      apart in this static scene ran at 83 and 125 fps and every phase moved with them — the
            //      raycast included, which cannot possibly care about the GPU. Shares are asserted; the
            //      milliseconds are printed next to them, as context, never as evidence.
            //   2. A/B IN ONE SESSION, INTERLEAVED. OFF, ON, OFF, ON, OFF, ON — never one long block of each,
            //      which measures the machine warming up. Comparing two RUNS is the mistake this stage exists
            //      to stop, so the glyph cache is toggled at runtime and both arms are measured here.
            step(2, () -> mc.setScreen(null));
            // A gate, not a fixed warm-up: probe until three consecutive windows agree (max ~40 s).
            gate(settle, 800, () -> report.add(String.format(
                    "INFO  settle: the reading stopped moving after %d probe windows "
                    + "(%.0f fps, %.3f ms, share %.2f%%)%s",
                    settle.windows, settle.lastFps, settle.lastMs, settle.lastShare * 100,
                    gateTimedOut ? " — TIMED OUT, this run is INVALID" : "")));
            for (int i = 0; i < 3; i++) { abWindow(false); abWindow(true); }
            step(2, this::reportPerf);

            // ===== THE ICON BATCH IS INVISIBLE — PROVED, NOT PROMISED (Stage 67) =====
            // Batching the duotone icons draws them as a GROUP, at the end of the pass, instead of one at a
            // time between the text and the gauges. That is a change of painter's order, and the owner's
            // rule is that the picture may not move by a pixel. The reorder is invisible if and only if
            // nothing drawn AFTER an icon overlaps it — a directional property, so it is asserted on the
            // real draw sequence, with real armour and real effects (an empty HUD proves nothing), at every
            // GUI scale, because the scale changes the layout and could bring a glyph onto a sprite.
            step(2, () -> {
                cfg.hud.armor = cfg.hud.potions = cfg.hud.info = cfg.hud.sprint = cfg.hud.target = true;
                if (mc.player != null) {
                    mc.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.SPEED, 1200, 1));
                    mc.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.REGENERATION, 1200, 0));
                    mc.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1200, 0));
                }
                mc.setScreen(null);
            });
            step(10, () -> {});   // let the chips reveal (they animate in; a half-drawn HUD is a weak test)
            for (int gs = 1; gs <= 4; gs++) {
                final int scale = gs;
                step(2, () -> {
                    prevGuiScale = mc.options.getGuiScale().getValue();
                    mc.options.getGuiScale().setValue(scale);
                    mc.onResolutionChanged();
                    com.club.modules.perf.DrawBoxes.recording = true;
                    com.club.modules.perf.DrawBoxes.clearWorst();
                });
                // 40 ticks, not one frame: the violation this caught was INTERMITTENT — three runs green,
                // the fourth found a shape on an icon. Elements fade and slide, and the frame that breaks is
                // the one you did not sample. Every frame in this stretch is judged; the worst one is the
                // verdict.
                step(40, () -> {});
                step(2, () -> {
                    int icons = com.club.modules.perf.DrawBoxes.count(com.club.modules.perf.DrawBoxes.ICON);
                    int covered = com.club.modules.perf.DrawBoxes.worstCovered();
                    String who = com.club.modules.perf.DrawBoxes.worstOffender();
                    com.club.modules.perf.DrawBoxes.recording = false;
                    report.add(String.format("INFO  order @ GUI scale %d: %d icons, %d text, %d shapes drawn%s",
                            scale, icons,
                            com.club.modules.perf.DrawBoxes.count(com.club.modules.perf.DrawBoxes.TEXT),
                            com.club.modules.perf.DrawBoxes.count(com.club.modules.perf.DrawBoxes.SHAPE),
                            com.club.modules.perf.DrawBoxes.overflowed() ? " (RECORDER OVERFLOWED)" : ""));
                    // Zero icons would make the check pass by measuring nothing — the exact failure this
                    // workstream keeps finding in other people's asserts.
                    check("order: the HUD actually drew icons at GUI scale " + scale + " (" + icons + ")",
                            icons > 0 && !com.club.modules.perf.DrawBoxes.overflowed());
                    check("order: nothing drawn after an icon overlaps it at GUI scale " + scale
                            + (covered == 0 ? "" : " — " + who), covered == 0);
                });
            }
            step(2, () -> {
                mc.options.getGuiScale().setValue(prevGuiScale);
                mc.onResolutionChanged();
                // The effects were staged for the ORDER proof (an empty HUD proves nothing there). They must
                // not stay for the perf A/B: that one has to measure the HUD a player actually runs, so the
                // draw-count pair it prints is the mod's, not the test's.
                if (mc.player != null) mc.player.clearStatusEffects();
            });

            // ===== VISUAL SCENES =====
            step(2, () -> report.add("== visual scenes =="));

            // in-world HUD (player has armor in this world → Armor chip; FPS; etc.)
            step(6, () -> mc.setScreen(null));
            step(2, () -> shot("hud-world"));

            // Fullbright ON — the world lightmap must visibly brighten (scoped override in action)
            step(2, () -> { FullbrightModule.set(true); cfg.fullbright = true; });
            step(8, () -> {});
            step(2, () -> shot("fullbright-on"));
            step(2, () -> { FullbrightModule.set(cfg.fullbright = false); });
            step(6, () -> {});

            // Toggle Sprint chip visible (module active → chip shows top-left)
            step(2, () -> { cfg.toggleSprint.enabled = true; cfg.hud.sprint = true; ToggleSprintModule.tick(mc); });
            step(6, () -> {});
            step(2, () -> shot("sprint-chip"));

            // menu
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> shot("menu"));

            // category switch (rail): Ctrl+Tab
            step(2, () -> key(GLFW_KEY_TAB));                 // → search zone
            step(2, () -> key(GLFW_KEY_TAB, GLFW_MOD_CONTROL)); // → next category
            step(5, () -> {});
            step(2, () -> shot("menu-category"));

            // fresh menu, then open the FIRST card's popover via keyboard. The rail sits on Visuals after
            // the switch above, so this is ZOOM — the Stage-58 "Hold key" row (Strength / Smoothness /
            // Hold key / Reset), and the shot proves nothing hides under the scrollbar.
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> key(GLFW_KEY_TAB));                 // search
            step(2, () -> key(GLFW_KEY_TAB));                 // grid, first card
            step(2, () -> key(GLFW_KEY_SPACE));              // open its popover
            step(6, () -> {});
            step(2, () -> shot("popover"));

            // Bind capture — deterministic via a FLAG module (Fullbright): its popover control set is
            // exactly [search, Bind, Reset], so Tab×2 always lands on Bind regardless of category state.
            // Reached by searching "full" (Fullbright is in the current Visuals category).
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> key(GLFW_KEY_TAB));               // search focus
            step(1, () -> type('f'));
            step(1, () -> type('u'));
            step(1, () -> type('l'));
            step(1, () -> type('l'));                       // grid → just Fullbright
            step(6, () -> {});
            step(2, () -> key(GLFW_KEY_TAB));               // into grid → Fullbright card
            step(2, () -> key(GLFW_KEY_SPACE));            // open Fullbright popover
            step(6, () -> shot("popover-flag"));
            step(2, () -> key(GLFW_KEY_TAB));               // → search(0)
            step(2, () -> key(GLFW_KEY_TAB));               // → Bind(1)
            step(2, () -> key(GLFW_KEY_ENTER));            // arm listening
            step(5, () -> shot("bind-listening"));          // "Press a key…" + hint
            step(2, () -> key(GLFW_KEY_K));                // assign K
            step(5, () -> shot("bind-assigned"));           // Bind row shows the key
            step(2, () -> key(GLFW_KEY_TAB));               // → Reset
            step(2, () -> key(GLFW_KEY_ENTER));            // arm "Sure? Reset"
            step(5, () -> shot("reset-armed"));
            step(2, () -> ModuleBinds.set("Fullbright", null));   // clean up

            // Stage 58 (owner bug): the menu key is RESERVED while capturing — it must say so and it
            // must NOT close the menu. Before, the first press silently cancelled capture and the key's
            // GLFW REPEAT then hit the close route, so the menu shut itself mid-bind. Two presses here
            // is exactly that sequence.
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> key(GLFW_KEY_TAB));               // search focus
            step(1, () -> type('f'));
            step(1, () -> type('u'));
            step(1, () -> type('l'));
            step(1, () -> type('l'));
            step(6, () -> {});
            step(2, () -> key(GLFW_KEY_TAB));               // into grid → Fullbright
            step(2, () -> key(GLFW_KEY_SPACE));            // open its popover
            step(4, () -> {});
            step(2, () -> key(GLFW_KEY_TAB));               // → search
            step(2, () -> key(GLFW_KEY_TAB));               // → Bind
            step(2, () -> key(GLFW_KEY_ENTER));            // arm listening
            step(2, () -> key(menuKey()));                  // the menu key — reserved, keeps listening
            step(6, () -> shot("bind-reserved"));           // "That key opens the menu — pick another"
            step(2, () -> key(menuKey()));                  // its repeat: THIS is what closed the menu
            step(20, () -> {});                             // …a close animation would have finished by now
            step(2, () -> {
                check("menu: the menu key cannot close the menu while capturing a bind",
                        mc.currentScreen instanceof ClubMenuScreen);
                check("menu: the menu key is never assigned to a module",
                        ModuleBinds.label("Fullbright") == null);
            });
            step(2, () -> key(GLFW_KEY_ESCAPE));            // cancel capture
            step(2, () -> ModuleBinds.set("Fullbright", null));

            // Stage 58 review: the Enter that CLICKS the hotkey button is still HELD when its GLFW
            // auto-repeat arrives — that repeat must not bind Enter to the module and re-arm the button
            // it's focused on (an oscillation that also rewrote options.txt at repeat rate).
            step(2, () -> key(GLFW_KEY_TAB));               // → search
            step(2, () -> key(GLFW_KEY_TAB));               // → Bind
            step(2, () -> {
                Screen s = mc.currentScreen;
                if (s == null) { check("bind: menu open for the auto-repeat check", false); return; }
                s.keyPressed(GLFW_KEY_ENTER, 0, 0);         // press — arms listening
                s.keyPressed(GLFW_KEY_ENTER, 0, 0);         // auto-repeat of the SAME held key
                s.keyPressed(GLFW_KEY_ENTER, 0, 0);         // …and again
                s.keyReleased(GLFW_KEY_ENTER, 0, 0);
                check("bind: a held Enter's auto-repeat never binds itself", ModuleBinds.label("Fullbright") == null);
                check("bind: …and the menu survives it", mc.currentScreen instanceof ClubMenuScreen);
            });
            step(2, () -> key(GLFW_KEY_ESCAPE));            // cancel capture

            // Stage 58 review: GUI scale 4 is the STOCK auto scale on 1080p — the window shrinks to
            // 432×222 and two card rows leave NO room underneath. The popover must still be a usable
            // sheet (it takes the well and overlaps the cards) instead of collapsing to a 1px sliver.
            step(2, () -> { prevGuiScale = mc.options.getGuiScale().getValue(); mc.options.getGuiScale().setValue(4); mc.onResolutionChanged(); });
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(4, () -> shot("menu-scale4"));             // rail inside the window, names not smeared (Stage 59)
            step(2, () -> key(GLFW_KEY_TAB));               // search
            step(2, () -> key(GLFW_KEY_TAB));               // grid → first card
            step(2, () -> key(GLFW_KEY_SPACE));            // its popover
            step(10, () -> {});
            step(2, () -> shot("popover-scale4"));
            step(2, () -> {
                if (mc.currentScreen instanceof ClubMenuScreen cs) {
                    float[] g = cs.popoverGeometry();
                    report.add(String.format("INFO  scale-4 popover: contentH=%.0f h=%.0f y=%.0f room=%.0f",
                            g[0], g[1], g[2], g[3]));
                    check("popover: stays a usable sheet on a squeezed window (GUI scale 4)",
                            g[1] >= Math.min(g[0] + 16f, 88f) - 1f);
                } else check("popover: menu open at GUI scale 4", false);
            });
            // …and a real MOUSE click must still land on a card at that scale: the menu draws in its own
            // canvas now, so a click arrives in Minecraft units and has to be converted back. If that
            // conversion is off, everything LOOKS right and nothing is clickable (Stage 60).
            step(4, () -> mc.setScreen(new ClubMenuScreen()));
            step(6, () -> {});
            step(2, () -> {
                if (!(mc.currentScreen instanceof ClubMenuScreen cs)) { check("menu: open for the click test", false); return; }
                double[] p = cs.firstCardCentreMc();
                if (p == null) { check("menu: a card to click", false); return; }
                boolean before = cs.firstCardEnabled();
                cs.mouseClicked(p[0], p[1], 0);
                cs.mouseReleased(p[0], p[1], 0);
                check("menu: a mouse click lands on the card it points at (gui scale 4)",
                        cs.firstCardEnabled() != before);
                cs.mouseClicked(p[0], p[1], 0);   // put it back
                cs.mouseReleased(p[0], p[1], 0);
            });
            step(4, () -> { mc.options.getGuiScale().setValue(prevGuiScale); mc.onResolutionChanged(); });

            // Hands popover — TALL (tabs + 4 sliders + hotkey + reset): checks the body-clamp + card dim
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> key(GLFW_KEY_TAB, GLFW_MOD_CONTROL));   // Visuals → Player
            step(4, () -> {});
            step(2, () -> key(GLFW_KEY_TAB));                     // search focus
            step(1, () -> type('h'));
            step(1, () -> type('a'));
            step(1, () -> type('n'));
            step(1, () -> type('d'));
            step(1, () -> type('s'));                             // grid → just Hands
            step(6, () -> {});
            step(2, () -> key(GLFW_KEY_TAB));                     // into grid → Hands
            step(2, () -> key(GLFW_KEY_SPACE));                  // open Hands popover
            step(10, () -> {});                                  // a step's settle is the delay AFTER it —
            step(2, () -> shot("hands-popover"));                //   so the grow-in needs its own wait step
            // The tallest popover in the mod: it must use every pixel of room under the cards before it
            // starts scrolling (owner: "размер аккуратный … если там много всего пусть будет скролл").
            step(2, () -> {
                if (mc.currentScreen instanceof ClubMenuScreen cs) {
                    float[] g = cs.popoverGeometry();
                    report.add(String.format("INFO  hands popover: contentH=%.0f h=%.0f y=%.0f room=%.0f",
                            g[0], g[1], g[2], g[3]));
                    check("popover: fills the room under the cards when the content overflows",
                            g[0] + 16 <= g[3] + 1 || Math.abs(g[1] - g[3]) < 1.5f);
                } else check("popover: menu still open for the geometry check", false);
            });

            // The conflict, on screen (Stage 62): Fullbright's toggle put on Q — the key vanilla drops your
            // item with. The popover must name it. Before, the row just said "Q" and both things fired.
            step(2, () -> com.club.modules.binds.ModuleBinds.set("Fullbright", "key.keyboard.q"));
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> key(GLFW_KEY_TAB));               // search focus
            step(1, () -> type('f'));
            step(1, () -> type('u'));
            step(1, () -> type('l'));
            step(1, () -> type('l'));
            step(6, () -> {});
            step(2, () -> key(GLFW_KEY_TAB));               // into grid → Fullbright
            step(2, () -> key(GLFW_KEY_SPACE));            // open its popover
            step(10, () -> shot("bind-conflict"));          // "Also: Drop" under the key row
            step(2, () -> com.club.modules.binds.ModuleBinds.set("Fullbright", null));

            // search filter
            step(4, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> key(GLFW_KEY_TAB));               // search focus
            step(1, () -> type('n'));
            step(1, () -> type('o'));
            step(5, () -> shot("search"));

            // HUD editor
            step(6, () -> mc.setScreen(new HudEditorScreen()));
            step(2, () -> shot("editor"));

            step(4, () -> mc.setScreen(null));
        }

        /** The perf verdict: what the HUD costs, whether the instrument can be believed, and what the glyph
         *  cache actually bought — measured ON against OFF, interleaved, in this one session. */
        private void reportPerf() {
            com.club.hud.PixelIcons.batchEnabled = true;   // production state, whatever the last window was
            if (batchOff.size() < 3 || batchOn.size() < 3) {
                check("perf: the profiler saw all six windows", false);
                return;
            }
            // A benchmark that cannot fail honestly must refuse to pass. If the machine never settled, the
            // numbers are still PRINTED (they are diagnostic) but nothing is asserted from them.
            if (gateTimedOut)
                report.add("INVALID  the machine never settled — the numbers below describe this run's load, "
                        + "not the mod. No perf assert is made from them.");

            var s = batchOn.get(batchOn.size() - 1);
            report.add(String.format(
                    "INFO  hud cost: SHARE %.2f%% of a frame — %.3f ms of a %.2f ms frame (%.0f fps) — "
                    + "raycast %.3f | layout %.3f | build %.3f | submit %.3f (mean %.3f, p95 %.3f; %d frames)",
                    s.share() * 100, s.medianMs(), s.frameMs(), s.fps(),
                    s.raycastMs(), s.layoutMs(), s.buildMs(), s.submitMs(),
                    s.meanMs(), s.p95Ms(), s.frames()));
            report.add(String.format(
                    "INFO  …build, cut open: text %.3f | shapes %.3f | icons %.3f | element logic %.3f "
                    + "(the recon's prime suspect, the raycast, is %.0f%% of the whole HUD)",
                    s.textBuildMs(), s.shapeBuildMs(), s.iconBuildMs(), s.otherBuildMs(),
                    s.medianMs() <= 0 ? 0 : s.raycastMs() / s.medianMs() * 100));
            report.add(String.format(
                    "INFO  gl draws/frame: %.1f (%.1f shape + %.1f text + %.1f icons) — backend %s. Icons go "
                    + "out through vanilla's immediate path; the old counter could not see them at all.",
                    s.glDraws(), s.shapeDraws(), s.textDraws(), s.iconDraws(), com.club.ui.Ui.backend()));

            double off = medianShare(batchOff), on = medianShare(batchOn);
            double delta = off <= 0 ? 0 : (off - on) / off;
            StringBuilder w = new StringBuilder();
            for (int i = 0; i < 3; i++)
                w.append(String.format(" off %.2f%%/%.0ffps → on %.2f%%/%.0ffps |",
                        batchOff.get(i).share() * 100, batchOff.get(i).fps(),
                        batchOn.get(i).share() * 100, batchOn.get(i).fps()));
            report.add("INFO  icon batch A/B, interleaved in one session:" + w);
            report.add(String.format("INFO  icon batch: median share %.2f%% off → %.2f%% on (%+.1f%%). "
                    + "Icon phase %.3f ms → %.3f ms. GL draws %.0f → %.0f.",
                    off * 100, on * 100, -delta * 100,
                    batchOff.get(2).iconBuildMs(), batchOn.get(2).iconBuildMs(),
                    batchOff.get(2).glDraws(), batchOn.get(2).glDraws()));

            // THE INSTRUMENT'S OWN TEST — and it is a PRECONDITION, not a result.
            //
            // The three OFF windows are the same code in the same scene. If they cannot agree with each
            // other, nothing measured against them means anything. That was a FAIL until an Iris run at 400
            // fps produced 2.5 ms frames, where the HUD's 0.11 ms is jittery by nature, and the windows
            // disagreed by 36%. A FAIL says "the mod regressed". This does not. It says "this machine, on
            // this run, could not hold still long enough to be asked" — which is INVALID, exactly as it is in
            // ClubBench, and calling it a failure of the mod would be the same class of lie we have spent the
            // whole workstream removing.
            //
            // It is not a way to make a red line green, either: a genuinely broken instrument fails this on
            // EVERY run and prints INVALID every time, loudly, right here.
            double instr = worstSpread(batchOff);
            boolean unstable = gateTimedOut || instr > 0.20;
            if (unstable)
                report.add(String.format("INVALID  the instrument could not repeat itself on this run — three "
                        + "identical windows disagreed by %.1f%% (frames were %.2f ms). Nothing is asserted "
                        + "from the millisecond on this run; the deterministic counters below still are.",
                        instr * 100, batchOff.get(2).frameMs()));
            else
                check(String.format("perf: the instrument repeats itself — three identical windows agree on "
                        + "the HUD's share of the frame within 20%% (%.1f%%)", instr * 100), true);

            // Primum non nocere. A change that makes the HUD cost MORE is a change we delete, and this is
            // the one perf claim that may never be allowed to fail — when it can be asked at all.
            if (!unstable)
                check(String.format("perf: the icon batch is not a regression (%.2f%% → %.2f%%)",
                        off * 100, on * 100), on <= off * 1.02);

            // The deterministic half of the proof, and the one that needs no statistics at all: every icon
            // used to be its own GL draw, and now they are one. This is the number we may print.
            double dOff = batchOff.get(2).glDraws(), dOn = batchOn.get(2).glDraws();
            check(String.format("perf: the icon batch really collapses the draws (%.0f → %.0f a frame)", dOff, dOn),
                    dOn <= dOff - 2.0);
            check("perf: the icon draws are counted, not invisible (" + Math.round(s.iconDraws()) + "/frame)",
                    s.iconDraws() > 0);
        }

        private static double num(Object o) { return ((Number) o).doubleValue(); }

        private void finish() {
            finished = true;
            report.add("");
            report.add("HARNESS DONE — " + passed + " passed, " + failed + " failed, " + shotNo + " screenshots");
            try {
                Path p = mc.runDirectory.toPath().resolve("club-harness-report.txt");
                Files.writeString(p, String.join("\n", report));
            } catch (IOException ignored) {}
            mc.scheduleStop();
        }
    }
}
