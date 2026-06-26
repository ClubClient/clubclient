package com.club.ui.backend;

import com.club.ui.Axis;
import com.club.ui.Color;
import com.club.ui.Radii;
import com.club.ui.UiRenderer;
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
import org.joml.Matrix4f;

/**
 * UiRenderer implementation backed by the analytic SDF shader (UiShaders.SDF).
 *
 * <p>Design goals (Stage 1):</p>
 * <ul>
 *   <li>Allocation-free render path — no per-frame object allocation from our own code.</li>
 *   <li>Batching-ready — all quad emission funnelled through {@link #drawShapeQuad}.</li>
 *   <li>Full clip stack with nested intersection, backed by primitive arrays.</li>
 *   <li>GPU-first — AA, rounding, glow, gradient and shadow are shader-only.</li>
 *   <li>Resolution-independent ({@link #isResolutionIndependent()} returns {@code true}).</li>
 * </ul>
 *
 * <p>Known Stage-1 limitation: each shape issues one {@code Tessellator.begin()} / draw /
 * {@code BufferBuilder.end()} triple (immediate mode). This is the ONLY unavoidable
 * per-draw allocation in Stage 1 — it comes from Minecraft's own buffer API. See the
 * BATCHING SEAM comment in {@link #drawShapeQuad} for the future upgrade path.</p>
 */
public final class ModernBackend implements UiRenderer {

    // -------------------------------------------------------------------------
    // Shader supplier cached ONCE — no lambda allocation per draw call.
    // -------------------------------------------------------------------------
    private final java.util.function.Supplier<ShaderProgram> sdfSupplier = () -> UiShaders.SDF;

    // -------------------------------------------------------------------------
    // Per-frame state
    // -------------------------------------------------------------------------
    private DrawContext ctx;

    // -------------------------------------------------------------------------
    // Opacity stack — primitive flat array, no boxing/allocation per push.
    // Stores the ACCUMULATED multiplier at each depth level.
    // -------------------------------------------------------------------------
    private static final int MAX_STACK = 64;
    private final float[] opacityStack = new float[MAX_STACK];
    private int opacityTop = 0;   // points to next free slot (0 == empty)

    // -------------------------------------------------------------------------
    // Clip stack — primitive flat array: [x, y, w, h, radius] per entry.
    // Nested clips INTERSECT with their parent. radius is stored for future
    // rounded-clip shader mask support (Stage 1 applies GL scissor rect only).
    // -------------------------------------------------------------------------
    private static final int CLIP_STRIDE = 5;               // fields per clip entry
    private final float[] clipStack = new float[MAX_STACK * CLIP_STRIDE];
    private int clipTop = 0;   // number of active clip entries

    // -------------------------------------------------------------------------
    // Frame lifecycle
    // -------------------------------------------------------------------------

    /**
     * Call once per frame before issuing any draw commands.
     * Resets opacity to 1.0 and discards any leftover clip entries.
     */
    public void begin(DrawContext drawContext) {
        this.ctx = drawContext;
        opacityTop = 0;
        clipTop = 0;
    }

    // -------------------------------------------------------------------------
    // UiRenderer — meta
    // -------------------------------------------------------------------------

    @Override
    public boolean isResolutionIndependent() { return true; }

    // -------------------------------------------------------------------------
    // UiRenderer — solid shapes
    // -------------------------------------------------------------------------

    @Override
    public void rect(float x, float y, float w, float h, int color) {
        // Routes through primitive-radii overload — no Radii record allocation.
        shapeWithRadii(x, y, w, h, 0f, 0f, 0f, 0f, 0f, MODE_FILL, color, color, 0, 0f, 0f);
    }

    @Override
    public void roundedRect(float x, float y, float w, float h, float radius, int color) {
        // Routes through primitive-radii overload — no Radii record allocation.
        shapeWithRadii(x, y, w, h, radius, radius, radius, radius, 0f, MODE_FILL, color, color, 0, 0f, 0f);
    }

    @Override
    public void roundedRect(float x, float y, float w, float h, Radii radii, int color) {
        // Caller owns the Radii record; we just unpack the four floats — no new alloc.
        shapeWithRadii(x, y, w, h, radii.tl(), radii.tr(), radii.br(), radii.bl(), 0f,
                MODE_FILL, color, color, 0, 0f, 0f);
    }

    @Override
    public void border(float x, float y, float w, float h, float radius, float thickness, int color) {
        shapeWithRadii(x, y, w, h, radius, radius, radius, radius, 0f,
                MODE_BORDER, color, color, 0, 0f, Math.max(thickness, 1e-4f));
    }

    @Override
    public void circle(float cx, float cy, float r, int color) {
        // A circle is a fully-rounded rect; routes through primitive overload (no Radii alloc).
        shapeWithRadii(cx - r, cy - r, r * 2f, r * 2f, r, r, r, r, 0f,
                MODE_FILL, color, color, 0, 0f, 0f);
    }

    // -------------------------------------------------------------------------
    // UiRenderer — gradient
    // -------------------------------------------------------------------------

    @Override
    public void gradient(float x, float y, float w, float h, float radius,
                         int colorA, int colorB, Axis axis) {
        int gradAxis = (axis == Axis.VERTICAL) ? 1 : 0;
        shapeWithRadii(x, y, w, h, radius, radius, radius, radius, 0f,
                MODE_GRADIENT, colorA, colorB, gradAxis, 0f, 0f);
    }

    // -------------------------------------------------------------------------
    // UiRenderer — glow / shadow  (GPU-only via shader Mode = glow)
    // -------------------------------------------------------------------------

    @Override
    public void glow(float x, float y, float w, float h, float radius, float size, int color) {
        // Expand quad by `size` on each side so the falloff has space.
        shapeWithRadii(x, y, w, h, radius, radius, radius, radius, size,
                MODE_GLOW, color, color, 0, size, 0f);
    }

    @Override
    public void shadow(float x, float y, float w, float h, float radius,
                       float dx, float dy, float blur, int color) {
        // Shadow = offset soft glow: shift origin by (dx,dy), blur via feather.
        // TODO: shader Mode_SHADOW with per-axis offset uniforms for non-uniform shadows.
        shapeWithRadii(x + dx, y + dy, w, h, radius, radius, radius, radius, blur,
                MODE_GLOW, color, color, 0, blur, 0f);
    }

    // -------------------------------------------------------------------------
    // UiRenderer — line
    // -------------------------------------------------------------------------

    @Override
    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        // Axis-aligned fast paths — route through primitive-radii overload (no Radii alloc).
        if (y1 == y2) {
            // Horizontal
            shapeWithRadii(Math.min(x1, x2), y1 - thickness * 0.5f,
                    Math.abs(x2 - x1), thickness,
                    0f, 0f, 0f, 0f, 0f, MODE_FILL, color, color, 0, 0f, 0f);
            return;
        }
        if (x1 == x2) {
            // Vertical
            shapeWithRadii(x1 - thickness * 0.5f, Math.min(y1, y2),
                    thickness, Math.abs(y2 - y1),
                    0f, 0f, 0f, 0f, 0f, MODE_FILL, color, color, 0, 0f, 0f);
            return;
        }
        // General diagonal: bounding-box approximation for Stage 1.
        // TODO: shader-side rotated capsule SDF for diagonal lines.
        shapeWithRadii(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(thickness, Math.abs(x2 - x1)), Math.max(thickness, Math.abs(y2 - y1)),
                0f, 0f, 0f, 0f, 0f, MODE_FILL, color, color, 0, 0f, 0f);
    }

    // -------------------------------------------------------------------------
    // UiRenderer — clip stack
    // -------------------------------------------------------------------------

    @Override
    public void pushClip(float x, float y, float w, float h) {
        pushClipEntry(x, y, w, h, 0f);
    }

    @Override
    public void pushRoundedClip(float x, float y, float w, float h, float radius) {
        // TODO rounded clip: Stage 1 applies rectangular GL scissor only; the radius
        // is stored so a future shader-mask pass can honour it without API changes.
        pushClipEntry(x, y, w, h, radius);
    }

    @Override
    public void popClip() {
        if (clipTop == 0) return;
        --clipTop;
        if (clipTop == 0) {
            RenderSystem.disableScissor();
        } else {
            // Re-apply the now-top entry.
            applyTopScissor();
        }
    }

    // -------------------------------------------------------------------------
    // UiRenderer — opacity stack
    // -------------------------------------------------------------------------

    @Override
    public void pushOpacity(float multiplier) {
        float clamped = Math.max(0f, Math.min(1f, multiplier));
        float current = (opacityTop == 0) ? 1f : opacityStack[opacityTop - 1];
        if (opacityTop < MAX_STACK) {
            opacityStack[opacityTop++] = current * clamped;
        }
        // If stack is full, silently clamp (defensive; 64-deep opacity nesting is pathological).
    }

    @Override
    public void popOpacity() {
        if (opacityTop > 0) --opacityTop;
    }

    // =========================================================================
    // INTERNAL IMPLEMENTATION
    // =========================================================================

    // Shader mode constants matching the SDF shader's Mode uniform.
    private static final int MODE_FILL     = 0;
    private static final int MODE_BORDER   = 1;
    private static final int MODE_GLOW     = 2;
    private static final int MODE_GRADIENT = 3;

    /** Returns the current accumulated opacity (1.0 when stack is empty). */
    private float currentOpacity() {
        return (opacityTop == 0) ? 1f : opacityStack[opacityTop - 1];
    }

    /**
     * Core shape dispatcher — all public shape methods ultimately reach here.
     *
     * <p>Parameters:</p>
     * <ul>
     *   <li>{@code rtl/rtr/rbr/rbl} — per-corner radii in GUI pixels (no Radii alloc).</li>
     *   <li>{@code pad} — extra quad expansion for glow falloff.</li>
     *   <li>{@code mode} — shader Mode uniform (fill/border/glow/gradient).</li>
     *   <li>{@code colorA/colorB} — ARGB; colorB = colorA for non-gradient shapes.</li>
     *   <li>{@code gradAxis} — 0=horizontal, 1=vertical (gradient only).</li>
     *   <li>{@code feather} — AA/glow softness radius.</li>
     *   <li>{@code thickness} — border thickness.</li>
     * </ul>
     */
    private void shapeWithRadii(float x, float y, float w, float h,
                                float rtl, float rtr, float rbr, float rbl,
                                float pad, int mode,
                                int colorA, int colorB, int gradAxis,
                                float feather, float thickness) {
        if (ctx == null || w <= 0 || h <= 0 || !UiShaders.ready()) return;

        // Apply current accumulated opacity to both colors — pure int arithmetic, no alloc.
        float opacity = currentOpacity();
        int ca = Color.scaleAlpha(colorA, opacity);
        int cb = Color.scaleAlpha(colorB, opacity);

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;

        // Expanded quad bounds (adds padding for glow/shadow falloff).
        float qx0 = x - pad;
        float qy0 = y - pad;
        float qx1 = x + w + pad;
        float qy1 = y + h + pad;

        // Texture coords = position relative to shape center (used as local SDF coords).
        float cx = x + halfW;
        float cy = y + halfH;

        // Upload uniforms then emit one quad.
        drawShapeQuad(qx0, qy0, qx1, qy1, cx, cy,
                halfW, halfH,
                rtl, rtr, rbr, rbl,
                mode, gradAxis,
                Math.max(feather, 1e-4f), Math.max(thickness, 1e-4f),
                ca, cb);
    }

    /**
     * Emits a single quad via Tessellator after setting all SDF shader uniforms.
     *
     * <p>BATCHING SEAM: In Stage 2, replace this method body with an accumulator:
     * move per-shape data (HalfSize, CornerRadii, Mode, ColorA, ColorB, etc.) from
     * uniforms into vertex attributes; accumulate many quads; call
     * {@code BufferRenderer.drawWithGlobalProgram(bb.end())} once per flush.
     * The public UiRenderer API does not change — only this method's internals.</p>
     *
     * <p>Stage-1 known allocation: {@code Tessellator.getInstance().begin(...)} and
     * {@code bb.end()} allocate a {@code BuiltBuffer} per call — this is Minecraft's
     * own immediate-mode API and cannot be avoided without a custom BufferBuilder
     * pre-allocated outside Minecraft's Tessellator. Deferred to Stage 2.</p>
     */
    private void drawShapeQuad(float qx0, float qy0, float qx1, float qy1,
                               float cx, float cy,
                               float halfW, float halfH,
                               float rtl, float rtr, float rbr, float rbl,
                               int mode, int gradAxis,
                               float feather, float thickness,
                               int colorA, int colorB) {
        ShaderProgram shader = UiShaders.SDF;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(sdfSupplier);   // cached supplier — no lambda alloc

        // Set uniforms.
        setUniform2f(shader, "HalfSize",     halfW, halfH);
        setUniform4f(shader, "CornerRadii",  rtl, rtr, rbr, rbl);
        setUniformI (shader, "Mode",         mode);
        setUniformI (shader, "GradAxis",     gradAxis);
        setUniform1f(shader, "Feather",      feather);
        setUniform1f(shader, "Thickness",    thickness);
        setUniformColor(shader, "ColorB",    colorB);

        // Decompose colorA into RGBA bytes for vertex color — pure int ops, no alloc.
        int r = (colorA >>> 16) & 0xFF;
        int g = (colorA >>>  8) & 0xFF;
        int b =  colorA         & 0xFF;
        int a = (colorA >>> 24) & 0xFF;

        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();

        // Stage-1 immediate mode: one begin/end per shape (unavoidable with Tessellator API).
        // BATCHING SEAM: accumulate vertex data here instead of submitting immediately.
        BufferBuilder bb = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(mat, qx0, qy0, 0f).texture(qx0 - cx, qy0 - cy).color(r, g, b, a);
        bb.vertex(mat, qx0, qy1, 0f).texture(qx0 - cx, qy1 - cy).color(r, g, b, a);
        bb.vertex(mat, qx1, qy1, 0f).texture(qx1 - cx, qy1 - cy).color(r, g, b, a);
        bb.vertex(mat, qx1, qy0, 0f).texture(qx1 - cx, qy0 - cy).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.enableCull();
    }

    // -------------------------------------------------------------------------
    // Clip stack helpers
    // -------------------------------------------------------------------------

    /**
     * Pushes a new clip, intersecting with the current top entry if one exists.
     * Intersection ensures that nested clips only ever narrow the visible region.
     */
    private void pushClipEntry(float x, float y, float w, float h, float radius) {
        if (clipTop >= MAX_STACK) return;  // defensive; 64-deep clip nesting is pathological.

        float nx, ny, nw, nh;
        if (clipTop > 0) {
            // Intersect with current top.
            int base = (clipTop - 1) * CLIP_STRIDE;
            float px  = clipStack[base];
            float py  = clipStack[base + 1];
            float pw  = clipStack[base + 2];
            float ph  = clipStack[base + 3];

            nx = Math.max(x, px);
            ny = Math.max(y, py);
            nw = Math.max(0f, Math.min(x + w, px + pw) - nx);
            nh = Math.max(0f, Math.min(y + h, py + ph) - ny);
        } else {
            nx = x; ny = y; nw = w; nh = h;
        }

        int base = clipTop * CLIP_STRIDE;
        clipStack[base]     = nx;
        clipStack[base + 1] = ny;
        clipStack[base + 2] = nw;
        clipStack[base + 3] = nh;
        clipStack[base + 4] = radius;   // stored for future rounded-clip shader support
        ++clipTop;

        applyTopScissor();
    }

    /** Converts the top clip entry to framebuffer pixels and enables GL scissor. */
    private void applyTopScissor() {
        if (clipTop == 0) return;
        int base = (clipTop - 1) * CLIP_STRIDE;
        float x = clipStack[base];
        float y = clipStack[base + 1];
        float w = clipStack[base + 2];
        float h = clipStack[base + 3];
        // radius stored at [base+4] — used by future rounded-clip shader mask; ignored here.

        MinecraftClient mc = MinecraftClient.getInstance();
        double scale = mc.getWindow().getScaleFactor();
        int fbHeight = mc.getWindow().getFramebufferHeight();

        // GUI → framebuffer pixel conversion; GL scissor origin is bottom-left.
        int sx = (int) Math.round(x * scale);
        int sw = (int) Math.round(w * scale);
        int sh = (int) Math.round(h * scale);
        int sy = fbHeight - (int) Math.round((y + h) * scale);   // flip Y

        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    // -------------------------------------------------------------------------
    // Uniform helpers — null-safe, no allocation.
    // -------------------------------------------------------------------------

    private static void setUniform1f(ShaderProgram s, String name, float v) {
        GlUniform u = s.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void setUniform2f(ShaderProgram s, String name, float a, float b) {
        GlUniform u = s.getUniform(name);
        if (u != null) u.set(a, b);
    }

    private static void setUniform4f(ShaderProgram s, String name, float a, float b, float c, float d) {
        GlUniform u = s.getUniform(name);
        if (u != null) u.set(a, b, c, d);
    }

    private static void setUniformI(ShaderProgram s, String name, int v) {
        GlUniform u = s.getUniform(name);
        if (u != null) u.set(v);
    }

    /**
     * Sets a vec4 color uniform from a packed ARGB int.
     * Uses {@code Color.rf/gf/bf/af} — pure float arithmetic, no allocation.
     */
    private static void setUniformColor(ShaderProgram s, String name, int color) {
        GlUniform u = s.getUniform(name);
        if (u != null) u.set(Color.rf(color), Color.gf(color), Color.bf(color), Color.af(color));
    }
}
