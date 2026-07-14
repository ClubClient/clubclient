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
    public static KeyBinding freelookKey;

    /** Raw-poll edge for the menu key (see the tick below) — a press, not a hold, opens the menu. */
    private static boolean menuKeyWas;

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

        // keybind: Freelook (hold; default LEFT ALT)
        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.club.freelook",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                "key.category.club"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // The key that OPENS the mod is raw-polled, not read through KeyBinding.wasPressed() (Stage 62).
            // Vanilla dispatches one binding per physical key — KEY_TO_BINDINGS is a single-winner map — so
            // a key another mod (or the player, from vanilla's Controls screen) also binds can lose the slot
            // and never fire. Zoom/Freelook already bypass that map (com.club.util.Keys); the menu key must
            // too, or the mod becomes unreachable and the only place to fix the bind is the menu it can no
            // longer open. reconcileMenuKey() then keeps one physical key from driving two CLUB actions.
            com.club.modules.binds.HoldKeys.reconcileMenuKey();
            while (openMenuKey.wasPressed()) { /* drain: the queued press must not also fire below */ }
            boolean menuDown = com.club.util.Keys.held(openMenuKey);
            if (menuDown && !menuKeyWas && client.player != null && client.currentScreen == null)
                client.setScreen(new ClubMenuScreen());
            menuKeyWas = menuDown;

            com.club.modules.togglesprint.ToggleSprintModule.tick(client);
            com.club.modules.freelook.FreelookModule.tick(client);
            com.club.modules.binds.ModuleBinds.tick(client);
        });
        com.club.modules.binds.ModuleBinds.init();

        // [SEAM:init] Module registration. One line per workstream, logic lives in the module's own package.
        com.club.modules.perf.PerfMenu.init();

        // config writes are async (Stage 30) — drain the writer before the JVM goes down
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClubConfig.close());

        // Dev-only self-driving verification harness (inert unless CLUB_HARNESS env var is set).
        if (com.club.harness.ClubHarness.enabled()) {
            ClubMod.LOGGER.info("[Club] verification harness ARMED");
            com.club.harness.ClubHarness.start();
        }

        // Dev-only promo director (CLUB_PROMO): stages the scenes the Modrinth gallery is shot from.
        if (com.club.harness.ClubPromo.enabled()) {
            ClubMod.LOGGER.info("[Club] promo director ARMED");
            com.club.harness.ClubPromo.start();
        }

        // Dev-only benchmark (CLUB_BENCH): a fixed-seed arena, pinned settings, interleaved A/B — and an
        // INVALID verdict rather than a number, whenever the run would be measuring something other than us.
        if (com.club.harness.ClubBench.enabled()) {
            ClubMod.LOGGER.info("[Club] benchmark ARMED");
            com.club.harness.ClubBench.start();
        }

        ClubMod.LOGGER.info("[Club] client initialized");
    }
}
