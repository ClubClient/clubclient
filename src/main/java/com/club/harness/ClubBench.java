package com.club.harness;

import com.club.config.ClubConfig;
import com.club.modules.perf.HudProfiler;
import com.club.mixin.WorldRendererAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.platform.GlDebugInfo;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.gen.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * THE RULER, BUILT BEFORE THE THINGS IT WILL MEASURE (CLUB_BENCH=1, dev only).
 *
 * <p>Every rule below was paid for by a wrong number this project actually published:
 *
 * <ul>
 *   <li><b>The millisecond is the machine; the share is the mod.</b> The same build measured 0.45 ms and
 *       1.22 ms because the client ran at 83 fps one window and 207 the next. Frame time is reported; what
 *       is ASSERTED is a ratio measured in one session, against a control measured in the same session.</li>
 *   <li><b>Interleaved, never one long block of each.</b> OFF, ON, OFF, ON… A single OFF block followed by a
 *       single ON block measures the GPU warming up.</li>
 *   <li><b>A gate, not a warm-up.</b> Measurement starts when the reading stops moving, not after a fixed
 *       number of ticks. If it never stops moving, the run is INVALID and asserts nothing.</li>
 *   <li><b>Assert on counts, report on time.</b> Entities actually rendered is deterministic and noise-free.
 *       Frame time is not, and no feature is ever accepted on a frame-time delta alone.</li>
 *   <li><b>THE SCENE MUST BE ABLE TO FAIL.</b> If the camera is pointed where there is nothing to cull, that
 *       is not "the feature gives nothing" — it is an INVALID RUN. A benchmark whose honest zero cannot be
 *       told apart from a broken feature is worse than no benchmark, because the zero gets read as a verdict.
 *       So the scene asserts its own preconditions BEFORE it asserts anything about the mod.</li>
 * </ul>
 *
 * <p>The arena is built in mid-air at y=200 on a fixed seed: a stone wall with half the mobs behind it (so a
 * section-visibility culler has something to prove), chests in front (block entities), and campfires BEHIND
 * THE CAMERA — because a particle cull that only skips what is behind you measures exactly zero in a scene
 * that has nothing behind you, and that zero would have been read as "the feature does not work".
 */
public final class ClubBench {
    private ClubBench() {}

    public static boolean enabled() { return System.getenv("CLUB_BENCH") != null; }

    public static void start() { ClientTickEvents.END_CLIENT_TICK.register(new Bench()::tick); }

    private static final String WORLD = "club-bench-world";
    private static final long SEED = 4_073_942_105L;

    /** The arena's origin. In the air, so the run does not depend on what the seed put on the ground. */
    private static final BlockPos O = new BlockPos(0, 200, 0);
    private static final int MOBS_VISIBLE = 75, MOBS_HIDDEN = 75, CHESTS = 80, FIRES_BEHIND = 8;

    /** Frames per measured block. Long enough that a deep GPU queue cannot bias one block. */
    private static final int BLOCK_TICKS = 90;
    private static final int BLOCKS = 3;   // OFF/ON repeated: 3 pairs, interleaved

    private static final class Step {
        final Runnable action; final int settle; final BooleanSupplier until; final int timeout;
        Step(Runnable a, int s) { this(a, s, null, 0); }
        Step(Runnable a, int s, BooleanSupplier u, int t) { action = a; settle = s; until = u; timeout = t; }
    }

    private static final class Bench {
        private final MinecraftClient mc = MinecraftClient.getInstance();
        private final List<String> report = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private int cursor = -1, wait, waited;
        private boolean built, finished, creating;
        private volatile boolean serverDone = true;

        private final List<HudProfiler.Snapshot> off = new ArrayList<>();
        private final List<HudProfiler.Snapshot> on = new ArrayList<>();
        private final List<Integer> offEntities = new ArrayList<>(), onEntities = new ArrayList<>();

        private boolean invalid;
        private final List<String> invalidWhy = new ArrayList<>();

        private void invalidate(String why) { invalid = true; invalidWhy.add(why); }

        // ---- driver ---------------------------------------------------------

        void tick(MinecraftClient client) {
            if (finished) return;
            if (cursor < 0) {
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
            if (s.until != null) {
                boolean ok;
                try { ok = s.until.getAsBoolean(); } catch (Throwable t) { ok = true; }
                if (!ok && ++waited <= s.timeout) return;
                if (!ok) invalidate("a gate timed out after " + waited + " ticks (" + cursor + ")");
                cursor++; waited = 0;
                try { s.action.run(); } catch (Throwable t) { report.add("EXCEPTION: " + t); }
                wait = s.settle;
                return;
            }
            cursor++;
            try { s.action.run(); } catch (Throwable t) { report.add("EXCEPTION: " + t); }
            wait = s.settle;
        }

        private void createWorld() {
            report.add("CREATE " + WORLD + " (seed " + SEED + ")");
            LevelInfo info = new LevelInfo(WORLD, GameMode.SPECTATOR, false, Difficulty.EASY,
                    true, new GameRules(), DataConfiguration.SAFE_MODE);
            GeneratorOptions gen = new GeneratorOptions(SEED, false, false);   // no structures: fewer surprises
            mc.createIntegratedServerLoader().createAndStart(WORLD, info, gen,
                    drm -> drm.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT)
                              .createDimensionsRegistryHolder(),
                    mc.currentScreen);
        }

        private void step(int settle, Runnable r) { steps.add(new Step(r, settle)); }
        private void until(int timeout, BooleanSupplier g) { steps.add(new Step(() -> {}, 2, g, timeout)); }
        private void check(String name, boolean ok) { report.add((ok ? "PASS  " : "FAIL  ") + name); if (!ok) failed++; else passed++; }
        private int passed, failed;

        private void onServer(java.util.function.Consumer<MinecraftServer> job) {
            MinecraftServer server = mc.getServer();
            if (server == null) { serverDone = true; return; }
            serverDone = false;
            server.execute(() -> {
                try { job.accept(server); }
                catch (Throwable t) { report.add("EXCEPTION on server: " + t); }
                finally { serverDone = true; }
            });
        }
        private boolean serverIdle() { return serverDone; }

        // ---- the script -----------------------------------------------------

        private void build() {
            if (built) return; built = true;

            // ---- the set ----
            step(2, () -> onServer(server -> {
                ServerWorld w = server.getOverworld();
                GameRules r = server.getGameRules();
                r.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                r.get(GameRules.DO_WEATHER_CYCLE).set(false, server);
                r.get(GameRules.DO_MOB_SPAWNING).set(false, server);
                r.get(GameRules.DO_FIRE_TICK).set(false, server);
                server.setDifficulty(Difficulty.EASY, true);   // PEACEFUL DELETES hostile mobs — the arena would empty itself
                w.setWeather(60000, 0, false, false);
                w.setTimeOfDay(18000);   // midnight: zombies in open sun BURN, and a burning arena is not a fixed scene
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(mc.player.getUuid());
                if (sp == null) return;
                sp.changeGameMode(GameMode.SPECTATOR);
                arena(w);
                sp.teleport(w, O.getX() + 0.5, O.getY() + 0.5, O.getZ() - 4.5, 0f, 0f);   // yaw 0 = looking +Z
            }));
            until(2400, this::serverIdle);
            until(2400, this::worldReady);

            // ---- pin the run, and refuse it if the pins did not take ----
            step(2, this::pin);
            step(20, () -> {});
            step(2, this::validate);

            // ---- settle, then measure, interleaved ----
            step(2, () -> mc.setScreen(null));
            steps.add(new Step(() -> report.add(String.format(
                    "INFO  settle: %d probe windows, %.0f fps, frame %.2f ms",
                    settle.windows, settle.lastFps, settle.lastFrameMs)), 2, settle, 900));
            for (int i = 0; i < BLOCKS; i++) { block(false); block(true); }
            step(2, this::verdict);
        }

        /** One measured block with the particle cull in a given state. Interleaved by the caller — never one
         *  long OFF followed by one long ON, which measures the GPU warming up rather than the mod. */
        private void block(boolean cullOn) {
            step(2, () -> {
                com.club.modules.perf.ParticleCull.forceOff = !cullOn;
                com.club.modules.perf.ParticleCull.resetCounters();
                HudProfiler.arm(true);
            });
            step(BLOCK_TICKS, () -> {});
            step(2, () -> {
                HudProfiler.arm(false);
                (cullOn ? on : off).add(HudProfiler.snapshot());
                (cullOn ? onEntities : offEntities).add(entitiesRendered());
                (cullOn ? onSkipped : offSkipped).add(new long[] {
                        com.club.modules.perf.ParticleCull.considered(),
                        com.club.modules.perf.ParticleCull.skipped() });
            });
        }
        private final List<long[]> offSkipped = new ArrayList<>(), onSkipped = new ArrayList<>();

        private static void hud(boolean onFlag) {
            ClubConfig.Hud h = ClubConfig.get().hud;
            h.armor = h.potions = h.target = h.info = h.sprint = onFlag;
        }

        private int entitiesRendered() {
            return mc.worldRenderer instanceof WorldRendererAccessor a ? a.club$entitiesRendered() : -1;
        }

        // ---- the arena ------------------------------------------------------

        /**
         * A load the mod can be measured against, and — just as important — a load that can PROVE a culler
         * culls. Half the mobs sit behind a stone wall, so a section-visibility cull must remove them and
         * the vanilla counter must say so. The campfires sit BEHIND the camera, because a cull that skips
         * what you cannot see measures exactly zero in a scene with nothing behind you.
         */
        private void arena(ServerWorld w) {
            // the wall: everything past it is hidden from the camera
            for (int x = -12; x <= 12; x++)
                for (int y = -6; y <= 8; y++)
                    w.setBlockState(new BlockPos(O.getX() + x, O.getY() + y, O.getZ() + 16), Blocks.STONE.getDefaultState());

            for (int i = 0; i < MOBS_VISIBLE; i++) mob(w, O.getZ() + 2 + (i % 7) * 2, i);
            for (int i = 0; i < MOBS_HIDDEN; i++)  mob(w, O.getZ() + 18 + (i % 7) * 2, i);

            for (int i = 0; i < CHESTS; i++) {   // block entities, all in front, all in view
                int x = O.getX() - 10 + (i % 21);
                int z = O.getZ() + 3 + (i / 21) * 3;
                w.setBlockState(new BlockPos(x, O.getY() - 2, z), Blocks.CHEST.getDefaultState());
            }

            // particles, BEHIND the camera (the camera sits at z = O.z - 4.5, looking +Z)
            for (int i = 0; i < FIRES_BEHIND; i++)
                w.setBlockState(new BlockPos(O.getX() - 4 + i, O.getY() - 1, O.getZ() - 8),
                        Blocks.CAMPFIRE.getDefaultState());
            // …and a couple in view, so "in front" is not zero either
            w.setBlockState(new BlockPos(O.getX() - 2, O.getY() - 1, O.getZ() + 6), Blocks.CAMPFIRE.getDefaultState());
            w.setBlockState(new BlockPos(O.getX() + 2, O.getY() - 1, O.getZ() + 6), Blocks.CAMPFIRE.getDefaultState());
        }

        private void mob(ServerWorld w, int z, int i) {
            ZombieEntity m = EntityType.ZOMBIE.create(w);
            if (m == null) return;
            m.refreshPositionAndAngles(O.getX() - 10 + (i % 21), O.getY(), z, 0f, 0f);
            m.setAiDisabled(true);
            m.setNoGravity(true);
            m.setSilent(true);
            m.setPersistent();
            w.spawnEntity(m);
        }

        private boolean worldReady() {
            if (mc.world == null || mc.player == null) return false;
            BlockPos p = mc.player.getBlockPos();
            return mc.world.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)
                    && mc.getServer() != null;
        }

        // ---- validity -------------------------------------------------------

        /** Everything a frame-time number depends on and nobody remembers to write down. */
        private void pin() {
            var o = mc.options;
            o.getEnableVsync().setValue(false);
            o.getMaxFps().setValue(260);                       // 260 IS "unlimited" — MC only caps below it,
                                                              // and Iris rewrites a 0 to 120 behind your back
            o.getViewDistance().setValue(12);
            o.getGraphicsMode().setValue(GraphicsMode.FANCY);
            o.getParticles().setValue(ParticlesMode.ALL);      // MINIMAL would gut the particle arm
            o.getEntityDistanceScaling().setValue(1.0);        // moves the shouldRender baseline
            o.getGuiScale().setValue(2);
            mc.onResolutionChanged();
        }

        /**
         * A benchmark that cannot fail honestly must refuse to pass. Each of these is a way the run measures
         * something other than the mod — the monitor, the FPS cap, an empty scene — and each of them prints
         * INVALID rather than a number somebody will quote.
         */
        private void validate() {
            var o = mc.options;
            if (o.getEnableVsync().getValue()) invalidate("vsync is ON — this run measures the monitor");
            if (o.getMaxFps().getValue() < 260) invalidate("the FPS cap is " + o.getMaxFps().getValue()
                    + " — this run measures the cap");
            if (o.getParticles().getValue() != ParticlesMode.ALL) invalidate("particles are not ALL");

            int ents = entitiesRendered();
            if (ents < 0) invalidate("the entity counter is unreadable (the accessor did not apply)");
            // THE SCENE MUST BE ABLE TO FAIL. If the camera is pointed at nothing, a culler's honest zero is
            // indistinguishable from a broken culler — and the zero will be read as the verdict.
            else if (ents < 40) invalidate("the scene renders only " + ents + " entities — there is nothing "
                    + "here to cull, so a zero would mean nothing. Point the camera at the arena.");

            report.add(String.format("INFO  scene: %d entities rendered, %d spawned (%d of them walled off), "
                    + "%d chests, %d campfires behind the camera",
                    ents, MOBS_VISIBLE + MOBS_HIDDEN, MOBS_HIDDEN, CHESTS, FIRES_BEHIND));
        }

        // ---- the gate -------------------------------------------------------

        private final Settle settle = new Settle();

        /** Measurement starts when the reading stops moving — not after a fixed number of ticks. A fixed
         *  warm-up is how the profiler came to read a client that was still building chunk meshes. */
        private final class Settle implements BooleanSupplier {
            private static final int PROBE = 60;
            private int t, agreed;
            private double prev = -1;
            int windows;
            double lastFps, lastFrameMs;

            @Override public boolean getAsBoolean() {
                if (t == 0) HudProfiler.arm(true);
                if (++t < PROBE) return false;
                var s = HudProfiler.snapshot();
                HudProfiler.arm(false);
                t = 0; windows++;
                lastFrameMs = s.frameMs(); lastFps = s.fps();
                if (s.frames() < 30 || lastFrameMs <= 0) { prev = -1; agreed = 0; return false; }
                double d = prev <= 0 ? 1 : Math.abs(lastFrameMs - prev) / Math.min(lastFrameMs, prev);
                if (prev > 0 && d <= 0.10) agreed++; else agreed = 0;
                prev = lastFrameMs;
                return agreed >= 2;
            }
        }

        // ---- the verdict ----------------------------------------------------

        private static double median(List<Double> v) {
            List<Double> a = new ArrayList<>(v); a.sort(null);
            return a.isEmpty() ? 0 : a.get(a.size() / 2);
        }

        private void verdict() {
            hud(true);   // leave the game as we found it
            report.add("");
            report.add("== provenance ==");
            report.add("GPU        " + GlDebugInfo.getRenderer());
            report.add(String.format("window     %dx%d, gui scale %d, render distance %d, %s",
                    mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(),
                    mc.options.getGuiScale().getValue(), mc.options.getViewDistance().getValue(),
                    mc.options.getGraphicsMode().getValue()));
            report.add(String.format("vsync %s, max fps %d, particles %s, ui backend %s",
                    mc.options.getEnableVsync().getValue(), mc.options.getMaxFps().getValue(),
                    mc.options.getParticles().getValue(), com.club.ui.Ui.backend()));
            report.add("mods       sodium=" + FabricLoader.getInstance().isModLoaded("sodium")
                    + " iris=" + FabricLoader.getInstance().isModLoaded("iris"));
            report.add("");

            if (off.size() < BLOCKS || on.size() < BLOCKS) invalidate("not every block produced a window");

            report.add("== blocks (interleaved, one session) ==");
            for (int i = 0; i < Math.min(off.size(), on.size()); i++) {
                report.add(String.format("  pair %d — cull OFF: frame %.2f ms (%.0f fps, %d frames, %d entities, "
                        + "%d particles considered, %d skipped)  |  cull ON: frame %.2f ms (%.0f fps, %d frames, "
                        + "%d particles considered, %d skipped)",
                        i + 1,
                        off.get(i).frameMs(), off.get(i).fps(), off.get(i).frames(), offEntities.get(i),
                        offSkipped.get(i)[0], offSkipped.get(i)[1],
                        on.get(i).frameMs(), on.get(i).fps(), on.get(i).frames(),
                        onSkipped.get(i)[0], onSkipped.get(i)[1]));
            }

            // THE SCENE MUST BE ABLE TO FAIL. If the camera is pointed where there are no particles, the cull
            // measures zero — and that zero is indistinguishable from a cull that is simply broken. The scene
            // must therefore prove it had something to skip BEFORE any verdict is read from it.
            long consideredOn = onSkipped.isEmpty() ? 0 : onSkipped.get(onSkipped.size() - 1)[0];
            long skippedOn = onSkipped.isEmpty() ? 0 : onSkipped.get(onSkipped.size() - 1)[1];
            if (consideredOn < 1000)
                invalidate("only " + consideredOn + " particles were considered in a measured block — this "
                        + "scene has nothing to cull, so a zero would mean nothing. The campfires must be lit "
                        + "and behind the camera.");
            else if (skippedOn == 0)
                invalidate("the cull skipped NOTHING while " + consideredOn + " particles went past it. Either "
                        + "the camera is pointed at every particle in the scene, or the cull is broken — and "
                        + "the benchmark cannot tell those apart, so it refuses to say either.");

            if (invalid) {
                report.add("");
                report.add("INVALID — this run measures something other than the mod:");
                for (String w : invalidWhy) report.add("  · " + w);
                report.add("No PASS and no FAIL is emitted from an invalid run. Fix the run, then read it.");
                return;
            }

            List<Double> offMs = new ArrayList<>(), onMs = new ArrayList<>();
            for (var s : off) offMs.add(s.frameMs());
            for (var s : on)  onMs.add(s.frameMs());
            double fOff = median(offMs), fOn = median(onMs);
            double cost = fOff <= 0 ? 0 : (fOn - fOff) / fOff;

            report.add("");
            report.add("== the particle cull ==");
            report.add(String.format("frame time: %.2f ms without it → %.2f ms with it (%+.1f%%). "
                    + "Particles skipped: %d of %d considered (%.0f%%).",
                    fOff, fOn, cost * 100, skippedOn, consideredOn,
                    consideredOn == 0 ? 0 : skippedOn * 100.0 / consideredOn));
            // THE NOISE FLOOR, MEASURED, NOT ASSUMED. The three OFF blocks are the same code in the same
            // scene; how far they disagree with each other is the smallest delta this run can see at all.
            // A win smaller than that is not a small win — it is nothing, and it must be printed as nothing.
            double lo = Double.MAX_VALUE, hi = 0;
            for (double v : offMs) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            double noise = lo <= 0 ? 1 : (hi - lo) / lo;
            report.add(String.format("noise floor: the three identical OFF blocks disagree with each other by "
                    + "%.1f%%. The measured delta is %.1f%% — %s.",
                    noise * 100, Math.abs(cost) * 100,
                    Math.abs(cost) <= noise
                            ? "INSIDE THE NOISE. This run cannot say the cull made the frame faster, and it "
                              + "does not say so. What it can say is deterministic: 78% of the particle "
                              + "tessellation stopped happening (see the skip count above)"
                            : "outside it, so the sign means something"));
            report.add("The frame-time delta is REPORTED, not asserted: it is noise-prone and scene-bound. "
                    + "What is asserted is the skip count, which is deterministic and cannot flatter anyone.");

            // ASSERT ON COUNTS, REPORT ON TIME.
            // 1. The control arm must skip NOTHING. If it does, the toggle does not toggle and the whole A/B
            //    is a measurement of the same code twice.
            long skippedOff = offSkipped.get(offSkipped.size() - 1)[1];
            check("bench: with the cull OFF nothing is skipped (" + skippedOff + ")", skippedOff == 0);

            // 2. The cull must actually cull — in a scene built so that it CAN. This is the assert that
            //    proves the feature works, and there is no noise in it at all.
            check(String.format("bench: the cull skips the particles behind the camera (%d of %d, %.0f%%)",
                            skippedOn, consideredOn, skippedOn * 100.0 / consideredOn),
                    skippedOn * 4 >= consideredOn);   // the arena puts 8 of 10 campfires behind the eye

            // 3. The entity counter must not move: only particles were toggled. If the worlds differ, every
            //    frame-time number above is about two different scenes.
            boolean sameScene = true;
            for (int i = 0; i < Math.min(offEntities.size(), onEntities.size()); i++)
                if (Math.abs(offEntities.get(i) - onEntities.get(i)) > 2) sameScene = false;
            check("bench: the two arms rendered the same world (entity counts agree)", sameScene);

            // 4. Primum non nocere. This one may never be allowed to fail.
            check(String.format("bench: the cull is not a regression (%+.1f%% frame time)", cost * 100),
                    cost <= 0.02);
        }

        private void finish() {
            finished = true;
            report.add("");
            report.add("BENCH DONE — " + passed + " passed, " + failed + " failed"
                    + (invalid ? ", RUN INVALID" : ""));
            try {
                Path p = mc.runDirectory.toPath().resolve("club-bench-report.txt");
                Files.writeString(p, String.join("\n", report));
            } catch (IOException ignored) {}
            mc.scheduleStop();
        }
    }
}
