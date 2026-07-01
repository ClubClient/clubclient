package com.club.ui.hud;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.text.TextEffect;
import com.club.ui.theme.Tokens;

/**
 * Shared HUD chrome — the flat translucent panel behind every element (Stage 10 "light structure").
 * A subtle recessed surface so the four HUD elements read as one system without blocking the world:
 * a ~50%-alpha {@code bg1} fill + a soft light hairline + a thin accent brand-edge on the left. Text sits
 * on a soft dark shadow ({@link #textShadow}) for depth + legibility over the world. Flat — no glow. Render-only.
 */
final class HudPaint {
    private HudPaint() {}

    /** Fraction of the darkest tone used for the panel fill (subtle — the world shows through). */
    private static final float FILL = 0.5f;
    /** Soft light hairline that defines the panel edge over the world (~10% white). */
    private static final int HAIRLINE = Color.withAlpha(0xFFFFFFFF, 0x1A);
    /** Accent brand-edge width (px) — the client signature, tying the HUD to the accent colour. */
    private static final float EDGE = 2f;
    /** Soft dark text backing (~60% black). */
    private static final int SHADOW = Color.withAlpha(0xFF000000, 0x99);

    /** Draws the element's panel. {@code a} in [0,1] fades the whole panel (element appear/disappear). */
    static void panel(UiContext ctx, float x, float y, float w, float h, float radius, float a) {
        if (a <= 0f) return;
        var r = ctx.renderer();
        r.roundedRect(x, y, w, h, radius, Color.scaleAlpha(Tokens.surface().bg1(), FILL * a));
        // accent brand-edge on the left, inset past the rounded corners (structure, not on the data text)
        r.rect(x, y + radius, EDGE, Math.max(0f, h - 2f * radius), Color.scaleAlpha(Tokens.accent().accent(), a));
        r.border(x, y, w, h, radius, Tokens.border().thickness(), Color.scaleAlpha(HAIRLINE, a));
    }

    /** Soft dark text shadow, faded by {@code a} to match the text alpha (appear/disappear). */
    static TextEffect textShadow(float a) {
        return new TextEffect.Shadow(1f, 1f, 1f, Color.scaleAlpha(SHADOW, a));
    }
}
