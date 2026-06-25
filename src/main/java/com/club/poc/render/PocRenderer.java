package com.club.poc.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * NEW-stack shape facade — mirrors the call shape of {@code util.RenderHelper} but
 * draws every primitive through the analytic SDF shader ({@code club:club_sdf_shape}).
 * Rounded fills, borders, glow and gradients are computed per-fragment, so they are
 * resolution-independent: perfect AA corners, continuous glow (no quad stamps) and
 * smooth dithered gradients (no banding) at any matrix zoom. PoC-only.
 */
public final class PocRenderer {
    private PocRenderer() {}

    private static final int MODE_FILL = 0, MODE_BORDER = 1, MODE_GLOW = 2, MODE_GRADIENT = 3;

    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
        drawShape(ctx, x, y, w, h, 0f, MODE_FILL, radius, color, color, 0, 0f, 0f);
    }

    public static void roundedBorder(DrawContext ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
        drawShape(ctx, x, y, w, h, 0f, MODE_BORDER, radius, color, color, 0, 0f, thickness);
    }

    /** Soft continuous glow halo expanding {@code feather} px beyond the box. */
    public static void glow(DrawContext ctx, float x, float y, float w, float h, float radius, float feather, int color) {
        drawShape(ctx, x, y, w, h, feather, MODE_GLOW, radius, color, color, 0, feather, 0f);
    }

    public static void gradientRoundedRect(DrawContext ctx, float x, float y, float w, float h, float radius, int colorA, int colorB, boolean vertical) {
        drawShape(ctx, x, y, w, h, 0f, MODE_GRADIENT, radius, colorA, colorB, vertical ? 1 : 0, 0f, 0f);
    }

    // ----------------------------------------------------------------- internals

    private static void drawShape(DrawContext ctx, float x, float y, float w, float h, float pad,
                                  int mode, float radius, int colorA, int colorB, int gradAxis,
                                  float feather, float thickness) {
        if (!PocShaders.ready() || w <= 0 || h <= 0) return;
        ShaderProgram sh = PocShaders.SDF_SHAPE;

        float halfW = w / 2f, halfH = h / 2f;
        float cx = x + halfW, cy = y + halfH;
        float x0 = x - pad, y0 = y - pad, x1 = x + w + pad, y1 = y + h + pad;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> sh);
        setF2(sh, "HalfSize", halfW, halfH);
        setF1(sh, "Radius", radius);
        setF1(sh, "Softness", 1.0f);
        setI(sh, "Mode", mode);
        setI(sh, "GradAxis", gradAxis);
        setF1(sh, "Feather", Math.max(feather, 0.0001f));
        setF1(sh, "Thickness", Math.max(thickness, 0.0001f));
        setColor4(sh, "ColorB", colorB);

        int r = (colorA >>> 16) & 0xFF, g = (colorA >>> 8) & 0xFF, b = colorA & 0xFF, a = (colorA >>> 24) & 0xFF;
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(mat, x0, y0, 0f).texture(x0 - cx, y0 - cy).color(r, g, b, a);
        bb.vertex(mat, x0, y1, 0f).texture(x0 - cx, y1 - cy).color(r, g, b, a);
        bb.vertex(mat, x1, y1, 0f).texture(x1 - cx, y1 - cy).color(r, g, b, a);
        bb.vertex(mat, x1, y0, 0f).texture(x1 - cx, y0 - cy).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableCull();
    }

    private static void setF1(ShaderProgram sh, String n, float v) { GlUniform u = sh.getUniform(n); if (u != null) u.set(v); }
    private static void setF2(ShaderProgram sh, String n, float a, float b) { GlUniform u = sh.getUniform(n); if (u != null) u.set(a, b); }
    private static void setI(ShaderProgram sh, String n, int v) { GlUniform u = sh.getUniform(n); if (u != null) u.set(v); }
    private static void setColor4(ShaderProgram sh, String n, int argb) {
        GlUniform u = sh.getUniform(n);
        if (u != null) u.set(((argb >>> 16) & 0xFF) / 255f, ((argb >>> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }
}
