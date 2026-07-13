package com.club.ui;

import com.club.ui.text.Align;
import com.club.ui.text.GlyphSink;
import com.club.ui.text.GlyphSource;
import com.club.ui.text.MsdfMetrics;
import com.club.ui.text.ResolvedGlyph;
import com.club.ui.text.TextLayout;
import com.club.ui.text.Weight;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The condition the owner put on touching the HUD: the picture does not change by one pixel, only its
 * price. Screenshots can only show that no pixel VISIBLY moved; this shows that the geometry handed to the
 * renderer is the same to the last bit — which is the property that actually guarantees it.
 *
 * <p>The cache holds resolutions, not positions, precisely so this can hold. Caching positions relative to
 * an origin and adding (x, y) at draw time would have been faster and WRONG: float addition is not
 * associative, so ((-advance/2) + pl*size) + x is a different number from (x - advance/2) + pl*size — a
 * sub-ULP difference, invisible in a screenshot, and a lie in a claim of bit-identity.
 */
class GlyphCacheIdentityTest {

    @AfterEach void restore() { TextLayout.cacheEnabled = true; }

    /** Records every emitted quad with full precision, plus the atlas id. */
    private static final class Rec implements GlyphSink {
        final List<float[]> q = new ArrayList<>();
        public void glyph(int a, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
            q.add(new float[]{a, x0, y0, x1, y1, u0, v0, u1, v1});
        }
    }

    private static List<float[]> run(boolean cached, String text, float size, float x, float y, Align align) {
        TextLayout.cacheEnabled = cached;
        TextLayout layout = new TextLayout(TextLayoutTest.fakeSource());   // a FRESH layout: cold cache
        Rec rec = new Rec();
        layout.layoutLine(text, Weight.MEDIUM, size, x, y, align, rec);
        return rec.q;
    }

    private static void assertBitIdentical(String text, float size, float x, float y, Align align) {
        List<float[]> off = run(false, text, size, x, y, align);
        List<float[]> on  = run(true,  text, size, x, y, align);
        assertEquals(off.size(), on.size(), "quad count differs for \"" + text + "\"");
        for (int i = 0; i < off.size(); i++)
            for (int c = 0; c < off.get(i).length; c++)
                assertEquals(
                        Float.floatToRawIntBits(off.get(i)[c]),
                        Float.floatToRawIntBits(on.get(i)[c]),
                        "quad " + i + " component " + c + " of \"" + text + "\" differs in its BITS: "
                                + off.get(i)[c] + " vs " + on.get(i)[c]);
    }

    @Test void theCachedPathEmitsBitIdenticalQuads() {
        // Ugly coordinates and sizes on purpose: round numbers hide non-associativity.
        for (Align a : Align.values()) {
            assertBitIdentical("HP", 13.7f, 100.3f, 40.1f, a);
            assertBitIdentical("ZOMBIE", 9.31f, -17.77f, 3.0009f, a);
            assertBitIdentical("A B C", 21.333f, 0.1f, 0.1f, a);
            assertBitIdentical("Q", 7f, 1e3f, 1e3f, a);
        }
    }

    @Test void aSecondDrawOfTheSameStringIsAlsoBitIdentical() {
        // The second call is the one that hits the cache — the first only fills it.
        TextLayout layout = new TextLayout(TextLayoutTest.fakeSource());
        TextLayout.cacheEnabled = true;
        Rec first = new Rec(), second = new Rec();
        layout.layoutLine("HP", Weight.MEDIUM, 13.7f, 100.3f, 40.1f, Align.CENTER, first);
        layout.layoutLine("HP", Weight.MEDIUM, 13.7f, 100.3f, 40.1f, Align.CENTER, second);
        assertEquals(first.q.size(), second.q.size());
        for (int i = 0; i < first.q.size(); i++)
            for (int c = 0; c < first.q.get(i).length; c++)
                assertEquals(Float.floatToRawIntBits(first.q.get(i)[c]),
                             Float.floatToRawIntBits(second.q.get(i)[c]));
    }

    @Test void widthAgreesToTheBitToo() {
        // Layout code calls width() to place things; a width that drifts by an ULP moves a chip.
        for (String s : new String[] {"HP", "FPS", "ZOMBIE", "A B C", ""}) {
            TextLayout.cacheEnabled = false;
            float uncached = new TextLayout(TextLayoutTest.fakeSource()).width(s, Weight.MEDIUM, 13.7f);
            TextLayout.cacheEnabled = true;
            TextLayout l = new TextLayout(TextLayoutTest.fakeSource());
            float cold = l.width(s, Weight.MEDIUM, 13.7f);
            float warm = l.width(s, Weight.MEDIUM, 13.7f);
            assertEquals(Float.floatToRawIntBits(uncached), Float.floatToRawIntBits(cold), "cold cache, \"" + s + "\"");
            assertEquals(Float.floatToRawIntBits(uncached), Float.floatToRawIntBits(warm), "warm cache, \"" + s + "\"");
        }
    }

    @Test void anUnresolvableCodePointIsStillSkipped() {
        // The fake source resolves A-Z and space and nothing else. The cached path must drop the rest in
        // exactly the same places — a glyph that survives caching but not the old loop would SHIFT the line.
        assertBitIdentical("A©B", 12f, 5f, 5f, Align.LEFT);   // (c) is unresolvable in the fixture
    }

    @Test void aSourceThatChangesItsAnswersInvalidatesTheCache() {
        // The icon atlas can die mid-session; after that the same PUA code point resolves to '?' instead of
        // an icon. A cache that cannot be told its answers are stale would keep drawing the dead icon.
        var src = new GlyphSource() {
            int epoch;
            boolean iconAlive = true;
            public boolean resolve(Weight w, int cp, ResolvedGlyph out) {
                MsdfMetrics.Glyph g = new MsdfMetrics.Glyph();
                g.hasBounds = true; g.pl = 0f; g.pb = 0f; g.pt = 0.7f;
                g.u0 = 0f; g.v0 = 0f; g.u1 = 1f; g.v1 = 1f;
                boolean icon = cp >= 0xE000 && cp <= 0xF8FF;
                g.advance = (icon && iconAlive) ? 1.0f : 0.5f;      // the icon is wider than the '?' it becomes
                g.pr = g.advance;
                out.atlasId = (icon && iconAlive) ? 3 : 1;
                out.glyph = g;
                return true;
            }
            public MsdfMetrics metrics(Weight w) { return MsdfMetrics.parse(MsdfMetricsTest.JSON); }
            public int epoch() { return epoch; }
            void killIcons() { iconAlive = false; epoch++; }
        };
        TextLayout.cacheEnabled = true;
        TextLayout layout = new TextLayout(src);
        final String PUA = "";   // a Private-Use-Area code point: an icon glyph
        assertEquals(1.0f * 10f, layout.width(PUA, Weight.MEDIUM, 10f), 1e-6, "the icon glyph, alive");

        src.killIcons();
        assertEquals(0.5f * 10f, layout.width(PUA, Weight.MEDIUM, 10f), 1e-6,
                "after the icon atlas dies the cache must re-resolve, not keep serving the dead glyph");
    }
}
