package com.club.compat;

import com.club.mixin.DrawContextStateAccessor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * The SDF shape pipeline: Club's rounded rects, borders, glows and gradients on 1.21.5+.
 *
 * <p><b>Why this class exists.</b> Club's UI is drawn by an analytic SDF shader — rounding, anti-aliasing,
 * glow and gradient are coverage maths, not textures, which is what makes the menu resolution-independent
 * ({@code com.club.ui.backend.ModernBackend}, the 1.21.1 path). From 1.21.5 that renderer does not compile,
 * and the UI fell back to LEGACY, where a rounded rect is drawn as a SQUARE and a glow as a flat bar. This
 * class is the way back: one custom {@code RenderPipeline} plus one custom {@code GuiElementRenderState}, so
 * vanilla still owns the layers, the flush and the batching while Club owns only the coverage maths.
 *
 * <p><b>This file is not built below 1.21.5</b> — see the exclude in {@code build.gradle}, the same
 * mechanism that drops {@code ModernBackend} above it. The two are exact opposites and must stay that way:
 * below 1.21.5 shapes go through ModernBackend and this class must never be reached; at and above it,
 * ModernBackend does not compile and this is the only shape path. Excluded rather than stubbed for the
 * reason build.gradle already gives: a stub would be a class pretending to be a renderer. So there is no
 * {@code >=1.21.5} guard anywhere below — the FILE is the guard, and the only {@code //?} left is the one
 * genuine seam between the versions this file DOES serve.
 *
 * <p><b>Why this needs a render state where {@link IconPipe} did not.</b> IconPipe rides
 * {@code DrawContext.drawTexture}, which straddles every 1.21.8/1.21.11 seam internally — but that call
 * gives a shape exactly one UV pair and one tint colour. A parametric shape also needs half-size, radius and
 * thickness, and there is nowhere in that signature to put them. So this class records its own element and
 * pays for it with one mixin ({@link DrawContextStateAccessor}) and one seam ({@code setupVertices}).
 *
 * <p><b>Eight scalar uniforms are impossible, so the parameters ride the VERTICES.</b> {@code UniformType}
 * offers only {@code UNIFORM_BUFFER}/{@code TEXEL_BUFFER} (javap, both jars, 2026-07-17) — there is no
 * {@code uniform float} to set. A uniform would be wrong even if it existed: the GUI RECORDS and flushes
 * later, so a value set at record time is long overwritten by draw time. {@code VertexFormat.builder()} is
 * public on both versions and {@code withVertexFormat} takes whatever it builds, so every per-shape number
 * travels on the quad itself. See club:club_shape_vert.glsl for the carrier layout.
 *
 * <p><b>A custom vertex format is safe, and that was measured rather than hoped.</b> {@code GuiRenderer}
 * keeps a {@code Map<VertexFormat, MappableRingBuffer>}, and the obvious fear is that it is pre-filled with
 * vanilla's formats and would fail on an unknown one. It is not: the map starts empty and
 * {@code initVertexBuffers} fills it from {@code collectVertexSizes()}, which walks the draws actually
 * recorded THIS frame and allocates a ring buffer per format it finds; {@code startBuffer} builds its
 * {@code BufferBuilder} straight from {@code pipeline.getVertexFormat()} (bytecode, 1.21.8). Vanilla keeps
 * no whitelist of formats — it serves whatever was queued.
 *
 * <p><b>The pipeline is CLONED from vanilla's, not spelled out</b> — same reason as {@link IconPipe}: the
 * base's settings differ between versions (1.21.11's GUI snippet adds {@code withDepthTestFunction(
 * NO_DEPTH_TEST)} and 1.21.8's does not), so a hand-copy would compile on both and silently differ on one.
 * The base is {@code RenderPipelines.GUI}, not {@code GUI_TEXTURED}: shapes sample no texture, and cloning
 * the textured pipeline would inherit a {@code Sampler0} this shader does not declare.
 */
public final class ShapePipe {
    private ShapePipe() {}

    /**
     * Club px per encoded unit on the half-size carrier. SHORT carriers, so the scale sets both the
     * precision and the ceiling: 1/32 px, up to ~1023 px of half-size — enough for a full-screen panel on a
     * large display. club:club_shape_vert.glsl divides by the same number; the two must move together.
     */
    public static final float SIZE_SCALE = 32f;

    /**
     * Club px per encoded unit on the radius/thickness carrier: 1/64 px, up to ~511 px. These two decide
     * where the SDF edge lands, so they buy precision with the range half-size spends elsewhere.
     */
    public static final float EDGE_SCALE = 64f;

    /**
     * The shaders for THIS version's GLSL dialect.
     *
     * <p>Measured from each jar's own {@code core/position_tex_color.vsh} on 2026-07-17: {@code #version 150}
     * on 1.21.5, 1.21.6 and 1.21.8; {@code #version 330} on 1.21.11. The flip is therefore somewhere in
     * 1.21.9..1.21.11, and {@code <1.21.11} is right for every version measured — Club ships 1.21.8 and
     * 1.21.11, which sit on opposite and correct sides of it. 1.21.9 and 1.21.10 are UNMEASURED (no jar
     * here): anyone adding a node between them owes this line a look inside that jar first. Identical
     * boundary and identical evidence to {@code IconPipe}'s.
     */
    private static final Identifier VERTEX =
            //? if <1.21.11 {
            Identifier.of("club", "core/club_ui_shape");
            //?} else {
            /*Identifier.of("club", "core/club_ui_shape330");*/
            //?}

    /** The fragment half of the same pair — see {@link #VERTEX} for the measurement. */
    private static final Identifier FRAGMENT =
            //? if <1.21.11 {
            Identifier.of("club", "core/club_ui_shape");
            //?} else {
            /*Identifier.of("club", "core/club_ui_shape330");*/
            //?}

    /**
     * Whether the shader actually COMPILED on this machine. Callers must check before {@link #shape} and
     * fall back to LEGACY when it is false.
     *
     * <p><b>Why this is not paranoia.</b> A pipeline whose GLSL fails to compile does not draw nothing — it
     * takes the client down. {@code GlCommandEncoder} throws {@code IllegalStateException("Pipeline contains
     * invalid shader program")} at DRAW time (bytecode, 1.21.8), and draw time is inside vanilla's
     * GuiRenderer flush, long after the recording call returned — so a try/catch around the call site cannot
     * see it, and the crash lands on vanilla's stack, blaming vanilla. The whole point of the LEGACY
     * fallback is that a shader which fails to load costs the player their rounded corners, not their
     * client; a shader that crashes the game on a driver we never tested would invert exactly that bargain.
     * Club cannot compile GLSL at build time, so this asks the GPU.
     *
     * <p>{@code precompilePipeline} memoises into the SAME cache the draw path reads, so this costs one
     * {@code computeIfAbsent} per call and never compiles twice — including the failure, which is cached as
     * an invalid program rather than retried every frame. Asking every time (rather than latching a verdict)
     * is deliberate: a resource reload clears that cache, and this then re-asks and heals on its own.
     */
    public static boolean ready() {
        try {
            return com.mojang.blaze3d.systems.RenderSystem.getDevice()
                    .precompilePipeline(pipeline()).isValid();
        } catch (Exception e) {
            return false;   // no device yet, or the build threw — either way, do not draw
        }
    }

    /**
     * Records ONE shape quad into the GUI's render state.
     *
     * <p>Coordinates are Club units; this applies the current GUI matrix itself and hands vanilla
     * screen-space vertices. The four colours are per-vertex in the quad's own winding (TL, BL, BR, TR) —
     * pass one value four times for a flat fill, or two pairs for a gradient. That is not a shortcut: the
     * old shader mixed toward a {@code ColorB} UNIFORM by a t it recomputed from localPos, and hardware
     * interpolation of vertex colours across the quad is the same linear ramp with nothing to upload.
     *
     * @param qx0 the quad to rasterise — the shape's box PLUS any glow padding, not the shape's own rect
     * @param cx  the SHAPE's centre, which is the quad's centre only when the padding is symmetric
     * @param halfW the shape's half-extent: the box the SDF is measured against
     * @param radius corner radius; clamped shader-side to min(halfW, halfH)
     * @param thickness SIGNED mode tag — 0 fill, &gt;0 border of that thickness, &lt;0 glow of that feather.
     *                  See club:club_shape_vert.glsl for why one signed carrier says both.
     * @param clip the scissor rect in SCREEN space from {@link #toScreenRect}, or null for none
     */
    public static void shape(DrawContext ctx,
                             float qx0, float qy0, float qx1, float qy1,
                             float cx, float cy, float halfW, float halfH,
                             float radius, float thickness,
                             int cTL, int cBL, int cBR, int cTR,
                             int[] clip) {
        GuiRenderState state = ((DrawContextStateAccessor) (Object) ctx).club$state();
        state.addSimpleElement(new ShapeState(
                new Matrix3x2f(ctx.getMatrices()),
                qx0, qy0, qx1, qy1, cx, cy,
                enc(halfW, SIZE_SCALE), enc(halfH, SIZE_SCALE),
                enc(radius, EDGE_SCALE), enc(thickness, EDGE_SCALE),
                cTL, cBL, cBR, cTR,
                clip == null ? null : new ScreenRect(clip[0], clip[1], clip[2] - clip[0], clip[3] - clip[1])));
    }

    /**
     * A clip rect in Club units, crossed over the matrix into the space a {@code ScreenRect} is measured in.
     *
     * <p>Returned as a bare {@code int[]} of {@code x0, y0, x1, y1} so callers can intersect nested clips
     * without this class owning a stack. Rounded OUTWARD (floor/ceil): a scissor that rounds inward eats a
     * pixel of the content it was meant to contain, and the menu's cards are drawn right up to their clip.
     */
    public static int[] toScreenRect(DrawContext ctx, float x, float y, float w, float h) {
        Matrix3x2f m = ctx.getMatrices();
        Vector2f a = m.transformPosition(x, y, new Vector2f());
        Vector2f b = m.transformPosition(x + w, y + h, new Vector2f());
        return new int[] {
                (int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)),  (int) Math.ceil(Math.max(a.y, b.y)) };
    }

    /** Club px -> the SHORT carrier the shader divides back down. Saturates rather than wrapping: a shape
     *  too big to encode should be drawn slightly wrong, not inside out. */
    static int enc(float v, float scale) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v * scale)));
    }

    /**
     * One recorded shape.
     *
     * <p>The pose is SNAPSHOT here, not read at flush: {@code ctx.getMatrices()} is a live stack that the
     * caller will have popped long before vanilla drains the queue. Vanilla's own
     * {@code TexturedQuadGuiElementRenderState} copies it the same way, for the same reason (bytecode,
     * 1.21.8).
     */
    record ShapeState(Matrix3x2f pose,
                      float qx0, float qy0, float qx1, float qy1,
                      float cx, float cy,
                      int hw, int hh, int rr, int th,
                      int cTL, int cBL, int cBR, int cTR,
                      ScreenRect scissorArea) implements SimpleGuiElementRenderState {

        @Override public RenderPipeline pipeline() { return ShapePipe.pipeline(); }

        /** No sampler in this shader, so there is nothing to bind — and {@code empty()} is the one factory
         *  whose signature survived 1.21.11 adding {@code GpuSampler} to every other overload (javap, both
         *  jars), so it costs no seam. */
        @Override public TextureSetup textureSetup() { return TextureSetup.empty(); }

        /**
         * The quad's screen-space box. Vanilla reads this to decide layering and to cull, so it must cover
         * every pixel the shader can touch — hence the QUAD, which already includes the glow padding, and
         * not the shape's own rect.
         */
        @Override public ScreenRect bounds() {
            // All FOUR corners, not two: line() draws diagonals through a rotated matrix, and the
            // axis-aligned box of a rotated quad is not the box of its two opposite corners.
            Vector2f a = pose.transformPosition(qx0, qy0, new Vector2f());
            Vector2f b = pose.transformPosition(qx1, qy1, new Vector2f());
            Vector2f c = pose.transformPosition(qx0, qy1, new Vector2f());
            Vector2f d = pose.transformPosition(qx1, qy0, new Vector2f());
            int x0 = (int) Math.floor(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)));
            int y0 = (int) Math.floor(Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)));
            int x1 = (int) Math.ceil(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)));
            int y1 = (int) Math.ceil(Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)));
            return new ScreenRect(x0, y0, x1 - x0, y1 - y0);
        }

        // THE ONLY SEAM between 1.21.8 and 1.21.11 in this file: 1.21.8 hands setupVertices a z to put on
        // every vertex, 1.21.11 dropped the parameter. Both then reach the SAME abstract
        // vertex(float, float, float) — vanilla's own vertex(Matrix3x2f, ...) helpers differ only in that
        // 1.21.8 passes the caller's z through and 1.21.11 hardcodes fconst_0 (bytecode, both jars). So
        // emit() does its own transformPosition and calls the identical method, and the seam costs two
        // lines instead of a second copy of the emitter.
        //? if <1.21.11 {
        @Override public void setupVertices(VertexConsumer vc, float z) { emit(vc, z); }
        //?} else {
        /*@Override public void setupVertices(VertexConsumer vc) { emit(vc, 0f); }*/
        //?}

        // Package-private, not private, so ShapePipeVertexTest can drive the emitter directly: the two
        // setupVertices overloads above differ only in arity, and a test that had to pick one would need a
        // //? of its own. What that costs is stated in the test.
        void emit(VertexConsumer vc, float z) {
            // Winding is vanilla's: (x1,y1) -> (x1,y2) -> (x2,y2) -> (x2,y1) = TL, BL, BR, TR, DrawMode
            // QUADS (read out of TexturedQuadGuiElementRenderState.setupVertices, 1.21.8 bytecode).
            v(vc, z, qx0, qy0, cTL);
            v(vc, z, qx0, qy1, cBL);
            v(vc, z, qx1, qy1, cBR);
            v(vc, z, qx1, qy0, cTR);
        }

        private void v(VertexConsumer vc, float z, float x, float y, int argb) {
            Vector2f p = pose.transformPosition(x, y, new Vector2f());
            // localPos is the UNTRANSFORMED offset from the shape's centre: the SDF is evaluated in the
            // shape's own axis-aligned space, and the matrix is exactly what must not reach it — that is
            // what lets a rotated diagonal still be an exact rounded box.
            vc.vertex(p.x, p.y, z)
              .color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
              .texture(x - cx, y - cy)
              .overlay(hw, hh)
              .light(rr, th);
        }
    }

    /** Club's vertex format: vanilla's element set, because {@code VertexConsumer} can write no other. A
     *  custom {@code VertexFormatElement} has no writer method, and {@code BufferBuilder} rejects a vertex
     *  whose declared elements were not all filled — so the carriers had to be chosen from
     *  POSITION/COLOR/UV0/UV1/UV2, and were. */
    static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color",    VertexFormatElement.COLOR)
            .add("UV0",      VertexFormatElement.UV0)
            .add("UV1",      VertexFormatElement.UV1)
            .add("UV2",      VertexFormatElement.UV2)
            .build();

    /** GUI plus Club's shaders and format. Built on first use and never registered, because
     *  {@code RenderPipelines.register} is private on both versions — an unregistered pipeline is compiled
     *  lazily by {@code GlBackend.compilePipelineCached} (a {@code computeIfAbsent}), so the only cost is
     *  that the first shape of a session pays the compile, and a resource reload makes the next one pay it
     *  again. Same bargain as IconPipe. */
    private static RenderPipeline pipeline;

    private static RenderPipeline pipeline() {
        if (pipeline != null) return pipeline;
        RenderPipeline base = RenderPipelines.GUI;
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(Identifier.of("club", "pipeline/club_ui_shape"))
                .withVertexShader(VERTEX)
                .withFragmentShader(FRAGMENT)
                // The one setting deliberately NOT copied from the base: GUI is POSITION_COLOR and has no
                // room for the shape parameters. Everything else is whatever vanilla's GUI pipeline is.
                .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                .withDepthTestFunction(base.getDepthTestFunction())
                .withPolygonMode(base.getPolygonMode())
                .withCull(base.isCull())
                .withColorWrite(base.isWriteColor(), base.isWriteAlpha())
                .withDepthWrite(base.isWriteDepth())
                // getColorLogic() is deliberately NOT copied: withColorLogic is @Deprecated on both versions
                // and copying it would be a no-op anyway — nothing in GUI's snippet chain calls it, so its
                // value IS the builder's own default of LogicOp.NONE (bytecode, both jars).
                .withDepthBias(base.getDepthBiasScaleFactor(), base.getDepthBiasConstant());
        for (String sampler : base.getSamplers()) b.withSampler(sampler);
        for (RenderPipeline.UniformDescription u : base.getUniforms()) {
            if (u.textureFormat() == null) b.withUniform(u.name(), u.type());
            else b.withUniform(u.name(), u.type(), u.textureFormat());
        }
        base.getBlendFunction().ifPresentOrElse(b::withBlend, b::withoutBlend);
        pipeline = b.build();
        return pipeline;
    }
}
