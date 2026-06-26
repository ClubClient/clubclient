package com.club.ui.text;

/** A glyph resolved to its atlas page + metrics. {@code atlasId} is opaque to layout — the renderer
 *  maps it to a GL texture. Glyph-cache seam: ids may span multiple atlases/families/fallback. */
public record ResolvedGlyph(int atlasId, MsdfMetrics.Glyph glyph) {}
