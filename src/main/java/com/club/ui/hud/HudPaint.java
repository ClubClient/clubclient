package com.club.ui.hud;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.theme.Tokens;

/**
 * Shared HUD chrome — the flat translucent panel behind every element (Stage 10 "light structure").
 * A subtle recessed surface so the four HUD elements read as one system without blocking the world:
 * a ~50%-alpha {@code bg1} fill + a soft light hairline. Flat — no shadow/glow. Render-only.
 */
final class HudPaint {
    private HudPaint() {}

    /** Fraction of the darkest tone used for the panel fill (subtle — the world shows through). */
    private static final float FILL = 0.5f;
    /** Soft light hairline that defines the panel edge over the world (~10% white). */
    private static final int HAIRLINE = Color.withAlpha(0xFFFFFFFF, 0x1A);

    /** Draws the element's panel. {@code a} in [0,1] fades the whole panel (element appear/disappear). */
    static void panel(UiContext ctx, float x, float y, float w, float h, float radius, float a) {
        if (a <= 0f) return;
        var r = ctx.renderer();
        r.roundedRect(x, y, w, h, radius, Color.scaleAlpha(Tokens.surface().bg1(), FILL * a));
        r.border(x, y, w, h, radius, Tokens.border().thickness(), Color.scaleAlpha(HAIRLINE, a));
    }
}
