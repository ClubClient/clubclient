package com.club.modules.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The instrument's own unit test. The bug these guard against is not hypothetical: the profiler this
 * replaces reported a mean, and a mean is exactly the statistic a single hitch can move by more than the
 * whole thing being measured.
 */
class FrameStatsTest {

    private static FrameStats of(int cap, long... samples) {
        FrameStats s = new FrameStats(cap);
        for (long v : samples) s.add(v);
        return s;
    }

    @Test void medianIsTheMiddleValue() {
        FrameStats s = of(16, ms(1), ms(5), ms(2), ms(4), ms(3));
        assertEquals(3.0, s.medianMs(), 1e-9);
        assertEquals(5, s.n());
    }

    @Test void oneHitchMovesTheMeanAndNotTheMedian() {
        // 100 honest 0.40 ms frames and one 100 ms stall — the chunk upload that produced 0.45 vs 1.22 ms.
        FrameStats s = new FrameStats(256);
        for (int i = 0; i < 100; i++) s.add(ms(0.40));
        s.add(ms(100));

        assertEquals(0.40, s.medianMs(), 1e-6, "the median must ignore the world's tail");
        assertTrue(s.meanMs() > 1.3, "the mean must be wrecked by it — that is the point (was " + s.meanMs() + ")");
        assertEquals(100.0, s.maxMs(), 1e-6, "…and the tail must still be visible, not smoothed away");
    }

    @Test void percentilesAreNearestRankAndClamped() {
        FrameStats s = new FrameStats(128);
        for (int i = 1; i <= 100; i++) s.add(ms(i));   // 1..100 ms
        assertEquals(95.0, s.percentileMs(0.95), 1e-6);
        assertEquals(1.0, s.percentileMs(0.0), 1e-6, "p0 clamps to the smallest sample, not to index -1");
        assertEquals(100.0, s.percentileMs(1.0), 1e-6);
    }

    @Test void theRingKeepsTheLastWindowNotTheFirst() {
        FrameStats s = of(3, ms(1), ms(1), ms(1), ms(9), ms(9), ms(9));
        assertEquals(3, s.n(), "capacity is a window, not a cap on how long you may measure");
        assertEquals(9.0, s.medianMs(), 1e-9, "the OLD samples must have been overwritten");
    }

    @Test void anEmptyWindowReportsZeroRatherThanExploding() {
        FrameStats s = new FrameStats(8);
        assertEquals(0, s.n());
        assertEquals(0.0, s.medianMs(), 1e-9);
        assertEquals(0.0, s.meanMs(), 1e-9);
        assertEquals(0.0, s.maxMs(), 1e-9);
    }

    @Test void tailSkewNamesTheDirtyWindow() {
        // A snapshot whose mean sits far above its median is a window that caught a hitch — the report has
        // to be able to SAY that, instead of quietly publishing the mean as the mod's cost.
        var clean = new HudProfiler.Snapshot(400, 0.40, 0.41, 0.5, 0.6, 0, 0, 0, 0, 0, 0, 0, 6.5, 4, 7, 5);
        var dirty = new HudProfiler.Snapshot(400, 0.40, 1.22, 0.9, 100, 0, 0, 0, 0, 0, 0, 0, 6.5, 4, 7, 5);
        assertTrue(clean.tailSkew() < 0.1);
        assertTrue(dirty.tailSkew() > 1.0);
        assertEquals(16.0, clean.glDraws(), 1e-9, "icons are GL draws too — the old counter could not see them");
    }

    @Test void theShareSurvivesTheMachineThatTheMillisecondDoesNot() {
        // The four windows actually measured on the owner's machine, one static scene, same build: the
        // client ran at 76-207 fps depending on what else it was doing, and the HUD's cost in MILLISECONDS
        // moved with it by 2.7x. Its share of the frame did not. This is why the harness asserts the share.
        var slow = new HudProfiler.Snapshot(510, 0.720, 0.755, 1.14, 4.7, 0, 0, 0, 0, 0, 0, 0, 11.76, 4, 7, 4);
        var fast = new HudProfiler.Snapshot(1240, 0.307, 0.348, 0.58, 1.1, 0, 0, 0, 0, 0, 0, 0, 4.84, 4, 7, 4);

        assertTrue(spread(slow.medianMs(), fast.medianMs()) > 1.3, "the millisecond disagrees with itself by >130%");
        assertTrue(spread(slow.share(), fast.share()) < 0.10, "…while the share agrees to within 10%");
        assertEquals(85.0, slow.fps(), 0.5);
        assertEquals(206.6, fast.fps(), 0.5);
    }

    @Test void aWindowWithNoFrameTimeReportsNoShareRatherThanDividingByZero() {
        var s = new HudProfiler.Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0.0, s.share(), 1e-9);
        assertEquals(0.0, s.fps(), 1e-9);
    }

    private static double spread(double x, double y) { return Math.abs(x - y) / Math.min(x, y); }

    private static long ms(double v) { return Math.round(v * 1_000_000.0); }
}
