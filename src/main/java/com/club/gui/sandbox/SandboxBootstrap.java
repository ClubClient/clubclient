package com.club.gui.sandbox;

import com.club.ui.menu.ClubMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;

/**
 * Headless capture helper for the UI Sandbox — gated entirely behind the env var
 * {@code CLUB_SANDBOX=1}. When unset (normal play) it does nothing, so the menu,
 * HUD and the rest of the client behave exactly as before. When set, it opens the
 * sandbox over the title screen, saves one screenshot to {@code run/screenshots/}
 * and stops the client — so a screenshot can be produced without manual input.
 */
public final class SandboxBootstrap {
    private SandboxBootstrap() {}

    private static int phase = 0; // 0 wait-title, 1 wait-shot, 2 wait-stop, 3 done
    private static int timer = 0;
    private static int total = 0;

    public static void init() {
        boolean on = "1".equals(System.getenv("CLUB_SANDBOX")) || "1".equals(System.getProperty("club.sandbox"));
        if (!on) return;
        ClientTickEvents.END_CLIENT_TICK.register(SandboxBootstrap::tick);
    }

    private static void tick(MinecraftClient mc) {
        switch (phase) {
            case 0 -> { // wait for the title screen, then open the real menu
                total++;
                if (mc.getOverlay() == null && (mc.currentScreen instanceof TitleScreen || total > 400)) {
                    mc.setScreen(new ClubMenuScreen());
                    timer = 0; phase = 1;
                }
            }
            case 1 -> { // shot #1 = the in-game menu
                if (++timer > 40) {
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory, mc.getFramebuffer(), t -> {});
                    mc.setScreen(new UiSandboxScreen());
                    timer = 0; phase = 2;
                }
            }
            case 2 -> { // shot #2 = the sandbox (newest file)
                if (++timer > 40) {
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory, mc.getFramebuffer(), t -> {});
                    timer = 0; phase = 3;
                }
            }
            case 3 -> { if (++timer > 10) { phase = 4; mc.scheduleStop(); } }
            default -> { }
        }
    }
}
