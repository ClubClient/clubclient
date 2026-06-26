package com.club.ui.text;

import java.util.ArrayList;
import java.util.List;

/** Pure text layout (Metrics -> Layout). No Minecraft/GL. Iterates by Unicode CODE POINT, so the API
 *  never assumes {@code char}; Stage 1 atlas coverage is BMP, and supplementary code points simply
 *  resolve to null and are skipped. Swappable independently of the renderer (future: bidi / RTL / shaping).
 *  The render path ({@link #layoutLine}, {@link #width}) allocates nothing; {@link #wrap} allocates
 *  (layout-time, not per-frame hot path). */
public final class TextLayout {
    private final GlyphSource source;
    public TextLayout(GlyphSource source) { this.source = source; }

    /** Advance width (px) of one line, without rendering. Allocation-free. */
    public float width(String text, Weight weight, float size) {
        float w = 0f;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            ResolvedGlyph rg = source.resolve(weight, cp);
            if (rg != null) w += rg.glyph().advance * size;
        }
        return w;
    }

    /** Lay out one line with TOP-LEFT at (x, yTop) before alignment; emit glyph quads to {@code sink}.
     *  {@code align} shifts horizontally relative to x. Returns advance width. Allocation-free. */
    public float layoutLine(String text, Weight weight, float size, float x, float yTop, Align align, GlyphSink sink) {
        float advance = width(text, weight, size);
        float penX = x;
        if (align == Align.CENTER) penX -= advance * 0.5f;
        else if (align == Align.RIGHT) penX -= advance;

        float baseY = yTop + source.metrics(weight).ascent(size);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            ResolvedGlyph rg = source.resolve(weight, cp);
            if (rg == null) continue;
            MsdfMetrics.Glyph g = rg.glyph();
            if (g.hasBounds) {
                float x0 = penX + g.pl * size, x1 = penX + g.pr * size;
                float y0 = baseY - g.pt * size, y1 = baseY - g.pb * size;
                sink.glyph(rg.atlasId(), x0, y0, x1, y1, g.u0, g.v0, g.u1, g.v1);
            }
            penX += g.advance * size;
        }
        return advance;
    }

    /** Greedy word wrap by max width (px). Layout-time (allocates). */
    public List<String> wrap(String text, Weight weight, float size, float maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String cand = line.length() == 0 ? word : line + " " + word;
            if (width(cand, weight, size) <= maxWidth || line.length() == 0) { line.setLength(0); line.append(cand); }
            else { out.add(line.toString()); line.setLength(0); line.append(word); }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
