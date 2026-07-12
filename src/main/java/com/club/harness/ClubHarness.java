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
        Step(Runnable a, int s) { action = a; settle = s; }
    }

    private static final class Harness {
        private final MinecraftClient mc = MinecraftClient.getInstance();
        private final List<String> report = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private int cursor = -1;    // -1 = not started (waiting for world)
        private int wait;
        private int shotNo, passed, failed;
        private int prevGuiScale = 2;   // restored after the squeezed-window scene
        private int fpsOff, fpsOn, offN, onN;
        private boolean built, finished;

        /** Silence every Club HUD element (the mod's entire in-world draw) for the A/B measurement. */
        private void hudOff(boolean off) {
            ClubConfig.Hud h = ClubConfig.get().hud;
            if (off) {
                hudWas = new boolean[] {h.armor, h.potions, h.target, h.info, h.sprint};
                h.armor = h.potions = h.target = h.info = h.sprint = false;
            } else if (hudWas != null) {
                h.armor = hudWas[0]; h.potions = hudWas[1]; h.target = hudWas[2];
                h.info = hudWas[3]; h.sprint = hudWas[4];
            }
        }
        private boolean[] hudWas;

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
            Step s = steps.get(cursor++);
            try { s.action.run(); } catch (Throwable t) { report.add("EXCEPTION in step " + (cursor - 1) + ": " + t); }
            wait = s.settle;
        }

        // ---- helpers -----------------------------------------------------------

        private void step(int settle, Runnable r) { steps.add(new Step(r, settle)); }
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

            // ===== COST OF THE MOD, MEASURED (Stage 59) =====
            // The owner asked whether it holds up under load. Everything the mod draws in-world goes
            // through the HUD callback, so measure frames with it ON vs fully OFF, in the same world, in
            // the same session. Noisy by nature (it's a real client), so this REPORTS rather than asserts
            // — it fails only on a cost big enough that no noise could explain it.
            // Time the DRAW ITSELF, not the frame rate: watching the fps counter mostly measured the world
            // (chunks, mobs, the time of day) and swung the same build between 0.3 and 1.1 ms run to run.
            step(2, () -> mc.setScreen(null));
            step(60, () -> {});                                    // warm up: chunks built, JIT settled
            step(2, () -> com.club.hud.HudManager.profile(true));
            step(120, () -> {});                                   // ~6s of real frames with the full HUD up
            step(2, () -> {
                double ms = com.club.hud.HudManager.avgDrawMs();
                double calls = com.club.hud.HudManager.avgDraws();
                int n = com.club.hud.HudManager.profiledFrames();
                com.club.hud.HudManager.profile(false);   // (read the numbers BEFORE this — it resets them)
                report.add(String.format("INFO  draw cost: the Club HUD takes %.3f ms/frame in %.0f GL draw calls "
                                + "(%.0f us each; mean of %d frames, backend %s)",
                        ms, calls, calls > 0 ? ms * 1000 / calls : 0, n, com.club.ui.Ui.backend()));
                // A REGRESSION guard at the level we actually measure, not a wish. ~1 ms is the honest
                // cost today and it is all draw-call overhead: the renderer submits ONE GL draw per
                // shape (its own "BATCHING SEAM" comment), so 43 chips/capsules/bars = 43 draws at
                // ~23 us each. Batching them into a handful is the known ~5x win — a real change to the
                // shader's vertex format, worth its own stage. Until then this stops it getting WORSE.
                check("perf: the in-world HUD draw stays under 1.5 ms/frame", n > 100 && ms < 1.5);
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
