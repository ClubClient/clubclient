package com.club.ui.backend;

import com.club.ClubMod;
import com.club.ui.UiText;
import com.club.ui.text.Align;
import com.club.ui.text.GlyphSink;
import com.club.ui.text.TextEffect;
import com.club.ui.text.TextLayout;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Club's font for the versions where MODERN does not compile — the same MSDF atlases, one draw per glyph.
 *
 * <p><b>Why this is not a rewrite of the text stack.</b> The only thing 1.21.5 took away was the way the
 * quads reach the GPU. Everything ABOVE that — which code point resolves to which cell of which atlas, what
 * a string is worth in pixels, where the baseline sits, how a line wraps — is {@link TextLayout} and
 * {@link FontRegistry}, both pure of Minecraft and both already compiled on every node. So this class
 * borrows them WHOLE and implements one method, {@link #glyph}, which is the only thing that differs from
 * {@code ModernText}. That is the point: {@link #width} cannot disagree with 1.21.1's {@code width} because
 * it is not a second implementation of it, it is a call to the same one. Had the layout been restated here,
 * the two would have agreed on the day it was written and drifted afterwards — and a {@code width()} that
 * lies by a pixel moves every card, label and column in the menu.</p>
 *
 * <p><b>One glyph is one {@code drawTexture}, and vanilla batches them.</b> Not per-glyph GL draws:
 * {@code GuiRenderer} sorts recorded elements by (scissor, pipeline, texture) and merges the runs, which is
 * the batching {@code ModernText} hand-rolls below 1.21.5 — see {@code com.club.compat.TextPipe}.</p>
 *
 * <p>Everything version-specific lives in {@code com.club.compat.TextPipe}; this file is ordinary Java and
 * compiles on every node. On 1.21.1 {@code TextPipe.supported()} is false, {@link #available()} is false
 * with it, and this class is inert — MODERN owns text there and nothing here runs.</p>
 */
public final class SpriteText implements UiText, GlyphSink {

    private final FontRegistry registry = new FontRegistry();
    private final TextLayout   layout   = new TextLayout(registry);

    private DrawContext ctx;

    /** Set on the first unrecoverable failure: text then falls back to the vanilla font for the session
     *  rather than re-throwing every frame. */
    private boolean broken;

    /**
     * This frame's answer to {@link #available()}, or null before it has been asked this frame.
     *
     * <p><b>Why the verdict is latched for a frame and not asked per call.</b> {@code TextPipe.ready} is
     * cheap but not constant: it can CHANGE, because a resource reload throws away the compiled-pipeline
     * cache. If it flipped between a caller's {@code width()} and the matching {@code draw()}, the menu
     * would be laid out in Club's metrics and painted in Minecraft's, or the reverse — every label off by
     * its own error, which reads as a corrupt UI rather than as a font that failed. A frame is exactly the
     * span over which that must not happen, and {@link #begin} is exactly the frame boundary.</p>
     */
    private Boolean frameVerdict;

    /** Once-flag per session, so a machine whose driver rejects the shader says so one time. */
    private boolean shaderFailureLogged;
    /** Once-flag per session for a glyph dropped because its atlas's pipeline would not compile. */
    private boolean skippedGlyphLogged;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Call once per frame before issuing text draws. Re-asks the shader verdict — see {@link #frameVerdict}. */
    public void begin(DrawContext ctx) {
        this.ctx = ctx;
        this.frameVerdict = null;
        this.haveMemo = false;   // a reload may have invalidated last frame's pipelines
    }

    /**
     * Whether Club's font can actually be drawn: the version supports the pipeline, the atlases loaded, AND
     * the shader compiled on THIS machine.
     *
     * <p>The last clause is the one that is not obvious. Club cannot compile GLSL at build time, so a driver
     * that rejects the shader is only discoverable at runtime — and an uncompiled pipeline does not draw
     * nothing, it throws inside vanilla's GUI flush where nothing of ours can catch it
     * ({@code TextPipe.ready}). Asking first is what keeps that the vanilla font instead of a crash.
     */
    public boolean available() {
        if (frameVerdict == null) frameVerdict = compute();
        return frameVerdict;
    }

    private boolean compute() {
        if (broken || !com.club.compat.TextPipe.supported()) return false;
        try {
            // Every weight, not just the one in front of us: a run may ask for SEMIBOLD after REGULAR has
            // already answered "available", and a half-loaded font is a half-drawn frame. This also forces
            // the atlases in, so the first draw is not the first read of a JSON.
            for (Weight w : Weight.values()) {
                if (!com.club.compat.TextPipe.ready(registry.metrics(w).distanceRange, 0f)) {
                    if (!shaderFailureLogged) {
                        shaderFailureLogged = true;
                        ClubMod.LOGGER.warn("[Club] the text shader did not compile on this machine — the UI "
                                + "falls back to Minecraft's font for this session; everything else is "
                                + "unaffected. The GL error itself is logged above by Minecraft's own shader "
                                + "loader.");
                    }
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            fail("readiness check", e);
            return false;
        }
    }

    /**
     * Records an unrecoverable failure: raises the fail-safe flag and says WHY, exactly once.
     *
     * <p>WARN and not ERROR, unlike {@code ModernText}'s equivalent, and the difference is real: there a
     * failure demotes the whole UI to a different backend; here it costs the font and nothing else. The
     * shapes, the icons and the layout all keep working — the menu comes out in Minecraft's font, which is
     * exactly where it was before this class existed.
     *
     * <p><b>This is the one thing allowed to break the frame latch,</b> and it has to be. {@link #available}
     * caches its verdict for a frame so that a width and its draw cannot disagree; but a failure ten labels
     * into a frame leaves that cached {@code true} pointing at a path that now throws, and every remaining
     * {@code width()} would answer 0 — the whole menu collapsed into a pile at the origin, which is far
     * worse than the mixed-font frame the latch exists to prevent. Dropping the latch here costs ONE frame
     * of two fonts and then settles, permanently, on the one that works. The latch is for a verdict that may
     * flip back; {@code broken} never does.
     */
    private void fail(String where, Exception e) {
        if (!broken) {
            ClubMod.LOGGER.warn("[Club] MSDF text failed in " + where
                    + " — the UI falls back to Minecraft's font for the rest of the session", e);
        }
        broken = true;
        frameVerdict = Boolean.FALSE;
    }

    // -------------------------------------------------------------------------
    // UiText — metrics. Every one of these is FontRegistry's/TextLayout's answer, unmodified: see the
    // class note on why restating any of them would be a bug waiting for a release.
    // -------------------------------------------------------------------------

    @Override public boolean isResolutionIndependent() { return true; }

    @Override
    public float width(String text, Weight weight, float size) {
        if (!available()) return 0f;
        try {
            return layout.width(text, weight, size);
        } catch (Exception e) {
            fail("width()", e);
            return 0f;
        }
    }

    @Override
    public float ascent(Weight weight, float size) {
        if (!available()) return 0f;
        try {
            return registry.metrics(weight).ascent(size);
        } catch (Exception e) {
            fail("ascent()", e);
            return 0f;
        }
    }

    @Override
    public float descent(Weight weight, float size) {
        if (!available()) return 0f;
        try {
            return registry.metrics(weight).descent(size);
        } catch (Exception e) {
            fail("descent()", e);
            return 0f;
        }
    }

    @Override
    public float lineHeight(Weight weight, float size) {
        if (!available()) return size;
        try {
            return registry.metrics(weight).lineHeight(size);
        } catch (Exception e) {
            fail("lineHeight()", e);
            return size;
        }
    }

    @Override
    public List<String> wrap(String text, Weight weight, float size, float maxWidth) {
        if (!available()) return List.of();
        try {
            return layout.wrap(text, weight, size, maxWidth);
        } catch (Exception e) {
            fail("wrap()", e);
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // UiText — draw
    // -------------------------------------------------------------------------

    /**
     * Draws {@code text} with the given style. Returns the x past the last glyph.
     *
     * <p>Effect dispatch mirrors {@code ModernText}'s, but through the one knob this path has:</p>
     * <ul>
     *   <li>{@link TextEffect.Shadow} — an offset pass first, then the glyph. Identical to MODERN: the
     *       shadow was never a shader feature there either, just the same run drawn twice.</li>
     *   <li>{@link TextEffect.Outline} — a dilated pass under the glyph. MODERN composites the two inside
     *       one fragment ({@code mix(outline, fill, fillA)}); here they are two passes, which is the same
     *       picture wherever the fill is opaque and a hair different where it is not. Reachable by nothing
     *       in the product today — no call site constructs an Outline — so this is the shape of the
     *       fallback, not a claim that it has been seen.</li>
     *   <li>{@link TextEffect.Glow} — NOT implemented; the glyph draws without it, once per session in the
     *       log. Glow is the one effect that needs a curve of its own rather than a shift of the existing
     *       one, i.e. new GLSL that no call site would exercise and no test here could reach. Shipping
     *       untestable shader code to satisfy a sealed interface is how a menu ends up full of black
     *       rectangles on someone else's driver.</li>
     * </ul>
     */
    @Override
    public float draw(String text, float x, float y, TextStyle style) {
        if (!available() || ctx == null || text.isEmpty()) return x;
        try {
            if (style.effect instanceof TextEffect.Shadow sh) {
                run(text, x + sh.dx(), y + sh.dy(), style.weight, style.size, style.align,
                        sh.color(), style.weightBias);
            } else if (style.effect instanceof TextEffect.Outline o) {
                // The outline IS the fill, dilated: MODERN's outline alpha is clamp(d + 0.5 + OutlineWidth)
                // and its fill is clamp(d + 0.5 - WeightBias), so an outline is a pass at bias -widthPx.
                // MODERN's outline ignores the style's own bias, and so does this.
                run(text, x, y, style.weight, style.size, style.align, o.color(), -o.widthPx());
            } else if (style.effect instanceof TextEffect.Glow) {
                noteGlowUnsupported();
            }
            return run(text, x, y, style.weight, style.size, style.align, style.color, style.weightBias);
        } catch (Exception e) {
            fail("draw()", e);
            return x;
        }
    }

    @Override
    public void drawWrapped(String text, float x, float y, float maxWidth, TextStyle style) {
        if (!available()) return;
        try {
            float lh = lineHeight(style.weight, style.size);
            float cy = y;
            for (String line : layout.wrap(text, style.weight, style.size, maxWidth)) {
                draw(line, x, cy, style);
                cy += lh;
            }
        } catch (Exception e) {
            fail("drawWrapped()", e);
        }
    }

    private boolean glowLogged;

    private void noteGlowUnsupported() {
        if (glowLogged) return;
        glowLogged = true;
        ClubMod.LOGGER.warn("[Club] TextEffect.Glow is not implemented on this Minecraft — the text draws "
                + "without its glow. Nothing in Club builds a Glow today; if something now does, it needs a "
                + "GLOW_RANGE define in club:shaders/include/msdf.glsl and a key for it in TextPipe's cache.");
    }

    // -------------------------------------------------------------------------
    // Internal — one run
    // -------------------------------------------------------------------------

    /** The colour and bias the in-flight run's glyphs are drawn with. Set by {@link #run}, read by
     *  {@link #glyph} — layout calls back synchronously, so this is a parameter, not state. */
    private int   runArgb;
    private float runBias;

    private float run(String text, float x, float yTop, Weight weight, float size, Align align,
                      int argb, float bias) {
        runArgb = argb;
        runBias = bias;
        // layoutLine calls back into glyph() once per visible glyph and returns the advance width.
        return x + layout.layoutLine(text, weight, size, x, yTop, align, this);
    }

    /**
     * One positioned glyph, straight from {@link TextLayout}. The only method that knows 1.21.5 happened.
     */
    @Override
    public void glyph(int atlasId, float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1) {
        MsdfAtlas a = registry.atlasById(atlasId);
        try {
            a.ensureTexture();
        } catch (RuntimeException e) {
            // Icons must never kill text — the same bargain ModernText strikes on this seam.
            if (registry.isIconAtlas(atlasId)) { registry.disableIcons(e); return; }
            throw e;
        }

        Cell c = cell(x0, y0, x1, y1, u0, v0, u1, v1, a.metrics.atlasW, a.metrics.atlasH);
        if (c == null) return;   // degenerate cell — nothing to draw

        float bias = runBias;
        if (!pipelineOk(a, bias)) {
            // A biased pipeline that will not compile is worth falling back for: WEIGHT_BIAS moves coverage
            // only, so nominal weight draws the same glyph in the same place, a shade heavier.
            if (bias == 0f || !pipelineOk(a, 0f)) { noteSkippedGlyph(); return; }
            bias = 0f;
        }
        com.club.compat.TextPipe.draw(ctx, a.textureId, c.x(), c.y(), c.k(), c.u(), c.v(),
                c.cellW(), c.cellH(), a.metrics.atlasW, a.metrics.atlasH, a.pxRange, bias, runArgb);

        // The order proof (Stage 67) — ModernText's recorder, restated on this side of the 1.21.5 seam.
        // ModernText is excluded from the build here, so without this the recorder reads zero text and the
        // harness's order check would pass by measuring nothing.
        //
        // Every glyph, including the ICON atlas's: on MODERN an icon composed by IconGlyph rides this very
        // pipeline and ModernText counts it as TEXT. Counting it as an ICON here instead would make the two
        // versions disagree about what they drew while drawing the same picture.
        //
        // Recorded AFTER the draw, so a glyph that was dropped (no pipeline) is not claimed as painted —
        // the recorder must own the same pixels the GPU does.
        if (com.club.modules.perf.DrawBoxes.recording) {
            var mt = com.club.compat.Mtx.model(ctx);
            com.club.modules.perf.DrawBoxes.add(com.club.modules.perf.DrawBoxes.TEXT,
                    mt.m00() * x0 + mt.m10() * y0 + mt.m30(), mt.m01() * x0 + mt.m11() * y0 + mt.m31(),
                    mt.m00() * x1 + mt.m10() * y1 + mt.m30(), mt.m01() * x1 + mt.m11() * y1 + mt.m31());
        }
    }

    // Memo of the last (atlas, bias) shader verdict. A run is almost always one atlas and one bias, so this
    // answers in two compares and TextPipe.ready is asked once per run rather than once per glyph. Cleared
    // every frame by begin(), because a resource reload can invalidate what it remembers.
    private MsdfAtlas memoAtlas;
    private float     memoBias;
    private boolean   memoOk;
    private boolean   haveMemo;

    private boolean pipelineOk(MsdfAtlas a, float bias) {
        if (haveMemo && memoAtlas == a && Float.compare(memoBias, bias) == 0) return memoOk;
        memoAtlas = a;
        memoBias = bias;
        memoOk = com.club.compat.TextPipe.ready(a.pxRange, bias);
        haveMemo = true;
        return memoOk;
    }

    private void noteSkippedGlyph() {
        if (skippedGlyphLogged) return;
        skippedGlyphLogged = true;
        ClubMod.LOGGER.warn("[Club] a glyph's MSDF pipeline would not compile and the glyph was dropped. The "
                + "font atlases are checked once a frame, so this is most likely the ICON atlas (it has a "
                + "distance range of its own) reached through a PUA code point inside a string.");
    }

    // -------------------------------------------------------------------------
    // Geometry — pure, so it can be tested (see SpriteTextPlacementTest)
    // -------------------------------------------------------------------------

    /** Where one glyph's quad goes and which texels it reads. Club units and TEXELS — the two things
     *  {@code TextPipe.draw} needs and the only things that can be wrong without a window open. */
    record Cell(float x, float y, float k, float u, float v, int cellW, int cellH) {}

    /**
     * Converts one {@link TextLayout} quad into the arguments {@code DrawContext.drawTexture} takes.
     *
     * <p>Layout speaks Club units and NORMALISED UVs (already flipped to a top-left origin by
     * {@code MsdfMetrics.parse}); drawTexture wants TEXELS and an int cell. So the UVs are multiplied back
     * out rather than re-derived — re-deriving the flip is how it gets applied twice, and a doubly-flipped
     * atlas draws every glyph as some other glyph, with nothing thrown and nothing logged.
     *
     * <p>{@code k} comes off the X axis alone because {@code Mtx.scale} is uniform. That is legitimate only
     * while every cell's plane box shares its aspect, which for these atlases is measured, not hoped —
     * see {@code SpriteTextPlacementTest.everyGlyphOfEveryWeightScalesUniformly}.
     *
     * <p>Null when the cell is degenerate.
     */
    static Cell cell(float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1, int atlasW, int atlasH) {
        float u = u0 * atlasW, v = v0 * atlasH;
        int cellW = Math.round((u1 - u0) * atlasW), cellH = Math.round((v1 - v0) * atlasH);
        if (cellW <= 0 || cellH <= 0) return null;
        return new Cell(x0, y0, (x1 - x0) / cellW, u, v, cellW, cellH);
    }
}
