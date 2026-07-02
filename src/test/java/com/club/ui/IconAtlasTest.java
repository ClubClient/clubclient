package com.club.ui;

import com.club.ui.text.MsdfMetrics;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the Stage-11 icon SDF atlas: the generated JSON must parse through the SAME
 * MsdfMetrics the runtime uses, every {@link IconGlyph} must exist in it with the fixed geometry
 * contract (advance 1.0, ascender 1.0 — IconGlyph.draw's baseline math relies on these), and the
 * PNG must actually contain a signed distance field (mid-gray edges, bright interior).
 */
class IconAtlasTest {

    private static final Path JSON = Path.of("src/main/resources/assets/club/ui/icon/msdf/icons.json");
    private static final Path PNG  = Path.of("src/main/resources/assets/club/ui/icon/msdf/icons.png");

    private static MsdfMetrics load() throws Exception {
        return MsdfMetrics.parse(Files.readString(JSON));
    }

    @Test void everyIconGlyphExistsInAtlas() throws Exception {
        MsdfMetrics m = load();
        for (IconGlyph g : IconGlyph.values()) {
            MsdfMetrics.Glyph gl = m.get(g.codePoint);
            assertNotNull(gl, g + " (U+" + Integer.toHexString(g.codePoint) + ") missing from icons.json");
            assertTrue(gl.hasBounds, g + " has no bounds");
            assertEquals(1.0f, gl.advance, 1e-6, g + " advance must be 1em");
        }
    }

    @Test void geometryContractMatchesIconGlyphMath() throws Exception {
        MsdfMetrics m = load();
        // IconGlyph.draw computes yTop = y + size - ascent(size); the plane top is 1em above the
        // baseline. Both only hold with these atlas metrics:
        assertEquals(1.0f, m.emAscender, 1e-6);
        assertEquals(0.0f, m.emDescender, 1e-6);
        assertEquals(1.0f, m.emLineHeight, 1e-6);
        assertTrue(m.distanceRange > 0f);
        for (IconGlyph g : IconGlyph.values()) {
            MsdfMetrics.Glyph gl = m.get(g.codePoint);
            // plane covers the padded 24-grid content box: symmetric around 0..1
            assertEquals(gl.pr - 1f, -gl.pl, 1e-4, g + " plane not symmetric");
            assertEquals(gl.pt - 1f, -gl.pb, 1e-4, g + " plane not symmetric");
        }
    }

    @Test void pngContainsADistanceField() throws Exception {
        MsdfMetrics m = load();
        BufferedImage img = ImageIO.read(new File(PNG.toString()));
        assertEquals(m.atlasW, img.getWidth());
        assertEquals(m.atlasH, img.getHeight());
        // For each glyph tile: the interior of a 2px stroke reaches 0.5 + (1u*SCALE)/range =
        // 0.5 + 3/8 = 0.875 -> 223 (fills clamp to 255); tile corner is far outside (near 0);
        // and SOME mid-gray band must exist (the distance ramp) — i.e. not a binary mask.
        for (IconGlyph g : IconGlyph.values()) {
            MsdfMetrics.Glyph gl = m.get(g.codePoint);
            int x0 = Math.round(gl.u0 * m.atlasW), x1 = Math.round(gl.u1 * m.atlasW);
            int y0 = Math.round(gl.v0 * m.atlasH), y1 = Math.round(gl.v1 * m.atlasH);
            int max = 0, mid = 0;
            for (int y = Math.min(y0, y1); y < Math.max(y0, y1); y++) {
                for (int x = x0; x < x1; x++) {
                    int v = img.getRGB(x, y) & 0xFF;
                    if (v > max) max = v;
                    if (v > 96 && v < 160) mid++;
                }
            }
            assertTrue(max >= 215, g + ": interior never crosses the stroke-core level (max=" + max + ")");
            assertTrue(mid > 50, g + ": no distance ramp — not an SDF? (mid=" + mid + ")");
            int corner = img.getRGB(x0, Math.min(y0, y1)) & 0xFF;
            assertTrue(corner <= 16, g + ": tile corner not 'far outside' (corner=" + corner + ")");
        }
    }
}
