package com.club.ui.backend;

import com.club.ClubMod;
import com.club.compat.ShapePipe;
import com.club.ui.Axis;
import com.club.ui.Color;
import com.club.ui.Radii;
import com.club.ui.UiRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * The MODERN shape renderer for 1.21.5+ — {@link ModernBackend}'s twin on the far side of the seam.
 *
 * <p><b>What it is.</b> The same analytic SDF shapes 1.21.1 has shipped since v0.1, re-expressed as
 * RECORDED state instead of immediate draws. Every pixel-level decision — the SDF, the coverage curve, the
 * glow falloff, the dither — lives in club:club_shape_frag.glsl and is copied from {@code ui_sdf_shape.fsh}
 * unchanged, because the owner's requirement is "один в один": a rounded rect must be the same rounded rect
 * on 1.21.1, 1.21.8 and 1.21.11. What differs is only HOW the parameters reach the shader, and that is
 * {@link ShapePipe}'s problem, not this class's.
 *
 * <p><b>This class is version-blind on purpose.</b> Not one {@code //?} lives here: every seam between
 * 1.21.8 and 1.21.11 is inside {@code com.club.compat}. It is excluded from the build below 1.21.5 (see
 * build.gradle) because {@link ShapePipe} does not exist there — on that side {@link ModernBackend} is the
 * shape path.
 *
 * <p><b>Why there is no batching seam here and no flush.</b> {@link ModernBackend} had to batch by hand: it
 * drew immediately, and one GL draw per shape cost ~23 us of submission overhead each. From 1.21.5 vanilla's
 * GuiRenderer does that job — it groups every recorded element by pipeline and vertex format and draws them
 * together — so the whole reason for {@code ModernBackend.flush()}, and for the ordering rules that hung off
 * it, is gone. Nothing here is pending, so nothing here can be forgotten at the end of a pass.
 */
public final class ModernShapes implements UiRenderer {

    private DrawContext ctx;

    /** Set true on the first unrecoverable failure; every later draw short-circuits and the UI drops to
     *  LEGACY for the rest of the session. Mirrors {@code ModernBackend.broken}. */
    private boolean broken;

    /** False once this instance has hit an unrecoverable error — read by the backend switch. */
    public boolean healthy() { return !broken; }

    public void begin(DrawContext drawContext) {
        this.ctx = drawContext;
        opacityTop = 0;
        clipTop = 0;
    }

    /** True: rounding, AA and glow are computed per-fragment from a distance field, so they are exact at
     *  any GUI scale. This is the whole reason this class exists rather than LEGACY's fills. */
    @Override public boolean isResolutionIndependent() { return true; }

    /** MC GUI units per CALLER unit. Kept for API parity with the other backends; the shape path does not
     *  need it, because the clip rect crosses the matrix in {@link ShapePipe#toScreenRect} rather than being
     *  converted by hand — see {@link com.club.compat.Mtx#scissor}, whose 1.21.5+ branch says the same. */
    public void unitScale(float k) { }

    // -------------------------------------------------------------------------
    // Solid shapes
    // -------------------------------------------------------------------------

    @Override public void rect(float x, float y, float w, float h, int color) {
        shape(x, y, w, h, 0f, 0f, 0f, color, color, color, color);
    }

    @Override public void roundedRect(float x, float y, float w, float h, float radius, int color) {
        shape(x, y, w, h, radius, 0f, 0f, color, color, color, color);
    }

    /**
     * Per-corner radii, drawn as FOUR quadrant quads that share one SDF box.
     *
     * <p>The vertex carriers hold a single radius (see club:club_shape_vert.glsl), so four different corners
     * cannot ride one quad. Splitting is not an approximation: each quadrant is given the WHOLE shape's
     * centre and half-size and differs only in its own corner's radius, and along the two seam lines
     * (x = cx, y = cy) the rounded-box SDF is provably independent of r — at localPos.x = 0 the distance
     * reduces to {@code max(-halfW, |y|-halfH)}, with every r cancelling. So the quadrants agree exactly
     * where they meet, and the alternative (interpolating four radii across one quad) would NOT: it would
     * drift a corner's radius by a few percent toward its neighbours' and show up as a subtly wrong curve.
     */
    @Override public void roundedRect(float x, float y, float w, float h, Radii r, int color) {
        if (r.tl() == r.tr() && r.tr() == r.br() && r.br() == r.bl()) {
            shape(x, y, w, h, r.tl(), 0f, 0f, color, color, color, color);   // uniform: one quad is enough
            return;
        }
        if (broken || ctx == null || w <= 0 || h <= 0 || !ready()) return;
        float cx = x + w * 0.5f, cy = y + h * 0.5f, hw = w * 0.5f, hh = h * 0.5f;
        int c = Color.scaleAlpha(color, currentOpacity());
        int[] clip = clipTop == 0 ? null : clipTop();
        quadrant(cx, cy, hw, hh, x,  y,  cx, cy, r.tl(), c, clip);
        quadrant(cx, cy, hw, hh, cx, y,  x + w, cy, r.tr(), c, clip);
        quadrant(cx, cy, hw, hh, cx, cy, x + w, y + h, r.br(), c, clip);
        quadrant(cx, cy, hw, hh, x,  cy, cx, y + h, r.bl(), c, clip);
    }

    private void quadrant(float cx, float cy, float hw, float hh,
                          float qx0, float qy0, float qx1, float qy1, float radius, int c, int[] clip) {
        ShapePipe.shape(ctx, qx0, qy0, qx1, qy1, cx, cy, hw, hh, radius, 0f, c, c, c, c, clip);
    }

    @Override public void border(float x, float y, float w, float h, float radius, float thickness, int color) {
        // thickness > 0 is what tells the shader "this is a BORDER". A hairline would encode to 0 and
        // silently become a FILLED shape, so a border always keeps at least one unit of the carrier.
        float th = Math.max(thickness, 1f / ShapePipe.EDGE_SCALE);
        shape(x, y, w, h, radius, th, 0f, color, color, color, color);
    }

    @Override public void circle(float cx, float cy, float r, int color) {
        // A circle is a rect whose radius equals its half-size; the shader's clamp makes that exact.
        shape(cx - r, cy - r, r * 2f, r * 2f, r, 0f, 0f, color, color, color, color);
    }

    // -------------------------------------------------------------------------
    // Gradient — per-vertex colour, no second draw and no ColorB uniform
    // -------------------------------------------------------------------------

    /**
     * A linear ramp between two colours across the shape.
     *
     * <p>The old shader carried {@code ColorB} as a uniform and recomputed the blend factor per fragment
     * from localPos. Uniforms are not available on this side (see {@link ShapePipe}) — and are not needed:
     * putting colourA on the two vertices at the start of the axis and colourB on the two at the end makes
     * the GPU's own interpolator produce the identical linear ramp, for free.
     */
    @Override public void gradient(float x, float y, float w, float h, float radius, int a, int b, Axis axis) {
        // Winding is TL, BL, BR, TR.
        if (axis == Axis.VERTICAL) shape(x, y, w, h, radius, 0f, 0f, a, b, b, a);   // top -> bottom
        else                       shape(x, y, w, h, radius, 0f, 0f, a, a, b, b);   // left -> right
    }

    // -------------------------------------------------------------------------
    // Glow / shadow — the quad grows, the SDF box does not
    // -------------------------------------------------------------------------

    @Override public void glow(float x, float y, float w, float h, float radius, float size, int color) {
        shape(x, y, w, h, radius, -Math.max(size, 1f / ShapePipe.EDGE_SCALE), size, color, color, color, color);
    }

    @Override public void shadow(float x, float y, float w, float h, float radius,
                                 float dx, float dy, float blur, int color) {
        // A shadow is an offset glow — the same falloff, drawn from a shifted origin. Same reduction
        // ModernBackend makes, so the two versions cast the same shadow.
        shape(x + dx, y + dy, w, h, radius, -Math.max(blur, 1f / ShapePipe.EDGE_SCALE), blur,
                color, color, color, color);
    }

    // -------------------------------------------------------------------------
    // Line
    // -------------------------------------------------------------------------

    @Override public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        if (y1 == y2) {
            rect(Math.min(x1, x2), y1 - thickness * 0.5f, Math.abs(x2 - x1), thickness, color);
            return;
        }
        if (x1 == x2) {
            rect(x1 - thickness * 0.5f, Math.min(y1, y2), thickness, Math.abs(y2 - y1), color);
            return;
        }
        if (broken || ctx == null) return;
        // A real rotated capsule: rotate the matrix about the line's midpoint and draw an ordinary
        // axis-aligned rounded rect (radius = half-thickness -> round caps). Only the screen vertices
        // rotate; the shader's localPos stays axis-aligned, so the rounded-box SDF is exact and its
        // fwidth AA adapts to the rotation on its own. The quad bakes the CURRENT matrix at record time
        // (ShapePipe snapshots the pose), so the pop below cannot undo it.
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float mx = (x1 + x2) * 0.5f, my = (y1 + y2) * 0.5f, rad = thickness * 0.5f;
        com.club.compat.Mtx.push(ctx);
        try {
            com.club.compat.Mtx.translate(ctx, mx, my);
            com.club.compat.Mtx.rotateZ(ctx, (float) Math.atan2(dy, dx));
            com.club.compat.Mtx.translate(ctx, -mx, -my);
            roundedRect(mx - (len + thickness) * 0.5f, my - rad, len + thickness, thickness, rad, color);
        } finally {
            com.club.compat.Mtx.pop(ctx);   // ALWAYS balance the stack, even if the record throws
        }
    }

    // -------------------------------------------------------------------------
    // Clip stack
    // -------------------------------------------------------------------------

    private static final int MAX_STACK = 64;
    private static final int CLIP_STRIDE = 4;                  // x0, y0, x1, y1 in SCREEN space
    private final int[] clipStack = new int[MAX_STACK * CLIP_STRIDE];
    private int clipTop = 0;
    private boolean clipOverflowWarned;

    @Override public void pushClip(float x, float y, float w, float h) { pushClipEntry(x, y, w, h); }

    /** Stage-1 parity with {@link ModernBackend}: the radius is accepted and the clip applied as a
     *  rectangle. A rounded clip needs a mask the recorded path has no channel for yet, and shipping a
     *  silently-square "rounded" clip is what LEGACY already does — so this is no worse, and honest. */
    @Override public void pushRoundedClip(float x, float y, float w, float h, float radius) {
        pushClipEntry(x, y, w, h);
    }

    private void pushClipEntry(float x, float y, float w, float h) {
        if (ctx == null) return;
        if (clipTop >= MAX_STACK) {
            if (!clipOverflowWarned) {
                clipOverflowWarned = true;
                ClubMod.LOGGER.warn("[Club] UI clip stack overflow at depth {} — push dropped, this draw stays "
                        + "clipped to the outer rectangle. A pushClip is missing its popClip.", MAX_STACK);
            }
            return;
        }
        int[] r = ShapePipe.toScreenRect(ctx, x, y, w, h);
        if (clipTop > 0) {   // nested clips only ever NARROW
            int base = (clipTop - 1) * CLIP_STRIDE;
            r[0] = Math.max(r[0], clipStack[base]);
            r[1] = Math.max(r[1], clipStack[base + 1]);
            r[2] = Math.min(r[2], clipStack[base + 2]);
            r[3] = Math.min(r[3], clipStack[base + 3]);
        }
        if (r[2] < r[0]) r[2] = r[0];
        if (r[3] < r[1]) r[3] = r[1];
        int base = clipTop * CLIP_STRIDE;
        clipStack[base] = r[0]; clipStack[base + 1] = r[1];
        clipStack[base + 2] = r[2]; clipStack[base + 3] = r[3];
        ++clipTop;
        // Vanilla's OWN scissor stack is pushed too, and must be: text and icons are drawn through
        // DrawContext (ModernText / IconPipe), and they read that stack, not this one. This class keeps its
        // own copy only because a recorded element must carry its scissor rect with it — the element is
        // drawn long after the stack has unwound.
        com.club.compat.Mtx.scissor(ctx, x, y, w, h, 1f);
    }

    @Override public void popClip() {
        if (ctx == null || clipTop == 0) return;
        --clipTop;
        ctx.disableScissor();   // push/pop is 1:1 — vanilla's stack re-applies the parent itself
    }

    /** The active scissor rect for a shape about to be recorded, or null when nothing is clipped. */
    private int[] clipTop() {
        if (clipTop == 0) return null;
        int base = (clipTop - 1) * CLIP_STRIDE;
        return new int[] { clipStack[base], clipStack[base + 1], clipStack[base + 2], clipStack[base + 3] };
    }

    // -------------------------------------------------------------------------
    // Opacity stack
    // -------------------------------------------------------------------------

    private final float[] opacityStack = new float[MAX_STACK];
    private int opacityTop = 0;
    private boolean opacityOverflowWarned;

    @Override public void pushOpacity(float multiplier) {
        float clamped = Math.max(0f, Math.min(1f, multiplier));
        float current = currentOpacity();
        if (opacityTop < MAX_STACK) {
            opacityStack[opacityTop++] = current * clamped;
        } else if (!opacityOverflowWarned) {
            opacityOverflowWarned = true;
            ClubMod.LOGGER.warn("[Club] UI opacity stack overflow at depth {} — push dropped, this draw uses the "
                    + "outer opacity. A pushOpacity is missing its popOpacity.", MAX_STACK);
        }
    }

    @Override public void popOpacity() { if (opacityTop > 0) --opacityTop; }

    private float currentOpacity() { return (opacityTop == 0) ? 1f : opacityStack[opacityTop - 1]; }

    // -------------------------------------------------------------------------
    // The one funnel every shape goes through
    // -------------------------------------------------------------------------

    /** Whether the shader compiled. Asked per shape, not latched, so a resource reload heals it — the same
     *  contract {@link ShapePipe#ready()} documents. */
    private boolean ready() {
        return ShapePipe.ready();
    }

    /**
     * @param th SIGNED: 0 fill, &gt;0 border thickness, &lt;0 glow feather (see club:club_shape_vert.glsl)
     * @param pad how far the quad grows beyond the shape on every side, so a glow's falloff has room
     */
    private void shape(float x, float y, float w, float h, float radius, float th, float pad,
                       int cTL, int cBL, int cBR, int cTR) {
        if (broken || ctx == null || w <= 0 || h <= 0 || !ready()) return;
        float o = currentOpacity();
        try {
            ShapePipe.shape(ctx,
                    x - pad, y - pad, x + w + pad, y + h + pad,
                    x + w * 0.5f, y + h * 0.5f, w * 0.5f, h * 0.5f,
                    radius, th,
                    Color.scaleAlpha(cTL, o), Color.scaleAlpha(cBL, o),
                    Color.scaleAlpha(cBR, o), Color.scaleAlpha(cTR, o),
                    clipTop());
        } catch (Exception e) {
            broken = true;
            // ERROR: this demotes the WHOLE UI to LEGACY for the rest of the session (the backend switch
            // reads healthy()). Only this line can say why. Fires at most once — `broken` short-circuits
            // every later draw.
            ClubMod.LOGGER.error("[Club] MODERN shape recording failed — UI falls back to LEGACY for the rest "
                    + "of the session", e);
        }
    }
}
