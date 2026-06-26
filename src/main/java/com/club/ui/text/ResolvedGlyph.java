package com.club.ui.text;

/** Mutable holder filled by {@link GlyphSource#resolve}. Reused per layout to avoid per-glyph
 *  allocation on the text hot path; implementations must not retain a reference to it. */
public final class ResolvedGlyph {
    public int atlasId;
    public MsdfMetrics.Glyph glyph;
}
