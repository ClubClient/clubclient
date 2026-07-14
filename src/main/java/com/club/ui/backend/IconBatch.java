package com.club.ui.backend;

import com.club.modules.perf.DrawBoxes;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.function.Supplier;

/**
 * The duotone icons, in ONE draw call (Stage 67).
 *
 * <p>What they cost before: {@code DrawContext.drawTexturedQuad} in 1.21.1 does not batch. It runs
 * {@code Tessellator.begin} -> four vertices -> {@code BufferBuilder.end} -> {@code drawWithGlobalProgram},
 * immediately, for every sprite — a fresh buffer, a shader bind and a texture bind each time. Measured on
 * the Club HUD: ~43 us per icon, four icons a frame, 0.173 ms — 35% of everything the HUD costs, and 4 of
 * its 15 GL draws. The profiler could not even see them: they never touched our backend's counter.
 *
 * <p>Two things had to be true for this to be a batch and not a redesign:
 *
 * <ul>
 *   <li><b>One texture.</b> Every sprite carried its own baked mask, so every sprite was its own texture
 *       bind and its own draw. {@code PixelIcons} now bakes into a shared atlas and passes UVs.</li>
 *   <li><b>The tint per vertex, without losing it.</b> The old path shipped the LIFTED tint as a float
 *       uniform, and an 8-bit vertex colour cannot carry that float. So the vertex carries the RAW tint
 *       byte — exact in 8 bits — and {@code ui_icon.fsh} does the lift and the clamp, in the same two float
 *       operations the CPU used to do. The colour is the same number, not a rounded one. Only the alpha is
 *       quantised, and only while an element is fading — which is precisely what our text has always done.</li>
 * </ul>
 *
 * <p>ORDER. Icons now go out together, at the end of the pass, i.e. after text and shapes that used to be
 * drawn between them. That is invisible only if nothing drawn after an icon overlaps it — a directional
 * property, asserted for real (armour + effects, every GUI scale) by {@link DrawBoxes}. The one thing the
 * batch does NOT outlive is a CLIP: a scissor change flushes it, so an icon can never escape the rectangle
 * it was drawn inside.
 */
public final class IconBatch {
    private IconBatch() {}

    private static final int MAX = 256;
    private static final float[] q = new float[MAX * 8];   // x0,y0,x1,y1,u0,v0,u1,v1 — pose space
    private static final int[] col = new int[MAX];         // packed ARGB: RGB = the RAW tint, A = alpha
    private static int n;
    private static Identifier atlas;

    private static final Supplier<ShaderProgram> SHADER = () -> UiShaders.ICON;

    /** GL draws this batch has issued since the counter was last read. One per flush, not one per icon. */
    public static int DRAWS;

    /** True when the batch can be used at all; a missing shader falls the caller back to the old path. */
    public static boolean ready() { return UiShaders.iconReady(); }

    /**
     * Queue one icon. Corners are in the caller's local space and are pre-transformed by {@code mat} here,
     * exactly as the text batch does — a later matrix change must not be able to move a queued sprite.
     * {@code argb} carries the RAW tint bytes in RGB (the shader lifts them) and the alpha in A.
     */
    public static void quad(Identifier tex, Matrix4f mat,
                            float x0, float y0, float x1, float y1,
                            float u0, float v0, float u1, float v1, int argb) {
        if (atlas != null && !atlas.equals(tex)) flush();   // a second atlas is a second draw, not a bug
        if (n >= MAX) flush();
        atlas = tex;

        float m00 = mat.m00(), m10 = mat.m10(), m30 = mat.m30();
        float m01 = mat.m01(), m11 = mat.m11(), m31 = mat.m31();
        int o = n * 8;
        q[o]     = m00 * x0 + m10 * y0 + m30;
        q[o + 1] = m01 * x0 + m11 * y0 + m31;
        q[o + 2] = m00 * x1 + m10 * y1 + m30;
        q[o + 3] = m01 * x1 + m11 * y1 + m31;
        q[o + 4] = u0; q[o + 5] = v0; q[o + 6] = u1; q[o + 7] = v1;
        col[n] = argb;
        n++;

        DrawBoxes.add(DrawBoxes.ICON, q[o], q[o + 1], q[o + 2], q[o + 3]);
    }

    /** Submit every queued icon in one draw. Safe any time; a no-op when nothing is queued. */
    public static void flush() {
        if (n == 0 || atlas == null) { n = 0; return; }
        int count = n;
        n = 0;
        Identifier tex = atlas;
        atlas = null;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(SHADER);
        RenderSystem.setShaderTexture(0, tex);

        BufferBuilder bb = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (int i = 0; i < count; i++) {
            int o = i * 8;
            float x0 = q[o], y0 = q[o + 1], x1 = q[o + 2], y1 = q[o + 3];
            float u0 = q[o + 4], v0 = q[o + 5], u1 = q[o + 6], v1 = q[o + 7];
            int c = col[i];
            int r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF, a = (c >>> 24) & 0xFF;
            bb.vertex(x0, y0, 0f).texture(u0, v0).color(r, g, b, a);
            bb.vertex(x0, y1, 0f).texture(u0, v1).color(r, g, b, a);
            bb.vertex(x1, y1, 0f).texture(u1, v1).color(r, g, b, a);
            bb.vertex(x1, y0, 0f).texture(u1, v0).color(r, g, b, a);
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableCull();
        DRAWS++;
    }
}
