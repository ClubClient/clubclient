package com.club;

import com.club.config.ClubConfig;
import com.club.ui.menu.ClubMenuScreen;
import com.club.ui.Ui;
import com.club.hud.PixelIcons;
import com.club.hud.HudManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ClubClient implements ClientModInitializer {
    public static KeyBinding openMenuKey;
    public static KeyBinding zoomKey;

    @Override
    public void onInitializeClient() {
        // Register the new UI render stack's core shaders (SDF/MSDF) at client init — this is the
        // ONLY correct point: CoreShaderRegistrationCallback listeners must be present before the
        // game loads core shaders during the startup resource reload. Registering later (e.g. lazily
        // when a screen opens) misses that load, leaving SDF/TEXT null and forcing the LEGACY fallback.
        Ui.init();

        // load settings
        ClubConfig.load();
        // seed the fullbright mirror — its mixin gates on a static flag, never a lazy config lookup
        com.club.modules.fullbright.FullbrightModule.set(ClubConfig.get().fullbright);

        // HUD elements
        HudManager.init();

        // duotone HUD icons bake lazily from live textures — drop the cache when packs change
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override public Identifier getFabricId() { return Identifier.of("club", "pixel_icons"); }
                    @Override public void reload(ResourceManager manager) { PixelIcons.reload(); }
                });

        // keybind: Open Club Menu (default RIGHT SHIFT), category "Club"
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.club.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.category.club"
        ));

        // keybind: Zoom (hold; default C, the OptiFine muscle-memory spot)
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.club.zoom",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "key.category.club"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new ClubMenuScreen());
                }
            }
            com.club.modules.togglesprint.ToggleSprintModule.tick(client);
        });

        // config writes are async (Stage 30) — drain the writer before the JVM goes down
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClubConfig.close());

        ClubMod.LOGGER.info("[Club] client initialized");
    }
}
