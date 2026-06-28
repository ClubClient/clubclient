package com.club.ui;

import com.club.ui.text.MsdfMetrics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MsdfMetricsTest {
    static final String JSON = "{\"atlas\":{\"distanceRange\":6,\"size\":40,\"width\":128,\"height\":128,\"yOrigin\":\"bottom\"},"
        + "\"metrics\":{\"emSize\":1,\"lineHeight\":1.2,\"ascender\":0.96,\"descender\":-0.24},"
        + "\"glyphs\":[{\"unicode\":32,\"advance\":0.3},"
        + "{\"unicode\":65,\"advance\":0.7,\"planeBounds\":{\"left\":0,\"bottom\":0,\"right\":0.6,\"top\":0.8},"
        + "\"atlasBounds\":{\"left\":10,\"bottom\":20,\"right\":40,\"top\":60}}]}";

    @Test void parsesAtlas() {
        MsdfMetrics m = MsdfMetrics.parse(JSON);
        assertEquals(128, m.atlasW); assertEquals(6f, m.distanceRange, 1e-6);
    }
    @Test void advanceAndWidth() {
        MsdfMetrics m = MsdfMetrics.parse(JSON);
        assertEquals(0.7f * 20f, m.width("A", 20f), 1e-4);   // single glyph
        assertEquals((0.7f + 0.3f) * 20f, m.width("A ", 20f), 1e-4); // glyph + space
    }
    @Test void glyphUvFlippedFromBottomOrigin() {
        MsdfMetrics.Glyph g = MsdfMetrics.parse(JSON).get(65);
        assertNotNull(g); assertTrue(g.hasBounds);
        assertEquals(10f / 128f, g.u0, 1e-6);
        assertEquals(1f - 60f / 128f, g.v0, 1e-6);  // top atlas bound flipped
    }
    @Test void missingGlyphNull() { assertNull(MsdfMetrics.parse(JSON).get(0x2603)); }
    @Test void lineHeightScales() { assertEquals(1.2f * 16f, MsdfMetrics.parse(JSON).lineHeight(16f), 1e-4); }
    @Test void ascentDescentScaleAndSign() {
        MsdfMetrics m = MsdfMetrics.parse(JSON);
        assertEquals(0.96f * 10f, m.ascent(10f), 1e-4);
        assertEquals(0.24f * 10f, m.descent(10f), 1e-4);
    }
    @Test void widthCodePointIterationAndSupplementary() {
        MsdfMetrics m = MsdfMetrics.parse(JSON);
        // Basic BMP: "A " = 0.7 + 0.3 = 1.0 em at size 10
        assertEquals((0.7f + 0.3f) * 10f, m.width("A ", 10f), 1e-4);
        // Supplementary code point U+1F600 (outside BMP, 2 char16 units) is unknown
        // → contributes 0; no '?' fallback in the JSON either, so result is 0
        String withEmoji = new String(Character.toChars(0x1F600));
        assertEquals(0f, m.width(withEmoji, 10f), 1e-4);
        // Mixed: "A" + supplementary → only "A" contributes
        assertEquals(0.7f * 10f, m.width("A" + withEmoji, 10f), 1e-4);
    }
}
