package com.club.ui.hud;

import com.club.ui.Axis;
import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.text.TextEffect;
import com.club.ui.theme.Tokens;

/**
 * Shared HUD chrome — the flat "milled tile" behind every element (Stage 10 "light structure", premium pass).
 * The brand rule forbids the HUD reading as a plain translucent rectangle, so the panel is built to feel like a
 * small crafted surface floating over the world, not a decoration: a soft neutral drop-shadow (elevation +
 * separation from a busy background), a whisper of neutral top→bottom depth in the fill, a hairline all round,
 * and a brighter "milled" top inner edge that catches the light. Flat — no accent, no glow-halo, no gradient on
 * data. Text sits on a soft dark backing ({@link #textShadow}). Render-only; every layer fades by {@code a}.
 */
final class HudPaint {
    private HudPaint() {}

    // Fill depth — top a touch lighter/airier, bottom settles darker, so the tile has body over bright worlds
    // (the slime/grass case) without becoming an opaque slab. Neutral tones only (never an accent gradient).
    private static final int FILL_TOP_A = 0x9E;   // ~62%
    private static final int FILL_BOT_A = 0xC8;   // ~78%
    /** Soft hairline that traces the whole tile edge (~8% white). */
    private static final int HAIRLINE = Color.withAlpha(0xFFFFFFFF, 0x14);
    /** Brighter inner top edge — the "milled" highlight that reads as a crafted, lit surface (~14% white). */
    private static final int TOP_EDGE = Color.withAlpha(0xFFFFFFFF, 0x24);
    /** Neutral drop-shadow — elevates the tile off a busy world (NOT an accent glow). ~22% black. */
    private static final int DROP = Color.withAlpha(0xFF000000, 0x38);
    /** Soft dark text backing (~60% black) for legibility over the world. */
    private static final int SHADOW = Color.withAlpha(0xFF000000, 0x99);

    /** Draws the element's tile. {@code a} in [0,1] fades every layer together (element appear/disappear). */
    static void panel(UiContext ctx, float x, float y, float w, float h, float radius, float a) {
        if (a <= 0f) return;
        var r = ctx.renderer();
        int top = Color.withAlpha(Tokens.surface().bg1(), FILL_TOP_A);
        int bot = Color.withAlpha(Tokens.surface().bg0(), FILL_BOT_A);
        // 1) elevation — a soft neutral shadow just beneath the tile (offset down, gently blurred)
        r.shadow(x, y, w, h, radius, 0f, 2f, 6f, Color.scaleAlpha(DROP, a));
        // 2) crafted fill — subtle neutral top→bottom depth (reads as a surface, not a flat rectangle)
        r.gradient(x, y, w, h, radius, Color.scaleAlpha(top, a), Color.scaleAlpha(bot, a), Axis.VERTICAL);
        // 3) hairline all round + a brighter milled top edge (inset past the rounded corners)
        r.border(x, y, w, h, radius, Tokens.border().thickness(), Color.scaleAlpha(HAIRLINE, a));
        float inset = Math.min(radius, w * 0.5f);
        r.rect(x + inset, y + 1f, Math.max(0f, w - 2f * inset), 1f, Color.scaleAlpha(TOP_EDGE, a));
    }

    /** Soft dark text shadow, faded by {@code a} to match the text alpha (appear/disappear). */
    static TextEffect textShadow(float a) {
        return new TextEffect.Shadow(1f, 1f, 1f, Color.scaleAlpha(SHADOW, a));
    }
}
