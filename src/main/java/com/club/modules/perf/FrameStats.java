package com.club.modules.perf;

import java.util.Arrays;

/**
 * A fixed-size sample window with ORDER STATISTICS — the instrument the mod measures itself with.
 *
 * <p>Why not a running mean, which is what the profiler used before: a mean is destroyed by one outlier.
 * A single 100 ms hitch (a chunk mesh upload, a JIT compile, the OS scheduling something else) spread over
 * a 400-frame window adds 0.25 ms to the average — which is the entire magnitude of the number we were
 * quoting. That is how the same build "cost" 0.45 ms one run and 1.22 ms the next: both were true averages
 * of a distribution whose tail belonged to the world, not to us. The median does not move when the tail
 * does, so it is a statement about the mod.
 *
 * <p>The tail is still worth reporting — it is where stutter lives — so p95/max are kept alongside, and
 * the mean is kept precisely so the report can show the two disagreeing.
 *
 * <p>Samples are nanoseconds. The window is a ring: once full, the oldest sample is overwritten, so a long
 * run measures its last {@code capacity} frames rather than growing without bound.
 *
 * <p><b>THIS IS AN INSTRUMENT, NOT AN FPS SOURCE. IT MUST NEVER REACH THE HUD.</b> It is fed only by
 * {@link HudProfiler}, which only the harness and the bench ever arm. There is deliberately no
 * {@code fps()} method here, and adding one would be a mistake: a frame rate derived from these samples
 * (as {@code HudProfiler.Snapshot.fps()} does, {@code 1000/median frame ms}) is a DIFFERENT QUANTITY from
 * the game's counter. The median throws away the slow frames; a frames-per-wall-second counter is forced
 * to include them. So it reads systematically HIGH, and by exactly the sort of margin — a few percent —
 * that produces two numbers on one screen calling each other liars. The player-facing chip
 * ({@code com.club.ui.hud.InfoElement}) reads {@code MinecraftClient.getCurrentFps()} and nothing else, on
 * purpose. Anything measured here is a number about the MOD, for a report, with its definition attached.
 */
public final class FrameStats {
    private final long[] ring;
    private int size, head;

    public FrameStats(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity < 1");
        ring = new long[capacity];
    }

    public void add(long nanos) {
        ring[head] = nanos;
        head = (head + 1) % ring.length;
        if (size < ring.length) size++;
    }

    public void reset() { size = 0; head = 0; }

    public int n() { return size; }

    /** Sorted copy of the live samples. Allocates — never call this from a frame. */
    private long[] sorted() {
        long[] a = Arrays.copyOf(ring, size);   // the live samples are always slots [0, size)
        Arrays.sort(a);
        return a;
    }

    /** Nearest-rank percentile, {@code p} in [0,1]. p=0.5 is the median. 0 when empty. */
    public double percentileMs(double p) {
        if (size == 0) return 0;
        long[] a = sorted();
        int i = (int) Math.ceil(p * a.length) - 1;
        if (i < 0) i = 0;
        if (i >= a.length) i = a.length - 1;
        return a[i] / 1_000_000.0;
    }

    public double medianMs() { return percentileMs(0.5); }

    public double meanMs() {
        if (size == 0) return 0;
        long sum = 0;
        for (int i = 0; i < size; i++) sum += ring[i];
        return sum / 1_000_000.0 / size;
    }

    public double maxMs() {
        if (size == 0) return 0;
        long m = Long.MIN_VALUE;
        for (int i = 0; i < size; i++) m = Math.max(m, ring[i]);
        return m / 1_000_000.0;
    }
}
