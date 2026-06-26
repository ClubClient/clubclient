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
            public ResolvedGlyph resolve(Weight w, int cp) {
                if (cp == ' ') { MsdfMetrics.Glyph g = new MsdfMetrics.Glyph(); g.advance = 0.3f; g.hasBounds = false; return new ResolvedGlyph(1, g); }
                if (cp >= 'A' && cp <= 'Z') {
                    MsdfMetrics.Glyph g = new MsdfMetrics.Glyph();
                    g.advance = 0.5f; g.hasBounds = true;
                    g.pl = 0f; g.pr = 0.4f; g.pb = 0f; g.pt = 0.7f;
                    g.u0 = 0f; g.v0 = 0f; g.u1 = 1f; g.v1 = 1f;
                    return new ResolvedGlyph(1, g);
                }
                return null;
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
        assertEquals(100f, r.q.get(0)[1], 1e-4);
        assertEquals(105f, r.q.get(1)[1], 1e-4);
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
        List<String> lines = L.wrap("AA AA", Weight.MEDIUM, 10f, 12f);
        assertEquals(2, lines.size());
    }
}
