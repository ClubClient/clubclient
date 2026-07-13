package com.club.harness;

import com.club.config.ClubConfig;
import com.club.hud.HudManager;
import com.club.ui.menu.ClubMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKey;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

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

    /** A staged frame: where to stand, when, which way to look, and what Club is showing. */
    private record Scene(String name, RegistryKey<Biome> biome, long time, float yaw, float pitch,
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

        void tick(MinecraftClient client) {
            if (finished) return;
            if (cursor < 0) {
                if (mc.player == null || mc.world == null || mc.getServer() == null) return;
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

        private void step(int settle, Runnable r) { steps.add(new Step(r, settle)); }
        private void until(int timeout, BooleanSupplier g) { steps.add(new Step(g, timeout)); }

        private void shot(String name) {
            String file = String.format("promo-%02d-%s.png", shotNo++, name);
            ScreenshotRecorder.saveScreenshot(mc.runDirectory, file, mc.getFramebuffer(), t -> {});
            log.add("SHOT  " + file);
        }

        /** Run on the server thread and block this tick's step until it has actually happened. */
        private void onServer(java.util.function.Consumer<MinecraftServer> job) {
            MinecraftServer server = mc.getServer();
            if (server != null) server.execute(() -> job.accept(server));
        }

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
                if (sp != null) sp.changeGameMode(GameMode.SPECTATOR);   // no hotbar, no hand, no gravity
            }));

            // ---- the camera: everything the player's own settings would otherwise dictate ----
            step(10, () -> {
                mc.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
                mc.options.getViewDistance().setValue(20);        // a horizon, not a wall of fog
                mc.options.getEntityShadows().setValue(true);
                mc.options.getFov().setValue(70);
                mc.options.getBobView().setValue(false);
                mc.options.hudHidden = true;                      // F1: no hotbar, no health, no debug text
                HudManager.promo(true);                           // …but the CLUB HUD stays (that's the product)
                cfg.hud.info = false;                             // the FPS chip is not a feature, it's furniture
            });
            step(20, () -> mc.setScreen(null));

            // ---- the frames ----
            // Each is one scene: a place, an hour, a look — and one thing Club is doing there.
            scene(new Scene("hero-peaks", BiomeKeys.JAGGED_PEAKS, 13200L, 140f, -4f, 2,
                    () -> mc.setScreen(new ClubMenuScreen())));            // the menu, over a sunset ridge
            scene(new Scene("world-peaks", BiomeKeys.JAGGED_PEAKS, 13200L, 140f, -4f, 2,
                    () -> mc.setScreen(null)));                            // …and the same frame, clean
            scene(new Scene("hud-cherry", BiomeKeys.CHERRY_GROVE, 23000L, 90f, 0f, 2,
                    () -> mc.setScreen(null)));                            // the HUD, at dawn, in pink
            scene(new Scene("hud-taiga", BiomeKeys.OLD_GROWTH_PINE_TAIGA, 12600L, 45f, -2f, 2,
                    () -> mc.setScreen(null)));
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
                var found = w.locateBiome(e -> e.matchesKey(s.biome()), from, 6400, 32, 64);
                BlockPos at = (found == null) ? from : found.getFirst();
                int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                log.add((found == null ? "MISS  " : "FOUND ") + s.name() + " @ " + at.getX() + "," + y + "," + at.getZ());
                sp.teleport(w, at.getX() + 0.5, y + s.eyeUp(), at.getZ() + 0.5, s.yaw(), s.pitch());
                purge(w);   // the teleport loaded new chunks — and new chunks come with new mobs
            }));
            // A screenshot taken before the terrain is built is a photograph of fog. Wait for the chunk
            // count to STOP growing (Iris also compiles its shaders on the first frames here), then a beat
            // more for the lighting to settle.
            step(40, () -> {});
            until(600, this::worldSettled);
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

        /** True once the client has stopped building chunks for a full second. */
        private boolean worldSettled() {
            if (mc.world == null) return false;
            int n = mc.world.getChunkManager().getLoadedChunkCount();
            if (n == lastChunks && n > 0) stableFor++; else { stableFor = 0; lastChunks = n; }
            return stableFor >= 20;
        }

        /** Everything that isn't the player leaves the frame. Mobs are the fastest way to make a promo shot
         *  look like a bug report. */
        private static void purge(ServerWorld w) {
            for (var e : w.iterateEntities())
                if (!(e instanceof ServerPlayerEntity)) e.discard();
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

    /** Unused hook kept for symmetry with the harness (screens are driven the same way there). */
    @SuppressWarnings("unused")
    private static void key(Screen s, int k) { if (s != null) { s.keyPressed(k, 0, 0); s.keyReleased(k, 0, 0); } }
}
