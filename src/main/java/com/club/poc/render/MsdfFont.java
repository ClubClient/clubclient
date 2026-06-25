package com.club.poc.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One MSDF font weight: atlas texture + glyph metrics produced offline by
 * msdf-atlas-gen ({@code assets/club/poc/msdf/inter_<weight>.png|json}). Glyphs are
 * drawn through the {@code club:club_msdf_text} shader, so text stays crisp at any
 * matrix zoom — the whole point of the PoC. PoC-only; nothing here is wired into
 * the live client.
 */
public final class MsdfFont {

    /** baseline offset as a fraction of the requested pixel size (top → baseline). */
    private static final float ASCENT = 0.82f;

    private static final class Glyph {
        float advance;
        boolean hasBounds;
        float pl, pb, pr, pt;   // plane bounds, em (baseline-relative, y up)
        float u0, v0, u1, v1;   // atlas UVs (top-left origin)
    }

    private final String weight;
    private final Identifier jsonId, pngId, texId;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private int atlasW, atlasH;
    private float distanceRange = 6f;
    private boolean metricsLoaded, textureLoaded;

    private MsdfFont(String weight) {
        this.weight = weight;
        this.jsonId = Identifier.of("club", "poc/msdf/inter_" + weight + ".json");
        this.pngId  = Identifier.of("club", "poc/msdf/inter_" + weight + ".png");
        this.texId  = Identifier.of("club", "poc_msdf_inter_" + weight);
    }

    public static MsdfFont load(String weight) { return new MsdfFont(weight); }

    private void ensureMetrics() {
        if (metricsLoaded) return;
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        try (InputStream in = rm.getResourceOrThrow(jsonId).getInputStream()) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject atlas = root.getAsJsonObject("atlas");
            atlasW = atlas.get("width").getAsInt();
            atlasH = atlas.get("height").getAsInt();
            distanceRange = atlas.get("distanceRange").getAsFloat();
            for (JsonElement ge : root.getAsJsonArray("glyphs")) {
                JsonObject g = ge.getAsJsonObject();
                Glyph gl = new Glyph();
                gl.advance = g.get("advance").getAsFloat();
                if (g.has("planeBounds") && g.has("atlasBounds")) {
                    JsonObject pb = g.getAsJsonObject("planeBounds");
                    gl.pl = pb.get("left").getAsFloat();   gl.pr = pb.get("right").getAsFloat();
                    gl.pb = pb.get("bottom").getAsFloat();  gl.pt = pb.get("top").getAsFloat();
                    JsonObject ab = g.getAsJsonObject("atlasBounds");
                    float al = ab.get("left").getAsFloat(),  ar = ab.get("right").getAsFloat();
                    float abm = ab.get("bottom").getAsFloat(), at = ab.get("top").getAsFloat();
                    gl.u0 = al / atlasW;  gl.u1 = ar / atlasW;
                    gl.v0 = 1f - at / atlasH;   // atlas yOrigin=bottom → flip to top-left
                    gl.v1 = 1f - abm / atlasH;
                    gl.hasBounds = true;
                }
                glyphs.put(g.get("unicode").getAsInt(), gl);
            }
            metricsLoaded = true;
        } catch (Exception e) {
            throw new RuntimeException("MSDF metrics load failed for " + weight, e);
        }
    }

    private void ensureTexture() {
        if (textureLoaded) return;
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        try (InputStream in = rm.getResourceOrThrow(pngId).getInputStream()) {
            NativeImage img = NativeImage.read(in);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            tex.setFilter(true, false); // bilinear sampling of the distance field
            MinecraftClient.getInstance().getTextureManager().registerTexture(texId, tex);
            textureLoaded = true;
        } catch (Exception e) {
            throw new RuntimeException("MSDF atlas load failed for " + weight, e);
        }
    }

    public float width(String s, float size) {
        ensureMetrics();
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            Glyph gl = glyphs.get((int) s.charAt(i));
            if (gl == null) gl = glyphs.get((int) '?');
            if (gl != null) w += gl.advance * size;
        }
        return w;
    }

    /** Draws {@code s} with its TOP-LEFT at (x, yTop). Returns the x advance. */
    public float drawString(DrawContext ctx, String s, float x, float yTop, float size, int color) {
        ensureMetrics();
        ensureTexture();
        if (!PocShaders.ready() || s.isEmpty()) return x;

        float baseY = yTop + size * ASCENT;
        int r = (color >>> 16) & 0xFF, g = (color >>> 8) & 0xFF, b = color & 0xFF, a = (color >>> 24) & 0xFF;

        // collect visible quads first (avoid begin() on an all-whitespace string)
        List<float[]> quads = new ArrayList<>();
        float pen = x;
        for (int i = 0; i < s.length(); i++) {
            Glyph gl = glyphs.get((int) s.charAt(i));
            if (gl == null) gl = glyphs.get((int) '?');
            if (gl == null) continue;
            if (gl.hasBounds) {
                float xL = pen + gl.pl * size, xR = pen + gl.pr * size;
                float yT = baseY - gl.pt * size, yB = baseY - gl.pb * size;
                quads.add(new float[]{xL, yT, xR, yB, gl.u0, gl.v0, gl.u1, gl.v1});
            }
            pen += gl.advance * size;
        }
        if (quads.isEmpty()) return pen;

        ShaderProgram sh = PocShaders.MSDF_TEXT;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, texId);
        GlUniform px = sh.getUniform("PxRange");
        if (px != null) px.set(distanceRange);

        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (float[] q : quads) {
            float xL = q[0], yT = q[1], xR = q[2], yB = q[3], u0 = q[4], v0 = q[5], u1 = q[6], v1 = q[7];
            bb.vertex(mat, xL, yT, 0f).texture(u0, v0).color(r, g, b, a);
            bb.vertex(mat, xL, yB, 0f).texture(u0, v1).color(r, g, b, a);
            bb.vertex(mat, xR, yB, 0f).texture(u1, v1).color(r, g, b, a);
            bb.vertex(mat, xR, yT, 0f).texture(u1, v0).color(r, g, b, a);
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableCull();
        return pen;
    }
}
