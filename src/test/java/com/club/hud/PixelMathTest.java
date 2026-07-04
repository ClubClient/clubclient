package com.club.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pins the duotone/quadtone bake math extracted in Stage 28 (the "баганный незерит" regression lived here). */
class PixelMathTest {

    /** Pack an ABGR pixel the way NativeImage.getColor returns it: R low byte, B bits 16..23. */
    private static int abgr(int a, int r, int g, int b) { return (a << 24) | (b << 16) | (g << 8) | r; }

    @Test void lumReadsAbgrChannelsInTheRightOrder() {
        assertEquals(0,   PixelMath.lum(abgr(255, 0, 0, 0)));
        assertEquals(255, PixelMath.lum(abgr(255, 255, 255, 255)));
        // channel weights, and proof R/B aren't swapped
        assertEquals(Math.round(0.299f * 255), PixelMath.lum(abgr(255, 255, 0, 0)));   // pure red
        assertEquals(Math.round(0.587f * 255), PixelMath.lum(abgr(255, 0, 255, 0)));   // pure green
        assertEquals(Math.round(0.114f * 255), PixelMath.lum(abgr(255, 0, 0, 255)));   // pure blue
    }

    @Test void percentileScansTheHistogram() {
        int[] hist = new int[256];
        hist[10] = 100; hist[200] = 100;
        assertEquals(10f,  PixelMath.percentile(hist, 200, 0.10f), 1e-4);
        assertEquals(10f,  PixelMath.percentile(hist, 200, 0.50f), 1e-4);
        assertEquals(200f, PixelMath.percentile(hist, 200, 0.90f), 1e-4);
    }

    @Test void percentileFallsBackToMaxWhenTargetExceedsCounts() {
        int[] hist = new int[256];
        hist[10] = 5;                                             // only 5 counted, total overstated
        assertEquals(255f, PixelMath.percentile(hist, 100, 0.9f), 1e-4);   // target 90 never met → 255
    }

    @Test void flatGuardTripsStrictlyUnderSpan24() {
        assertTrue(PixelMath.flat(100f, 110f));    // span 10 → flat
        assertFalse(PixelMath.flat(100f, 130f));   // span 30 → not flat
        assertFalse(PixelMath.flat(0f, 24f));      // exactly 24 → NOT flat (strict <)
    }

    @Test void quadFactorBandsMatchThePrototype() {
        assertEquals(PixelMath.OUTLINE, PixelMath.quadFactor(0.00f), 1e-6);
        assertEquals(PixelMath.OUTLINE, PixelMath.quadFactor(0.10f), 1e-6);
        assertEquals(PixelMath.SHADOW,  PixelMath.quadFactor(0.20f), 1e-6);
        assertEquals(PixelMath.SHADOW,  PixelMath.quadFactor(0.41f), 1e-6);
        assertEquals(1f,                PixelMath.quadFactor(0.50f), 1e-6);
        assertEquals(1f,                PixelMath.quadFactor(0.74f), 1e-6);   // 0.74 not > 0.74 → base
        assertEquals(PixelMath.LIFT,    PixelMath.quadFactor(0.75f), 1e-6);
    }

    @Test void normLumClampsIntoTheAutoLevelsWindow() {
        assertEquals(0f,   PixelMath.normLum(50,  100, 200), 1e-6);   // below lo
        assertEquals(1f,   PixelMath.normLum(250, 100, 200), 1e-6);   // above hi
        assertEquals(0.5f, PixelMath.normLum(150, 100, 200), 1e-6);
    }

    @Test void maskLevelInvertsTheShaderLift() {
        assertEquals(Math.round(255f / PixelMath.LIFT), PixelMath.maskLevel(1f));   // base stored below white
        assertEquals(255, PixelMath.maskLevel(PixelMath.LIFT));                     // lift band = full white
    }

    @Test void tightCenterUsesInclusiveBounds() {
        assertEquals(8f,   PixelMath.tightCenter(0, 15), 1e-6);   // (0 + 15 + 1) / 2
        assertEquals(4.5f, PixelMath.tightCenter(4, 4),  1e-6);   // single-texel art
    }

    @Test void shaderChannelLiftsAndClamps() {
        assertEquals(0f, PixelMath.shaderChannel(0),   1e-6);
        assertEquals(1f, PixelMath.shaderChannel(255), 1e-6);     // 255/255 * 1.28 → clamps to 1
        assertEquals(100 / 255f * PixelMath.LIFT, PixelMath.shaderChannel(100), 1e-6);
    }
}
