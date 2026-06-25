package com.club.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * The CLUB icon set — a tiny, consistent family of functional marks. Only the
 * glyphs with a real job survive: the dropdown chevron, the active-module dot,
 * and the small state dot (armour durability). There is no invented logo, no
 * window buttons and no decorative one-off glyphs. Each icon draws inside an
 * {@code n × n} box anchored at (x, y); colour is the fill colour.
 */
public final class Icons {
    private Icons() {}

    // ---- chrome -----------------------------------------------------------

    /** Chevron, {@code down} for ▼ else ▲. {@code w} wide. */
    public static void chevron(DrawContext ctx, int x, int y, int w, int color, boolean down) {
        int h = w / 2;
        for (int i = 0; i <= h; i++) {
            int yy = down ? y + i : y + h - i;
            ctx.fill(x + i, yy, x + i + 1, yy + 1, color);
            ctx.fill(x + w - i, yy, x + w - i + 1, yy + 1, color);
        }
    }

    // ---- the dot motif ----------------------------------------------------

    /** A small flat filled dot of diameter {@code n} in {@code color}. */
    public static void dot(DrawContext ctx, int x, int y, int n, int color) {
        double cx = x + n / 2.0, cy = y + n / 2.0, r = n / 2.0;
        int ri = (int) Math.ceil(r);
        for (int py = -ri; py <= ri; py++)
            for (int px = -ri; px <= ri; px++)
                if (px * px + py * py <= r * r)
                    ctx.fill((int) cx + px, (int) cy + py, (int) cx + px + 1, (int) cy + py + 1, color);
    }

    /**
     * The active-module marker — a small flat accent dot (no gradient: the accent
     * gradient is reserved for the active tab and toggle only). {@code n} is the
     * dot diameter.
     */
    public static void brandDot(DrawContext ctx, int x, int y, int n) {
        dot(ctx, x, y, n, Theme.ACCENT);
    }
}
