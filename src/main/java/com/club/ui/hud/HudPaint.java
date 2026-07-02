package com.club.ui.hud;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.text.TextEffect;
import com.club.ui.theme.Tokens;

/**
 * Shared HUD chrome — the flat translucent ground behind every element (Stage 10 "light structure").
 * Strictly flat and deliberately recessive: a subtle {@code bg1} fill + the system's faint {@code border.subtle}
 * hairline (~6%), so it grounds the content over the world without reading as an outlined rectangle. No depth
 * tricks (no drop-shadow / neutral gradient / lit edge). All four elements share this exact ground + geometry so
 * they read as one product, not a set of boxes. Text sits on a soft dark backing ({@link #textShadow}).
 * Render-only; every layer fades by {@code a}.
 */
final class HudPaint {
    private HudPaint() {}

    /** Fraction of the darkest tone used for the panel fill (subtle — the world shows through). */
    private static final float FILL = 0.5f;
    /** Soft dark text backing (~60% black). */
    private static final int SHADOW = Color.withAlpha(0xFF000000, 0x99);

    /** Draws the element's ground. {@code a} in [0,1] fades the whole panel (element appear/disappear). */
    static void panel(UiContext ctx, float x, float y, float w, float h, float radius, float a) {
        if (a <= 0f) return;
        var r = ctx.renderer();
        r.roundedRect(x, y, w, h, radius, Color.scaleAlpha(Tokens.surface().bg1(), FILL * a));
        r.border(x, y, w, h, radius, Tokens.border().thickness(), Color.scaleAlpha(Tokens.border().subtle(), a));
    }

    /** Soft dark text shadow, faded by {@code a} to match the text alpha (appear/disappear). */
    static TextEffect textShadow(float a) {
        return new TextEffect.Shadow(1f, 1f, 1f, Color.scaleAlpha(SHADOW, a));
    }

    // ---- V4 "Chips" (Stage 13) ------------------------------------------------

    /** Chip capsule radius (unscaled) — shape geometry shared by every element. */
    static final float CHIP_RAD = 9f;
    /** Edge-bar side inset / bottom margin (unscaled): keeps the pill mathematically inside the
     *  capsule's rounded corners (the renderer's clip is rectangular, so a full-bleed bar would
     *  poke past the corner curve). */
    static final float EDGE_INSET = 4f, EDGE_BOT = 2f;

    /** V4 capsule: borderless translucent ground — definition comes from the edge bar, not a hairline. */
    static void chip(UiContext ctx, float x, float y, float w, float h, float radius, float a) {
        if (a <= 0f) return;
        ctx.renderer().roundedRect(x, y, w, h, radius, Color.scaleAlpha(Tokens.surface().bg2(), 0.55f * a));
    }

    /**
     * The chip's LIVE EDGE (Stage 13): a recessed pill track along the capsule's bottom + a
     * state-coloured fill for {@code frac} of it. All measurable data speaks through this line —
     * armor durability, effect time draining, Target HP. {@code s} scales geometry; {@code a} fades.
     */
    static void edgeBar(UiContext ctx, float chipX, float chipY, float chipW, float chipH,
                        float barH, float frac, int color, float s, float a) {
        if (a <= 0f) return;
        var r = ctx.renderer();
        float inset = EDGE_INSET * s, bh = barH * s;
        float bx = chipX + inset, bw = chipW - 2 * inset;
        float by = chipY + chipH - bh - EDGE_BOT * s;
        float rr = bh / 2f;
        r.roundedRect(bx, by, bw, bh, rr, Color.scaleAlpha(Color.scaleAlpha(Tokens.surface().surfaceHi(), 0.6f), a));
        float f = Math.max(0f, Math.min(1f, frac));
        if (f > 0f) r.roundedRect(bx, by, Math.max(bh, bw * f), bh, rr, Color.scaleAlpha(color, a));
    }
}
