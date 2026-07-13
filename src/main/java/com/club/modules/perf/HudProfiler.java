package com.club.modules.perf;

/**
 * What the Club HUD costs, measured so that the number means something.
 *
 * <p>THE INSTRUMENT WAS THE FIRST BUG. The profiler this replaces reported a MEAN of one accumulator over
 * the whole window, and it produced 0.45 ms on one run and 1.22 ms on the next from identical code — a 2.7x
 * spread that got quoted on a mod page and used to accept a change (Stage 63: 1.20 ms "before", 1.06 ms
 * "after", the difference declared a win). Both numbers were honest averages of a distribution whose tail
 * is the world's, not ours. Two fixes:
 *
 * <ul>
 *   <li><b>Order statistics, not a sum.</b> Per-frame samples land in a {@link FrameStats} ring; the report
 *       leads with the MEDIAN and prints the mean beside it. When the two disagree, the mean is measuring
 *       a hitch — and the report says so instead of averaging it into us.</li>
 *   <li><b>Phases, not one number.</b> A single figure cannot be acted on. The frame is split into the four
 *       things the HUD actually does, so the report says WHERE the time is instead of inviting us to guess
 *       (the recon guessed the target raycast; it is measured here rather than assumed).</li>
 * </ul>
 *
 * <p>SUBMIT is the phase to read with suspicion: CPU wall-time around a GL submit is time the DRIVER spent,
 * which depends on how deep its queue is, which depends on the scene. It is reported, never asserted.
 * The three CPU phases are ours and reproducible.
 *
 * <p>Off, this costs one {@code getstatic} + branch per frame. Armed, it costs five {@code nanoTime} calls.
 */
public final class HudProfiler {
    private HudProfiler() {}

    /** Frames retained. ~14 s at 300 fps — long enough for a stable median, small enough to sort freely. */
    private static final int WINDOW = 4096;

    private static volatile boolean armed;

    private static final FrameStats TOTAL   = new FrameStats(WINDOW);
    private static final FrameStats RAYCAST = new FrameStats(WINDOW);   // TargetHud's world + entity ray
    private static final FrameStats LAYOUT  = new FrameStats(WINDOW);   // element boxes from config
    private static final FrameStats BUILD   = new FrameStats(WINDOW);   // text shaping + vertex build
    private static final FrameStats SUBMIT  = new FrameStats(WINDOW);   // the batched GL flush
    private static final FrameStats FRAME   = new FrameStats(WINDOW);   // the whole frame, HUD callback to HUD callback

    // THE MILLISECOND IS A PROPERTY OF THE MACHINE. THE SHARE OF THE FRAME IS A PROPERTY OF THE MOD.
    // Measured: across four windows in two sessions, on one machine, in one static scene, the HUD's median
    // cost ranged 0.31-0.83 ms — a 2.7x spread, because the client itself ran anywhere from 76 to 207 fps
    // (background load, JIT, chunk meshing). Over the same four windows its SHARE OF THE FRAME was 5.7% to
    // 6.4%. The ms moved with the weather; the share did not. So the frame is timed here too, and the share
    // is the number the harness asserts on — while the ms is printed, because a share needs its context.
    private static long lastFrameStart;

    // Draw calls, counted per frame. `icons` is the one the old counter was blind to: every PixelIcons
    // sprite goes out through vanilla's immediate path (a distinct texture => a distinct RenderLayer =>
    // its own flush), so it is a real GL draw that our backend never sees and never counted. An assert on
    // "GL draws <= N" that cannot see 4-to-9 of them is not an assert, it is a decoration.
    private static long shapeSum, textSum, iconSum;
    private static int frames;

    /** True while a measurement window is open. Callers must gate their {@code nanoTime} calls on this. */
    public static boolean armed() { return armed; }

    /** Open a window (clears everything) or close it (keeps the samples for {@link #snapshot()}). */
    public static void arm(boolean on) {
        if (on) {
            TOTAL.reset(); RAYCAST.reset(); LAYOUT.reset(); BUILD.reset(); SUBMIT.reset(); FRAME.reset();
            shapeSum = textSum = iconSum = 0;
            frames = 0;
            lastFrameStart = 0;
        }
        armed = on;
    }

    /**
     * One frame of the Club HUD. Durations in nanoseconds; counts are that frame's GL draws.
     * {@code frameStartNs} is the nanoTime at the top of our callback — the gap between two of them is the
     * frame the player actually got, which is what our own cost has to be weighed against.
     */
    public static void frame(long frameStartNs,
                             long raycastNs, long layoutNs, long buildNs, long submitNs,
                             int shapeDraws, int textDraws, int iconDraws) {
        if (!armed) return;
        RAYCAST.add(raycastNs);
        LAYOUT.add(layoutNs);
        BUILD.add(buildNs);
        SUBMIT.add(submitNs);
        TOTAL.add(raycastNs + layoutNs + buildNs + submitNs);
        // The first frame of a window has no predecessor, and the gap across the arming boundary would be
        // however long the harness sat between steps — a garbage sample that would flatter the share.
        if (lastFrameStart != 0) FRAME.add(frameStartNs - lastFrameStart);
        lastFrameStart = frameStartNs;
        shapeSum += shapeDraws; textSum += textDraws; iconSum += iconDraws;
        frames++;
    }

    /**
     * One window's worth of measurement. {@code *MedianMs} are the numbers to reason with; {@code meanMs}
     * exists to be compared against {@code medianMs} — a large gap is the report telling you the window
     * caught a hitch, which is the failure the old profiler silently averaged in.
     */
    public record Snapshot(int frames,
                           double medianMs, double meanMs, double p95Ms, double maxMs,
                           double raycastMs, double layoutMs, double buildMs, double submitMs,
                           double frameMs,
                           double shapeDraws, double textDraws, double iconDraws) {
        /** Every GL draw the HUD issues in a frame — our batches AND the icons vanilla draws for us. */
        public double glDraws() { return shapeDraws + textDraws + iconDraws; }
        /** How far the mean is from the median, as a fraction of the median. > ~0.3 = the window is dirty. */
        public double tailSkew() { return medianMs <= 0 ? 0 : (meanMs - medianMs) / medianMs; }
        /** THE NUMBER THAT MEANS SOMETHING: the fraction of a frame the Club HUD costs. */
        public double share() { return frameMs <= 0 ? 0 : medianMs / frameMs; }
        /** The frame rate the window actually ran at — the context a share is meaningless without. */
        public double fps() { return frameMs <= 0 ? 0 : 1000.0 / frameMs; }
    }

    public static Snapshot snapshot() {
        double per = frames == 0 ? 0 : 1.0 / frames;
        return new Snapshot(frames,
                TOTAL.medianMs(), TOTAL.meanMs(), TOTAL.percentileMs(0.95), TOTAL.maxMs(),
                RAYCAST.medianMs(), LAYOUT.medianMs(), BUILD.medianMs(), SUBMIT.medianMs(),
                FRAME.medianMs(),
                shapeSum * per, textSum * per, iconSum * per);
    }
}
