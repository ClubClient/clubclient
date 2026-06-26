package com.club.ui.text;

/** Resolves code points to atlas glyphs and exposes per-weight metrics. Implementations may consult
 *  multiple atlases / font families / fallback (glyph-cache seam) without changing layout. */
public interface GlyphSource {
    /** Resolve a code point at a weight to its atlas+glyph, or null if unrenderable (after fallback). */
    ResolvedGlyph resolve(Weight weight, int codePoint);
    /** Metrics for a weight (line height / ascent / descent). */
    MsdfMetrics metrics(Weight weight);
}
