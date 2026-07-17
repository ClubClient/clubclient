package com.club.ui.backend;

import com.club.ui.text.Align;
import com.club.ui.text.GlyphSource;
import com.club.ui.text.MsdfMetrics;
import com.club.ui.text.ResolvedGlyph;
import com.club.ui.text.TextLayout;
import com.club.ui.text.Weight;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Club's font has to land where MODERN lands it, and read the cells that are actually there.
 *
 * <h2>Why this arithmetic is worth a test</h2>
 *
 * <p>From 1.21.5 Club draws glyphs as texture cells through {@code DrawContext.drawTexture} rather than as
 * vertices of its own, and every number involved is a fact about the committed atlas rather than about our
 * code: the em-relative plane box, the baseline, and the y flip between an atlas written
 * {@code yOrigin:"bottom"} and UVs that count down from the top. Get any one wrong and the build is still
 * green, the checker is still silent, and the menu is set in Club's font — stretched, or upside down, or
 * reading its neighbour's cell. Nothing throws.
 *
 * <p>These tests read the REAL {@code assets/club/ui/font/msdf/inter_*.json} through the REAL parser and
 * drive the REAL {@link TextLayout}. They are not a restatement of the maths with the same assumptions baked
 * in: if the atlases are regenerated with a different padding, em size or origin, these assertions are what
 * notice.
 *
 * <p><b>What they do NOT prove,</b> plainly: that the pipeline compiles, that the sampler binds LINEAR, that
 * the MSDF shader resolves a coverage curve, that WEIGHT_BIAS reaches the fragment stage, or that the tint
 * arrives. Those need a GL context and a window, and this suite has neither. They prove the geometry Club
 * hands vanilla is the geometry Club meant, and that a string is worth the width the atlas says it is.
 */
class SpriteTextPlacementTest {

    private static final String[] WEIGHTS = { "regular", "medium", "semibold" };

    /** A committed font atlas, parsed by the code that parses it at runtime. */
    private static MsdfMetrics font(String weight) throws Exception {
        try (InputStream in = SpriteTextPlacementTest.class.getClassLoader()
                .getResourceAsStream("assets/club/ui/font/msdf/inter_" + weight + ".json")) {
            assertNotNull(in, "assets/club/ui/font/msdf/inter_" + weight + ".json must be on the classpath");
            return MsdfMetrics.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * One atlas as a {@link GlyphSource}, so {@link TextLayout} can be driven without Minecraft.
     *
     * <p>{@code FontRegistry} is the real source and cannot be used here: it reaches for
     * {@code MinecraftClient.getInstance()} to find the resource manager. This mirrors the one behaviour
     * layout depends on — resolve, or fall back to '?' — over the real parsed metrics.
     */
    private record OneAtlas(MsdfMetrics m) implements GlyphSource {
        @Override public boolean resolve(Weight weight, int cp, ResolvedGlyph out) {
            MsdfMetrics.Glyph g = m.get(cp);
            if (g == null) g = m.get((int) '?');
            if (g == null) return false;
            out.atlasId = 0;
            out.glyph = g;
            return true;
        }
        @Override public MsdfMetrics metrics(Weight weight) { return m; }
    }

    /**
     * The assumption the whole draw path rests on: one uniform scale per glyph.
     *
     * <p>{@code TextPipe.draw} places a glyph with {@code Mtx.scale(k)} — a SINGLE factor, because the 2D
     * matrix stack scales both axes together — and {@code SpriteText.cell} derives that {@code k} from the X
     * axis alone. That is only correct while every cell's plane box shares its aspect. It does here, and by a
     * wide margin (measured 2026-07-17: planeW/cellW == planeH/cellH == 1/40 for all 271 bounded glyphs of
     * all three weights, worst relative deviation 4e-16 — the atlases are generated at 40 px/em with the
     * distance range padding applied equally on both axes). This test is what fails the day a regenerated
     * atlas stops being true, instead of the owner noticing the font looks subtly squashed.
     */
    @Test
    void everyGlyphOfEveryWeightScalesUniformly() throws Exception {
        float size = 13f;
        int checked = 0;
        for (String w : WEIGHTS) {
            MsdfMetrics m = font(w);
            for (int cp = 0; cp <= 0xFFFF; cp++) {
                MsdfMetrics.Glyph g = m.get(cp);
                if (g == null || !g.hasBounds) continue;

                // Exactly what TextLayout emits for this glyph, with the pen at 0 and the baseline at 0.
                float x0 = g.pl * size, x1 = g.pr * size;
                float y0 = -g.pt * size, y1 = -g.pb * size;
                SpriteText.Cell c = SpriteText.cell(x0, y0, x1, y1, g.u0, g.v0, g.u1, g.v1, m.atlasW, m.atlasH);

                final int fcp = cp; final String fw = w;
                assertNotNull(c, () -> fw + " U+" + String.format("%04X", fcp) + " has a degenerate cell");
                float ky = (y1 - y0) / c.cellH();
                assertEquals(c.k(), ky, 1e-5, () -> fw + " U+" + String.format("%04X", fcp)
                        + ": k comes off the X axis and Mtx.scale is uniform — a glyph whose Y scale differs "
                        + "would draw stretched, silently");
                checked++;
            }
        }
        assertTrue(checked > 700, "expected ~271 bounded glyphs x 3 weights; got " + checked
                + " — if this collapsed, the atlases moved and every other assertion here is worthless");
    }

    /**
     * The y flip, pinned against a glyph whose answer can be checked by hand.
     *
     * <p>'A' in inter_regular.json is {@code atlasBounds {left:175.5, bottom:188.5, right:207.5, top:223.5}}
     * in a 516x516 atlas written {@code yOrigin:"bottom"}. Texture coordinates count DOWN from the top, so
     * its v must be {@code 516 - 223.5 = 292.5} texels. Drop the flip and it comes back as 188.5; apply it
     * twice and it comes back as 223.5. Either way every letter in the menu draws as some other letter's
     * neighbour — which is why the two wrong answers are named here rather than left implicit.
     */
    @Test
    void theAtlasCellIsReadInTexelsWithTheOriginFlipped() throws Exception {
        MsdfMetrics m = font("regular");
        MsdfMetrics.Glyph g = m.get((int) 'A');
        assertNotNull(g, "'A' must be in the regular atlas");

        float size = 13f;
        SpriteText.Cell c = SpriteText.cell(g.pl * size, -g.pt * size, g.pr * size, -g.pb * size,
                g.u0, g.v0, g.u1, g.v1, m.atlasW, m.atlasH);
        assertNotNull(c);

        assertEquals(175.5f, c.u(), 1e-2, "'A' starts at atlas texel 175.5 — drawTexture wants TEXELS, and "
                + "divides by texW itself; a normalised u here would read the atlas's top-left corner");
        assertEquals(292.5f, c.v(), 1e-2, "'A' spans bottom=188.5..top=223.5 of a 516-tall yOrigin=bottom "
                + "atlas, so in top-left-origin texture coords it starts at 516-223.5=292.5, not 188.5 (flip "
                + "dropped) and not 223.5 (flip applied twice)");
        assertEquals(32, c.cellW(), "207.5-175.5 = 32 texels");
        assertEquals(35, c.cellH(), "223.5-188.5 = 35 texels");
    }

    /**
     * {@code k} must map the cell back onto exactly the quad layout asked for.
     *
     * <p>The glyph is drawn at its NATIVE texel size from the origin and scaled — that is the only way to
     * keep sub-pixel placement through an API whose x/y/w/h are ints. So {@code k * cellW} has to reproduce
     * the quad's width to the float, or every glyph is a fraction of a pixel too wide and the error
     * accumulates along the line.
     */
    @Test
    void scalingTheCellReproducesTheQuadLayoutAskedFor() throws Exception {
        MsdfMetrics m = font("regular");
        float size = 13f;
        for (int cp : new int[] { 'A', 'i', 'W', '.', 'g' }) {
            MsdfMetrics.Glyph g = m.get(cp);
            assertNotNull(g, "U+" + String.format("%04X", cp) + " must be in the regular atlas");
            float x0 = 40f + g.pl * size, x1 = 40f + g.pr * size;
            float y0 = 25f - g.pt * size, y1 = 25f - g.pb * size;
            SpriteText.Cell c = SpriteText.cell(x0, y0, x1, y1, g.u0, g.v0, g.u1, g.v1, m.atlasW, m.atlasH);
            assertNotNull(c);

            assertEquals(x0, c.x(), 1e-4, "the quad's left edge is the draw origin");
            assertEquals(y0, c.y(), 1e-4, "the quad's top edge is the draw origin");
            assertEquals(x1 - x0, c.k() * c.cellW(), 1e-3, "k*cellW must be the quad's width");
            assertEquals(y1 - y0, c.k() * c.cellH(), 1e-3, "k*cellH must be the quad's height — the same k, "
                    + "which is the uniform-scale promise restated per glyph");
        }
    }

    /**
     * The number the entire menu layout stands on: a string is worth what the atlas says it is worth.
     *
     * <p>Every card, column and centred label is placed off {@code width()}. If it lies by a percent, nothing
     * throws and nothing logs — the menu just comes out visibly wrong. So this asserts the width against the
     * {@code advance} fields of the real JSON, summed independently here, rather than against another call
     * into the same code.
     *
     * <p>Note what this pins on the multi-version question specifically: {@code SpriteText.width} IS
     * {@code TextLayout.width} — one delegating line, the same object {@code ModernText} calls on 1.21.1. So
     * a width that matches the atlas here is the same width 1.21.1 draws, which is the whole "один в один"
     * requirement, expressed as an assertion instead of a hope.
     */
    @Test
    void widthMatchesTheAtlasAdvancesExactly() throws Exception {
        for (String w : WEIGHTS) {
            MsdfMetrics m = font(w);
            TextLayout layout = new TextLayout(new OneAtlas(m));
            for (String s : new String[] { "Club", "Hi", "Fullbright", "Item Scroll", "A W.", "Настройки" }) {
                float expected = 0f;
                for (int i = 0; i < s.length(); ) {
                    int cp = s.codePointAt(i);
                    i += Character.charCount(cp);
                    MsdfMetrics.Glyph g = m.get(cp);
                    assertNotNull(g, () -> w + ": U+" + String.format("%04X", cp)
                            + " — this test's strings must all be inside the atlas's coverage, or it is "
                            + "measuring the '?' fallback rather than the font");
                    expected += g.advance * 13f;
                }
                assertEquals(expected, layout.width(s, Weight.REGULAR, 13f), 1e-3,
                        () -> w + ": width(\"" + s + "\") must be the sum of the atlas's own advances");
            }
        }
    }

    /**
     * A space advances the pen but draws nothing, and the line still ends up the right length.
     *
     * <p>U+0020 is one of the two glyphs in each atlas with an {@code advance} and NO {@code planeBounds}
     * (measured: 271 of 273 are bounded). A cell emitted for it would be degenerate — and
     * {@code SpriteText.cell} returning null on a degenerate cell is only correct if layout never emits one
     * in the first place. This pins both halves: no quad for the space, and the advance still counted.
     */
    @Test
    void spacesAdvanceWithoutEmittingAQuad() throws Exception {
        MsdfMetrics m = font("regular");
        TextLayout layout = new TextLayout(new OneAtlas(m));

        List<Float> xs = new ArrayList<>();
        float advance = layout.layoutLine("A A", Weight.REGULAR, 13f, 0f, 0f, Align.LEFT,
                (atlasId, x0, y0, x1, y1, u0, v0, u1, v1) -> xs.add(x0));

        assertEquals(2, xs.size(), "\"A A\" must emit exactly two quads — the space has no plane box");
        MsdfMetrics.Glyph a = m.get((int) 'A'), sp = m.get((int) ' ');
        assertFalse(sp.hasBounds, "U+0020 is expected to be unbounded in this atlas");
        assertEquals((a.advance + sp.advance + a.advance) * 13f, advance, 1e-3,
                "the space must still advance the pen");
        assertEquals((a.advance + sp.advance) * 13f, xs.get(1) - xs.get(0), 1e-3,
                "the second 'A' sits one 'A' plus one space along");
    }

    /**
     * Alignment shifts the whole run and nothing else.
     *
     * <p>CENTER and RIGHT are how every chip label and column header in the menu is placed, and they are
     * pure arithmetic on {@code width()} — so they are checkable here, and worth checking: a sign error puts
     * a centred label a full string-width away, which looks like a layout bug rather than a text one.
     */
    @Test
    void alignmentShiftsTheRunByWidth() throws Exception {
        MsdfMetrics m = font("regular");
        TextLayout layout = new TextLayout(new OneAtlas(m));
        String s = "Club";
        float size = 13f, x = 100f;
        float w = layout.width(s, Weight.REGULAR, size);

        assertEquals(x, firstQuadX(layout, s, size, x, Align.LEFT) - m.get((int) 'C').pl * size, 1e-3,
                "LEFT starts the pen at x");
        assertEquals(x - w / 2f, firstQuadX(layout, s, size, x, Align.CENTER) - m.get((int) 'C').pl * size, 1e-3,
                "CENTER starts the pen half a width left of x");
        assertEquals(x - w, firstQuadX(layout, s, size, x, Align.RIGHT) - m.get((int) 'C').pl * size, 1e-3,
                "RIGHT starts the pen a full width left of x");
    }

    private static float firstQuadX(TextLayout layout, String s, float size, float x, Align align) {
        float[] first = { Float.NaN };
        layout.layoutLine(s, Weight.REGULAR, size, x, 0f, align, (id, x0, y0, x1, y1, u0, v0, u1, v1) -> {
            if (Float.isNaN(first[0])) first[0] = x0;
        });
        return first[0];
    }

    /**
     * The baseline is the ascender below the top of the line, and glyphs hang off it.
     *
     * <p>This is the contract {@code drawWrapped} and every HUD element assume when they step {@code y} by
     * {@code lineHeight}: {@code draw(s, x, y, ...)} treats {@code y} as the TOP of the line, not the
     * baseline. The atlas's own ascender is what converts between them.
     */
    @Test
    void theBaselineSitsOneAscenderBelowTheTopOfTheLine() throws Exception {
        MsdfMetrics m = font("regular");
        TextLayout layout = new TextLayout(new OneAtlas(m));
        float size = 13f, yTop = 12.5f;   // fractional on purpose: text is not grid-aligned

        float[] y0 = { Float.NaN };
        layout.layoutLine("A", Weight.REGULAR, size, 0f, yTop, Align.LEFT,
                (id, qx0, qy0, qx1, qy1, u0, v0, u1, v1) -> y0[0] = qy0);

        MsdfMetrics.Glyph a = m.get((int) 'A');
        // Walk the baseline back out of the quad: the quad's top is `pt` em above it.
        float baseline = y0[0] + a.pt * size;
        assertEquals(yTop + m.ascent(size), baseline, 1e-3,
                "baseline must be yTop + ascent — if this drifts, every line of wrapped text stacks wrong");
        assertTrue(m.ascent(size) > 0f && m.descent(size) > 0f,
                "ascent and descent are both positive magnitudes; the em descender is stored negative");
    }
}
