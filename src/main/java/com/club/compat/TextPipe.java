package com.club.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * The MSDF text pipeline, across Minecraft versions — Club's own font, drawn by vanilla's GUI.
 *
 * <p><b>Why this class exists.</b> Club's UI is set in Onest, shipped as MSDF atlases, and MSDF needs a
 * fragment shader vanilla does not have. Below 1.21.5 that shader is Club's own immediate-mode renderer
 * ({@code com.club.ui.backend.ModernText}); from 1.21.5 that renderer does not compile — the GUI no longer
 * draws but RECORDS into a private {@code GuiRenderState}, and an immediate-mode pass inside it does not
 * exist any more — so text fell back to the vanilla {@code TextRenderer} and the menu came out set in
 * Minecraft's font instead of Club's. This class is the way back, and it is deliberately the SAME way back
 * {@link IconPipe} took for icons: one custom {@code RenderPipeline} handed to
 * {@code DrawContext.drawTexture}, so vanilla keeps the vertices, the layers, the matrix and the scissor,
 * and Club keeps only the coverage maths.</p>
 *
 * <p><b>One glyph is one {@code drawTexture}, and that is not the draw call it looks like.</b> The obvious
 * objection is that a menu is thousands of glyphs. It does not cost thousands of GL draws: vanilla SORTS the
 * recorded elements and merges them. {@code GuiRenderer.SIMPLE_ELEMENT_COMPARATOR} is
 * {@code comparing(scissorArea).thenComparing(pipeline).thenComparing(textureSetup)} (bytecode, 1.21.8,
 * 2026-07-17), so a run of glyphs sharing this pipeline, one atlas and one clip collapses into a single
 * draw — which is precisely the batching {@code ModernText} had to hand-roll below 1.21.5. Rebuilding that
 * batcher here would be re-solving a problem vanilla now solves.</p>
 *
 * <p><b>What is NOT needed, measured before it was written.</b> No {@code GuiRenderState}, no
 * {@code TextureSetup}, no {@code VertexConsumer}, no {@code GpuSampler}, no mixin. Every one of those is a
 * seam that moved between 1.21.8 and 1.21.11 — vanilla's own private {@code drawTexturedQuad} grew a
 * {@code GpuSampler} parameter in 1.21.11 and 1.21.8 has none (javap, both jars, 2026-07-17) — and the
 * public {@code drawTexture} straddles all of them internally. Its signature is IDENTICAL on both:
 * <pre>{@code drawTexture(RenderPipeline, Identifier, int x, int y, float u, float v,
 *             int w, int h, int regionW, int regionH, int texW, int texH, int tint)}</pre>
 * Taking the public door means the version seam is IconPipe's problem and Tex's problem, and not this
 * class's beyond the one {@code #version} line below.</p>
 *
 * <p><b>Why the weight bias is a DEFINE and not a uniform.</b> {@code drawTexture} carries exactly one
 * per-draw value through to the shader: {@code tint}, which the fill colour already owns. There is no seam
 * to hang a per-draw float on without giving up {@code drawTexture} and inheriting every difference listed
 * above. So an optical weight becomes a compile-time constant and a pipeline of its own. That is affordable
 * only because the value space is tiny and static — the whole product asks for exactly two biases, {@code 0}
 * and {@code 0.03f} ({@code ArmorElement.VAL_BIAS}, {@code EffectsElement.TIME_BIAS}) — and
 * {@link #MAX_PIPELINES} is the bound that keeps it that way if someone ever animates one.</p>
 */
public final class TextPipe {
    private TextPipe() {}

    /**
     * The fragment shader for THIS version's GLSL dialect.
     *
     * <p>Measured from each jar's own {@code core/position_tex_color.fsh} on 2026-07-17: {@code #version 150}
     * on 1.21.5, 1.21.6 and 1.21.8; {@code #version 330} on 1.21.11. The flip is therefore somewhere in
     * 1.21.9..1.21.11, and {@code <1.21.11} is right for every version measured — Club ships 1.21.8 and
     * 1.21.11, which sit on opposite and correct sides of it. 1.21.9 and 1.21.10 are UNMEASURED (no jar
     * here): anyone adding a node between them owes this line a look inside that jar first. Same boundary,
     * same reason and same gap as {@link IconPipe}'s own fragment constant — deliberately not folded
     * together, because a shared constant would imply the two shaders must always move as one.
     *
     * <p>Unused below 1.21.5, where {@link #supported()} is false and nothing reads this.
     */
    private static final Identifier FRAGMENT =
            //? if <1.21.11 {
            Identifier.of("club", "core/club_msdf_text");
            //?} else {
            /*Identifier.of("club", "core/club_msdf_text330");*/
            //?}

    /**
     * How many distinct pipelines this class will ever build.
     *
     * <p>Each one is a GLSL compile and a resident GPU program, and the cache key includes a caller-supplied
     * float ({@code weightBias}). Two biases exist in the product today, both constants. A caller that
     * ANIMATED a bias — one value per frame — would otherwise compile a program per frame forever, which is
     * a GPU memory leak that no test would catch and that would look like a slow drift, not a bug. Past this
     * bound a biased draw silently uses the nominal-weight pipeline instead: see {@link #pipeline}. Text
     * that is a shade too heavy beats a client that dies at minute forty.
     */
    private static final int MAX_PIPELINES = 8;

    /** Whether this version can draw text through the pipeline path at all. False below 1.21.5, where
     *  MODERN owns text and this class is dead code. Callers MUST check before {@link #draw}. */
    public static boolean supported() {
        //? if <1.21.5 {
        return false;
        //?} else {
        /*return true;*/
        //?}
    }

    /**
     * Whether the shader for {@code (pxRange, weightBias)} actually COMPILED on this machine. Callers must
     * check before {@link #draw} and fall back to the vanilla font when it is false.
     *
     * <p><b>Why this is not paranoia.</b> A pipeline whose GLSL fails to compile does not draw nothing — it
     * takes the client down. {@code GlCommandEncoder} throws {@code IllegalStateException("Pipeline contains
     * invalid shader program")} at DRAW time (bytecode, 1.21.8), and draw time is inside vanilla's GuiRenderer
     * flush, long after {@code drawTexture} returned — so a try/catch around the call site cannot see it, and
     * the crash lands on vanilla's stack, blaming vanilla. Club cannot compile GLSL at build time, so this
     * asks the GPU. A shader Club got wrong must cost the player Club's font, not their session.
     *
     * <p>{@code precompilePipeline(p)} passes a null source-getter, which {@code GlBackend} explicitly
     * replaces with its own {@code defaultShaderSourceGetter} — the game's real loader. It memoises into the
     * SAME cache the draw path reads, so this costs one lookup per call and never compiles twice, including
     * the failure. Asking every time rather than latching a verdict is deliberate: a resource reload clears
     * that cache, and this then re-asks and heals on its own instead of trusting a pre-reload answer.
     * ({@code com.club.ui.backend.SpriteText} latches it for the span of one FRAME, which is a different
     * concern — see there.)
     */
    public static boolean ready(float pxRange, float weightBias) {
        //? if <1.21.5 {
        return false;
        //?} else {
        /*try {
            return com.mojang.blaze3d.systems.RenderSystem.getDevice()
                    .precompilePipeline(pipeline(pxRange, weightBias)).isValid();
        } catch (Exception e) {
            return false;   // no device yet, or the build threw — either way, do not draw
        }*/
        //?}
    }

    /**
     * Draws one atlas cell — one glyph — through the MSDF pipeline, tinted {@code argb}.
     *
     * <p>The quad is placed by the matrix rather than by the arguments: {@code drawTexture} takes ints for
     * position and size, and a glyph lands at a fractional position and a fractional size. So the cell is
     * drawn at its NATIVE texel size from the origin and {@link Mtx} scales it by {@code k}, which is what
     * keeps the sub-pixel placement an int API would have thrown away. Same trick, same reason, as
     * {@link IconPipe#draw}.
     *
     * @param k     Club units per atlas texel — uniform, because {@link Mtx#scale} is uniform. For text that
     *              is a measured property of the atlases and not an assumption: every glyph of all three
     *              weights has planeWidth/cellWidth == planeHeight/cellHeight == 1/40 to within 4e-16, and
     *              {@code SpriteTextPlacementTest.everyGlyphOfEveryWeightScalesUniformly} is what fails the
     *              day a regenerated atlas stops being true. A non-uniform cell would draw the font
     *              stretched, silently.
     * @param u     left edge of the cell in TEXELS, not normalised — drawTexture divides by texW itself
     * @param v     top edge of the cell in TEXELS (top-left origin; the atlas's yOrigin=bottom flip has
     *              already been undone by {@code MsdfMetrics.parse})
     * @param weightBias optical weight in coverage units: >0 thinner, <0 heavier, 0 nominal. Costs a
     *              pipeline per distinct value — see {@link #MAX_PIPELINES}.
     */
    public static void draw(DrawContext ctx, Identifier tex, float x, float y, float k,
                            float u, float v, int cellW, int cellH, int atlasW, int atlasH,
                            float pxRange, float weightBias, int argb) {
        //? if <1.21.5 {
        throw new UnsupportedOperationException(
                "TextPipe.draw is not the text path below 1.21.5 — ModernText is. Guard with supported().");
        //?} else {
        /*Mtx.push(ctx);
        try {
            Mtx.translate(ctx, x, y);
            Mtx.scale(ctx, k);
            ctx.drawTexture(pipeline(pxRange, weightBias), tex, 0, 0, u, v, cellW, cellH, cellW, cellH,
                    atlasW, atlasH, argb);
        } finally {
            Mtx.pop(ctx);   // ALWAYS balance the stack, even if the draw throws
        }*/
        //?}
    }

    //? if >=1.21.5 {
    /*/^* One pipeline per (pxRange, weightBias). Built on first use, kept for the process. ^/
    private static final java.util.HashMap<Long, com.mojang.blaze3d.pipeline.RenderPipeline> CACHE =
            new java.util.HashMap<>();

    private static long key(float pxRange, float weightBias) {
        return ((long) Float.floatToIntBits(pxRange) << 32) | (Float.floatToIntBits(weightBias) & 0xFFFFFFFFL);
    }

    /^*
     * GUI_TEXTURED plus Club's fragment shader and this variant's two constants.
     *
     * <p>Never registered, and cannot be: {@code RenderPipelines.register} is private on both versions.
     * Registration only feeds {@code getAll()}, which {@code ShaderLoader.apply} uses to PRECOMPILE on
     * resource reload; an unregistered pipeline is compiled on first use instead. The only cost is that the
     * first glyph of a session pays the compile, and that a resource reload makes the next one pay it again.
     *
     * <p>Non-finite biases are folded to 0 rather than rejected. String.valueOf(Float.NaN) is "NaN", which
     * is not a GLSL float literal — it would compile to nothing and, but for {@link #ready}, crash at draw
     * time far from whoever produced the NaN.
     ^/
    private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline(float pxRange, float weightBias) {
        final float bias = Float.isFinite(weightBias) ? weightBias : 0f;
        // Past the bound, a biased draw borrows the nominal-weight pipeline. Sound because WEIGHT_BIAS
        // touches COVERAGE only: no advance, plane box or cell depends on it, so the fallback changes how
        // heavy a glyph looks and cannot move one. Never applied to bias 0 itself — that is the pipeline
        // every atlas needs to draw at all, and there are two atlases (pxRange 6 for the font, 8 for the
        // icons), so the nominal set is bounded by construction.
        if (bias != 0f && !CACHE.containsKey(key(pxRange, bias)) && CACHE.size() >= MAX_PIPELINES) {
            return pipeline(pxRange, 0f);
        }
        return CACHE.computeIfAbsent(key(pxRange, bias), k -> build(pxRange, bias));
    }

    /^*
     * The pipeline is CLONED from vanilla's, not spelled out. {@code RenderPipelines.GUI_TEXTURED} is built
     * from private {@code Snippet} constants a mod cannot reach, so the config is copied off the built
     * pipeline through its own getters and only the fragment shader and the defines are swapped. That is not
     * merely convenient: 1.21.11's {@code POSITION_TEX_COLOR_SNIPPET} adds
     * {@code withDepthTestFunction(NO_DEPTH_TEST)} and 1.21.8's does not (bytecode, both jars). Hand-copying
     * the settings would have compiled on both and silently depth-tested the text on one. Reading them off
     * GUI_TEXTURED means Club's pipeline is whatever vanilla's GUI pipeline is, on any version, plus one
     * shader — a difference that cannot drift.
     ^/
    private static com.mojang.blaze3d.pipeline.RenderPipeline build(float pxRange, float weightBias) {
        com.mojang.blaze3d.pipeline.RenderPipeline base = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
        com.mojang.blaze3d.pipeline.RenderPipeline.Builder b = com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                // A location PER VARIANT, not one for the class. RenderPipeline overrides neither equals nor
                // hashCode (javap, 1.21.8), so the compile cache keys on object identity and distinct
                // variants could not collide anyway — but GlDevice is not in the merged jar and that keying
                // could not be read here, so this makes the question moot instead of trusting the answer: a
                // cache keyed on location alone would hand one bias another's program, and the only symptom
                // would be text of the wrong weight.
                .withLocation(Identifier.of("club", "pipeline/club_msdf_text_"
                        + Integer.toHexString(Float.floatToIntBits(pxRange)) + "_"
                        + Integer.toHexString(Float.floatToIntBits(weightBias))))
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
                // The atlas's own distanceRange, compiled in, so the constant has one home and cannot drift
                // from the JSON it came out of.
                .withShaderDefine("PX_RANGE", pxRange)
                .withShaderDefine("WEIGHT_BIAS", weightBias);
        for (String sampler : base.getSamplers()) b.withSampler(sampler);
        for (com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription u : base.getUniforms()) {
            if (u.textureFormat() == null) b.withUniform(u.name(), u.type());
            else b.withUniform(u.name(), u.type(), u.textureFormat());
        }
        base.getBlendFunction().ifPresentOrElse(b::withBlend, b::withoutBlend);
        return b.build();
    }*/
    //?}
}
