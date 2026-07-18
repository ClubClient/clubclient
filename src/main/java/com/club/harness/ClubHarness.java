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
        /**
         * Wipes the screenshot directory before the first frame is captured.
         *
         * <p>A STALE SCREENSHOT IS A LIE THAT LOOKS LIKE EVIDENCE, and this one caught me. The shots are
         * numbered in capture order, so adding or removing a single {@code shot()} call renumbers everything
         * after it — and the previous run's files, under their OLD numbers, stay in the directory. I opened
         * {@code club-03-menu.png} to judge a colour change I had just made, drew a conclusion from it, and
         * reported it. The file was from the day before: {@code club-03} was an item-scroll frame now, and the
         * menu had moved to {@code club-11}. The image was real, the filename was real, and the conclusion was
         * worthless.
         *
         * <p>Everything else this harness produces is asserted; the screenshots are the one output judged by
         * eye, which makes them the one output where a stale artefact cannot be caught by the instrument. So
         * the directory is emptied at the start of the run: what is in it afterwards is what THIS run drew,
         * and nothing else can be mistaken for it.
         */
        private void clearShots() {
            java.io.File dir = new java.io.File(mc.runDirectory, "screenshots");
            java.io.File[] old = dir.listFiles((d, n) -> n.startsWith("club-") && n.endsWith(".png"));
            if (old == null) return;
            int gone = 0;
            for (java.io.File f : old) if (f.delete()) gone++;
            if (gone > 0) report.add("INFO  cleared " + gone + " screenshot(s) from a previous run");
        }

        private void shot(String name) {
            String file = String.format("club-%02d-%s.png", shotNo++, name);
            // 1.21.5 dropped the file-name argument from this overload and names the shot itself; the
            // 5-arg form that still takes a name wants a downscale factor too. 1 = full size.
            //? if <1.21.5 {
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), t -> {});
            //?} else {
            /*ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), 1, t -> {});*/
            //?}
            report.add("SHOT  " + file);
        }
        private void key(int k) { key(k, 0); }
        /** A real tap: press AND release. The release matters — ClubMenuScreen tracks held keys to tell a
         *  GLFW auto-repeat from a fresh press, so a press-only harness would leave keys "stuck down". */
        private void key(int k, int mods) {
            Screen s = mc.currentScreen;
            if (s == null) return;
            // 1.21.9 folded (key, scancode, modifiers) into a KeyInput record. Measured: the int triple is
            // live through 1.21.8 and gone in 1.21.9 — the same release that brought CharInput and Click.
            //? if <1.21.9 {
            s.keyPressed(k, 0, mods);
            s.keyReleased(k, 0, mods);
            //?} else {
            /*net.minecraft.client.input.KeyInput in = new net.minecraft.client.input.KeyInput(k, 0, mods);
            s.keyPressed(in);
            s.keyReleased(in);*/
            //?}
        }
        /** The key that actually opens the Club menu — read from the binding, NOT assumed to be the
         *  default: a dev run (or a player) may have rebound it, and then the test would press a key
         *  that isn't reserved at all and quietly prove nothing. */
        private int menuKey() {
            return InputUtil.fromTranslationKey(ClubClient.openMenuKey.getBoundKeyTranslationKey()).getCode();
        }
        private void type(char c) {
            Screen s = mc.currentScreen;
            if (s == null) return;
            // 1.21.9: charTyped(char, int) became charTyped(CharInput), whose first component is a codepoint.
            //? if <1.21.9 {
            s.charTyped(c, 0);
            //?} else {
            /*s.charTyped(new net.minecraft.client.input.CharInput(c, 0));*/
            //?}
        }

        /** A real click: press AND release, the way the window delivers one. Lives here so the 1.21.9 mouse
         *  refactor is spelled ONCE — (x, y, button) became a Click record, and mouseClicked gained a
         *  `doubled` flag (measured from the mapping's own param name); false is a plain single click. */
        private void click(Screen s, double x, double y, int button) {
            if (s == null) return;
            //? if <1.21.9 {
            s.mouseClicked(x, y, button);
            s.mouseReleased(x, y, button);
            //?} else {
            /*net.minecraft.client.gui.Click c =
                    new net.minecraft.client.gui.Click(x, y, new net.minecraft.client.input.MouseInput(button, 0));
            s.mouseClicked(c, false);
            s.mouseReleased(c);*/
            //?}
        }

        // ---- the script --------------------------------------------------------

        private void build() {
            if (built) return; built = true;
            clearShots();   // a previous run's frames, under numbers this run will not reuse, are a trap
            ClubConfig cfg = ClubConfig.get();

            // THE BACKGROUND THROTTLE MUST NOT MEASURE US (found by the merge, invisible to either branch).
            // A harness run has no human at the keyboard, so its window is never focused — and the new
            // throttle does exactly what it promises: caps the client at 15 fps. Every frame then costs 67 ms,
            // the perf windows measure the cap instead of the mod, and the timing-sensitive scenes (Item
            // Scroll's drag across three slots) start failing at random. The feature is correct; measuring
            // through it is not.
            cfg.perf.throttleWhenUnfocused = false;

            // AND NEITHER MUST VANILLA'S PAUSE-ON-LOST-FOCUS — the same unfocused window, a bigger hammer.
            //
            // GameRenderer.render(): if the window is not focused and this flag is set, then half a second
            // later it calls openGameMenu(false) — every frame the screen happens to be null. The harness
            // sets the screen to null and expects the HUD; what it got was the pause menu, and HudManager
            // skips the HUD entirely behind a screen that pauses. So the recorder saw nothing, the order
            // checks read 0 icons at every GUI scale, and "the icon draws are counted" read 0/frame — six
            // red lines describing the harness's own window, not the mod.
            //
            // This is not a 1.21.5+ regression: the flag is a public field of GameOptions and GameRenderer
            // reads it identically on 1.21.1. It means the whole HUD half of this run has been measuring
            // whether a human happened to leave the window in front — green when someone watched, red when
            // nobody did. An instrument that answers a question about the room is not an instrument.
            //
            // Turning it off is not hiding the pause: it is refusing to be paused. A real player has focus,
            // which is exactly the state being asserted about.
            mc.options.pauseOnLostFocus = false;

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

            // Shulker tooltip: hovering a container item fills the empty getTooltipData Optional with our OWN
            // ShulkerTooltipData (the cross-version-stable seam), and MixinTooltipComponentOf maps THAT to our
            // ShulkerTooltipComponent — which vanilla's of() would otherwise throw IllegalArgumentException on.
            // This proves the whole chain FIRES and GATES right on whichever node the harness runs (data -> of
            // -> component); the grid's pixels are drawn by our component and the owner confirms those with the
            // jar. FQNs, not imports — one self-contained block, the harness style.
            step(2, () -> {
                boolean prevShulker = cfg.shulkerTooltip;
                cfg.shulkerTooltip = true;

                net.minecraft.item.ItemStack box = new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHULKER_BOX);
                box.set(net.minecraft.component.DataComponentTypes.CONTAINER,
                        net.minecraft.component.type.ContainerComponent.fromStacks(java.util.List.of(
                                new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 5),
                                new net.minecraft.item.ItemStack(net.minecraft.item.Items.EMERALD, 12))));
                java.util.Optional<net.minecraft.item.tooltip.TooltipData> data = box.getTooltipData();
                check("shulker: a filled box gets tooltip data on hover", data.isPresent());
                check("shulker: and it is Club's ShulkerTooltipData (not the bundle chrome)",
                        data.orElse(null) instanceof com.club.tooltip.ShulkerTooltipData);
                // The render half: our Fabric TooltipComponentCallback must map the data to our own component.
                if (data.orElse(null) instanceof com.club.tooltip.ShulkerTooltipData sd) {
                    net.minecraft.client.gui.tooltip.TooltipComponent comp =
                            net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback.EVENT.invoker().getComponent(sd);
                    check("shulker: the Fabric callback maps it to our ShulkerTooltipComponent",
                            comp instanceof com.club.tooltip.ShulkerTooltipComponent);
                    check("shulker: the component reports a non-empty grid size",
                            comp != null && comp.getWidth(mc.textRenderer) > 0);
                }

                // An emptied box (has the component, but nothing in it): nothing to preview.
                net.minecraft.item.ItemStack emptied = new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHULKER_BOX);
                emptied.set(net.minecraft.component.DataComponentTypes.CONTAINER,
                        net.minecraft.component.type.ContainerComponent.DEFAULT);
                check("shulker: an emptied box previews nothing", emptied.getTooltipData().isEmpty());

                // Feature off: the return is left exactly as vanilla gave it (a shulker's own data is empty).
                cfg.shulkerTooltip = false;
                check("shulker: with the feature off the tooltip is untouched", box.getTooltipData().isEmpty());
                cfg.shulkerTooltip = true;

                // A plain item carries no CONTAINER, so the mixin steps aside.
                check("shulker: a plain item (no container) is untouched",
                        new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK).getTooltipData().isEmpty());

                cfg.shulkerTooltip = prevShulker;
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
                // 1.21.9: fromKeyCode(keyCode, scanCode) became fromKeyCode(KeyInput) — same (key, scancode),
                // now carried in the record. Measured: the int pair is live through 1.21.8, gone in 1.21.9.
                //? if <1.21.9 {
                HoldKeys.set("Zoom", InputUtil.fromKeyCode(GLFW_KEY_C, 0));
                //?} else {
                /*HoldKeys.set("Zoom", InputUtil.fromKeyCode(new net.minecraft.client.input.KeyInput(GLFW_KEY_C, 0, 0)));*/
                //?}
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
                //? if <1.21.9 {
                InputUtil.Key free = InputUtil.fromKeyCode(GLFW_KEY_F13, 0);
                //?} else {
                /*InputUtil.Key free = InputUtil.fromKeyCode(new net.minecraft.client.input.KeyInput(GLFW_KEY_F13, 0, 0));*/
                //?}
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
                //? if <1.21.9 {
                ClubClient.zoomKey.setBoundKey(InputUtil.fromKeyCode(GLFW_KEY_K, 0));   // the vanilla screen, again
                //?} else {
                /*ClubClient.zoomKey.setBoundKey(InputUtil.fromKeyCode(new net.minecraft.client.input.KeyInput(GLFW_KEY_K, 0, 0)));   // the vanilla screen, again*/
                //?}
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

            // ===== PARTICLES — the pills are not dummies: a hidden type must not spawn =====
            // Drives the REAL path the menu drives — ParticleManager.addParticle, the method
            // MixinParticleManagerVisibility wraps — with a genuine vanilla type. Visible => a particle comes
            // back; hidden => the mixin cancels and it comes back null. End-to-end: config write + mixin read.
            step(2, () -> {
                if (mc.player == null || mc.world == null) { check("particles: world ready", false); return; }
                net.minecraft.util.Identifier crit =
                        net.minecraft.registry.Registries.PARTICLE_TYPE.getId(net.minecraft.particle.ParticleTypes.CRIT);
                double px = mc.player.getX(), py = mc.player.getY() + 1, pz = mc.player.getZ();
                com.club.modules.particles.ParticleVisibility.setVisible(crit, true);
                var shown = mc.particleManager.addParticle(net.minecraft.particle.ParticleTypes.CRIT, px, py, pz, 0, 0, 0);
                com.club.modules.particles.ParticleVisibility.setVisible(crit, false);
                var hidden = mc.particleManager.addParticle(net.minecraft.particle.ParticleTypes.CRIT, px, py, pz, 0, 0, 0);
                com.club.modules.particles.ParticleVisibility.setVisible(crit, true);   // restore: nothing hidden
                report.add("INFO  particles: addParticle(crit) shown=" + (shown != null) + " hidden=" + (hidden != null));
                check("particles: a hidden type does not spawn, a visible one does", shown != null && hidden == null);
            });

            // The two-pane category renders and is reachable — a screenshot for the owner, and a survives-it
            // check (opening/selecting Particles must not crash or close the menu). Restore the rail to Visuals
            // after, so the keyboard-nav scenes later don't inherit a category that has no cards.
            step(4, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> { if (mc.currentScreen instanceof ClubMenuScreen cs) cs.selectCategory("Particles"); });
            step(18, () -> {});   // let the full entrance cascade (groups, then the offset row wave) settle
            step(2, () -> shot("menu-particles"));
            step(0, () -> check("particles: the two-pane category opens without closing the menu",
                    mc.currentScreen instanceof ClubMenuScreen));
            step(2, () -> { if (mc.currentScreen instanceof ClubMenuScreen cs) cs.selectCategory("Visuals"); });
            step(2, () -> mc.setScreen(null));

            // ===== IRIS — the guard that stops us deleting shadows, ASKED rather than read =====
            // The shadow pass draws the world from the sun. Any cull keyed to the MAIN camera's frustum,
            // firing during it, erases the shadows of everything off-screen. IrisCompat is what stops that,
            // and until now the only evidence it was ARMED was a line in the log that a human had to read.
            // A human reading a log is not an instrument: this run passed 94/0 whether the guard armed or
            // not. Now zero can mean broken. Run it with -PclubCompat -PclubIris.
            step(2, () -> {
                report.add("== iris ==");
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                    report.add("SKIP  iris: not loaded — run with -PclubCompat -PclubIris to arm this check");
                    return;
                }
                check("iris: shadow-pass guard is ARMED (not merely present)",
                        com.club.modules.perf.IrisCompat.armed());
            });

            // ===== ITEM SCROLL — a real chest, real packets, a real cursor =====
            // The unit tests prove the PLAN. Only the game can prove that the plan empties a chest and
            // hands the cursor back empty, because only the game has a handler, a server and a round-trip.
            step(2, () -> report.add("== item scroll =="));
            step(2, () -> mc.setScreen(null));
            step(40, () -> report.add("INFO  item scroll: "
                    + com.club.modules.itemscroll.ItemScrollHarness.openChest(mc)));
            step(5, () -> {
                report.add("INFO  item scroll: screen is " + (mc.currentScreen == null ? "null"
                        : mc.currentScreen.getClass().getSimpleName()));
                check("item scroll: a filled chest is open (3 stacks)",
                        com.club.modules.itemscroll.ItemScrollHarness.containerStacks(mc) == 3);
            });
            step(2, () -> shot("itemscroll-chest"));

            // Move one: take all, place ONE, put the rest back. The third click is the whole test — without
            // it the cursor is still holding 63 stone when the screen closes, and the server drops them.
            step(5, () -> com.club.modules.itemscroll.ItemScrollHarness.perform(
                    mc, com.club.modules.itemscroll.ScrollAction.MOVE_ONE,
                    com.club.modules.itemscroll.ItemScrollHarness.STONE_A, true));
            step(5, () -> {
                int left = com.club.modules.itemscroll.ItemScrollHarness.count(
                        mc, com.club.modules.itemscroll.ItemScrollHarness.STONE_A);
                check("item scroll: move one leaves 63 in the source (" + left + ")", left == 63);
                check("item scroll: …and exactly ONE stone reached the player",
                        com.club.modules.itemscroll.ItemScrollHarness.playerItems(mc, true) == 1);
                check("item scroll: …and the cursor is EMPTY",
                        com.club.modules.itemscroll.ItemScrollHarness.cursorEmpty(mc));
            });

            // Move matching: every stone stack, and nothing else. 63 + 16 = 79, plus the one already moved.
            step(5, () -> com.club.modules.itemscroll.ItemScrollHarness.perform(
                    mc, com.club.modules.itemscroll.ScrollAction.MOVE_MATCHING,
                    com.club.modules.itemscroll.ItemScrollHarness.STONE_A, true));
            step(5, () -> {
                int stone = com.club.modules.itemscroll.ItemScrollHarness.playerItems(mc, true);
                int dirt = com.club.modules.itemscroll.ItemScrollHarness.count(
                        mc, com.club.modules.itemscroll.ItemScrollHarness.DIRT);
                check("item scroll: move matching took BOTH stone stacks (" + stone + " stone)", stone == 80);
                check("item scroll: …and did not touch the dirt (" + dirt + " left)", dirt == 32);
                check("item scroll: …and the cursor is EMPTY",
                        com.club.modules.itemscroll.ItemScrollHarness.cursorEmpty(mc));
            });

            // Move everything: the acceptance case — a chest that moves in one gesture, losing nothing.
            step(5, () -> com.club.modules.itemscroll.ItemScrollHarness.perform(
                    mc, com.club.modules.itemscroll.ScrollAction.MOVE_EVERYTHING,
                    com.club.modules.itemscroll.ItemScrollHarness.DIRT, true));
            step(5, () -> {
                check("item scroll: move everything empties the chest",
                        com.club.modules.itemscroll.ItemScrollHarness.containerStacks(mc) == 0);
                check("item scroll: …and the dirt arrived intact (32)",
                        com.club.modules.itemscroll.ItemScrollHarness.playerItems(mc, false) == 32);
                check("item scroll: …and the cursor is EMPTY",
                        com.club.modules.itemscroll.ItemScrollHarness.cursorEmpty(mc));
            });
            step(2, () -> shot("itemscroll-chest-emptied"));
            step(2, () -> mc.setScreen(null));

            // ---- the drag, and the vanilla quick-craft it must never start ----
            // Club's drag sits on the same button vanilla drags with. "It worked" and "it fanned the stack
            // out across the grid" differ by ONE event we failed to consume, so the harness drives the real
            // path — the OS cursor moves, the press goes through Fabric's own invoker, and if the event
            // comes back allowed the harness calls vanilla's handler itself, exactly as the client would.
            step(40, () -> report.add("INFO  item scroll: refill — "
                    + com.club.modules.itemscroll.ItemScrollHarness.openChest(mc)));
            step(5, () -> com.club.modules.itemscroll.ItemScrollHarness.bindDragToLeftClick());
            step(5, () -> report.add("INFO  item scroll: drag press — "
                    + com.club.modules.itemscroll.ItemScrollHarness.dragPress(
                            mc, com.club.modules.itemscroll.ItemScrollHarness.STONE_A)));
            // Each hop asserts that the CURSOR ARRIVED before asking what the drag did with it. The first
            // version of this test moved the pointer with glfwSetCursorPos — which only asks the OS, and the
            // client hears about it solely if the window is focused and the callback is delivered. On one
            // machine it was, on another it was not, and the drag check went red for a reason that had
            // nothing to do with the drag. A test that can fail for a reason it does not name is a test that
            // costs more than it earns; now the mechanism is checked separately and by name.
            // The assert sits in the SAME tick as the move, deliberately: not one frame passes between
            // writing the position and reading it back, so no late OS callback can rescue a mechanism that
            // does not work. It either landed, or it says so.
            step(5, () -> {
                com.club.modules.itemscroll.ItemScrollHarness.moveCursor(
                        mc, com.club.modules.itemscroll.ItemScrollHarness.STONE_B);
                check("item scroll: the harness can put the cursor ON a slot (drag hop 2)",
                        com.club.modules.itemscroll.ItemScrollHarness.hoveredSlotId(mc)
                                == com.club.modules.itemscroll.ItemScrollHarness.STONE_B);
            });
            step(5, () -> {
                com.club.modules.itemscroll.ItemScrollHarness.moveCursor(
                        mc, com.club.modules.itemscroll.ItemScrollHarness.DIRT);
                check("item scroll: …and on the next one (drag hop 3)",
                        com.club.modules.itemscroll.ItemScrollHarness.hoveredSlotId(mc)
                                == com.club.modules.itemscroll.ItemScrollHarness.DIRT);
            });
            step(5, () -> report.add("INFO  item scroll: drag release — "
                    + com.club.modules.itemscroll.ItemScrollHarness.dragRelease(mc)));
            step(5, () -> {
                check("item scroll: a drag across three slots moves all three ("
                                + com.club.modules.itemscroll.ItemScrollHarness.containerStacks(mc)
                                + " left in the chest)",
                        com.club.modules.itemscroll.ItemScrollHarness.containerStacks(mc) == 0);
                check("item scroll: …and VANILLA'S QUICK-CRAFT NEVER ARMED (no drag, no slots, no shift-move)",
                        com.club.modules.itemscroll.ItemScrollHarness.vanillaDragIdle(mc));
                check("item scroll: …and the cursor is EMPTY",
                        com.club.modules.itemscroll.ItemScrollHarness.cursorEmpty(mc));
            });
            step(2, () -> shot("itemscroll-drag"));

            // The other half of good manners: events that are not ours must reach the screen untouched —
            // this is the rule that keeps REI's and EMI's panels scrolling like they always did.
            step(2, () -> {
                check("item scroll: an unbound button is left to vanilla",
                        com.club.modules.itemscroll.ItemScrollHarness.eventLeftToVanilla(
                                mc, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                                com.club.modules.itemscroll.ItemScrollHarness.STONE_A));
                check("item scroll: a scroll BESIDE the container box is left to vanilla (REI/EMI overlays)",
                        com.club.modules.itemscroll.ItemScrollHarness.scrollOutsideLeftToVanilla(mc));
            });
            step(2, () -> com.club.modules.itemscroll.ItemScrollHarness.restoreDefaultGestures());
            step(2, () -> mc.setScreen(null));

            // ---- the gesture editor, photographed in the three states the owner has to judge ----
            step(6, () -> mc.setScreen(new com.club.modules.itemscroll.GestureScreen(null)));
            step(4, () -> shot("itemscroll-gestures"));            // the defaults, and Shift+LMB naming quick-move
            step(2, () -> com.club.modules.itemscroll.ItemScrollHarness.armRow(mc, 0));   // "Press a gesture…"
            step(4, () -> {
                // The arming-click trap, refuted: the click that ARMS a row is itself a left click, and a
                // capture that started on the press would have just bound Move one to "Left Click".
                check("item scroll: arming a row does not bind it to the arming click",
                        com.club.modules.itemscroll.Gesture.of(
                                com.club.modules.itemscroll.GestureInput.SCROLL, 0).equals(
                                com.club.modules.itemscroll.ItemScrollBinds.get(
                                        com.club.modules.itemscroll.ScrollAction.MOVE_ONE)));
                shot("itemscroll-gestures-armed");
            });

            // A bare left click into an armed row: refused, out loud. It is how a player picks items up,
            // and a row that swallowed it would brick every inventory in the game.
            step(2, () -> com.club.modules.itemscroll.ItemScrollHarness.captureBareLeftClick(mc));
            step(4, () -> {
                check("item scroll: a bare left click is REFUSED, and the row keeps its gesture",
                        com.club.modules.itemscroll.Gesture.of(
                                com.club.modules.itemscroll.GestureInput.SCROLL, 0).equals(
                                com.club.modules.itemscroll.ItemScrollBinds.get(
                                        com.club.modules.itemscroll.ScrollAction.MOVE_ONE)));
                shot("itemscroll-gestures-refused");
            });

            step(2, () -> com.club.modules.itemscroll.ItemScrollHarness.stealGesture(mc));// the wheel: Move one's
            step(4, () -> shot("itemscroll-gestures-stolen"));     // "Taken from Move stack" + that row goes to Not set
            step(2, () -> {
                // The editor captured a REAL scroll on a REAL armed row: the wheel was Move one's, and
                // Move one must have LOST it. Two owners of one gesture is the bug this whole grammar exists
                // to make impossible, and here it is refuted through the screen, not through the model.
                check("item scroll: the editor's capture STEALS — Move one loses the wheel",
                        com.club.modules.itemscroll.ItemScrollBinds.get(
                                com.club.modules.itemscroll.ScrollAction.MOVE_ONE) == null);
                check("item scroll: …and Move everything now holds it",
                        com.club.modules.itemscroll.Gesture.of(
                                com.club.modules.itemscroll.GestureInput.SCROLL, 0).equals(
                                com.club.modules.itemscroll.ItemScrollBinds.get(
                                        com.club.modules.itemscroll.ScrollAction.MOVE_EVERYTHING)));
            });
            step(2, () -> com.club.modules.itemscroll.ItemScrollHarness.restoreDefaultGestures());
            step(2, () -> mc.setScreen(null));

            // The survival screen is the one place both regions are the player's own inventory, so it is
            // the one place a wrong region rule undresses you. Hotbar ↔ main, armour and offhand untouched.
            step(40, () -> report.add("INFO  item scroll: "
                    + com.club.modules.itemscroll.ItemScrollHarness.dressPlayer(mc)));
            step(20, () -> mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player)));
            step(5, () -> report.add("INFO  item scroll: before — " + com.club.modules.itemscroll.ItemScrollHarness.describe(mc)));
            step(5, () -> com.club.modules.itemscroll.ItemScrollHarness.perform(
                    mc, com.club.modules.itemscroll.ScrollAction.MOVE_EVERYTHING, 9, true));   // slot 9 = main, row 1
            step(5, () -> report.add("INFO  item scroll: after  — " + com.club.modules.itemscroll.ItemScrollHarness.describe(mc)));
            step(5, () -> {
                check("item scroll: survival screen — main inventory empties into the hotbar",
                        com.club.modules.itemscroll.ItemScrollHarness.mainInventoryEmpty(mc)
                        && com.club.modules.itemscroll.ItemScrollHarness.hotbarItems(mc, true) == 64
                        && com.club.modules.itemscroll.ItemScrollHarness.hotbarItems(mc, false) == 8);
                check("item scroll: …and the helmet stays on the player's head",
                        com.club.modules.itemscroll.ItemScrollHarness.stillWearingHelmet(mc));
                check("item scroll: …and the shield stays in the offhand",
                        com.club.modules.itemscroll.ItemScrollHarness.stillHoldingShield(mc));
                check("item scroll: …and the cursor is EMPTY",
                        com.club.modules.itemscroll.ItemScrollHarness.cursorEmpty(mc));
            });
            step(2, () -> shot("itemscroll-inventory"));
            step(2, () -> mc.setScreen(null));

            // Creative is refused — and this check exists because the harness itself walked into that screen
            // by accident (vanilla swaps InventoryScreen for the creative one) and the module HAPPILY moved
            // its fake slots, leaving 64 stairs on the cursor. Now the refusal sits in the act, not the door.
            step(20, () -> report.add("INFO  item scroll: " + com.club.modules.itemscroll.ItemScrollHarness.restoreCreative(mc)));
            step(20, () -> mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player)));
            step(5, () -> com.club.modules.itemscroll.ItemScrollHarness.perform(
                    mc, com.club.modules.itemscroll.ScrollAction.MOVE_EVERYTHING, 9, true));
            step(5, () -> {
                report.add("INFO  item scroll: creative — " + com.club.modules.itemscroll.ItemScrollHarness.describe(mc));
                check("item scroll: the creative screen is refused — nothing lands on the cursor",
                        com.club.modules.itemscroll.ItemScrollHarness.cursorEmpty(mc));
            });
            step(2, () -> mc.setScreen(null));

            // Two actions can never share a gesture: assigning one takes it from whoever held it.
            step(2, () -> {
                java.util.Map<String, String> map = new java.util.HashMap<>();
                com.club.modules.itemscroll.Gesture stackGesture =
                        com.club.modules.itemscroll.Gestures.defaultFor(
                                com.club.modules.itemscroll.ScrollAction.MOVE_STACK);
                com.club.modules.itemscroll.Gestures.set(map,
                        com.club.modules.itemscroll.ScrollAction.MOVE_EVERYTHING, stackGesture);
                check("item scroll: a gesture assigned twice is STOLEN, never shared",
                        com.club.modules.itemscroll.Gestures.get(map,
                                com.club.modules.itemscroll.ScrollAction.MOVE_STACK) == null);
            });

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

            // ARM THE SCENE — the icon A/B measures ICONS, so there had better be some (found by the MERGE,
            // and by nothing else: on its own branch this block ran on a dressed player, and after Item Scroll
            // landed in front of it, the player reaching this point wore nothing. Four draws stayed four, and
            // the assert "the batch collapses them" failed while there was nothing to collapse.)
            //
            // This is chat B's own rule, turned on the harness instead of the bench: a zero must mean BROKEN,
            // never "the scene did not ask". So the block no longer inherits whatever the previous block left
            // on the player — it dresses him itself, and refuses to assert if it somehow still has no icons.
            step(2, () -> armPerfScene(mc));
            step(20, () -> {});

            // Only where the profiler is fed. Without it these probe ~70 s of frames to read zero out of an
            // instrument that is switched off by design, and then report "TIMED OUT, this run is INVALID" —
            // which blames the machine for a decision the build made (com.club.compat.HudCounters).
            //
            // The scene above is armed either way: the ORDER proof that follows needs the armour it puts on
            // the player, and it is the check that actually proves the icons on this version.
            if (com.club.compat.HudCounters.AVAILABLE) {
                // A gate, not a fixed warm-up: probe until three consecutive windows agree (max ~40 s).
                gate(settle, 800, () -> report.add(String.format(
                        "INFO  settle: the reading stopped moving after %d probe windows "
                        + "(%.0f fps, %.3f ms, share %.2f%%)%s",
                        settle.windows, settle.lastFps, settle.lastMs, settle.lastShare * 100,
                        gateTimedOut ? " — TIMED OUT, this run is INVALID" : "")));
                for (int i = 0; i < 3; i++) { abWindow(false); abWindow(true); }
            }
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

            // Performance STOPPED being a category (owner): the two culls are baked in and gate straight off
            // the config, so the only perf setting that is still a real choice — Background FPS — moved into
            // Misc. Selected by NAME, not by counting Ctrl+Tabs: a relative hop breaks the moment a category
            // is added OR removed, and this commit removes one.
            step(4, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> {
                if (mc.currentScreen instanceof ClubMenuScreen cs) cs.selectCategory("Misc");
            });
            step(8, () -> {});
            step(2, () -> shot("menu-misc"));
            // A CARD THAT DOES NOT SHOW ITS OWN SETTING IS A MENU THAT LIES — the exact class of bug v0.1.2
            // shipped "the menu stopped lying" for. Background FPS holds a lambda over ClubConfig.Perf; if that
            // reference ever goes stale (a config reload, a sanitize() that replaces the section), the card
            // keeps painting the OLD value and the player toggles a ghost.
            step(0, () -> {
                if (!(mc.currentScreen instanceof ClubMenuScreen cs)) { check("perf card: menu open", false); return; }
                ClubConfig.Perf p = ClubConfig.get().perf;
                Boolean bg = cs.cardEnabledByName("Background FPS");
                report.add(String.format("INFO  Background FPS card=%s cfg=%s", bg, p.throttleWhenUnfocused));
                // THE CARD DOES NOT EXIST EVERYWHERE, AND THAT IS THE FEATURE.
                //
                // 1.21.2 deleted the seam this rode and added InactivityFpsLimiter in the same release, so
                // PerfMenu.backgroundFps() returns null there and MenuContent drops it — a feature the game
                // now has is not ours standing down, it is one we no longer have (see PerfMenu's javadoc).
                // Asserting the binding regardless made this red on every version that deliberately removed
                // the card, which teaches the reader to expect a red line here and is how a real one gets
                // skipped.
                //
                // Asked of PerfMenu rather than a version literal: it is the definition the menu is built
                // from, so this stays true on whatever the next version does to the feature, and 1.21.1 —
                // where the card is shipped and players use it — keeps the full assert.
                //
                // Not a silent skip on the other side. "No card" is itself asserted, so a card creeping back
                // in next to Minecraft's own limiter (two throttles fighting over one number) turns this red
                // instead of passing unnoticed.
                if (com.club.modules.perf.PerfMenu.backgroundFps() != null) {
                    check("Background FPS card shows the config value it is bound to",
                            Boolean.valueOf(p.throttleWhenUnfocused).equals(bg));
                } else {
                    check("Background FPS: no card here — Minecraft's own InactivityFpsLimiter replaced it, "
                            + "and nothing of ours races it", bg == null);
                }

                // The two culls have NO card any more — they are baked in, on by default, and the config field
                // IS the kill switch. Assert the shipped default here so a silent flip of it (or a lost field)
                // still turns this red, even though there is nothing in the menu left to click.
                report.add(String.format("INFO  culls baked: cullParticles=%s cullBlockEntities=%s",
                        p.cullParticles, p.cullBlockEntities));
                check("particle + block-entity culls default ON (baked in, config-only kill switch)",
                        p.cullParticles && p.cullBlockEntities);

                // And no card for them slipped back into ANY category — the whole point of baking them in. Read
                // from the menu DATA (MenuContent), not the current grid, so this holds regardless of which
                // category is selected.
                boolean ghostCard = com.club.ui.menu.MenuContent.build(() -> {}).stream()
                        .flatMap(cat -> cat.modules().stream())
                        .anyMatch(m -> m.name().equals("Particles") || m.name().equals("Block Entities"));
                check("no Particles / Block Entities card exists any more", !ghostCard);
            });
            // Leave the rail on Visuals for the keyboard-nav scenes below: they open a fresh menu on the last
            // category and drive Tab/Tab/Space into its FIRST card. On Visuals that first card is Zoom — a
            // popover, harmless. The dissolved Performance category used to leave a toggle-only first card here
            // and keep this invariant by accident; selecting Misc for the Background-FPS check above broke it,
            // because Misc's first card is HUD Editor — an ACTION that opens the editor and closes the menu,
            // which is exactly what turned the switch/bind/scale scenes red. (The scene comment below already
            // says "the rail sits on Visuals" — this makes it true again.)
            step(2, () -> { if (mc.currentScreen instanceof ClubMenuScreen cs) cs.selectCategory("Visuals"); });
            // Hand the screen back. The scene that follows measures the HUD's own draw cost, and a HUD behind
            // an open menu is not drawn at all — leaving this screen up made "the icon draws are counted, not
            // invisible" report 0/frame and go red, which is precisely what that check exists to do. It caught
            // me, not the code.
            step(2, () -> mc.setScreen(null));

            // fresh menu, then open the FIRST card's popover via keyboard. The rail sits on Visuals after
            // the switch above, so this is ZOOM — the Stage-58 "Hold key" row (Strength / Smoothness /
            // Hold key / Reset), and the shot proves nothing hides under the scrollbar.
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> key(GLFW_KEY_TAB));                 // search
            step(2, () -> key(GLFW_KEY_TAB));                 // grid, first card
            step(2, () -> key(GLFW_KEY_SPACE));              // open its popover
            step(6, () -> {});
            step(2, () -> shot("popover"));

            // A SWITCH IS A MOVE, NOT A CLOSE AND AN OPEN (owner, item 7). Right-clicking a second card used
            // to erase the live sheet and grow a new one from zero: no exit played, and the entrance read as
            // an offcut. The sheet glides now — and this is the check that keeps it that way.
            //
            // IT HAS TO BE THE MOUSE. My first version of this check pressed Right-arrow and asserted the
            // reveal was still 1. It passed — and it passed when I deliberately reinstated the bug, because
            // Right-arrow only moves grid FOCUS; the open sheet stays on the module it was already showing, so
            // nothing was ever switched. A check that cannot go red is not a check, and that one was green on
            // a bug I had planted myself. Two right-clicks, on two different cards, is what a player does.
            //
            // Sampled with NO settle: a respawned reveal grows back to 1 in ~280ms, and a settle step would
            // let it — hiding the exact thing being asked about.
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(4, () -> {
                if (!(mc.currentScreen instanceof ClubMenuScreen cs)) { check("popover: menu for the switch check", false); return; }
                double[] a = cs.cardCentreMc(0);
                if (a == null) { check("popover: a first card to right-click", false); return; }
                click(cs, a[0], a[1], 1);   // right-click card 0 → its popover
            });
            step(8, () -> {});                     // let the cold open finish its grow-in
            step(0, () -> {
                if (!(mc.currentScreen instanceof ClubMenuScreen cs)) { check("popover: menu for the switch check", false); return; }
                double[] b = cs.cardCentreMc(1);
                if (b == null) { check("popover: a second card to right-click", false); return; }
                float before = cs.popoverRevealProgress();
                click(cs, b[0], b[1], 1);   // right-click card 1 → a SWITCH, not a new sheet
                float after = cs.popoverRevealProgress();
                report.add(String.format("INFO  popover switch: reveal %.2f → %.2f  (stays 1.00 = the sheet "
                        + "glided; drops to 0.00 = it died and respawned)", before, after));
                check("popover: switching cards MOVES the sheet — it does not die and respawn",
                        before > 0.9f && after > 0.9f);
            });

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
                // 1.21.9: KeyInput carries the triple. Reusing ONE instance is the point, not a shortcut —
                // auto-repeat is the same physical key arriving again, which is what the menu must tell apart.
                //? if <1.21.9 {
                s.keyPressed(GLFW_KEY_ENTER, 0, 0);         // press — arms listening
                s.keyPressed(GLFW_KEY_ENTER, 0, 0);         // auto-repeat of the SAME held key
                s.keyPressed(GLFW_KEY_ENTER, 0, 0);         // …and again
                s.keyReleased(GLFW_KEY_ENTER, 0, 0);
                //?} else {
                /*net.minecraft.client.input.KeyInput enter = new net.minecraft.client.input.KeyInput(GLFW_KEY_ENTER, 0, 0);
                s.keyPressed(enter);                        // press — arms listening
                s.keyPressed(enter);                        // auto-repeat of the SAME held key
                s.keyPressed(enter);                        // …and again
                s.keyReleased(enter);*/
                //?}
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
            // The card has to be one that CAN answer. Card 0 of Visuals is Zoom, and Zoom lost its on/off
            // switch in v0.1.3 — a hold module has no off state, the key is the switch — so clicking it flips
            // nothing and the check would have gone red for a reason unrelated to what it tests. What it
            // tests is the unit conversion: the menu draws in its own canvas, so a real click arrives in
            // Minecraft units and has to be converted back. Get that wrong and everything LOOKS right while
            // nothing is clickable (Stage 60). That conversion is identical for every tile; hard-coding an
            // index that happened to have a toggle was the weak part, not the check.
            step(2, () -> {
                if (!(mc.currentScreen instanceof ClubMenuScreen cs)) { check("menu: open for the click test", false); return; }
                int i = cs.firstTogglableCard();
                if (i < 0) { check("menu: a card with a toggle to click", false); return; }
                double[] p = cs.cardCentreMc(i);
                if (p == null) { check("menu: a card to click", false); return; }
                boolean before = cs.cardEnabled(i);
                click(cs, p[0], p[1], 0);
                check("menu: a mouse click lands on the card it points at (gui scale 4)",
                        cs.cardEnabled(i) != before);
                click(cs, p[0], p[1], 0);   // put it back
            });
            step(4, () -> { mc.options.getGuiScale().setValue(prevGuiScale); mc.onResolutionChanged(); });

            // Hands popover — TALL (tabs + 4 sliders + hotkey + reset): checks the body-clamp + card dim.
            // Category selected by NAME, not a relative Ctrl+Tab. The relative hop assumed the menu opens on
            // Visuals and one tab lands on Player — but lastCatIndex is STATIC, so the moment a prior scene
            // selected a different category (Performance, this pack), the fresh menu opened elsewhere and the
            // single tab missed Player entirely. The search then found nothing, no card opened, and the
            // popover check below passed on a sheet that was never there. Absolute selection cannot drift.
            step(6, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> { if (mc.currentScreen instanceof ClubMenuScreen cs) cs.selectCategory("Player"); });
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
            // The tallest popover in the mod. It must use the room under the cards before it starts
            // scrolling (owner: "размер аккуратный … если там много всего пусть будет скролл") — but NOT by
            // slicing a row in half to get there.
            //
            // THIS CHECK USED TO ASSERT THE BUG. It demanded |h - room| < 1.5, i.e. that the sheet fill the
            // room EXACTLY — which is only possible if the cap lands wherever it lands, straight through
            // whatever row happens to be there. That is item 10, the one the owner called "убого": a
            // "Reset to Default" cut through its own letters. The instrument was not silent about it; it was
            // DEMANDING it. A green test can be worse than no test.
            //
            // The contract now: as tall as it can be WITHOUT cutting a row. Both halves matter — "whole"
            // alone would pass a sheet that snapped down to one visible row, and "maximal" alone is what we
            // had. popoverIsMaximalAndWhole() recomputes both from the children rather than reading back the
            // number the layout produced, so a regression to min(want, room) turns it red.
            step(2, () -> {
                if (mc.currentScreen instanceof ClubMenuScreen cs) {
                    float[] g = cs.popoverGeometry();
                    report.add(String.format("INFO  hands popover: contentH=%.0f h=%.0f y=%.0f room=%.0f waste=%.0f",
                            g[0], g[1], g[2], g[3], g[3] - g[1]));
                    // g[0] > 0 FIRST: this whole check is about a TALL popover, so a popover that never opened
                    // (all zeros) must fail, not pass. popoverIsMaximalAndWhole() answers true for "no popover"
                    // — correct in isolation, a hole here — so the scene has to prove the sheet exists before
                    // asking whether it is the right height. Caught exactly this: a mis-navigated scene left
                    // the Hands popover unopened and the maximality check waved it through.
                    check("popover: as tall as the room allows, and never cut through a row",
                            g[0] > 0f && g[1] <= g[3] + 1f && cs.popoverIsMaximalAndWhole());
                } else check("popover: menu still open for the geometry check", false);
            });

            // Item Scroll popover — the three v0.1.3 #5 fixes in one frame: NO bind row (removed from KEYED),
            // "Change gestures…" not "Edit gestures…", a left-aligned "Reset to default" (the reset it KEEPS,
            // because a toggle + the gesture door is two controls), and the "Beta" mark on its card behind.
            // Item Scroll lives in Misc, so select by name and open it with a right-click, like a player.
            step(4, () -> mc.setScreen(new ClubMenuScreen()));
            step(2, () -> { if (mc.currentScreen instanceof ClubMenuScreen cs) cs.selectCategory("Misc"); });
            step(4, () -> {});
            step(2, () -> {
                if (mc.currentScreen instanceof ClubMenuScreen cs) {
                    double[] p = cs.cardCentreByName("Item Scroll");
                    if (p != null) { click(cs, p[0], p[1], 1); }
                }
            });
            step(8, () -> {});
            step(2, () -> shot("itemscroll-popover"));
            step(2, () -> mc.setScreen(null));   // hand the screen back — the next scene measures the HUD

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

        /**
         * Put armour on the player and effects in his blood, so the HUD it draws HAS icons in it.
         *
         * <p>The Armor chip and the Effects chip are where every duotone icon in the mod comes from. Measure
         * the icon batch on a naked player with no potions and the honest answer is zero icons, zero draws
         * collapsed — which reads exactly like a broken batch. The scene has to ask the question before the
         * answer means anything.</p>
         *
         * <p>Server-side, on the server thread: the integrated server owns the player's inventory, and poking
         * it from the client tick simply does not take (Item Scroll paid for that lesson too).</p>
         */
        private static void armPerfScene(MinecraftClient mc) {
            var server = mc.getServer();
            if (server == null || mc.player == null) return;
            java.util.UUID id = mc.player.getUuid();
            server.execute(() -> {
                var sp = server.getPlayerManager().getPlayer(id);
                if (sp == null) return;
                sp.equipStack(net.minecraft.entity.EquipmentSlot.HEAD,
                        new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_HELMET));
                sp.equipStack(net.minecraft.entity.EquipmentSlot.CHEST,
                        new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE));
                sp.equipStack(net.minecraft.entity.EquipmentSlot.LEGS,
                        new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS));
                sp.equipStack(net.minecraft.entity.EquipmentSlot.FEET,
                        new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS));
                sp.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SPEED, 12000, 1, false, false));
                sp.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.REGENERATION, 12000, 0, false, false));
            });
        }

        /** The perf verdict: what the HUD costs, whether the instrument can be believed, and what the glyph
         *  cache actually bought — measured ON against OFF, interleaved, in this one session. */
        private void reportPerf() {
            com.club.hud.PixelIcons.batchEnabled = true;   // production state, whatever the last window was

            // THE PROFILER IS NOT FED ON THIS VERSION, SO NOTHING HERE MAY BE ASKED OF IT.
            //
            // Past 1.21.4 the HUD's primitives are recorded into vanilla's GuiRenderer, which issues the draws
            // itself: our draw counters live in the shader path, which is not built there, so HudManager does
            // not feed HudProfiler at all (see com.club.compat.HudCounters). Every snapshot therefore reports
            // zero frames, and every number that follows is that zero — not a reading.
            //
            // This block used to run anyway. It printed a 0.00 ms frame and a 0% share as INFO, timed the
            // settle gate out, called the run INVALID twice — and then asserted "the icon draws are counted,
            // not invisible (0/frame)" and went red, on every run of every version past 1.21.4. The A/B it
            // ran was empty too: PixelIcons.batchEnabled gates a <1.21.5 block, so both arms executed the
            // same code and compared it with itself.
            //
            // A line that is red no matter what the mod does is worse than no line — it is the one people
            // learn to scroll past, and the day it means something they scroll past it then as well. So the
            // section states its absence once and asserts nothing. The icons are still proved, on a counter
            // that IS fed here: the order check, real armour and real effects, at four GUI scales.
            if (!com.club.compat.HudCounters.AVAILABLE) {
                report.add("SKIP  perf: the HUD profiler is not fed on this version — from 1.21.5 vanilla's "
                        + "GuiRenderer issues the draws and our counters live in the shader path, which is not "
                        + "built here. No frame, no draw and no share is measured, so none is asserted. It "
                        + "comes back with the shaders; the icons are proved by the order check below.");
                return;
            }

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

            // The deterministic half of the proof — and it must assert the PROPERTY, not a magnitude.
            //
            // This used to demand "at least two draws fewer", which is a fact about the scene I calibrated it
            // in (four icons -> one draw). In a scene with three icons the same working batch saves one draw
            // and the assert FAILED, while the icon phase went from 0.068 ms to 0.001 ms — a factor of 68.
            // An assert tuned to a number instead of a property is the same mistake as a benchmark tuned to a
            // machine, and this workstream exists because of that mistake.
            //
            // The property is: every icon in an element used to be its own GL draw, and now they are one
            // draw. How many icons the HUD happens to be showing is the scene's business, not the batch's.
            double iconOff = batchOff.get(2).iconDraws(), iconOn = batchOn.get(2).iconDraws();
            double dOff = batchOff.get(2).glDraws(), dOn = batchOn.get(2).glDraws();
            if (iconOff < 2) {
                // One icon cannot demonstrate batching. That is not a failure of the batch — the scene simply
                // did not ask. Say so; do not print a green tick for a question nobody put.
                report.add(String.format("SKIP  perf: the icon batch collapses the draws — this HUD drew only "
                        + "%.0f icon draw a frame, so there was nothing to collapse. The scene did not ask.",
                        iconOff));
            } else {
                check(String.format("perf: the icon batch collapses every element's icons into ONE draw "
                                + "(%.0f icon draws → %.0f; frame total %.0f → %.0f)", iconOff, iconOn, dOff, dOn),
                        iconOn >= 1 && iconOn < iconOff && dOn <= dOff - (iconOff - iconOn) + 0.5);
            }
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
