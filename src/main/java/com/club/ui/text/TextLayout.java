package com.club.ui.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure text layout (Metrics -> Layout). No Minecraft/GL. Iterates by Unicode CODE POINT, so the API
 *  never assumes {@code char}; Stage 1 atlas coverage is BMP, and supplementary code points simply
 *  resolve to null and are skipped. Swappable independently of the renderer (future: bidi / RTL / shaping).
 *  The render path ({@link #layoutLine}, {@link #width}) allocates nothing on a cache hit; {@link #wrap}
 *  allocates (layout-time, not per-frame hot path).
 *
 *  <p>RESOLUTION IS CACHED, ARITHMETIC IS NOT (Stage 65). Measured: shaping is the largest single slice of
 *  what the Club HUD costs per frame, and almost all of it is the same few strings — "FPS", "HP", a mob's
 *  name, an effect's level — re-resolved code point by code point, several times a frame, because layout
 *  calls {@link #width} repeatedly on strings it is about to draw anyway. The cache holds only the RESULT
 *  OF RESOLUTION (which glyph, which atlas). Every float operation that follows is still performed, in the
 *  same order, on the same values — so the quads are bit-identical to the uncached path, by construction
 *  rather than by inspection. Caching the POSITIONS instead would have been faster and wrong: float
 *  addition is not associative, and (x - advance/2) + pl*size is not the same number as
 *  ((-advance/2) + pl*size) + x.</p>
 */
public final class TextLayout {
    private final GlyphSource source;
    private final ResolvedGlyph scratch = new ResolvedGlyph();

    public TextLayout(GlyphSource source) { this.source = source; }

    // ---- resolution cache ---------------------------------------------------

    /** One string's resolved glyphs, in order, skipping the unresolvable ones exactly as the loops did. */
    private record Shaped(MsdfMetrics.Glyph[] glyphs, int[] atlasIds) {}

    /** Bounded per-weight LRU. The HUD's live strings number in the tens; the menu's in the low hundreds.
     *  A cap keeps a pathological caller (a name-tag stream, a chat mirror) from turning this into a leak. */
    private static final int CAP = 256;

    @SuppressWarnings("unchecked")
    private final Map<String, Shaped>[] cache = new Map[Weight.values().length];
    private int cachedEpoch;

    private Shaped shaped(String text, Weight weight) {
        // The source can start answering differently (the icon atlas dying mid-session): drop everything.
        int e = source.epoch();
        if (e != cachedEpoch) { for (int i = 0; i < cache.length; i++) cache[i] = null; cachedEpoch = e; }

        int w = weight.ordinal();
        Map<String, Shaped> m = cache[w];
        if (m == null) {
            m = new LinkedHashMap<>(64, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Shaped> eldest) {
                    return size() > CAP;
                }
            };
            cache[w] = m;
        }
        Shaped s = m.get(text);
        if (s != null) return s;

        int n = 0;
        MsdfMetrics.Glyph[] gs = new MsdfMetrics.Glyph[text.length()];
        int[] ids = new int[text.length()];
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!source.resolve(weight, cp, scratch)) continue;   // skipped, exactly as the old loops did
            gs[n] = scratch.glyph;
            ids[n] = scratch.atlasId;
            n++;
        }
        s = new Shaped(java.util.Arrays.copyOf(gs, n), java.util.Arrays.copyOf(ids, n));
        m.put(text, s);
        return s;
    }

    // ---- render path --------------------------------------------------------

    /**
     * A/B SEAM (harness only). Off, {@link #width} and {@link #layoutLine} run the ORIGINAL resolve-every-
     * code-point-every-call loops, unchanged and allocation-free — not "the cache with a cold map", which
     * would be slower than the code it replaced and would flatter the cache. The benchmark toggles this
     * inside ONE session, because comparing two runs is exactly the mistake this whole stage exists to fix.
     * Production never touches it.
     */
    public static volatile boolean cacheEnabled = true;

    /** Advance width (px) of one line, without rendering. Allocation-free on a cache hit. */
    public float width(String text, Weight weight, float size) {
        if (!cacheEnabled) return widthUncached(text, weight, size);
        MsdfMetrics.Glyph[] gs = shaped(text, weight).glyphs();
        float w = 0f;
        for (MsdfMetrics.Glyph g : gs) w += g.advance * size;   // same adds, same order as the resolve loop
        return w;
    }

    /** Lay out one line with TOP-LEFT at (x, yTop) before alignment; emit glyph quads to {@code sink}.
     *  {@code align} shifts horizontally relative to x. Returns advance width. Allocation-free on a hit. */
    public float layoutLine(String text, Weight weight, float size, float x, float yTop, Align align, GlyphSink sink) {
        if (!cacheEnabled) return layoutLineUncached(text, weight, size, x, yTop, align, sink);
        Shaped s = shaped(text, weight);
        MsdfMetrics.Glyph[] gs = s.glyphs();
        int[] ids = s.atlasIds();

        float advance = 0f;
        for (MsdfMetrics.Glyph g : gs) advance += g.advance * size;

        float penX = x;
        if (align == Align.CENTER) penX -= advance * 0.5f;
        else if (align == Align.RIGHT) penX -= advance;

        float baseY = yTop + source.metrics(weight).ascent(size);
        for (int i = 0; i < gs.length; i++) {
            MsdfMetrics.Glyph g = gs[i];
            if (g.hasBounds) {
                float x0 = penX + g.pl * size, x1 = penX + g.pr * size;
                float y0 = baseY - g.pt * size, y1 = baseY - g.pb * size;
                sink.glyph(ids[i], x0, y0, x1, y1, g.u0, g.v0, g.u1, g.v1);
            }
            penX += g.advance * size;
        }
        return advance;
    }

    // ---- the pre-cache path, kept verbatim as the A/B control ---------------

    private float widthUncached(String text, Weight weight, float size) {
        float w = 0f;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (source.resolve(weight, cp, scratch)) w += scratch.glyph.advance * size;
        }
        return w;
    }

    private float layoutLineUncached(String text, Weight weight, float size,
                                     float x, float yTop, Align align, GlyphSink sink) {
        float advance = widthUncached(text, weight, size);
        float penX = x;
        if (align == Align.CENTER) penX -= advance * 0.5f;
        else if (align == Align.RIGHT) penX -= advance;

        float baseY = yTop + source.metrics(weight).ascent(size);
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!source.resolve(weight, cp, scratch)) continue;
            MsdfMetrics.Glyph g = scratch.glyph;
            if (g.hasBounds) {
                float x0 = penX + g.pl * size, x1 = penX + g.pr * size;
                float y0 = baseY - g.pt * size, y1 = baseY - g.pb * size;
                sink.glyph(scratch.atlasId, x0, y0, x1, y1, g.u0, g.v0, g.u1, g.v1);
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
