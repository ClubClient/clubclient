package com.club.harness;

import com.club.config.ClubConfig;
import com.club.hud.HudManager;
import com.club.ui.hud.HudEditorScreen;
import com.club.ui.menu.ClubMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;

/**
 * The promo director (dev only — {@code CLUB_PROMO=1}, inert otherwise): drives the client to a scenic
 * place, at a chosen hour, with the vanilla HUD gone and the world empty, and captures the frames the
 * Modrinth gallery is built from.
 *
 * <p>Why this exists. The first gallery was shot in the dev world — a flat green plain, a crowd of mobs, the
 * vanilla hotbar and the debug FPS counter in frame — and the owner's verdict was "это просто скрины": not
 * a reason to press Download, and half the pixels were junk that sells nothing. You cannot fix that in a
 * compositor. It has to be fixed in the shot: a real vantage, real light, and nothing in frame that isn't
 * the world or Club.</p>
 *
 * <p>How the scene is staged WITHOUT chat commands: the world may have cheats off, and a promo run must not
 * depend on the owner's world settings. Everything here goes through the integrated server's own API on the
 * server thread — time, weather, difficulty, the entity purge, the teleport — so it works in any world,
 * including one the harness copied. The location is not hard-coded either: {@link ServerWorld#locateBiome}
 * is asked for the real thing (a peaks ridge, a cherry grove), so the shots survive a change of seed.</p>
 *
 * <p>The vanilla HUD goes away through {@code options.hudHidden} — F1, exactly what a player uses for a clean
 * screenshot — which would take the Club HUD down with it (HudManager checks that flag), so the manager gets
 * a promo bypass. Spectator mode does the rest: no hotbar, no health, no hand, and the camera can stand
 * where a player could not.</p>
 */
public final class ClubPromo {
    private ClubPromo() {}

    public static boolean enabled() { return System.getenv("CLUB_PROMO") != null; }

    public static void start() {
        Director d = new Director();
        ClientTickEvents.END_CLIENT_TICK.register(d::tick);
    }

    /**
     * The world the gallery is shot in. NOT the owner's dev world — that one is SUPERFLAT, which is the real
     * reason the first gallery was a green plain with mobs on it: there is nothing else in it to photograph.
     * A promo world is generated from a fixed seed, so a re-shoot is a re-run and not a new set of pictures.
     */
    private static final String WORLD = "club-promo-world";
    private static final long SEED = 4_073_942_105L;

    /** A staged frame: where to stand, when, which way to look, and what Club is showing.
     *  Biomes are a PREFERENCE LIST — terrain generation owes us nothing, so each scene names the vantage it
     *  wants and the ones it will settle for. */
    private record Scene(String name, List<RegistryKey<Biome>> biomes, long time, float yaw, float pitch,
                         int eyeUp, Runnable ui) {}

    private static final class Step {
        final Runnable action; final int settle; final BooleanSupplier until; final int timeout;
        Step(Runnable a, int s) { action = a; settle = s; until = null; timeout = 0; }
        Step(BooleanSupplier u, int t) { action = null; settle = 0; until = u; timeout = t; }
    }

    private static final class Director {
        private final MinecraftClient mc = MinecraftClient.getInstance();
        private final List<Step> steps = new ArrayList<>();
        private final List<String> log = new ArrayList<>();
        private int cursor = -1, wait, waited, shotNo;
        private boolean built, finished;

        private boolean creating;

        void tick(MinecraftClient client) {
            if (finished) return;
            if (cursor < 0) {
                // No world? Make one. The promo does not borrow the player's world — it generates its own,
                // from a fixed seed, so what the gallery shows is reproducible and nobody's save is touched.
                if (mc.world == null || mc.player == null) {
                    if (!creating && mc.currentScreen != null && mc.getOverlay() == null) {
                        creating = true;
                        createWorld();
                    }
                    return;
                }
                if (mc.getServer() == null) return;
                build(); cursor = 0; wait = 40;
                return;
            }
            if (wait-- > 0) return;
            if (cursor >= steps.size()) { finish(); return; }
            Step s = steps.get(cursor);
            if (s.until != null) {                       // a gate: hold here until the world is ready
                boolean ok;
                try { ok = s.until.getAsBoolean(); } catch (Throwable t) { ok = true; }
                if (ok || ++waited > s.timeout) {
                    if (!ok) log.add("WARN  gate timed out after " + waited + " ticks");
                    cursor++; waited = 0;
                }
                return;
            }
            cursor++;
            try { s.action.run(); } catch (Throwable t) { log.add("EXCEPTION: " + t); }
            wait = s.settle;
        }

        /** Generate the promo world and drop into it — the vanilla "create world" path, without the screen. */
        private void createWorld() {
            log.add("CREATE " + WORLD + " (seed " + SEED + ")");
            LevelInfo info = new LevelInfo(WORLD, GameMode.SPECTATOR, false, Difficulty.PEACEFUL,
                    true, new GameRules(), DataConfiguration.SAFE_MODE);
            GeneratorOptions gen = new GeneratorOptions(SEED, true, false);
            mc.createIntegratedServerLoader().createAndStart(WORLD, info, gen,
                    drm -> drm.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT)
                              .createDimensionsRegistryHolder(),
                    mc.currentScreen);   // where creation returns to if it fails
        }

        private void step(int settle, Runnable r) { steps.add(new Step(r, settle)); }
        private void until(int timeout, BooleanSupplier g) { steps.add(new Step(g, timeout)); }

        private void shot(String name) {
            String file = String.format("promo-%02d-%s.png", shotNo++, name);
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), t -> {});
            log.add("SHOT  " + file);
        }

        /** Run a job on the SERVER thread. It does not happen now — it happens when that thread gets to it,
         *  which is why every caller must then wait on {@link #serverIdle()}. The first cut of this director
         *  didn't, and the screenshots came back before the teleport did: four photographs of empty sky. */
        private volatile boolean serverDone = true;

        private void onServer(java.util.function.Consumer<MinecraftServer> job) {
            MinecraftServer server = mc.getServer();
            if (server == null) { serverDone = true; return; }
            serverDone = false;
            server.execute(() -> {
                try { job.accept(server); }
                catch (Throwable t) { log.add("EXCEPTION on server: " + t); }
                finally { serverDone = true; }
            });
        }

        private boolean serverIdle() { return serverDone; }

        private ServerPlayerEntity serverPlayer(MinecraftServer server) {
            return server.getPlayerManager().getPlayer(mc.player.getUuid());
        }

        // ---- the shot list -----------------------------------------------------

        private void build() {
            if (built) return; built = true;
            ClubConfig cfg = ClubConfig.get();

            // ---- the set: an empty, quiet, good-looking world ----
            step(2, () -> onServer(server -> {
                ServerWorld w = server.getOverworld();
                GameRules r = server.getGameRules();
                r.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);   // the sun holds its position for the shot
                r.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
                r.get(GameRules.DO_MOB_SPAWNING).set(false, server);
                server.setDifficulty(Difficulty.PEACEFUL, true);
                w.setWeather(6000, 0, false, false);                     // clear, and staying clear
                purge(w);
                ServerPlayerEntity sp = serverPlayer(server);
                if (sp == null) return;
                sp.changeGameMode(GameMode.SPECTATOR);                   // no hotbar, no hand, no gravity
                // A HUD with nothing in it sells nothing. The player is a spectator — invisible, weightless,
                // and perfectly able to WEAR armour and carry effects, which is all our HUD reads. So the
                // Armor and Effects chips have real, live, slightly-worn data in them, and nobody has to fake
                // a screenshot.
                sp.equipStack(EquipmentSlot.HEAD,  worn(Items.NETHERITE_HELMET, 0.18f));
                sp.equipStack(EquipmentSlot.CHEST, worn(Items.NETHERITE_CHESTPLATE, 0.09f));
                sp.equipStack(EquipmentSlot.LEGS,  worn(Items.DIAMOND_LEGGINGS, 0.34f));
                sp.equipStack(EquipmentSlot.FEET,  worn(Items.DIAMOND_BOOTS, 0.51f));
                // Long enough that the timers read like a session, not a test: the shoot itself takes minutes,
                // and a chip counting down from 0:18 looks like something is about to break. No Night Vision —
                // it fights the shaderpack's lighting, which is the one thing in frame we did not write.
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 9600, 1, false, false));
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 7200, 0, false, false));
                sp.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 6000, 0, false, false));
            }));

            // ---- the language: the gallery is English, whatever this machine runs in ----
            // The dev client is Russian, and it showed: the Zoom popover's new conflict line came out as
            // "Also: Сохранить инструменты" in a frame meant for Modrinth. The name comes from VANILLA's own
            // translation table (deliberately — the player will go to THEIR Controls screen to fix it), so the
            // only fix is to shoot in English.
            step(2, () -> {
                if (!"en_us".equals(mc.getLanguageManager().getLanguage())) {
                    mc.getLanguageManager().setLanguage("en_us");
                    mc.options.language = "en_us";
                    mc.reloadResources();
                    log.add("LANG  forced en_us for the shoot");
                }
            });
            step(120, () -> {});   // the resource reload is asynchronous — let it land before anything is shot

            // Zoom ships on C, and so does vanilla's Save Toolbar Activator — so the popover, correctly, warns
            // about it. That warning is a real thing the OWNER still has to rule on (keep C, or move it), and
            // until he does, a gallery frame is not the place to hold the argument. The vanilla binding is
            // moved out of the way for the shoot only: no options.write() is called, so nothing on disk
            // changes and the next launch is exactly as it was.
            step(2, () -> {
                var toolbar = mc.options.saveToolbarActivatorKey;
                if (toolbar != null && !toolbar.isUnbound()) {
                    toolbar.setBoundKey(net.minecraft.client.util.InputUtil.UNKNOWN_KEY);
                    net.minecraft.client.option.KeyBinding.updateKeysByCode();
                    log.add("STAGE unbound vanilla Save Toolbar Activator (in memory only) — see the C-key question");
                }
            });

            // ---- the camera: everything the player's own settings would otherwise dictate ----
            step(10, () -> {
                mc.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
                mc.options.getViewDistance().setValue(16);        // a horizon, not a wall of fog (and not a
                                                                 // ten-minute wait for chunks that never show)
                mc.options.getEntityShadows().setValue(true);
                mc.options.getFov().setValue(70);
                mc.options.getBobView().setValue(false);
                mc.options.hudHidden = true;                      // F1: no hotbar, no health, no debug text
                HudManager.promo(true);                           // …but the CLUB HUD stays (that's the product)
                cfg.hud.info = false;                             // the FPS chip is not a feature, it's furniture
            });
            step(20, () -> mc.setScreen(null));

            // ---- the frames ----
            // Each is one scene: a place, an hour, a look — and one thing Club is doing there. The camera
            // stands a little above the surface and tips slightly down: a horizon at eye level is a postcard,
            // a horizon a few degrees below you is a vantage.
            List<RegistryKey<Biome>> peaks = List.of(
                    BiomeKeys.JAGGED_PEAKS, BiomeKeys.SNOWY_SLOPES, BiomeKeys.MEADOW, BiomeKeys.WINDSWEPT_HILLS);
            List<RegistryKey<Biome>> pretty = List.of(
                    BiomeKeys.CHERRY_GROVE, BiomeKeys.MEADOW, BiomeKeys.FLOWER_FOREST, BiomeKeys.BIRCH_FOREST);
            List<RegistryKey<Biome>> deep = List.of(
                    BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.TAIGA, BiomeKeys.FOREST);

            // The menu opens on Combat, which holds exactly one card — an empty grid is not the shot. Two taps
            // put it on Visuals, the category that shows what the mod actually is. (The panel is opaque, so
            // the scrim it draws over the world does not matter: the compositor cuts the panel out and stands
            // it on the clean frame below.)
            // The clock matters more than the place. Minecraft's day: 0 = dawn, 6000 = noon, 12000 = the sun
            // touching the horizon, 13000+ = night. The first cut shot the cherry grove at 23200 — an hour
            // before sunrise — and photographed a black field under a moon. Golden hour is ~12200-12600
            // (facing west, yaw ~90-120, so the low sun rakes across the frame instead of blinding it) and
            // ~800-1500 in the morning (facing east, yaw ~270-300).
            scene(new Scene("hero-peaks", peaks, 12250L, 112f, -8f, 18, () -> {
                ClubMenuScreen m = new ClubMenuScreen();
                mc.setScreen(m);
                m.selectCategory("Visuals");    // by NAME — the menu reopens wherever the player last was
            }));
            scene(new Scene("world-peaks", peaks, 12250L, 112f, -8f, 18,
                    () -> mc.setScreen(null)));                            // …and the same vantage, with the HUD
            // The same vantage again with NOTHING of ours in it. The hero frame stands the menu on this one,
            // and a HUD chip left in the plate would sit under the headline looking like a leftover.
            scene(new Scene("plate-peaks", peaks, 12250L, 112f, -8f, 18, () -> {
                mc.setScreen(null);
                hud(false);
            }));
            // …and give it straight back. The first cut of this scene turned the HUD off and never turned it
            // on again, so every frame AFTER the plate — the popover, the HUD shot, the editor — was silently
            // shot with no HUD in it. A scene that changes global state owns putting it back.
            step(2, () -> hud(true));
            // Zoom's popover, open, over the ridge: Hold key / Strength / Smoothness — the rows the frame is
            // about. Reached the way the harness reaches it (Tab into the grid, Space to open), because that
            // path is already proven and a promo run must not invent its own way to drive the menu.
            scene(new Scene("zoom-popover", peaks, 12250L, 112f, -8f, 18, () -> {
                ClubMenuScreen m = new ClubMenuScreen();
                mc.setScreen(m);
                m.selectCategory("Visuals");                // Zoom is the first card there
                tap(m, GLFW_KEY_TAB, 0);                    // → search
                tap(m, GLFW_KEY_TAB, 0);                    // → grid, first card
                tap(m, GLFW_KEY_SPACE, 0);                  // open its popover
            }));

            scene(new Scene("hud-cherry", pretty, 1400L, 250f, -14f, 6,
                    () -> mc.setScreen(null)));                            // the HUD, at first light, in pink
            scene(new Scene("hud-taiga", deep, 12500L, 100f, -16f, 5,
                    () -> mc.setScreen(null)));
            scene(new Scene("editor-taiga", deep, 12500L, 100f, -16f, 5,
                    () -> mc.setScreen(new HudEditorScreen())));           // drag it where you want it
        }

        /** One staged frame: find the biome, stand there, set the hour, wait for the world to actually be
         *  there, aim, show the UI, shoot. */
        private void scene(Scene s) {
            step(2, () -> onServer(server -> {
                ServerWorld w = server.getOverworld();
                ServerPlayerEntity sp = serverPlayer(server);
                if (sp == null) return;
                w.setTimeOfDay(s.time());
                BlockPos from = w.getSpawnPos();
                BlockPos at = null;
                for (RegistryKey<Biome> want : s.biomes()) {                  // first choice, then what we'll accept
                    var found = w.locateBiome(e -> e.matchesKey(want), from, 3000, 32, 64);
                    if (found != null) {
                        at = found.getFirst();
                        log.add("FOUND " + s.name() + " → " + want.getValue().getPath()
                                + " @ " + at.getX() + "," + at.getZ());
                        break;
                    }
                }
                if (at == null) { at = from; log.add("MISS  " + s.name() + " — nothing within 3000 blocks, using spawn"); }
                int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                sp.teleport(w, at.getX() + 0.5, y + s.eyeUp(), at.getZ() + 0.5, s.yaw(), s.pitch());
                purge(w);   // the teleport loaded new chunks — and new chunks come with new mobs
            }));
            // A screenshot taken before the terrain is built is a photograph of fog — which is precisely what
            // the first run produced. Wait for the SERVER to have done the teleport, then for the world to
            // actually be there under the camera, then a beat more for the light to settle (Iris compiles its
            // shaders on the first frames here too).
            // Generous, because the thing being waited on is honest work: a teleport a thousand blocks away
            // makes the server generate terrain it has never seen, and that took longer than the first cut's
            // 20-second gate — so three frames were shot at the PREVIOUS location and nobody noticed until the
            // report was read line by line. A gate that gives up early is worse than no gate.
            until(1200, this::serverIdle);
            step(40, () -> {});
            until(2400, this::worldReady);
            step(20, () -> {
                if (mc.player == null) return;
                mc.player.setYaw(s.yaw()); mc.player.setPitch(s.pitch());
                mc.player.prevYaw = s.yaw(); mc.player.prevPitch = s.pitch();   // no interpolated swing into frame
            });
            step(20, s.ui());
            step(10, () -> {});
            step(2, () -> shot(s.name()));
        }

        private int lastChunks, stableFor;

        /**
         * True once there is a WORLD in front of the camera — not merely a client that has finished loading
         * whatever it happened to have.
         *
         * <p>Chunk-count alone is a liar: right after a teleport 1000 blocks away it sits perfectly stable at
         * the number of chunks the client had BEFORE the teleport, so a "has it stopped changing?" gate passes
         * instantly and the shutter fires on an empty sky. So this asks the question the photograph asks: is
         * the chunk I am standing in real, is there ground under me, and has the client stopped streaming.</p>
         */
        private boolean worldReady() {
            if (mc.world == null || mc.player == null) return false;
            BlockPos p = mc.player.getBlockPos();
            if (!mc.world.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return false;
            boolean ground = false;
            for (int dy = 1; dy <= 48 && !ground; dy++)
                if (!mc.world.getBlockState(p.down(dy)).isAir()) ground = true;
            if (!ground) return false;
            int n = mc.world.getChunkManager().getLoadedChunkCount();
            if (n == lastChunks && n > 0) stableFor++; else { stableFor = 0; lastChunks = n; }
            return stableFor >= 30;
        }

        /** Every Club HUD element, on or off — the plate frame needs a world with nothing of ours in it. */
        private static void hud(boolean on) {
            ClubConfig.Hud h = ClubConfig.get().hud;
            h.armor = h.potions = h.target = h.info = h.sprint = on;
        }

        /** Everything that isn't the player leaves the frame. Mobs are the fastest way to make a promo shot
         *  look like a bug report. */
        private static void purge(ServerWorld w) {
            // iterateEntities() walks live sections and can hand back a null — one NPE here aborted the sweep
            // half-done, which is how a mob survives into a promo frame.
            for (var e : w.iterateEntities())
                if (e != null && !(e instanceof ServerPlayerEntity)) e.discard();
        }

        /** A used piece of armour. Full durability everywhere reads as a debug room; a worn set reads as a
         *  session — and it gives the Armor chip's numbers something to actually say. */
        private static ItemStack worn(net.minecraft.item.Item item, float used) {
            ItemStack st = new ItemStack(item);
            st.setDamage(Math.round(st.getMaxDamage() * used));
            return st;
        }

        private void finish() {
            finished = true;
            HudManager.promo(false);
            log.add("PROMO DONE — " + shotNo + " frames");
            try {
                java.nio.file.Files.writeString(mc.runDirectory.toPath().resolve("club-promo-report.txt"),
                        String.join("\n", log));
            } catch (java.io.IOException ignored) {}
            mc.scheduleStop();
        }
    }

    /** A real tap: press AND release (the menu tracks held keys to tell a repeat from a fresh press). */
    private static void tap(Screen s, int k, int mods) {
        if (s == null) return;
        s.keyPressed(k, 0, mods);
        s.keyReleased(k, 0, mods);
    }
}
