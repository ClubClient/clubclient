package com.club.harness;

import com.club.ClubClient;
import com.club.config.ClubConfig;
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
        private boolean built, finished;

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
        private void key(int k, int mods) { Screen s = mc.currentScreen; if (s != null) s.keyPressed(k, 0, mods); }
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

            // Module keybinds: assign/label, conflict-steal, clear, malformed self-heal (no crash).
            step(2, () -> {
                ModuleBinds.set("Zoom", "key.keyboard.k");
                String lbl = ModuleBinds.label("Zoom");
                check("binds: assign records a displayable bind", lbl != null && !lbl.trim().isEmpty());
                ModuleBinds.set("Fullbright", "key.keyboard.k");
                check("binds: conflict steals the key from the other module", ModuleBinds.label("Zoom") == null);
                check("binds: new holder keeps it", ModuleBinds.label("Fullbright") != null);
                ModuleBinds.set("Zoom", null); ModuleBinds.set("Fullbright", null);
                check("binds: cleared", ModuleBinds.label("Fullbright") == null);
                boolean crashed = false;
                try {
                    ClubConfig.get().moduleBinds.put("Zoom", "key.keyboard.THIS_IS_JUNK");
                    ModuleBinds.tick(mc);
                } catch (Throwable t) { crashed = true; }
                check("binds: malformed config value self-heals (no crash)",
                        !crashed && ClubConfig.get().moduleBinds.get("Zoom") == null);
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

            // fresh menu, then open the FIRST card's popover (Animations — known control order) via keyboard
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
            step(10, () -> shot("hands-popover"));               // settle the grow-in before capture

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
