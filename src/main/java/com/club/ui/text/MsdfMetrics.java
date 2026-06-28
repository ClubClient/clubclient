package com.club.ui.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.TreeMap;

/** Pure model + parser of msdf-atlas-gen JSON. No Minecraft/GL dependencies. */
public final class MsdfMetrics {
    public static final class Glyph {
        public float advance;
        public boolean hasBounds;
        public float pl, pb, pr, pt;     // plane bounds, em (baseline-relative, y up)
        public float u0, v0, u1, v1;     // atlas UVs (top-left origin)
    }

    public int atlasW, atlasH;
    public float distanceRange;
    public float emLineHeight, emAscender, emDescender;

    // int-keyed lookup — no autoboxing on get()
    private int[]   keys = new int[0];
    private Glyph[] vals = new Glyph[0];

    public static MsdfMetrics parse(String json) {
        MsdfMetrics m = new MsdfMetrics();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject atlas = root.getAsJsonObject("atlas");
        m.atlasW = atlas.get("width").getAsInt();
        m.atlasH = atlas.get("height").getAsInt();
        m.distanceRange = atlas.get("distanceRange").getAsFloat();
        JsonObject me = root.getAsJsonObject("metrics");
        m.emLineHeight = me.get("lineHeight").getAsFloat();
        m.emAscender  = me.get("ascender").getAsFloat();
        m.emDescender = me.get("descender").getAsFloat();

        // Collect into TreeMap (load-time only — not a hot path)
        TreeMap<Integer, Glyph> tmp = new TreeMap<>();
        for (JsonElement ge : root.getAsJsonArray("glyphs")) {
            JsonObject g = ge.getAsJsonObject();
            Glyph gl = new Glyph();
            gl.advance = g.has("advance") ? g.get("advance").getAsFloat() : 0f;
            if (g.has("planeBounds") && g.has("atlasBounds")) {
                JsonObject pb = g.getAsJsonObject("planeBounds");
                gl.pl = pb.get("left").getAsFloat();  gl.pr = pb.get("right").getAsFloat();
                gl.pb = pb.get("bottom").getAsFloat(); gl.pt = pb.get("top").getAsFloat();
                JsonObject ab = g.getAsJsonObject("atlasBounds");
                float al = ab.get("left").getAsFloat(), ar = ab.get("right").getAsFloat();
                float abm = ab.get("bottom").getAsFloat(), at = ab.get("top").getAsFloat();
                gl.u0 = al / m.atlasW; gl.u1 = ar / m.atlasW;
                gl.v0 = 1f - at / m.atlasH;   // atlas yOrigin=bottom → flip
                gl.v1 = 1f - abm / m.atlasH;
                gl.hasBounds = true;
            }
            tmp.put(g.get("unicode").getAsInt(), gl);
        }

        // Materialize sorted parallel arrays
        int n = tmp.size();
        m.keys = new int[n];
        m.vals = new Glyph[n];
        int i = 0;
        for (java.util.Map.Entry<Integer, Glyph> e : tmp.entrySet()) {
            m.keys[i] = e.getKey();
            m.vals[i] = e.getValue();
            i++;
        }
        return m;
    }

    public Glyph get(int cp) {
        int idx = Arrays.binarySearch(keys, cp);
        return idx >= 0 ? vals[idx] : null;
    }

    public float advanceOf(int cp) {
        Glyph g = get(cp);
        return g == null ? 0f : g.advance;
    }

    public float width(String s, float size) {
        float w = 0;
        int len = s.length();
        for (int i = 0; i < len; ) {
            int cp = s.codePointAt(i);
            Glyph g = get(cp);
            if (g == null) g = get('?');
            if (g != null) w += g.advance * size;
            i += Character.charCount(cp);
        }
        return w;
    }

    public float lineHeight(float size) { return emLineHeight * size; }
    /** Pixels above the baseline (positive). */
    public float ascent(float size) { return emAscender * size; }
    /** Pixels below the baseline (positive magnitude; em descender is stored negative). */
    public float descent(float size) { return -emDescender * size; }
}
