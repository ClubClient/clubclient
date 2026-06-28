package com.club.ui;

import com.club.ui.text.Align;
import com.club.ui.text.GlyphSink;
import com.club.ui.text.GlyphSource;
import com.club.ui.text.MsdfMetrics;
import com.club.ui.text.ResolvedGlyph;
import com.club.ui.text.TextLayout;
import com.club.ui.text.Weight;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TextLayoutTest {
    // Fake source: A-Z = glyph advance 0.5, bounds [0..0.4]x[0..0.7], full uv, atlasId 1.
    // space = advance 0.3, no bounds. Anything else = null (unrenderable).
    static GlyphSource fakeSource() {
        return new GlyphSource() {
            public boolean resolve(Weight w, int cp, ResolvedGlyph out) {
                if (cp == ' ') { out.atlasId = 1; MsdfMetrics.Glyph g = new MsdfMetrics.Glyph(); g.advance = 0.3f; g.hasBounds = false; out.glyph = g; return true; }
                if (cp >= 'A' && cp <= 'Z') {
                    MsdfMetrics.Glyph g = new MsdfMetrics.Glyph();
                    g.advance = 0.5f; g.hasBounds = true; g.pl = 0f; g.pr = 0.4f; g.pb = 0f; g.pt = 0.7f;
                    g.u0 = 0f; g.v0 = 0f; g.u1 = 1f; g.v1 = 1f;
                    out.atlasId = 1; out.glyph = g; return true;
                }
                return false;
            }
            public MsdfMetrics metrics(Weight w) { return MsdfMetrics.parse(MsdfMetricsTest.JSON); }
        };
    }

    static final class Rec implements GlyphSink {
        final List<float[]> q = new ArrayList<>();
        public void glyph(int a, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
            q.add(new float[]{a, x0, y0, x1, y1});
        }
    }

    @Test void widthSumsAdvances() {
        TextLayout L = new TextLayout(fakeSource());
        assertEquals((0.5f + 0.5f) * 20f, L.width("AB", Weight.MEDIUM, 20f), 1e-4);
        assertEquals((0.5f + 0.3f + 0.5f) * 20f, L.width("A B", Weight.MEDIUM, 20f), 1e-4);
    }
    @Test void unknownCodepointSkipped() {
        TextLayout L = new TextLayout(fakeSource());
        assertEquals(0.5f * 10f, L.width("A中", Weight.MEDIUM, 10f), 1e-4);
    }
    @Test void layoutEmitsPositionedGlyphs() {
        TextLayout L = new TextLayout(fakeSource()); Rec r = new Rec();
        L.layoutLine("AB", Weight.MEDIUM, 10f, 100f, 0f, Align.LEFT, r);
        assertEquals(2, r.q.size());
        // x0 checks (index [1])
        assertEquals(100f, r.q.get(0)[1], 1e-4);
        assertEquals(105f, r.q.get(1)[1], 1e-4);
        // y0 checks (index [2]): baseY = yTop(0) + ascent(10) = 0.96*10 = 9.6; y0 = baseY - pt*size = 9.6 - 0.7*10 = 2.6
        float expectedBaseY = 0f + MsdfMetrics.parse(MsdfMetricsTest.JSON).ascent(10f); // 9.6
        float expectedY0    = expectedBaseY - 0.7f * 10f;                               // 2.6
        assertEquals(expectedY0, r.q.get(0)[2], 1e-4, "y0 of first glyph");
        assertEquals(expectedY0, r.q.get(1)[2], 1e-4, "y0 of second glyph");
    }
    @Test void centerAlignShiftsByHalfWidth() {
        TextLayout L = new TextLayout(fakeSource()); Rec r = new Rec();
        // "AB": advance = (0.5+0.5)*10 = 10; penX = 100 - 10/2 = 95; first glyph x0 = 95 + pl(0)*10 = 95
        L.layoutLine("AB", Weight.MEDIUM, 10f, 100f, 0f, Align.CENTER, r);
        assertEquals(2, r.q.size());
        assertEquals(95f, r.q.get(0)[1], 1e-4, "CENTER: first glyph x0");
        assertEquals(100f, r.q.get(1)[1], 1e-4, "CENTER: second glyph x0");
    }
    @Test void spaceEmitsNoQuadButAdvances() {
        TextLayout L = new TextLayout(fakeSource()); Rec r = new Rec();
        L.layoutLine("A B", Weight.MEDIUM, 10f, 0f, 0f, Align.LEFT, r);
        assertEquals(2, r.q.size());
        assertEquals(8f, r.q.get(1)[1], 1e-4);
    }
    @Test void rightAlignShiftsByWidth() {
        TextLayout L = new TextLayout(fakeSource()); Rec r = new Rec();
        L.layoutLine("AB", Weight.MEDIUM, 10f, 100f, 0f, Align.RIGHT, r);
        assertEquals(100f - 10f, r.q.get(0)[1], 1e-4);
    }
    @Test void wrapsByWidth() {
        TextLayout L = new TextLayout(fakeSource());
        // "AA" = 10px fits in 12px; "AA AA" = 23px doesn't; wraps to ["AA", "AA"]
        List<String> lines = L.wrap("AA AA", Weight.MEDIUM, 10f, 12f);
        assertEquals(2, lines.size());
        assertEquals("AA", lines.get(0), "first wrapped line content");
        assertEquals("AA", lines.get(1), "second wrapped line content");
    }
}
