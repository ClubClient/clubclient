package com.club.ui.backend;

import com.club.ui.IconGlyph;
import com.club.ui.text.MsdfMetrics;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The icon has to land where MODERN lands it, and read the cell that is actually there.
 *
 * <h2>Why this arithmetic is worth a test</h2>
 *
 * <p>From 1.21.5 Club draws icons as texture cells rather than as glyphs, and every number involved is a
 * fact about the committed atlas rather than about our code: the em-relative plane box, the baseline the
 * text pipeline would have used, and the y flip between an atlas written {@code yOrigin:"bottom"} and UVs
 * that count down from the top. Get any one of them wrong and the build is still green, the checker is
 * still silent, and an icon is drawn upside down or a hundred pixels away. Nothing throws. The owner sees a
 * wrong menu and cannot tell that from "the icon never drew".
 *
 * <p>The test reads the REAL {@code assets/club/ui/icon/msdf/icons.json} through the REAL parser. It is not
 * a restatement of the maths with the same assumptions baked in — if the atlas is regenerated with a
 * different cell size, padding or origin, these assertions are what notice.
 *
 * <p><b>What it does NOT prove,</b> plainly: that the pipeline compiles, that the sampler binds, that the
 * MSDF shader resolves a coverage curve, or that the tint arrives. Those need a GL context and a window,
 * and this suite has neither. It proves the geometry we hand vanilla is the geometry we meant.
 */
class SpriteIconsPlacementTest {

    /** The committed icon atlas, parsed by the code that parses it at runtime. */
    private static MsdfMetrics icons() throws Exception {
        try (InputStream in = SpriteIconsPlacementTest.class.getClassLoader()
                .getResourceAsStream("assets/club/ui/icon/msdf/icons.json")) {
            assertNotNull(in, "assets/club/ui/icon/msdf/icons.json must be on the classpath");
            return MsdfMetrics.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * The promise {@code IconGlyph.draw} makes on EVERY backend: the icon's content box is exactly
     * {@code (x, y)..(x+size, y+size)}.
     *
     * <p>The drawn quad is deliberately BIGGER than that — the atlas pads each glyph by the distance range
     * so the field has room to fall off — so the test cannot just compare the quad to the box. It maps the
     * content box back out of the quad the way the GPU will: the quad starts at plane {@code pl} em and
     * spans {@code k} Club units per texel, so plane 0 (the content box's left edge) sits {@code -pl*size}
     * Club units into the quad. That is the number that has to come back as {@code x}.
     */
    @Test
    void theContentBoxLandsExactlyWhereTheCallerAskedFor() throws Exception {
        MsdfMetrics m = icons();
        MsdfMetrics.Glyph g = m.get(IconGlyph.COMBAT.codePoint);
        assertNotNull(g, "COMBAT (U+E000) must be packed into the icon atlas");

        float x = 40f, y = 12.5f, size = 13f;   // fractional y on purpose: icons are not grid-aligned
        SpriteIcons.Quad q = SpriteIcons.quad(g, x, y, size, m.atlasW, m.atlasH);
        assertNotNull(q);

        // Plane 0,0 is the content box's top-left. Walk it back out of the quad in Club units.
        float contentLeft = q.x() - g.pl * size;
        float contentTop = q.y() + g.pt * size - size;
        assertEquals(x, contentLeft, 1e-3, "the icon's content box must start at the caller's x");
        assertEquals(y, contentTop, 1e-3, "the icon's content box must start at the caller's y");

        // ...and be exactly `size` across, which is the other half of the same promise.
        float contentRight = q.x() + (1f - g.pl) * size;
        assertEquals(x + size, contentRight, 1e-3, "the icon's content box must be `size` wide");
    }

    /**
     * The y flip, pinned against the one glyph whose answer is known by eye.
     *
     * <p>U+E000 is the FIRST cell of the atlas: {@code atlasBounds {left:0, bottom:528, right:88, top:616}}
     * in a 704x616 atlas written {@code yOrigin:"bottom"}. Counting from the bottom it is the top row, so in
     * texture coordinates — which count DOWN from the top — it must start at v=0. If the flip were dropped
     * or applied twice, v would come back as 528, and every icon in the menu would draw as some other icon's
     * neighbour.
     */
    @Test
    void theTopRowOfTheAtlasReadsAsTheTopOfTheTexture() throws Exception {
        MsdfMetrics m = icons();
        SpriteIcons.Quad q = SpriteIcons.quad(m.get(IconGlyph.COMBAT.codePoint), 0f, 0f, 13f, m.atlasW, m.atlasH);
        assertNotNull(q);

        assertEquals(0f, q.u(), 1e-3, "U+E000 is the atlas's leftmost cell: u must be 0 texels");
        assertEquals(0f, q.v(), 1e-3,
                "U+E000 spans bottom=528..top=616 of a 616-tall yOrigin=bottom atlas — that is the TOP row, "
                        + "so in top-left-origin texture coords it must start at v=0, not 528");
        assertEquals(88, q.cellW(), "the atlas packs 88x88 cells (72px em + 8px distance range each side)");
        assertEquals(88, q.cellH(), "the atlas packs 88x88 cells (72px em + 8px distance range each side)");
    }

    /**
     * Every icon Club can name must actually be in the atlas, and every cell must be square.
     *
     * <p>The squareness is not a curiosity: {@code IconPipe.draw} scales by a single uniform {@code k}
     * because {@code Mtx.scale} is uniform, which is only correct while the cells and their plane boxes are
     * square. This is the assertion that catches the day that stops being true — a non-square cell would
     * otherwise draw stretched, silently.
     */
    @Test
    void everyNamedIconIsPackedAndEveryCellIsSquare() throws Exception {
        MsdfMetrics m = icons();
        for (IconGlyph icon : IconGlyph.values()) {
            MsdfMetrics.Glyph g = m.get(icon.codePoint);
            assertNotNull(g, () -> icon + " (U+" + String.format("%04X", icon.codePoint)
                    + ") is named in IconGlyph but is not packed into icons.json — it would draw nothing");
            assertTrue(g.hasBounds, () -> icon + " has no atlas bounds — it would draw nothing");

            SpriteIcons.Quad q = SpriteIcons.quad(g, 0f, 0f, 13f, m.atlasW, m.atlasH);
            assertNotNull(q, () -> icon + " has a degenerate cell");
            assertEquals(q.cellW(), q.cellH(), () -> icon + ": cell must be square — IconPipe scales it by a "
                    + "single uniform k and a non-square cell would draw stretched");
            // The plane box must be square too, for the same reason: k is derived from the X axis alone.
            assertEquals(g.pr - g.pl, g.pt - g.pb, 1e-4, () -> icon + ": plane box must be square — k comes "
                    + "off the X axis and a non-square plane box would draw stretched on Y");
        }
    }
}
