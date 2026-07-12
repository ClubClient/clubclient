package com.club.ui.backend;

import com.club.ui.Color;
import com.club.ui.UiText;
import com.club.ui.text.Align;
import com.club.ui.text.GlyphSink;
import com.club.ui.text.TextEffect;
import com.club.ui.text.TextLayout;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
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

import java.util.List;
import java.util.function.Supplier;

/**
 * {@link UiText} implementation backed by the MSDF text shader ({@link UiShaders#TEXT}).
 *
 * <p>Design:</p>
 * <ul>
 *   <li>Glyph layout is fully delegated to {@link TextLayout} (pure, no GL/Minecraft).</li>
 *   <li>Layout writes positioned quads into a reusable {@link GlyphBatch} via the
 *       {@link GlyphSink} interface — no per-glyph allocation from our own code.</li>
 *   <li>One {@code Tessellator.begin()} / draw / {@code bb.end()} per run (= per line).
 *       This is the only per-run unavoidable allocation; it comes from Minecraft's own
 *       buffer API, not from our code.</li>
 *   <li>Effects (shadow / outline / glow) are implemented as a two-pass scheme:
 *       shadow emits an offset run first; outline and glow are shader uniforms on the
 *       main pass.</li>
 * </ul>
 */
public final class ModernText implements UiText {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final FontRegistry registry = new FontRegistry();
    private final TextLayout   layout   = new TextLayout(registry);
    private final GlyphBatch   batch    = new GlyphBatch();

    private DrawContext ctx;

    /** Cached supplier — avoids a new lambda instance per draw call. */
    private final Supplier<ShaderProgram> textSupplier = () -> UiShaders.TEXT;

    /** Set to true on the first unrecoverable error; subsequent calls short-circuit. */
    private boolean broken;

    /** Returns false if this instance has encountered an unrecoverable error. */
    public boolean healthy() { return !broken; }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Call once per frame before issuing text draws. */
    public void begin(DrawContext ctx) {
        this.ctx = ctx;
    }

    // -------------------------------------------------------------------------
    // UiText — meta
    // -------------------------------------------------------------------------

    @Override
    public boolean isResolutionIndependent() { return true; }

    // -------------------------------------------------------------------------
    // UiText — metrics
    // -------------------------------------------------------------------------

    @Override
    public float width(String text, Weight weight, float size) {
        if (broken) return 0f;
        try {
            return layout.width(text, weight, size);
        } catch (Exception e) {
            broken = true;
            System.err.println("[club.ui] modern text unavailable -> LEGACY: " + e);
            return 0f;
        }
    }

    @Override
    public float ascent(Weight weight, float size) {
        if (broken) return 0f;
        try {
            return registry.metrics(weight).ascent(size);
        } catch (Exception e) {
            broken = true;
            System.err.println("[club.ui] modern text unavailable -> LEGACY: " + e);
            return 0f;
        }
    }

    @Override
    public float descent(Weight weight, float size) {
        if (broken) return 0f;
        try {
            return registry.metrics(weight).descent(size);
        } catch (Exception e) {
            broken = true;
            System.err.println("[club.ui] modern text unavailable -> LEGACY: " + e);
            return 0f;
        }
    }

    @Override
    public float lineHeight(Weight weight, float size) {
        if (broken) return size;
        try {
            return registry.metrics(weight).lineHeight(size);
        } catch (Exception e) {
            broken = true;
            System.err.println("[club.ui] modern text unavailable -> LEGACY: " + e);
            return size;
        }
    }

    @Override
    public List<String> wrap(String text, Weight weight, float size, float maxWidth) {
        if (broken) return List.of();
        try {
            return layout.wrap(text, weight, size, maxWidth);
        } catch (Exception e) {
            broken = true;
            System.err.println("[club.ui] modern text unavailable -> LEGACY: " + e);
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // UiText — draw
    // -------------------------------------------------------------------------

    /**
     * Draws {@code text} with the given style, applying effects.
     * Returns the x coordinate after the last glyph (useful for inline composition).
     *
     * <p>Effect dispatch:</p>
     * <ul>
     *   <li>{@link TextEffect.Shadow} — emits an offset dark pass first, then the main pass.</li>
     *   <li>{@link TextEffect.Outline} — main pass with outline width + color uniforms.</li>
     *   <li>{@link TextEffect.Glow}    — main pass with glow radius + color uniforms.</li>
     *   <li>{@link TextEffect.None}    — main pass with no effect uniforms.</li>
     * </ul>
     */
    @Override
    public float draw(String text, float x, float y, TextStyle style) {
        if (broken) return x;
        try {
            float outlineW = 0f;
            int   outlineC = 0;
            float glowR    = 0f;
            int   glowC    = 0;

            float weightBias = style.weightBias;
            if (style.effect instanceof TextEffect.Shadow sh) {
                // Shadow pass — offset, shadow color, no outline/glow uniforms. Same weight bias as the glyph.
                drawRun(text, x + sh.dx(), y + sh.dy(),
                        style.weight, style.size, style.align,
                        sh.color(), 0f, 0, 0f, 0, weightBias);
            } else if (style.effect instanceof TextEffect.Outline o) {
                outlineW = o.widthPx();
                outlineC = o.color();
            } else if (style.effect instanceof TextEffect.Glow g) {
                glowR = g.radius();
                glowC = g.color();
            }

            return drawRun(text, x, y,
                    style.weight, style.size, style.align,
                    style.color, outlineW, outlineC, glowR, glowC, weightBias);
        } catch (Exception e) {
            broken = true;
            System.err.println("[club.ui] modern text unavailable -> LEGACY: " + e);
            return x;
        }
    }

    /**
     * Draws word-wrapped text. Each line is placed at {@code y += lineHeight} after the
     * first. Alignment is applied per-line by the underlying {@link TextLayout}.
     */
    @Override
    public void drawWrapped(String text, float x, float y, float maxWidth, TextStyle style) {
        if (broken) return;
        try {
            float lh = lineHeight(style.weight, style.size);
            float cy = y;
            for (String line : layout.wrap(text, style.weight, style.size, maxWidth)) {
                draw(line, x, cy, style);
                cy += lh;
            }
        } catch (Exception e) {
            broken = true;
            System.err.println("[club.ui] modern text unavailable -> LEGACY: " + e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal — single-run renderer
    // -------------------------------------------------------------------------

    /**
     * Lays out one line, accumulates glyph quads into the batch, then issues ONE draw call.
     *
     * <p>Allocation discipline: layout and batch accumulation are allocation-free.
     * The only allocation per run is Minecraft's {@code Tessellator.begin()} /
     * {@code BufferBuilder.end()} pair (unavoidable in Stage 1).</p>
     *
     * @return x-advance past the last glyph (= x + advance width, before alignment shift).
     */
    private float drawRun(String text,
                          float x, float yTop,
                          Weight weight, float size, Align align,
                          int color,
                          float outlineW, int outlineC,
                          float glowR,   int glowC,
                          float weightBias) {
        if (!UiShaders.ready() || ctx == null || text.isEmpty()) return x;

        // Lay out into the reusable batch — no per-glyph allocation.
        batch.begin(color);
        float advance = layout.layoutLine(text, weight, size, x, yTop, align, batch);

        if (batch.count == 0) return x + advance;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(textSupplier);               // cached supplier — no lambda alloc

        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        int r = batch.r, g = batch.g, b = batch.b, al = batch.al;
        float[] d = batch.data;

        // Draw CONTIGUOUS SAME-ATLAS groups. A normal run resolves entirely to one weight atlas
        // (one group = one draw, as before); the Stage-11 icon seam can interleave the icon atlas
        // into a text run (e.g. a server entity name carrying PUA glyphs reaches the Target HUD),
        // and each glyph must sample ITS atlas with ITS PxRange — last-wins binding renders garbage.
        int start = 0;
        while (start < batch.count) {
            int aid = batch.atlas[start];
            int groupEnd = start + 1;
            while (groupEnd < batch.count && batch.atlas[groupEnd] == aid) groupEnd++;

            MsdfAtlas a = registry.atlasById(aid);
            try {
                a.ensureTexture();
            } catch (RuntimeException e) {
                if (registry.isIconAtlas(aid)) {   // icons must never kill text: drop icon quads, disable icons
                    registry.disableIcons(e);
                    start = groupEnd;
                    continue;
                }
                throw e;                            // font atlas failure → existing broken/LEGACY path
            }
            RenderSystem.setShaderTexture(0, a.textureId);
            setUniform1f("PxRange",      a.pxRange);
            setUniform1f("OutlineWidth", outlineW);
            setUniformColor("OutlineColor", outlineC);
            setUniform1f("GlowRange",    glowR);
            setUniformColor("GlowColor", glowC);
            setUniform1f("WeightBias",   weightBias);

            BufferBuilder bb = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int i = start * 8, end = groupEnd * 8; i < end; i += 8) {
                float x0 = d[i],     y0 = d[i + 1];
                float x1 = d[i + 2], y1 = d[i + 3];
                float u0 = d[i + 4], v0 = d[i + 5];
                float u1 = d[i + 6], v1 = d[i + 7];
                // Quad: top-left, bottom-left, bottom-right, top-right (QUADS winding).
                bb.vertex(mat, x0, y0, 0f).texture(u0, v0).color(r, g, b, al);
                bb.vertex(mat, x0, y1, 0f).texture(u0, v1).color(r, g, b, al);
                bb.vertex(mat, x1, y1, 0f).texture(u1, v1).color(r, g, b, al);
                bb.vertex(mat, x1, y0, 0f).texture(u1, v0).color(r, g, b, al);
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());
            ModernBackend.DRAWS++;   // profiler: text IS batched — one draw per atlas run, not per glyph
            start = groupEnd;
        }
        RenderSystem.enableCull();

        batch.reset();
        return x + advance;
    }

    // -------------------------------------------------------------------------
    // Uniform helpers — null-safe, no allocation
    // -------------------------------------------------------------------------

    private static void setUniform1f(String name, float v) {
        GlUniform u = UiShaders.TEXT.getUniform(name);
        if (u != null) u.set(v);
    }

    /** Packs a packed ARGB int into a vec4 uniform (r, g, b, a). */
    private static void setUniformColor(String name, int color) {
        GlUniform u = UiShaders.TEXT.getUniform(name);
        if (u != null) u.set(Color.rf(color), Color.gf(color), Color.bf(color), Color.af(color));
    }

    // =========================================================================
    // GlyphBatch — private reusable accumulator (implements GlyphSink)
    // =========================================================================

    /**
     * Reusable accumulator for glyph quads emitted by {@link TextLayout#layoutLine}.
     *
     * <p>Stores raw floats (x0, y0, x1, y1, u0, v0, u1, v1) per glyph in a backing
     * {@code float[]} that grows ×2 on overflow — NO per-glyph allocation once the
     * array has been sized for the longest line in this session. A parallel per-quad
     * {@code atlas[]} records each glyph's atlas: normal text resolves to one weight
     * atlas, but the Stage-11 icon seam may interleave the icon atlas into a run, and
     * the renderer draws contiguous same-atlas groups.</p>
     *
     * <p>BATCHING SEAM: Stage 1 flushes one draw per same-atlas group (= per line for
     * pure text). A future batcher keeps the GlyphBatch open across multiple runs
     * sharing an atlas and flushes once per frame per atlas → 1 texture bind +
     * 1 shader bind + 1 draw for many lines. The {@link UiText} API does not change.</p>
     */
    private static final class GlyphBatch implements GlyphSink {

        // 8 floats per quad: x0,y0,x1,y1,u0,v0,u1,v1
        float[] data = new float[1024];

        /** Per-quad atlas index (parallel to {@link #data}, one entry per quad). */
        int[] atlas = new int[128];

        /** Number of complete quads accumulated since the last {@link #begin}. */
        int count;

        /** Unpacked RGBA bytes of the current run color. */
        int r, g, b, al;

        /** Reset accumulator and unpack {@code color} (ARGB) into per-channel bytes. */
        void begin(int color) {
            count   = 0;
            r  = (color >>> 16) & 0xFF;
            g  = (color >>>  8) & 0xFF;
            b  =  color         & 0xFF;
            al = (color >>> 24) & 0xFF;
        }

        /** Appends one glyph quad — called by {@link TextLayout#layoutLine} per visible glyph. */
        @Override
        public void glyph(int atlasId,
                          float x0, float y0, float x1, float y1,
                          float u0, float v0, float u1, float v1) {
            int base = count * 8;
            if (base + 8 > data.length) {
                // Grow backing arrays ×2 — amortised O(1), zero per-glyph alloc after warm-up.
                float[] grown = new float[data.length * 2];
                System.arraycopy(data, 0, grown, 0, data.length);
                data = grown;
                int[] grownA = new int[atlas.length * 2];
                System.arraycopy(atlas, 0, grownA, 0, atlas.length);
                atlas = grownA;
            }
            data[base]     = x0;
            data[base + 1] = y0;
            data[base + 2] = x1;
            data[base + 3] = y1;
            data[base + 4] = u0;
            data[base + 5] = v0;
            data[base + 6] = u1;
            data[base + 7] = v1;
            atlas[count] = atlasId;
            count++;
        }

        /** Clears accumulated quads without releasing the backing array. */
        void reset() { count = 0; }
    }
}
