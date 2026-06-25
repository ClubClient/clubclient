package com.club.ui.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;

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
    private final Map<Integer, Glyph> glyphs = new HashMap<>();

    public static MsdfMetrics parse(String json) {
        MsdfMetrics m = new MsdfMetrics();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject atlas = root.getAsJsonObject("atlas");
        m.atlasW = atlas.get("width").getAsInt();
        m.atlasH = atlas.get("height").getAsInt();
        m.distanceRange = atlas.get("distanceRange").getAsFloat();
        JsonObject me = root.getAsJsonObject("metrics");
        m.emLineHeight = me.get("lineHeight").getAsFloat();
        m.emAscender = me.get("ascender").getAsFloat();
        m.emDescender = me.get("descender").getAsFloat();
        for (JsonElement ge : root.getAsJsonArray("glyphs")) {
            JsonObject g = ge.getAsJsonObject();
            Glyph gl = new Glyph();
            gl.advance = g.get("advance").getAsFloat();
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
            m.glyphs.put(g.get("unicode").getAsInt(), gl);
        }
        return m;
    }

    public Glyph get(int cp) { return glyphs.get(cp); }
    public float advanceOf(int cp) { Glyph g = glyphs.get(cp); return g == null ? 0f : g.advance; }

    public float width(String s, float size) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            Glyph g = glyphs.get((int) s.charAt(i));
            if (g == null) g = glyphs.get((int) '?');
            if (g != null) w += g.advance * size;
        }
        return w;
    }
    public float lineHeight(float size) { return emLineHeight * size; }
    public float ascent(float size) { return emAscender * size; }
    public float descent(float size) { return -emDescender * size; }
}
