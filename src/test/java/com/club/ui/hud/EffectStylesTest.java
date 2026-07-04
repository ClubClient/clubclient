package com.club.ui.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pins EffectStyles.normalize — the pastel-band clamp for unknown/modded effect liquid colors. */
class EffectStylesTest {

    private static int r(int c) { return (c >> 16) & 0xFF; }
    private static int g(int c) { return (c >> 8) & 0xFF; }
    private static int b(int c) { return c & 0xFF; }
    private static float value(int c) { return Math.max(r(c), Math.max(g(c), b(c))) / 255f; }
    private static float sat(int c) {
        int mx = Math.max(r(c), Math.max(g(c), b(c))), mn = Math.min(r(c), Math.min(g(c), b(c)));
        return mx > 0 ? (float) (mx - mn) / mx : 0f;
    }

    @Test void alwaysOpaque() {
        assertEquals(0xFF, EffectStyles.normalize(0x000000) >>> 24);
        assertEquals(0xFF, EffectStyles.normalize(0x3A7FE0) >>> 24);
    }

    @Test void blackInputBecomesTheFloorGray() {
        // max == min branch, value forced up to the 0.72 floor → a neutral gray
        int c = EffectStyles.normalize(0x000000);
        assertEquals(r(c), g(c));
        assertEquals(g(c), b(c), "achromatic input stays gray");
        assertEquals(0.72f, value(c), 0.01f);
    }

    @Test void whiteInputIsCappedAtTheValueCeiling() {
        int c = EffectStyles.normalize(0xFFFFFF);
        assertEquals(r(c), g(c));
        assertEquals(g(c), b(c));
        assertEquals(0.88f, value(c), 0.01f, "value clamped to the 0.88 ceiling");
    }

    @Test void midGrayIsPushedToTheValueFloor() {
        int c = EffectStyles.normalize(0x808080);   // v≈0.50 → up to 0.72
        assertEquals(r(c), g(c));
        assertEquals(g(c), b(c));
        assertEquals(0.72f, value(c), 0.01f);
    }

    @Test void saturationIsClampedToTheBand() {
        // a fully saturated primary must come back at sat ≤ .52 and value in [.72,.88]
        int red = EffectStyles.normalize(0xFF0000);
        assertTrue(sat(red) <= 0.53f, "saturation capped: " + sat(red));
        assertEquals(0.88f, value(red), 0.01f);
        assertEquals(Math.max(g(red), b(red)), Math.min(g(red), b(red)), "red hue: g == b");
        assertTrue(r(red) > g(red), "still reads red");

        int green = EffectStyles.normalize(0x00FF00);
        assertTrue(sat(green) <= 0.53f);
        assertTrue(g(green) > r(green) && g(green) > b(green));
    }

    @Test void hueWrapNegativeBranchStaysInRange() {
        // magenta drives max==r with (g-b) negative → h computes to -60 and must wrap to 300°
        int c = EffectStyles.normalize(0xFF00FF);
        assertEquals(0xFF, c >>> 24);
        assertTrue(r(c) >= 0 && r(c) <= 255 && g(c) >= 0 && g(c) <= 255 && b(c) >= 0 && b(c) <= 255);
        assertEquals(r(c), b(c), "magenta hue preserved: r == b");
        assertTrue(g(c) < r(c), "green is the trough");
        assertEquals(0.88f, value(c), 0.01f);
        assertTrue(sat(c) <= 0.53f);
    }

    @Test void lowValueColorIsLiftedToTheFloor() {
        int c = EffectStyles.normalize(0x000040);   // dark blue, v≈0.25 → lifted to ≥0.72
        assertTrue(value(c) >= 0.71f, "value lifted into the band: " + value(c));
        assertTrue(b(c) >= r(c) && b(c) >= g(c), "stays blue-dominant");
    }
}
