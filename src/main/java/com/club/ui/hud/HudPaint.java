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
}
