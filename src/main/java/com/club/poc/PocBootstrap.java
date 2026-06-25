package com.club.poc;

import com.club.poc.render.PocShaders;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

/**
 * PoC entry point — a SEPARATE client entrypoint, fully gated behind
 * {@code CLUB_POC=1} (no-op otherwise, live client untouched). Registers the two
 * custom shaders and keybinds: J = UI Showcase (←/→ pages), P = Premium Showcase,
 * H = the old Legacy-vs-New compare.
 *
 * With {@code CLUB_POC_CAPTURE=1} it runs the headless capture: each showcase page,
 * a 400%+ zoom pair, and the premium showcase (1x + zoom) into {@code run/screenshots/},
 * then stops.
 */
public final class PocBootstrap implements ClientModInitializer {

    private static boolean enabled() { return flag("CLUB_POC", "club.poc"); }
    private static boolean capture() { return flag("CLUB_POC_CAPTURE", "club.poc.capture"); }
    private static boolean flag(String env, String prop) {
        return "1".equals(System.getenv(env)) || "1".equals(System.getProperty(prop));
    }

    private static final String[] SHOTS = {
        "show_01_typography", "show_02_scaling", "show_03_primitives", "show_04_controls",
        "show_05_hud", "show_06_zoom_pair", "show_07_premium", "show_08_premium_zoom"
    };

    private KeyBinding showKey, premKey, compareKey;
    private UiShowcaseScreen show;
    private PremiumShowcaseScreen prem;
    private int phase = 0, timer = 0, total = 0, idx = 0;

    @Override
    public void onInitializeClient() {
        if (!enabled()) return;
        PocShaders.register();
        showKey = reg("key.club.poc_show", GLFW.GLFW_KEY_J);
        premKey = reg("key.club.poc_prem", GLFW.GLFW_KEY_P);
        compareKey = reg("key.club.poc_compare", GLFW.GLFW_KEY_H);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static KeyBinding reg(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, key, "Club PoC"));
    }

    private void tick(MinecraftClient mc) {
        while (showKey != null && showKey.wasPressed()) mc.setScreen(new UiShowcaseScreen());
        while (premKey != null && premKey.wasPressed()) mc.setScreen(new PremiumShowcaseScreen());
        while (compareKey != null && compareKey.wasPressed()) mc.setScreen(new RenderCompareScreen());
        if (!capture()) return;

        switch (phase) {
            case 0 -> {
                total++;
                if (mc.getOverlay() == null && (mc.currentScreen instanceof TitleScreen || total > 600)) {
                    show = new UiShowcaseScreen();
                    prem = new PremiumShowcaseScreen();
                    idx = 0;
                    applyStep(mc, 0);
                    timer = 0; phase = 1;
                }
            }
            case 1 -> {
                int warmup = idx >= 6 ? 45 : 28;   // premium needs the panorama to settle
                if (++timer > warmup) {
                    shot(mc, SHOTS[idx]);
                    idx++;
                    if (idx >= SHOTS.length) { timer = 0; phase = 2; }
                    else { applyStep(mc, idx); timer = 0; }
                }
            }
            case 2 -> { if (++timer > 10) { phase = 3; mc.scheduleStop(); } }
            default -> { }
        }
    }

    private void applyStep(MinecraftClient mc, int i) {
        float w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        switch (i) {
            case 0, 1, 2, 3, 4 -> { ensureShow(mc); show.setPage(i); }
            case 5 -> { ensureShow(mc); show.setPage(5); show.setZoom(4.5f, w / 2f, h / 2f); }
            case 6 -> { mc.setScreen(prem); }
            case 7 -> { prem.setZoom(2.2f, w / 2f, h / 2f); }
        }
    }

    private void ensureShow(MinecraftClient mc) {
        if (mc.currentScreen != show) mc.setScreen(show);
    }

    private static void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(), t -> {});
    }
}
