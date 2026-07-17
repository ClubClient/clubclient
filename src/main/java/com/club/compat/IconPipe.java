package com.club.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * The MSDF icon pipeline, across Minecraft versions.
 *
 * <p><b>Why this class exists.</b> Club's icons are MSDF glyphs, and MSDF needs a fragment shader that
 * vanilla does not have. Below 1.21.5 that shader is Club's own immediate-mode renderer
 * ({@code com.club.ui.backend.ModernText}); from 1.21.5 that renderer does not compile, the GUI no longer
 * draws but RECORDS, and the icons fell back to nothing — a letter initial where an icon belonged
 * (see {@code com.club.ui.IconGlyph}). This class is the way back: one custom {@code RenderPipeline},
 * handed to {@code DrawContext.drawTexture}, which lets vanilla own the vertices, the layers and the
 * scissor while Club owns only the coverage maths.</p>
 *
 * <p><b>What is NOT needed, measured before it was written.</b> No {@code SimpleGuiElementRenderState}, no
 * {@code TextureSetup}, no {@code VertexConsumer}, no mixin — all four of those are seams that differ
 * between 1.21.8 and 1.21.11, and {@code drawTexture} straddles every one of them internally. Its public
 * signature is IDENTICAL on both (javap, 1.21.8 and 1.21.11 jars, 2026-07-17):
 * <pre>{@code drawTexture(RenderPipeline, Identifier, int x, int y, float u, float v,
 *             int w, int h, int regionW, int regionH, int texW, int texH, int tint)}</pre>
 * and its body records {@code new Matrix3x2f(this.matrices)} plus {@code scissorStack.peekLast()} into the
 * GuiRenderState on both — which is why {@link Mtx} still places the icon and why clipping still cuts it.</p>
 *
 * <p><b>The pipeline is CLONED from vanilla's, not spelled out.</b> {@code RenderPipelines.GUI_TEXTURED} is
 * built from private {@code Snippet} constants a mod cannot reach, so the config is copied off the built
 * pipeline through its own getters and only the fragment shader is swapped. That is not merely convenient:
 * 1.21.11's {@code POSITION_TEX_COLOR_SNIPPET} adds {@code withDepthTestFunction(NO_DEPTH_TEST)} and
 * 1.21.8's does not (bytecode, both jars). Hand-copying the settings would have compiled on both and
 * silently depth-tested the icons on one. Reading them off {@code GUI_TEXTURED} means Club's pipeline is
 * whatever vanilla's GUI pipeline is, on any version, plus one shader — a difference that cannot drift.</p>
 *
 * <p><b>The pipeline is not registered, and cannot be.</b> {@code RenderPipelines.register} is private on
 * both versions. Registration only feeds {@code getAll()}, which {@code ShaderLoader.apply} uses to
 * PRECOMPILE on resource reload; an unregistered pipeline is compiled on first use instead —
 * {@code GlBackend.compilePipelineCached} is a {@code computeIfAbsent} over {@code pipelineCompileCache}
 * (bytecode, 1.21.8). The only cost is that the first icon of a session pays the compile, and that a
 * resource reload ({@code clearPipelineCache}) makes the next one pay it again.</p>
 *
 * <p><b>The 1.21.5 boundary is measured, not guessed.</b> It is the same boundary
 * {@code com.club.ui.backend.Backends} turns on and for the same reason — below it MODERN draws the icons
 * through the shader path and this class must never be reached; at and above it MODERN does not compile.
 * It is NOT {@link Mtx}'s 1.21.6 and NOT {@link Tex}'s 1.21.11: three rewrites, three releases.</p>
 */
public final class IconPipe {
    private IconPipe() {}

    /**
     * The fragment shader for THIS version's GLSL dialect.
     *
     * <p>Measured from each jar's own {@code core/position_tex_color.fsh} on 2026-07-17: {@code #version 150}
     * on 1.21.5, 1.21.6 and 1.21.8; {@code #version 330} on 1.21.11. The flip is therefore somewhere in
     * 1.21.9..1.21.11, and {@code <1.21.11} is right for every version measured — Club ships 1.21.8 and
     * 1.21.11, which sit on opposite and correct sides of it. 1.21.9 and 1.21.10 are UNMEASURED (no jar
     * here): anyone adding a node between them owes this line a look inside that jar first.
     *
     * <p>Unused below 1.21.5, where {@link #supported()} is false and nothing reads this.
     */
    private static final Identifier FRAGMENT =
            //? if <1.21.11 {
            Identifier.of("club", "core/club_msdf_icon");
            //?} else {
            /*Identifier.of("club", "core/club_msdf_icon330");*/
            //?}

    /** Whether this version can draw icons through the pipeline path at all. False below 1.21.5, where
     *  MODERN owns icons and this class is dead code. Callers MUST check before {@link #draw}. */
    public static boolean supported() {
        //? if <1.21.5 {
        return false;
        //?} else {
        /*return true;*/
        //?}
    }

    /**
     * Whether the shader actually COMPILED on this machine. Callers must check before {@link #draw}, and
     * fall back to a letter when it is false.
     *
     * <p><b>Why this is not paranoia.</b> A pipeline whose GLSL fails to compile does not draw nothing — it
     * takes the client down. {@code GlCommandEncoder} throws {@code IllegalStateException("Pipeline contains
     * invalid shader program")} at DRAW time (bytecode, 1.21.8), and draw time is inside vanilla's GuiRenderer
     * flush, long after {@code drawTexture} returned — so a try/catch around the call site cannot see it and
     * the crash lands on vanilla's stack, blaming vanilla. The whole point of the LEGACY fallback is that a
     * shader which fails to load costs the player their rounded corners, not their client
     * ({@code com.club.ui.backend.Backends}); a shader that crashes the game on a driver we never tested
     * would invert exactly that bargain. Club cannot compile GLSL at build time, so this asks the GPU.
     *
     * <p>{@code precompilePipeline(p)} passes a null source-getter, which {@code GlBackend} explicitly
     * replaces with its own {@code defaultShaderSourceGetter} (bytecode, 1.21.8) — the game's real loader.
     * It memoises into {@code pipelineCompileCache}, the SAME map the draw path reads, so this costs one
     * {@code computeIfAbsent} per call and never compiles twice — including the failure, which is cached as
     * an invalid program rather than retried every frame. Asking every time (rather than latching a verdict)
     * is deliberate: a resource reload calls {@code clearPipelineCache()}, and this then re-asks and heals
     * on its own instead of trusting an answer from before the reload.
     */
    public static boolean ready(float pxRange) {
        //? if <1.21.5 {
        return false;
        //?} else {
        /*try {
            return com.mojang.blaze3d.systems.RenderSystem.getDevice()
                    .precompilePipeline(pipeline(pxRange)).isValid();
        } catch (Exception e) {
            return false;   // no device yet, or the build threw — either way, do not draw
        }*/
        //?}
    }

    /**
     * Draws one atlas cell through the MSDF pipeline, tinted {@code argb}.
     *
     * <p>The quad is placed by the matrix rather than by the arguments: {@code drawTexture} takes ints for
     * position and size, and an icon lands at a fractional position and a fractional size. So the cell is
     * drawn at its NATIVE texel size from the origin and {@link Mtx} scales it by {@code k} — the same
     * trick {@code com.club.hud.PixelIcons} already uses on this version, and the reason the icon keeps
     * sub-pixel placement that an int API would have thrown away.
     *
     * @param k     Club units per atlas texel — uniform, because {@link Mtx#scale} is uniform and every
     *              glyph in the icon atlas is a square cell with a square plane box (measured: all 54
     *              glyphs of icons.json carry identical 88x88 atlasBounds and identical planeBounds)
     * @param u     left edge of the cell in TEXELS, not normalised — drawTexture divides by texW itself
     * @param v     top edge of the cell in TEXELS (top-left origin; the atlas's yOrigin=bottom flip has
     *              already been undone by {@code MsdfMetrics.parse})
     */
    public static void draw(DrawContext ctx, Identifier tex, float x, float y, float k,
                            float u, float v, int cellW, int cellH, int atlasW, int atlasH,
                            float pxRange, int argb) {
        //? if <1.21.5 {
        throw new UnsupportedOperationException(
                "IconPipe.draw is not the icon path below 1.21.5 — MODERN is. Guard with supported().");
        //?} else {
        /*Mtx.push(ctx);
        try {
            Mtx.translate(ctx, x, y);
            Mtx.scale(ctx, k);
            ctx.drawTexture(pipeline(pxRange), tex, 0, 0, u, v, cellW, cellH, cellW, cellH,
                    atlasW, atlasH, argb);
        } finally {
            Mtx.pop(ctx);   // ALWAYS balance the stack, even if the draw throws
        }*/
        //?}
    }

    //? if >=1.21.5 {
    /*/^* GUI_TEXTURED plus Club's fragment shader. Built on first use — see the class note on why it is
     *  never registered — and rebuilt only if the atlas's distanceRange ever changes under it. ^/
    private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline;
    private static float builtForPxRange = Float.NaN;

    private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline(float pxRange) {
        if (pipeline != null && builtForPxRange == pxRange) return pipeline;
        com.mojang.blaze3d.pipeline.RenderPipeline base = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
        com.mojang.blaze3d.pipeline.RenderPipeline.Builder b = com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                .withLocation(Identifier.of("club", "pipeline/club_msdf_icon"))
                // Vanilla's OWN vertex shader, taken off the pipeline it belongs to: it already emits the
                // texCoord0/vertexColor this fragment shader reads, so Club supplies no vertex stage at all.
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(FRAGMENT)
                .withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
                .withDepthTestFunction(base.getDepthTestFunction())
                .withPolygonMode(base.getPolygonMode())
                .withCull(base.isCull())
                .withColorWrite(base.isWriteColor(), base.isWriteAlpha())
                .withDepthWrite(base.isWriteDepth())
                // getColorLogic() is deliberately NOT copied: withColorLogic is @Deprecated on both 1.21.8
                // and 1.21.11, and copying it would be a no-op anyway — nothing in GUI_TEXTURED's snippet
                // chain ever calls it, so its value IS the builder's own default of LogicOp.NONE (bytecode,
                // both jars). Taking the deprecation to copy a default would buy nothing.
                .withDepthBias(base.getDepthBiasScaleFactor(), base.getDepthBiasConstant())
                // The atlas's distanceRange, compiled in. Icons want no outline, glow or weight bias, so
                // unlike the MODERN text shader this one needs no custom uniforms at all.
                .withShaderDefine("PX_RANGE", pxRange);
        for (String sampler : base.getSamplers()) b.withSampler(sampler);
        for (com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription u : base.getUniforms()) {
            if (u.textureFormat() == null) b.withUniform(u.name(), u.type());
            else b.withUniform(u.name(), u.type(), u.textureFormat());
        }
        base.getBlendFunction().ifPresentOrElse(b::withBlend, b::withoutBlend);
        pipeline = b.build();
        builtForPxRange = pxRange;
        return pipeline;
    }*/
    //?}
}
