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

    // 0=REGULAR, 1=MEDIUM, 2=SEMIBOLD, 3=ICONS (Stage 11 — PUA-mapped SDF icon glyphs)
    private final MsdfAtlas[] atlases = new MsdfAtlas[4];
    private static final int ICONS = 3;
    /** Set on the first icon-atlas load failure: PUA lookups then fall through to the '?' path
     *  instead of re-throwing every frame (a missing icon atlas must not break text). */
    private boolean iconsBroken;
    /** Bumped when icon resolution dies — see {@link GlyphSource#epoch()}. Anything caching a resolution
     *  (TextLayout does) must drop it: the same PUA code point now answers '?' instead of an icon glyph. */
    private int epoch;

    // -------------------------------------------------------------------------
    // GlyphSource
    // -------------------------------------------------------------------------

    /**
     * Resolves {@code codePoint} at {@code weight} into {@code out}.
     * Private-Use-Area code points (U+E000..U+F8FF) resolve through the ICON atlas first
     * (the icon seam — an icon IS a glyph; layout/renderer key the texture off
     * {@code out.atlasId}). Everything else falls back to '?' if the primary code point
     * is absent. Returns {@code false} only when nothing resolves.
     */
    @Override
    public boolean resolve(Weight weight, int codePoint, ResolvedGlyph out) {
        if (codePoint >= 0xE000 && codePoint <= 0xF8FF && !iconsBroken) {
            try {
                MsdfMetrics.Glyph ig = atlas(ICONS).metrics.get(codePoint);
                if (ig != null) {
                    out.atlasId = ICONS;
                    out.glyph   = ig;
                    return true;
                }
            } catch (Exception e) {
                iconsBroken = true; epoch++;   // cached resolutions are now wrong — see epoch()
                System.err.println("[club.ui] icon atlas unavailable — icons disabled: " + e);
            }
        }
        int id = atlasId(weight);
        MsdfAtlas a = atlas(id);
        MsdfMetrics.Glyph g = a.metrics.get(codePoint);
        if (g == null) g = a.metrics.get((int) '?');
        if (g == null) return false;
        out.atlasId = id;
        out.glyph   = g;
        return true;
    }

    @Override
    public int epoch() { return epoch; }

    /** Returns the metrics object for a given weight (line height, ascent, descent). */
    @Override
    public MsdfMetrics metrics(Weight weight) {
        return atlas(atlasId(weight)).metrics;
    }

    // -------------------------------------------------------------------------
    // Renderer accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the atlas for {@code id} (0/1/2/3). Used by the renderer to bind the
     * correct texture and read {@code pxRange}.
     */
    public MsdfAtlas atlasById(int id) {
        return atlas(id);
    }

    /** True if {@code id} is the icon atlas — the renderer keeps ICON failures non-fatal. */
    public boolean isIconAtlas(int id) { return id == ICONS; }

    /**
     * Disables icon resolution for the session — called by the renderer when the icon TEXTURE
     * fails after a successful metrics load (icons.json fine, icons.png missing/corrupt).
     * Subsequent PUA lookups fall through to the '?' path; text keeps rendering MODERN.
     */
    public void disableIcons(Exception cause) {
        if (iconsBroken) return;
        System.err.println("[club.ui] icon atlas texture unavailable — icons disabled: " + cause);
        iconsBroken = true;
        epoch++;   // cached resolutions are now wrong — see epoch()
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

    /** Lazy atlas accessor — loads on first access. */
    private MsdfAtlas atlas(int id) {
        if (atlases[id] == null) {
            atlases[id] = (id == ICONS) ? MsdfAtlas.loadIcons() : MsdfAtlas.load(atlasName(id));
        }
        return atlases[id];
    }
}
