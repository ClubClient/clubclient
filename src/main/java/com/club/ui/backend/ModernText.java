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
        // Profiler seam (Stage 65): shaping a string into glyph quads is the suspected bulk of BUILD.
        // Measured here rather than argued about. Free when no measurement window is open.
        boolean prof = com.club.modules.perf.HudProfiler.armed();
        long t0 = prof ? System.nanoTime() : 0L;
        try {
            return draw0(text, x, y, style);
        } finally {
            if (prof) com.club.modules.perf.HudProfiler.addTextNs(System.nanoTime() - t0);
        }
    }

    private float draw0(String text, float x, float y, TextStyle style) {
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

        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        int r = batch.r, g = batch.g, b = batch.b, al = batch.al;
        float[] d = batch.data;

        // QUEUE contiguous same-atlas groups instead of drawing them. A normal run resolves entirely to
        // one weight atlas; the Stage-11 icon seam can interleave the icon atlas into a text run (a PUA
        // glyph in an entity name reaching the Target HUD), and each glyph must sample ITS atlas with ITS
        // PxRange — last-wins binding renders garbage, so the atlas is part of the queue key.
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
            queue(a, outlineW, outlineC, glowR, glowC, weightBias, mat, d, start, groupEnd, r, g, b, al);
            start = groupEnd;
        }

        batch.reset();
        return x + advance;
    }

    // -------------------------------------------------------------------------
    // Text batching (Stage 61)
    // -------------------------------------------------------------------------
    // The HUD renders TABULAR numbers — every digit placed at its own x, i.e. its own draw call. That
    // was 33 of the HUD's 37 GL draws a frame, and draw calls, not pixels, were the whole cost. Runs
    // that share an atlas AND the same shader uniforms (they are per-STYLE, not per-glyph) now queue up
    // and go out together.
    //
    // Strict painter's order is kept by an invariant: at most ONE of the two batches (shapes, text) is
    // ever pending. Submitting text flushes the pending shapes; submitting a shape flushes the pending
    // text (ModernBackend does that). So a chip's capsule can never land on top of its own label.
    //
    // Positions are pre-transformed by the pose matrix as they are queued, so a matrix change (the
    // menu's canvas scale) can't retroactively move already-queued glyphs.

    private float[] tq = new float[8 * 256];   // queued quads: x0,y0,x1,y1,u0,v0,u1,v1 (pose space)
    private int[]   tc = new int[256];         // one packed ARGB per quad
    private int     tn;                        // queued quad count
    private MsdfAtlas tAtlas;
    private float tOutlineW, tGlowR, tWeightBias;
    private int   tOutlineC, tGlowC;

    private boolean sameKey(MsdfAtlas a, float ow, int oc, float gr, int gc, float wb) {
        return tAtlas == a && tOutlineW == ow && tOutlineC == oc && tGlowR == gr && tGlowC == gc && tWeightBias == wb;
    }

    private void queue(MsdfAtlas a, float ow, int oc, float gr, int gc, float wb,
                       Matrix4f mat, float[] d, int from, int to, int r, int g, int b, int al) {
        if (tn > 0 && !sameKey(a, ow, oc, gr, gc, wb)) flush();   // different uniforms → its own draw
        if (tn == 0) {
            Backends.MODERN_R.flush();   // shapes queued so far belong UNDER this text
            tAtlas = a; tOutlineW = ow; tOutlineC = oc; tGlowR = gr; tGlowC = gc; tWeightBias = wb;
        }
        int need = tn + (to - from);
        if (need > tc.length) {
            int cap = Math.max(need, tc.length * 2);
            tq = java.util.Arrays.copyOf(tq, cap * 8);
            tc = java.util.Arrays.copyOf(tc, cap);
        }
        int argb = (al << 24) | (r << 16) | (g << 8) | b;
        float m00 = mat.m00(), m10 = mat.m10(), m30 = mat.m30();
        float m01 = mat.m01(), m11 = mat.m11(), m31 = mat.m31();
        for (int i = from * 8, end = to * 8; i < end; i += 8) {
            float x0 = d[i], y0 = d[i + 1], x1 = d[i + 2], y1 = d[i + 3];
            int o = tn * 8;
            tq[o]     = m00 * x0 + m10 * y0 + m30;   // pre-transform: pose space, so a later matrix
            tq[o + 1] = m01 * x0 + m11 * y0 + m31;   // change can't move these glyphs
            tq[o + 2] = m00 * x1 + m10 * y1 + m30;
            tq[o + 3] = m01 * x1 + m11 * y1 + m31;
            tq[o + 4] = d[i + 4]; tq[o + 5] = d[i + 5];
            tq[o + 6] = d[i + 6]; tq[o + 7] = d[i + 7];
            tc[tn] = argb;
            // Order proof (Stage 67): a glyph drawn AFTER an icon that overlaps it would be stolen by the
            // icon batch. Recorded per glyph, in the same pose space as the icon quads.
            com.club.modules.perf.DrawBoxes.add(com.club.modules.perf.DrawBoxes.TEXT,
                    tq[o], tq[o + 1], tq[o + 2], tq[o + 3]);
            tn++;
        }
    }

    /** Submit every queued glyph. Safe any time; a no-op when nothing is queued. */
    public void flush() {
        if (tn == 0 || broken) { tn = 0; return; }
        int n = tn;
        tn = 0;
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShader(textSupplier);
            RenderSystem.setShaderTexture(0, tAtlas.textureId);
            setUniform1f("PxRange",      tAtlas.pxRange);
            setUniform1f("OutlineWidth", tOutlineW);
            setUniformColor("OutlineColor", tOutlineC);
            setUniform1f("GlowRange",    tGlowR);
            setUniformColor("GlowColor", tGlowC);
            setUniform1f("WeightBias",   tWeightBias);

            BufferBuilder bb = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (int q = 0; q < n; q++) {
                int o = q * 8;
                float x0 = tq[o], y0 = tq[o + 1], x1 = tq[o + 2], y1 = tq[o + 3];
                float u0 = tq[o + 4], v0 = tq[o + 5], u1 = tq[o + 6], v1 = tq[o + 7];
                int c = tc[q];
                int r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF, al = (c >>> 24) & 0xFF;
                // Quad: top-left, bottom-left, bottom-right, top-right (QUADS winding).
                bb.vertex(x0, y0, 0f).texture(u0, v0).color(r, g, b, al);
                bb.vertex(x0, y1, 0f).texture(u0, v1).color(r, g, b, al);
                bb.vertex(x1, y1, 0f).texture(u1, v1).color(r, g, b, al);
                bb.vertex(x1, y0, 0f).texture(u1, v0).color(r, g, b, al);
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());
            RenderSystem.enableCull();
            ModernBackend.DRAWS++; ModernBackend.TEXT_DRAWS++;
        } catch (Exception e) {
            broken = true;
            System.err.println("[club.ui] modern text unavailable -> LEGACY: " + e);
        }
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
