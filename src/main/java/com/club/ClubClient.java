package com.club;

import com.club.config.ClubConfig;
import com.club.gui.ClubScreen;
import com.club.gui.sandbox.SandboxBootstrap;
import com.club.gui.sandbox.UiSandboxScreen;
import com.club.ui.Ui;
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
    public static KeyBinding openDMenuKey;   // [DEV MENU — TEMPORARY, remove before merge]
    public static KeyBinding openHudKey;     // [DEV HUD — TEMPORARY, remove before merge]

    @Override
    public void onInitializeClient() {
        // Register the new UI render stack's core shaders (SDF/MSDF) at client init — this is the
        // ONLY correct point: CoreShaderRegistrationCallback listeners must be present before the
        // game loads core shaders during the startup resource reload. Registering later (e.g. lazily
        // when a screen opens) misses that load, leaving SDF/TEXT null and forcing the LEGACY fallback.
        Ui.init();

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

        // [DEV MENU — TEMPORARY, remove before merge] keybind: Open Club Menu V2 (default H)
        openDMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.club.open_dmenu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "key.category.club"
        ));

        // [DEV HUD — TEMPORARY, remove before merge] keybind: Open HUD Editor (default J)
        openHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.club.open_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
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
            // [DEV MENU — TEMPORARY, remove before merge]
            while (openDMenuKey.wasPressed()) {
                client.setScreen(new com.club.ui.devmenu.ClubMenuScreen());
            }
            // [DEV HUD — TEMPORARY, remove before merge]
            while (openHudKey.wasPressed()) {
                client.setScreen(new com.club.ui.hud.HudEditorScreen());
            }
        });

        // headless screenshot helper (no-op unless env CLUB_SANDBOX=1)
        SandboxBootstrap.init();

        // [M2.2 DEV GALLERY — TEMPORARY, remove before merge] headless gallery capture (no-op unless CLUB_GALLERY=1)
        com.club.gui.sandbox.GalleryBootstrap.init();

        // [DEV MENU — TEMPORARY, remove before merge] headless menu capture (no-op unless CLUB_MENU=1)
        com.club.ui.devmenu.MenuBootstrap.init();

        ClubMod.LOGGER.info("[Club] client initialized");
    }
}