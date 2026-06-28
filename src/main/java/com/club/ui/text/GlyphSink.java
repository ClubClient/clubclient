package com.club.ui.text;

/** Receives positioned glyph quads from layout. Extension point for BOTH rendering and diagnostics
 *  (a debug sink can record glyph bounds / baseline / uv / atlas page). */
public interface GlyphSink {
    void glyph(int atlasId, float x0, float y0, float x1, float y1,
               float u0, float v0, float u1, float v1);
}
