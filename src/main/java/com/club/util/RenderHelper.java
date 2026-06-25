package com.club.util;

import net.minecraft.client.gui.DrawContext;

/**
 * Lightweight rendering helpers for the Premium Dark Glass UI. Rounded corners
 * and borders are anti-aliased via per-pixel coverage blending, so cards and
 * panels read as smooth glass rather than stair-stepped pixels.
 */
public final class RenderHelper {
    private RenderHelper() {}

    /** Filled rounded rectangle with anti-aliased corners. */
    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, color); return; }
        // solid body: middle band full-height, plus left/right strips between corners
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        // four anti-aliased corner discs
        aaCorner(ctx, x, y, r, x + r, y + r, color);                 // top-left
        aaCorner(ctx, x + w - r, y, r, x + w - r, y + r, color);     // top-right
        aaCorner(ctx, x, y + h - r, r, x + r, y + h - r, color);     // bottom-left
        aaCorner(ctx, x + w - r, y + h - r, r, x + w - r, y + h - r, color); // bottom-right
    }

    /**
     * The one shared building block for boxed controls (dropdown / button /
     * segmented track): an element fill plus a 1px border. Every boxed control in
     * the UI goes through this so they read as one consistent component family.
     */
    public static void controlSurface(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int border) {
        roundedRect(ctx, x, y, w, h, r, fill);
        roundedBorder(ctx, x, y, w, h, r, border);
    }

    /** Anti-aliased 1px rounded border outline. */
    public static void roundedBorder(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        // straight edges
        ctx.fill(x + r, y, x + w - r, y + 1, color);
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        // anti-aliased corner arcs
        aaArc(ctx, x, y, r, x + r, y + r, color);
        aaArc(ctx, x + w - r, y, r, x + w - r, y + r, color);
        aaArc(ctx, x, y + h - r, r, x + r, y + h - r, color);
        aaArc(ctx, x + w - r, y + h - r, r, x + w - r, y + h - r, color);
    }

    /** Fills an r×r corner box with a coverage-blended quarter disc centered at (cx,cy). */
    private static void aaCorner(DrawContext ctx, int bx, int by, int r, double cx, double cy, int color) {
        int baseA = (color >>> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        for (int py = 0; py < r; py++) {
            for (int px = 0; px < r; px++) {
                double dx = (bx + px + 0.5) - cx;
                double dy = (by + py + 0.5) - cy;
                double cov = r - Math.sqrt(dx * dx + dy * dy) + 0.5;
                if (cov <= 0) continue;
                if (cov > 1) cov = 1;
                int a = (int) Math.round(baseA * cov);
                if (a <= 0) continue;
                ctx.fill(bx + px, by + py, bx + px + 1, by + py + 1, (a << 24) | rgb);
            }
        }
    }

    /** Draws a coverage-blended 1px-wide quarter-circle arc (for borders). */
    private static void aaArc(DrawContext ctx, int bx, int by, int r, double cx, double cy, int color) {
        int baseA = (color >>> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        double edge = r - 0.5;
        for (int py = 0; py < r; py++) {
            for (int px = 0; px < r; px++) {
                double dx = (bx + px + 0.5) - cx;
                double dy = (by + py + 0.5) - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double cov = 1.0 - Math.abs(dist - edge);
                if (cov <= 0) continue;
                if (cov > 1) cov = 1;
                int a = (int) Math.round(baseA * cov);
                if (a <= 0) continue;
                ctx.fill(bx + px, by + py, bx + px + 1, by + py + 1, (a << 24) | rgb);
            }
        }
    }

    /** 1px horizontal hairline. */
    public static void hLine(DrawContext ctx, int x, int y, int w, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
    }

    /** Linear interpolation of two ARGB colors, t in [0,1]. */
    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ca = (int) (aa + (ba - aa) * t);
        int cr = (int) (ar + (br - ar) * t);
        int cg = (int) (ag + (bg - ag) * t);
        int cb = (int) (ab + (bb - ab) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    /** Horizontal gradient rounded bar (left color -> right color). */
    public static void gradientBar(DrawContext ctx, int x, int y, int w, int h, int r, int left, int right) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < w; i++) {
            int c = lerpColor(left, right, w <= 1 ? 0f : (float) i / (w - 1));
            ctx.fill(x + i, y, x + i + 1, y + h, c);
        }
        // soften the two ends with the rounded mask by re-stamping rounded alpha corners is overkill;
        // a short rounded cap keeps the look clean enough at these sizes.
    }

    /**
     * Rounded rectangle filled with a left→right gradient. Corners are masked by
     * re-stamping the rounded shape's coverage, so it reads as smooth glass.
     */
    public static void gradientRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int left, int right) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { gradientBar(ctx, x, y, w, h, 0, left, right); return; }
        // gradient body in the same three bands roundedRect uses
        gradStrip(ctx, x + r, y, w - 2 * r, h, x, w, left, right);
        gradStrip(ctx, x, y + r, r, h - 2 * r, x, w, left, right);
        gradStrip(ctx, x + w - r, y + r, r, h - 2 * r, x, w, left, right);
        gradCorner(ctx, x, y, r, x + r, y + r, x, w, left, right);
        gradCorner(ctx, x + w - r, y, r, x + w - r, y + r, x, w, left, right);
        gradCorner(ctx, x, y + h - r, r, x + r, y + h - r, x, w, left, right);
        gradCorner(ctx, x + w - r, y + h - r, r, x + w - r, y + h - r, x, w, left, right);
    }

    private static void gradStrip(DrawContext ctx, int x, int y, int w, int h, int gx, int gw, int left, int right) {
        for (int i = 0; i < w; i++) {
            int c = lerpColor(left, right, gw <= 1 ? 0f : (float) (x + i - gx) / (gw - 1));
            ctx.fill(x + i, y, x + i + 1, y + h, c);
        }
    }

    private static void gradCorner(DrawContext ctx, int bx, int by, int r, double cx, double cy, int gx, int gw, int left, int right) {
        for (int py = 0; py < r; py++) {
            for (int px = 0; px < r; px++) {
                double dx = (bx + px + 0.5) - cx;
                double dy = (by + py + 0.5) - cy;
                double cov = r - Math.sqrt(dx * dx + dy * dy) + 0.5;
                if (cov <= 0) continue;
                if (cov > 1) cov = 1;
                int base = lerpColor(left, right, gw <= 1 ? 0f : (float) (bx + px - gx) / (gw - 1));
                int a = (int) Math.round(((base >>> 24) & 0xFF) * cov);
                if (a <= 0) continue;
                ctx.fill(bx + px, by + py, bx + px + 1, by + py + 1, (a << 24) | (base & 0xFFFFFF));
            }
        }
    }

    /** 3-stop colour ramp: top→mid over the first half, mid→bottom over the second. */
    private static int lerp3(int top, int mid, int bottom, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t < 0.5f ? lerpColor(top, mid, t * 2f) : lerpColor(mid, bottom, (t - 0.5f) * 2f);
    }

    /**
     * Rounded rectangle filled with a barely-there vertical 3-stop gradient
     * ({@code top}→{@code mid}→{@code bottom}). Rows are clipped to the rounded
     * silhouette so the window reads as one calm surface with a faint centre lift.
     */
    public static void gradientRoundedRectV3(DrawContext ctx, int x, int y, int w, int h, int r, int top, int mid, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < h; i++) {
            int inset = 0;
            if (r > 0) {
                if (i < r) inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) (r - i) * (r - i)));
                else if (i >= h - r) { int j = i - (h - r); inset = (int) Math.round(Math.sqrt((double) r * r - (double) (r - 1 - j) * (r - 1 - j))); inset = r - inset; }
            }
            int c = lerp3(top, mid, bottom, h <= 1 ? 0f : (float) i / (h - 1));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
        }
    }

    /** Rounded rectangle filled with a top→bottom gradient. */
    public static void gradientRoundedRectV(DrawContext ctx, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            for (int i = 0; i < h; i++) ctx.fill(x, y + i, x + w, y + i + 1, lerpColor(top, bottom, h <= 1 ? 0f : (float) i / (h - 1)));
            return;
        }
        gradStripV(ctx, x, y + r, w, h - 2 * r, y, h, top, bottom);
        gradStripV(ctx, x + r, y, w - 2 * r, r, y, h, top, bottom);
        gradStripV(ctx, x + r, y + h - r, w - 2 * r, r, y, h, top, bottom);
        gradCornerV(ctx, x, y, r, x + r, y + r, y, h, top, bottom);
        gradCornerV(ctx, x + w - r, y, r, x + w - r, y + r, y, h, top, bottom);
        gradCornerV(ctx, x, y + h - r, r, x + r, y + h - r, y, h, top, bottom);
        gradCornerV(ctx, x + w - r, y + h - r, r, x + w - r, y + h - r, y, h, top, bottom);
    }

    private static void gradStripV(DrawContext ctx, int x, int y, int w, int h, int gy, int gh, int top, int bottom) {
        for (int i = 0; i < h; i++) {
            int c = lerpColor(top, bottom, gh <= 1 ? 0f : (float) (y + i - gy) / (gh - 1));
            ctx.fill(x, y + i, x + w, y + i + 1, c);
        }
    }

    private static void gradCornerV(DrawContext ctx, int bx, int by, int r, double cx, double cy, int gy, int gh, int top, int bottom) {
        for (int py = 0; py < r; py++) {
            for (int px = 0; px < r; px++) {
                double dx = (bx + px + 0.5) - cx;
                double dy = (by + py + 0.5) - cy;
                double cov = r - Math.sqrt(dx * dx + dy * dy) + 0.5;
                if (cov <= 0) continue;
                if (cov > 1) cov = 1;
                int base = lerpColor(top, bottom, gh <= 1 ? 0f : (float) (by + py - gy) / (gh - 1));
                int a = (int) Math.round(((base >>> 24) & 0xFF) * cov);
                if (a <= 0) continue;
                ctx.fill(bx + px, by + py, bx + px + 1, by + py + 1, (a << 24) | (base & 0xFFFFFF));
            }
        }
    }

    /**
     * Paints a very soft radial colour volume inside a rounded rectangle — the
     * "depth glow" of the window. The glow is centred at {@code (cx,cy)} and fades
     * out by {@code radius} with a quadratic falloff, so it reads as a calm wash
     * rather than a hard spot. Painted in small cells (cheap, ~a few thousand
     * fills) and clipped to the rounded silhouette so it never spills past the
     * window edge. {@code color}'s alpha is the peak intensity.
     */
    public static void radialGlow(DrawContext ctx, int x, int y, int w, int h, int r,
                                  double cx, double cy, double radius, int color) {
        if (w <= 0 || h <= 0 || radius <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        int baseA = (color >>> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        final int CELL = 4;
        for (int gy = 0; gy < h; gy += CELL) {
            int ch = Math.min(CELL, h - gy);
            // rounded inset for this band (sampled at its vertical centre)
            int mid = gy + ch / 2;
            int inset = 0;
            if (mid < r) inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) (r - mid) * (r - mid)));
            else if (mid >= h - r) { int j = h - 1 - mid; inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) (r - 1 - j) * (r - 1 - j))); }
            for (int gx = 0; gx < w; gx += CELL) {
                int cw = Math.min(CELL, w - gx);
                int dx0 = Math.max(gx, inset);
                int dx1 = Math.min(gx + cw, w - inset);
                if (dx1 <= dx0) continue;
                double pxc = x + gx + cw / 2.0, pyc = y + gy + ch / 2.0;
                double d = Math.hypot(pxc - cx, pyc - cy);
                double f = 1.0 - d / radius;
                if (f <= 0) continue;
                f = f * f * (3.0 - 2.0 * f); // smoothstep → soft, bandless shoulder
                int a = (int) Math.round(baseA * f);
                if (a <= 0) continue;
                ctx.fill(x + dx0, y + gy, x + dx1, y + gy + ch, (a << 24) | rgb);
            }
        }
    }

    /** ARGB with replaced alpha (0..255). */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** ARGB with its alpha scaled by {@code mul} (0..1). */
    public static int scaleAlpha(int color, float mul) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, mul)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /**
     * Subtle glassy top sheen for a panel: a soft vertical highlight fading out
     * over the top {@code band} pixels, plus a 1px inner top hairline.
     */
    public static void topSheen(DrawContext ctx, int x, int y, int w, int r, int band, int color) {
        int baseA = (color >>> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        for (int i = 0; i < band; i++) {
            float t = 1f - (float) i / band;
            int a = Math.round(baseA * t * t);
            if (a <= 0) continue;
            int inset = i < r ? r - (int) Math.sqrt(r * (double) r - (r - i) * (double) (r - i)) : 0;
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, (a << 24) | rgb);
        }
    }

    /** Soft rounded halo (used under slider fills / active accents). */
    public static void glow(DrawContext ctx, int x, int y, int w, int h, int color) {
        roundedRect(ctx, x - 1, y - 1, w + 2, h + 2, (Math.min(w, h) + 2) / 2, scaleAlpha(color, 0.5f));
        roundedRect(ctx, x - 2, y - 2, w + 4, h + 4, (Math.min(w, h) + 4) / 2, scaleAlpha(color, 0.28f));
    }

    /** Soft drop shadow under a rounded panel — concentric fading rings. */
    public static void dropShadow(DrawContext ctx, int x, int y, int w, int h, int r, int spread) {
        for (int i = spread; i >= 1; i--) {
            int a = Math.max(0, 7 - (7 * i) / Math.max(1, spread)) + 1;
            roundedRect(ctx, x - i, y - i + 2, w + 2 * i, h + 2 * i, r + i, withAlpha(0x000000, a));
        }
    }

    /** 1px inner top highlight that hugs the top edge between the rounded corners. */
    public static void topHighlight(DrawContext ctx, int x, int y, int w, int r, int color) {
        ctx.fill(x + r, y + 1, x + w - r, y + 2, color);
        // tiny tapers just inside the corners so the line doesn't end abruptly
        ctx.fill(x + r - 1, y + 2, x + r, y + 3, scaleAlpha(color, 0.6f));
        ctx.fill(x + w - r, y + 2, x + w - r + 1, y + 3, scaleAlpha(color, 0.6f));
    }

    /** Accent-tinted 1px top edge — the faint iridescent rim on premium glass. */
    public static void topEdge(DrawContext ctx, int x, int y, int w, int r, int color) {
        ctx.fill(x + r, y, x + w - r, y + 1, color);
    }

    /**
     * A soft moving light band sweeping horizontally across a panel — the glass
     * "shimmer". {@code phase} in [0,1) drives its position; clamped to the panel
     * box (rounded corners are covered by the border, so no scissor needed).
     */
    public static void sheenSweep(DrawContext ctx, int x, int y, int w, int h, float phase, int peakAlpha) {
        int bandW = Math.max(20, w / 7);
        double travel = w + bandW * 2.0;
        double center = x - bandW + phase * travel;
        int c0 = Math.max(x, (int) (center - bandW));
        int c1 = Math.min(x + w, (int) (center + bandW));
        for (int cx = c0; cx < c1; cx++) {
            double d = Math.abs(cx - center) / bandW;
            if (d > 1) continue;
            float f = (float) (1 - d);
            f = f * f;
            int a = Math.round(peakAlpha * f);
            if (a <= 0) continue;
            ctx.fill(cx, y, cx + 1, y + h, (a << 24) | 0xFFFFFF);
        }
    }

}
