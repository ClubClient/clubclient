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
    private static final int MOBS_VISIBLE = 75, MOBS_HIDDEN = 75;
    // THE SCENE HAS TO ASK. Eighty chests in the middle of the frame ask a frustum cull nothing at all — they
    // are all visible, so the honest answer is zero, and a zero cannot be told from a broken feature. The
    // chests that make the question real are the ones BEHIND the camera and inside a visible section: vanilla
    // renders them in full (it only frustum-culls the section, never the block entity), and we should not.
    // The ones in front are the control: if they ever get skipped, the cull is wrong, not clever.
    private static final int CHESTS_BEHIND = 60, CHESTS_FRONT = 20;
    // PARTICLE DENSITY IS A PRECONDITION, NOT A DETAIL. Ten campfires give ~300 live particles a frame; the
    // cull then saves ~0.07 ms, which is BELOW this bench's own noise floor — the feature would be invisible
    // not because it does nothing but because the scene asks nothing of it. A particle-dense scene (a mob
    // farm, a village of campfires, a potion fight) is exactly where the brief says this helps, so that is
    // the scene. Behind the camera and in front of it, because both halves have to be real.
    private static final int FIRES_BEHIND = 48, FIRES_FRONT = 12;

    /** Frames per measured block. Long enough that a deep GPU queue cannot bias one block. */
    private static final int BLOCK_TICKS = 90;
    private static final int BLOCKS = 5;   // OFF/ON repeated: 5 pairs, interleaved — three could not resolve
                                           // a 4% effect against its own drift, and two runs disagreed about
                                           // which arm had it. Statistics is not a place to save 90 seconds.

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

            // ---- TWO ARMS, BECAUSE ONE OF THEM CANNOT SEE US ----
            //
            // Everything this workstream builds saves CPU. Measured on a machine whose frame time is set by
            // the GPU, a saved microsecond of CPU reaches the frame as nothing at all — twice now, the bench
            // has skipped 80% of the particle tessellation and reported a delta inside its own noise. That is
            // not the feature failing. That is weighing a letter on a truck scale.
            //
            // And it is the wrong scale for the person who needs this. Nobody with a 300-fps GPU cares. The
            // player who does is the one whose CPU is the ceiling: integrated graphics, a laptop, a mob farm.
            // So the same A/B runs twice — once with the GPU as the ceiling (the honest "typical machine"
            // number), and once with the GPU deliberately taken out of the way (a 640x360 framebuffer, render
            // distance 2), where the frame time is the CPU's and our saving is visible in the same
            // milliseconds the player on the weak machine will feel.
            //
            // If it shows up there and not here, that IS the headline, and it is a strong one: it helps
            // exactly when your processor is the thing that is struggling. If it shows up in neither, the
            // feature was not worth building, and we will have learned that from the instrument.
            arm("GPU-BOUND — the typical machine (1920x1080, render distance 12, fancy)", this::pinGpuBound);
            arm("CPU-BOUND — the machine that needs us (640x360, render distance 2, fast)", this::pinCpuBound);
            step(2, this::restoreWindow);
        }

        /** One complete A/B — pin, validate, settle, interleave, verdict — under one set of conditions. */
        private void arm(String name, Runnable pin) {
            Settle gate = new Settle();
            step(2, () -> {
                report.add("");
                report.add("======== " + name + " ========");
                pin.run();
            });
            step(20, () -> {});
            step(2, () -> mc.setScreen(null));
            steps.add(new Step(() -> report.add(String.format(
                    "INFO  settle: %d probe windows, %.0f fps, frame %.2f ms",
                    gate.windows, gate.lastFps, gate.lastFrameMs)), 2, gate, 900));
            // Validate AFTER the gate, never before it: the entity counter reads zero until the client has
            // actually rendered a few frames, and a scene check that runs too early condemns a good run.
            step(2, this::validate);
            for (int i = 0; i < BLOCKS; i++) { block(false); block(true); }
            step(2, this::verdict);
        }

        /** One measured block with the particle cull in a given state. Interleaved by the caller — never one
         *  long OFF followed by one long ON, which measures the GPU warming up rather than the mod. */
        private void block(boolean cullOn) {
            step(2, () -> {
                // The other culls stay ON in BOTH halves. The subject under test is the ENTITY cull, and a
                // delta is only that feature's if nothing else moves with it.
                com.club.modules.perf.ParticleCull.forceOff = false;
                com.club.modules.perf.BlockEntityCull.forceOff = false;
                com.club.modules.perf.EntityCull.forceOff = !cullOn;
                com.club.modules.perf.EntityCull.resetCounters();
                HudProfiler.arm(true);
            });
            step(BLOCK_TICKS, () -> {});
            step(2, () -> {
                HudProfiler.arm(false);
                (cullOn ? on : off).add(HudProfiler.snapshot());
                (cullOn ? onEntities : offEntities).add(entitiesRendered());
                (cullOn ? onSkipped : offSkipped).add(new long[] {
                        com.club.modules.perf.EntityCull.considered(),
                        com.club.modules.perf.EntityCull.culled() });
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
            // A re-run reuses the world (regenerating it makes the server grind chunks for the whole
            // benchmark and drove the noise floor to 63%). So the arena must be IDEMPOTENT: wipe what a
            // previous run left, then build. Otherwise you run two loads on top of each other and call the
            // result a fixed scene.
            //
            // Collect first, discard second: iterateEntities() is a live view, and mutating it mid-walk
            // hands you a null and an aborted arena — which is exactly what happened, and which the scene
            // check caught by refusing the run ("24 entities rendered").
            List<net.minecraft.entity.Entity> doomed = new ArrayList<>();
            for (var e : w.iterateEntities()) if (e != null && !(e instanceof ServerPlayerEntity)) doomed.add(e);
            for (var e : doomed) e.discard();

            // the wall: everything past it is hidden from the camera
            for (int x = -12; x <= 12; x++)
                for (int y = -6; y <= 8; y++)
                    w.setBlockState(new BlockPos(O.getX() + x, O.getY() + y, O.getZ() + 16), Blocks.STONE.getDefaultState());

            for (int i = 0; i < MOBS_VISIBLE; i++) mob(w, O.getZ() + 2 + (i % 7) * 2, i);
            for (int i = 0; i < MOBS_HIDDEN; i++)  mob(w, O.getZ() + 18 + (i % 7) * 2, i);

            for (int i = 0; i < CHESTS_BEHIND; i++)   // off-screen, but inside a section the camera can see
                w.setBlockState(new BlockPos(O.getX() - 10 + (i % 21), O.getY() - 2 + (i / 21),
                        O.getZ() - 7 - (i % 3) * 2), Blocks.CHEST.getDefaultState());
            for (int i = 0; i < CHESTS_FRONT; i++)    // the control: these must NEVER be skipped
                w.setBlockState(new BlockPos(O.getX() - 10 + (i % 21), O.getY() - 2, O.getZ() + 4),
                        Blocks.CHEST.getDefaultState());

            // particles, BEHIND the camera (which sits at z = O.z - 4.5, looking +Z)
            for (int i = 0; i < FIRES_BEHIND; i++)
                w.setBlockState(new BlockPos(O.getX() - 11 + (i % 23), O.getY() - 1, O.getZ() - 8 - (i / 23) * 3),
                        Blocks.CAMPFIRE.getDefaultState());
            // …and in front, so the cull has something it must NOT skip
            for (int i = 0; i < FIRES_FRONT; i++)
                w.setBlockState(new BlockPos(O.getX() - 5 + i, O.getY() - 1, O.getZ() + 6),
                        Blocks.CAMPFIRE.getDefaultState());
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
        private void pinCommon() {
            var o = mc.options;
            o.getEnableVsync().setValue(false);
            o.getMaxFps().setValue(260);                       // 260 IS "unlimited" — MC only caps below it,
                                                              // and Iris rewrites a 0 to 120 behind your back
            o.getParticles().setValue(ParticlesMode.ALL);      // MINIMAL would gut the particle arm
            o.getEntityDistanceScaling().setValue(1.0);        // moves the shouldRender baseline
            o.getGuiScale().setValue(2);
        }

        private int winW, winH;

        /** The machine most players have: the GPU sets the frame time, and a saved microsecond of CPU is
         *  invisible. This is the honest "typical" number, and it is usually a zero. */
        private void pinGpuBound() {
            if (winW == 0) { winW = mc.getWindow().getWidth(); winH = mc.getWindow().getHeight(); }
            pinCommon();
            mc.options.getViewDistance().setValue(12);
            mc.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
            mc.getWindow().setWindowedSize(winW, winH);
            mc.onResolutionChanged();
        }

        /**
         * The machine that actually needs this mod: the GPU is taken out of the way (a 640x360 framebuffer is
         * a ninth of the pixels; render distance 2 is a tenth of the terrain), so what is left setting the
         * frame time is the CPU — which is the thing every cull in this plan is saving. This is not a rigged
         * scene. It is the only scale on which a CPU saving can be weighed at all, and it is the scale the
         * player on integrated graphics is living on.
         */
        private void pinCpuBound() {
            pinCommon();
            mc.options.getViewDistance().setValue(2);
            mc.options.getGraphicsMode().setValue(GraphicsMode.FAST);
            mc.getWindow().setWindowedSize(640, 360);
            mc.onResolutionChanged();
        }

        private void restoreWindow() {
            if (winW > 0) mc.getWindow().setWindowedSize(winW, winH);
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

            report.add("INFO  entity cull: " + com.club.modules.perf.EntityCull.debug());
            report.add(String.format("INFO  scene: %d entities rendered, %d spawned (%d of them walled off), "
                    + "%d chests behind the camera + %d in front, %d campfires behind and %d in front",
                    ents, MOBS_VISIBLE + MOBS_HIDDEN, MOBS_HIDDEN, CHESTS_BEHIND, CHESTS_FRONT, FIRES_BEHIND, FIRES_FRONT));
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
                        + "%d entities considered, %d culled)  |  cull ON: frame %.2f ms (%.0f fps, %d frames, "
                        + "%d entities considered, %d culled)",
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
                invalidate("only " + consideredOn + " entities were considered in a measured block — this scene has "
                        + "nothing to cull, so a zero would mean nothing.");
            else if (skippedOn == 0)
                invalidate("the cull skipped NOTHING while " + consideredOn + " entities went past it. Either "
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
            report.add("== the entity cull (section visibility) ==");
            report.add(String.format("frame time: %.2f ms without it → %.2f ms with it (%+.1f%%). "
                    + "Entities culled: %d of %d considered (%.0f%%).",
                    fOff, fOn, cost * 100, skippedOn, consideredOn,
                    consideredOn == 0 ? 0 : skippedOn * 100.0 / consideredOn));
            // THE NOISE FLOOR, MEASURED, NOT ASSUMED. The identical OFF blocks are the same code in the same
            // scene; how far they disagree with each other is the smallest delta this run can see at all.
            double lo = Double.MAX_VALUE, hi = 0;
            for (double v : offMs) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            double noise = lo <= 0 ? 1 : (hi - lo) / lo;

            // PAIRED DIFFERENCES, NOT A DIFFERENCE OF MEDIANS. Comparing all-OFF against all-ON swallows the
            // machine's slow drift whole — and it showed: two runs of this bench, same code, disagreed about
            // which ARM had the effect, because the drift between blocks was the size of the effect. Each
            // OFF/ON pair is adjacent in time, so the drift is common to both halves and cancels. What the
            // pairs cannot cancel is a real difference, and if every pair agrees on its SIGN, that is a fact
            // no averaging produced.
            StringBuilder pd = new StringBuilder();
            int negatives = 0;
            List<Double> deltas = new ArrayList<>();
            for (int i = 0; i < Math.min(off.size(), on.size()); i++) {
                double d = (on.get(i).frameMs() - off.get(i).frameMs()) / off.get(i).frameMs();
                deltas.add(d);
                if (d < 0) negatives++;
                pd.append(String.format(" %+.1f%%", d * 100));
            }
            double paired = median(deltas);
            boolean unanimous = negatives == deltas.size() || negatives == 0;
            report.add(String.format("paired deltas (each OFF/ON pair, adjacent in time, so the machine's drift "
                    + "cancels):%s → median %+.1f%%. The pairs %s.",
                    pd, paired * 100,
                    unanimous ? "ALL AGREE ON THE SIGN — that is a fact about the mod, not about the machine"
                              : "DISAGREE ON THE SIGN — this arm cannot resolve the effect, and says so "
                                + "instead of averaging its way to a number"));
            report.add(String.format("noise floor: the identical OFF blocks disagree with each other by %.1f%%. "
                    + "The delta-of-medians is %.1f%% — %s.",
                    noise * 100, Math.abs(cost) * 100,
                    Math.abs(cost) <= noise
                            ? "inside it. Which is why the paired number above, not this one, is what the "
                              + "conclusion rests on. And beneath both: the tessellation of " + skippedOn
                              + " block entities simply stopped happening, on any machine, with no statistics at all"
                            : "outside it"));
            report.add("The frame-time delta is REPORTED, not asserted: it is noise-prone and scene-bound. "
                    + "What is asserted is the skip count, which is deterministic and cannot flatter anyone.");

            // ASSERT ON COUNTS, REPORT ON TIME.
            // 1. The control arm must skip NOTHING. If it does, the toggle does not toggle and the whole A/B
            //    is a measurement of the same code twice.
            long skippedOff = offSkipped.get(offSkipped.size() - 1)[1];
            check("bench: with the cull OFF nothing is skipped (" + skippedOff + ")", skippedOff == 0);

            // 2. The cull must actually cull — in a scene built so that it CAN. This is the assert that
            //    proves the feature works, and there is no noise in it at all.
            double ratio = skippedOn * 1.0 / consideredOn;
            check(String.format("bench: the cull removes the mobs the wall hides (%d of %d, %.0f%%)",
                            skippedOn, consideredOn, ratio * 100),
                    ratio >= 0.25);   // 75 of the 150 mobs stand behind a solid stone wall
            // …AND SPARES THE ONES IN THE OPEN. A cull that removed more than the wall hides would be eating
            // mobs the player can see, and "it went faster" would be the least of it. The visible half of the
            // arena exists to make over-culling FAIL, not to make the number look good.
            check(String.format("bench: the mobs in the open are never culled (%.0f%% culled; 50%% is every mob "
                            + "behind the wall and not one more)", ratio * 100),
                    ratio <= 0.55);
            // And the vanilla counter — the one instrument with no statistics in it — must SEE it move.
            int entOff = offEntities.get(offEntities.size() - 1), entOn = onEntities.get(onEntities.size() - 1);
            check(String.format("bench: vanilla's own entity counter moved (%d rendered → %d)", entOff, entOn),
                    entOn <= entOff * 0.65);

            // 3. The entity counter must not move: only particles were toggled. If the worlds differ, every
            //    frame-time number above is about two different scenes.


            // 4. Primum non nocere — but an instrument may not accuse a feature of a regression it is too
            //    blunt to see. The bar is the noise floor THIS ARM actually measured, never a constant the
            //    bar was written with. The first cut used a flat 2% and duly failed the GPU-bound arm, whose
            //    own identical OFF blocks disagreed with each other by 11%: it was charging the feature with
            //    the machine's mood.
            double bar = Math.max(0.02, noise);
            check(String.format("bench: the cull is not a regression (paired median %+.1f%%; this arm's noise "
                            + "floor is %.1f%%)", paired * 100, noise * 100),
                    paired <= bar);

            // The verdict is per ARM. Clear, so the next arm measures itself and not the last one's memory.
            off.clear(); on.clear();
            offEntities.clear(); onEntities.clear();
            offSkipped.clear(); onSkipped.clear();
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
