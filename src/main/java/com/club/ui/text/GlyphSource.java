package com.club.ui.text;

/** Resolves code points to atlas glyphs and exposes per-weight metrics. Implementations may consult
 *  multiple atlases / font families / fallback (glyph-cache seam) without changing layout. */
public interface GlyphSource {
    /** Resolve {@code codePoint} at {@code weight} into {@code out} (atlasId + glyph). Returns false if
     *  unrenderable (after fallback). {@code out} is caller-owned and reused — do NOT retain it. */
    boolean resolve(Weight weight, int codePoint, ResolvedGlyph out);

    /** Metrics for a weight (line height / ascent / descent). */
    MsdfMetrics metrics(Weight weight);
}
