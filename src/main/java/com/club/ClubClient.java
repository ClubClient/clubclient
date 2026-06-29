package com.club;

import com.club.config.ClubConfig;
import com.club.gui.ClubScreen;
import com.club.gui.sandbox.SandboxBootstrap;
import com.club.gui.sandbox.UiSandboxScreen;
import com.club.ui.devgallery.WidgetGalleryScreen; // [M2.2 DEV GALLERY — TEMPORARY, remove before merge]
import com.club.hud.HudManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ClubClient implements ClientModInitializer {
    public static KeyBinding openMenuKey;
    public static KeyBinding openSandboxKey;
    public static KeyBinding openGalleryKey; // [M2.2 DEV GALLERY — TEMPORARY, remove before merge]

    @Override
    public void onInitializeClient() {
        // load settings
        ClubConfig.load();

        // HUD elements
        HudManager.init();

        // keybind: Open Club Menu (default RIGHT SHIFT), category "Club"
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.club.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.category.club"
        ));

        // keybind: Open UI Sandbox (default K) — isolated component test screen
        openSandboxKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.club.open_sandbox",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.category.club"
        ));

        // [M2.2 DEV GALLERY — TEMPORARY, remove before merge] keybind: Open Widget Gallery (default G)
        openGalleryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.club.open_gallery",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "key.category.club"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new ClubScreen());
                }
            }
            while (openSandboxKey.wasPressed()) {
                client.setScreen(new UiSandboxScreen());
            }
            // [M2.2 DEV GALLERY — TEMPORARY, remove before merge]
            while (openGalleryKey.wasPressed()) {
                client.setScreen(new WidgetGalleryScreen());
            }
        });

        // headless screenshot helper (no-op unless env CLUB_SANDBOX=1)
        SandboxBootstrap.init();

        ClubMod.LOGGER.info("[Club] client initialized");
    }
}