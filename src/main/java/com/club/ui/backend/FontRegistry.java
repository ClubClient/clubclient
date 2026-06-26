package com.club.ui.backend;

import com.club.ui.text.GlyphSource;
import com.club.ui.text.MsdfMetrics;
import com.club.ui.text.ResolvedGlyph;
import com.club.ui.text.Weight;

/**
 * Lazy atlas registry that exposes the three Inter MSDF weights as a {@link GlyphSource}.
 *
 * <p>Atlas indices: 0 = REGULAR, 1 = MEDIUM, 2 = SEMIBOLD. Each atlas is loaded once on
 * first access and then cached for the lifetime of this registry.</p>
 *
 * <p>GLYPH-CACHE SEAM: {@link #resolve} currently consults only the atlas matching the
 * requested weight. A future implementation may try other atlases, families, or icon/emoji
 * atlases when the primary lookup misses, and write a different {@code out.atlasId} — the
 * layout and renderer are unaffected because they key the texture off {@code out.atlasId}.</p>
 */
public final class FontRegistry implements GlyphSource {

    // 0=REGULAR, 1=MEDIUM, 2=SEMIBOLD
    private final MsdfAtlas[] atlases = new MsdfAtlas[3];

    // -------------------------------------------------------------------------
    // GlyphSource
    // -------------------------------------------------------------------------

    /**
     * Resolves {@code codePoint} at {@code weight} into {@code out}.
     * Falls back to '?' if the primary code point is absent. Returns {@code false} only
     * when neither the glyph nor the '?' fallback exists in the atlas.
     *
     * <p>GLYPH-CACHE SEAM: future fallback may try other atlases/families/icon/emoji
     * atlases and set {@code out.atlasId} to that atlas — layout/renderer unchanged
     * because they key the texture off {@code out.atlasId}.</p>
     */
    @Override
    public boolean resolve(Weight weight, int codePoint, ResolvedGlyph out) {
        int id = atlasId(weight);
        MsdfAtlas a = atlas(id);
        MsdfMetrics.Glyph g = a.metrics.get(codePoint);
        if (g == null) g = a.metrics.get((int) '?');
        if (g == null) return false;
        out.atlasId = id;
        out.glyph   = g;
        return true;
    }

    /** Returns the metrics object for a given weight (line height, ascent, descent). */
    @Override
    public MsdfMetrics metrics(Weight weight) {
        return atlas(atlasId(weight)).metrics;
    }

    // -------------------------------------------------------------------------
    // Renderer accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the atlas for {@code id} (0/1/2). Used by the renderer to bind the
     * correct texture and read {@code pxRange}.
     */
    public MsdfAtlas atlasById(int id) {
        return atlas(id);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Maps a Weight enum to an atlas index (0/1/2). */
    private static int atlasId(Weight w) {
        return switch (w) {
            case REGULAR  -> 0;
            case MEDIUM   -> 1;
            case SEMIBOLD -> 2;
        };
    }

    /** Returns the name string used in the resource path for atlas index {@code id}. */
    private static String atlasName(int id) {
        return switch (id) {
            case 0  -> "regular";
            case 1  -> "medium";
            default -> "semibold";
        };
    }

    /** Drop cached atlas pages so the next {@link #resolve} reloads them — used to exercise the
     *  missing-atlas fail-safe. A future eviction policy (multi-atlas/emoji) would also live here. */
    public void clearCache() { java.util.Arrays.fill(atlases, null); }

    /** Lazy atlas accessor — loads on first access. */
    private MsdfAtlas atlas(int id) {
        if (atlases[id] == null) {
            atlases[id] = MsdfAtlas.load(atlasName(id));
        }
        return atlases[id];
    }
}
