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

    //? if >=1.21.9 {
    /*/^*
     * Club's keybind category, created ONCE.
     *
     * <p>1.21.9 turned a KeyBinding's category from a translation-key STRING into a
     * {@code KeyBinding.Category} object, and {@code Category.create} appends to a static registry and
     * <b>throws {@code IllegalArgumentException("Category '%s' is already registered.")} on a duplicate id</b>
     * (read out of the 1.21.11 bytecode, not assumed). Club registers three keybinds. Creating the category
     * at each construction site would compile perfectly and crash the client on startup, every launch, for
     * everyone — so there is one instance, and {@link #clubKey} is the only thing that reads it.
     *^/
    private static final net.minecraft.client.option.KeyBinding.Category CLUB_CATEGORY =
            net.minecraft.client.option.KeyBinding.Category.create(Identifier.of("club", "club"));*/
    //?}

    /**
     * One of Club's KEYSYM keybinds, in Club's category.
     *
     * <p>The category argument is the whole reason this method exists: a {@code String} translation key
     * through 1.21.8, a {@code KeyBinding.Category} from 1.21.9 on (measured across all eleven mappings
     * 1.21.1..1.21.11 — the same release that renamed {@code getTranslationKey} to {@code getId} and moved
     * {@code isKeyPressed} onto {@code Window}). Folding it here keeps one guard instead of three.
     *
     * <p><b>The label is a different string on either side, and both are shipped.</b> Through 1.21.8 the
     * String IS the translation key, so the menu reads {@code key.category.club}. From 1.21.9 the label is
     * derived — {@code Category.getLabel()} is {@code Text.translatable(id.toTranslationKey("key.category"))},
     * i.e. {@code key.category.<namespace>.<path>} — so {@code club:club} reads {@code key.category.club.club}
     * instead. That is not a guess: 1.21.11's own en_us.json carries {@code key.category.minecraft.movement}
     * next to the legacy {@code key.categories.movement}, which is the rule stated in vanilla's own data. Both
     * keys are therefore present in our lang files; each version reads the one it asks for, and neither can
     * fall back to showing a raw translation key in the Controls screen.
     */
    private static KeyBinding clubKey(String translationKey, int glfwCode) {
        return new KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                glfwCode,
                //? if <1.21.9 {
                "key.category.club"
                //?} else {
                /*CLUB_CATEGORY*/
                //?}
        );
    }

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
        openMenuKey = KeyBindingHelper.registerKeyBinding(clubKey("key.club.open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT));

        // keybind: Zoom (hold; default C, the OptiFine muscle-memory spot)
        zoomKey = KeyBindingHelper.registerKeyBinding(clubKey("key.club.zoom", GLFW.GLFW_KEY_C));

        // keybind: Freelook (hold; default LEFT ALT)
        freelookKey = KeyBindingHelper.registerKeyBinding(clubKey("key.club.freelook", GLFW.GLFW_KEY_LEFT_ALT));

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
        com.club.modules.itemscroll.ItemScrollModule.init();
        // (Perf has no init any more: the two culls are baked in and gate straight off the config, and the
        //  Sodium notice that PerfMenu.init() registered belonged to cards that no longer exist.)

        // Shulker tooltip render half: map the ShulkerTooltipData that MixinItemStackShulkerTooltip produces to
        // our own grid component. This is Fabric's callback, NOT a mixin on TooltipComponent.of — that method is
        // on an INTERFACE, which a class mixin cannot target, and vanilla's of() throws on any unknown data
        // anyway. The event is the sanctioned seam for exactly this, and it exists in fabric-rendering-v1 on all
        // three versions (measured).
        net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback.EVENT.register(data ->
                data instanceof com.club.tooltip.ShulkerTooltipData shulker
                        ? new com.club.tooltip.ShulkerTooltipComponent(shulker.items())
                        : null);

        // config writes are async (Stage 30) — drain the writer before the JVM goes down
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClubConfig.close());

        // ---- dev instruments: harness / promo director / benchmark ------------------------------------
        // Each block TESTS THE ENV VAR FIRST and only then names a com.club.harness class. That order is
        // load-bearing, not style. The release jar does not ship those classes (see the `jar` task in
        // build.gradle: they hold changeGameMode/teleport/setBlockState, and a mod that says "not a cheat
        // client, read the source" should not make a decompiler find those calls in its artifact). The JVM
        // resolves a class the first time it executes an instruction referencing it — so with no
        // CLUB_HARNESS/CLUB_PROMO/CLUB_BENCH set, which is every real player, these branches never run and
        // the classes are never looked up. The previous shape, `if (ClubHarness.enabled())`, resolved the
        // class on EVERY startup: with the classes stripped it would have killed a released client outright.
        //
        // The env names below duplicate the harness classes' own enabled() checks on purpose — reading them
        // from there would mean loading the very class we are avoiding. Keep them in sync by hand.
        //
        // Only LinkageError is swallowed (the "this build has no instrument" case). A harness that throws a
        // real exception in dev must still blow up loudly — that is what it is for.

        // Self-driving verification harness: drives the client, asserts, screenshots, quits.
        if (System.getenv("CLUB_HARNESS") != null) {
            try {
                com.club.harness.ClubHarness.start();
                ClubMod.LOGGER.info("[Club] verification harness ARMED");
            } catch (LinkageError e) { noSuchInstrument("CLUB_HARNESS"); }
        }

        // Promo director (CLUB_PROMO): stages the scenes the Modrinth gallery is shot from.
        if (System.getenv("CLUB_PROMO") != null) {
            try {
                com.club.harness.ClubPromo.start();
                ClubMod.LOGGER.info("[Club] promo director ARMED");
            } catch (LinkageError e) { noSuchInstrument("CLUB_PROMO"); }
        }

        // Benchmark (CLUB_BENCH): a fixed-seed arena, pinned settings, interleaved A/B — and an INVALID
        // verdict rather than a number, whenever the run would be measuring something other than us.
        if (System.getenv("CLUB_BENCH") != null) {
            try {
                com.club.harness.ClubBench.start();
                ClubMod.LOGGER.info("[Club] benchmark ARMED");
            } catch (LinkageError e) { noSuchInstrument("CLUB_BENCH"); }
        }

        ClubMod.LOGGER.info("[Club] client initialized");
    }

    /** Someone set a dev-instrument env var on a RELEASE jar, where those classes do not exist. Say so
     *  plainly and keep booting — poking at an env var must not cost the player their client. */
    private static void noSuchInstrument(String envVar) {
        ClubMod.LOGGER.warn("[Club] {} is set, but this build ships no dev instruments (release jar) — ignoring", envVar);
    }
}
