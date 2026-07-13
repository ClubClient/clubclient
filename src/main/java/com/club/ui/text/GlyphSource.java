package com.club.ui.text;

/** Resolves code points to atlas glyphs and exposes per-weight metrics. Implementations may consult
 *  multiple atlases / font families / fallback (glyph-cache seam) without changing layout. */
public interface GlyphSource {
    /** Resolve {@code codePoint} at {@code weight} into {@code out} (atlasId + glyph). Returns false if
     *  unrenderable (after fallback). {@code out} is caller-owned and reused — do NOT retain it. */
    boolean resolve(Weight weight, int codePoint, ResolvedGlyph out);

    /** Metrics for a weight (line height / ascent / descent). */
    MsdfMetrics metrics(Weight weight);

    /**
     * Bumps whenever {@link #resolve} could start answering differently for the same input — the only
     * such event today is the icon atlas failing and PUA code points falling back to '?'. {@link TextLayout}
     * caches resolutions, and a cache that cannot be told "your answers are stale" is a bug waiting for a
     * broken resource pack.
     */
    default int epoch() { return 0; }
}
