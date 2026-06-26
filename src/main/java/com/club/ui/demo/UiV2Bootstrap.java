package com.club.ui.demo;

import com.club.ui.Ui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

/** Gated entry: CLUB_UI=1 enables shaders + keybind U; CLUB_UI_CAPTURE=1 runs headless capture. */
public final class UiV2Bootstrap implements ClientModInitializer {
    private static boolean on(String e, String p) { return "1".equals(System.getenv(e)) || "1".equals(System.getProperty(p)); }
    private KeyBinding open;
    private int phase, timer, total, idx;
    private UiAcceptanceScreen screen;
    private static final String[] SHOTS = {"uiv2_modern", "uiv2_modern_zoom", "uiv2_legacy", "uiv2_fallback_no_atlas", "uiv2_forced_legacy"};

    @Override public void onInitializeClient() {
        if (!on("CLUB_UI", "club.ui")) return;
        Ui.init();
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.club.ui_open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "Club UI V2"));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient mc) {
        while (open != null && open.wasPressed()) { Ui.setAuto(); mc.setScreen(new UiAcceptanceScreen()); }
        if (!on("CLUB_UI_CAPTURE", "club.ui.capture")) return;
        switch (phase) {
            case 0 -> { total++; if (mc.getOverlay() == null && (mc.currentScreen instanceof TitleScreen || total > 600)) {
                screen = new UiAcceptanceScreen(); Ui.setBackend(Ui.Backend.MODERN); mc.setScreen(screen); phase = 1; timer = 0; idx = 0; } }
            case 1 -> { if (++timer > 30) {
                ScreenshotRecorder.saveScreenshot(mc.runDirectory, SHOTS[idx] + ".png", mc.getFramebuffer(), x -> {});
                idx++;
                if (idx == 1) { screen.setZoom(5f, mc.getWindow().getScaledWidth() / 2f, mc.getWindow().getScaledHeight() / 2f); timer = 0; }
                else if (idx == 2) { screen.setZoom(1f, 0, 0); Ui.setBackend(Ui.Backend.LEGACY); timer = 0; }
                else if (idx == 3) { System.setProperty("club.ui.breakAtlas", "1"); Ui.setAuto(); screen = new UiAcceptanceScreen(); mc.setScreen(screen); timer = 0; }
                else if (idx == 4) { System.clearProperty("club.ui.breakAtlas"); Ui.setBackend(Ui.Backend.LEGACY); timer = 0; }
                else { phase = 2; timer = 0; } } }
            case 2 -> { if (++timer > 10) { phase = 3; mc.scheduleStop(); } }
        }
    }
}
