package com.club.ui.hud;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.text.TextEffect;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;

/**
 * Shared HUD chrome for the V4 "Chips" language (Stage 13+): the capsule ground ({@link #chip}),
 * the live edge/gauge lines ({@link #edgeBar}/{@link #rowBar}) and the text backings. Strictly
 * flat — no depth tricks. Render-only; every layer fades by {@code a}. (The Stage-10 shared
 * "light structure" panel was retired with the chips language.)
 */
final class HudPaint {
    private HudPaint() {}

    /** Soft dark text backing (~60% black). */
    private static final int SHADOW = Color.withAlpha(0xFF000000, 0x99);
    /** Lighter backing (~35%) for text sitting on the raw world without a capsule (Armor values) —
     *  the full-strength shadow read as a dirty black outline there (owner). */
    private static final int SHADOW_SOFT = Color.withAlpha(0xFF000000, 0x59);

    /** Soft dark text shadow, faded by {@code a} to match the text alpha (appear/disappear). */
    static TextEffect textShadow(float a) {
        return new TextEffect.Shadow(1f, 1f, 1f, Color.scaleAlpha(SHADOW, a));
    }

    /** The lighter world-backing variant (see {@link #SHADOW_SOFT}). */
    static TextEffect textShadowSoft(float a) {
        return new TextEffect.Shadow(1f, 1f, 1f, Color.scaleAlpha(SHADOW_SOFT, a));
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

    /** LEGACY letter fallback (Stage 26): the slot's/effect's INITIAL centered in the icon box —
     *  keeps the row an identity when neither the duotone bake nor the SDF glyph could draw.
     *  Same tint as the icon it stands in for; the vanilla-font letter is the honest parachute. */
    static void iconLetter(UiContext ctx, String initial, float x, float y, float box, float s, int color, float a) {
        if (a <= 0f) return;
        float size = 12f * s;
        float w = ctx.text().width(initial, Weight.SEMIBOLD, size);
        float lh = ctx.text().lineHeight(Weight.SEMIBOLD, size);
        ctx.text().draw(initial, x + (box * s - w) / 2f, y + (box * s - lh) / 2f,
                TextStyle.of(Weight.SEMIBOLD, size, Color.scaleAlpha(color, a)).effect(textShadow(a)));
    }

    /** A row's live line INSIDE a capsule (Armor): recessed pill + state fill at the given spot —
     *  the in-capsule sibling of {@link #edgeBar} (echoes the menu card's state stripe). */
    static void rowBar(UiContext ctx, float x, float y, float w, float barH, float frac, int color, float s, float a) {
        if (a <= 0f) return;
        var r = ctx.renderer();
        float bh = barH * s, rr = bh / 2f;
        r.roundedRect(x, y, w, bh, rr, Color.scaleAlpha(Color.scaleAlpha(Tokens.surface().surfaceHi(), 0.6f), a));
        float f = Math.max(0f, Math.min(1f, frac));
        if (f > 0f) r.roundedRect(x, y, Math.max(bh, w * f), bh, rr, Color.scaleAlpha(color, a));
    }
}
