package com.club.gui;

/**
 * Design tokens for the CLUB UI — "UI rework v2.5": a quiet, expensive, game-client
 * look. Near-neutral dark surfaces (not navy), a soft blue accent reserved for
 * active controls only, dense layout, crisp text. No glassmorphism, no blur, no
 * glow, no acid colours, and gradients live ONLY on the active tab underline and
 * the active toggle. Palette taken from the v2.5 reference board.
 * All colours are ARGB ints; spacing is on a 4px grid.
 */
public final class Theme {
    private Theme() {}

    // --- Background palette (deepest → window → surfaces) ---
    // Near-neutral darks (reference board): main #0B111A, secondary #0F1624,
    // element #131B2A. The window is not a flat rectangle — it carries a barely
    // perceptible vertical gradient and an almost-invisible edge depth so the eye
    // reads depth without ever seeing an "effect". No glass, no blur.
    public static final int BG_0      = 0xFF06090F; // deepest backdrop (behind the window)
    public static final int BG_1      = 0xFF0B111A; // window body (reference: main)
    public static final int BG_2      = 0xFF0F1624; // raised surface / popover (reference: secondary)
    public static final int SURFACE   = 0xFF131B2A; // element fill — dropdown / button / segmented (reference: element)
    public static final int SURFACE_HI = 0xFF18212F; // element fill on hover (one step up)
    public static final int BORDER    = 0xFF1D2536; // 1px control / element border (reference: border)

    // Window background = base #0B111A ± a barely-there vertical gradient. The
    // difference is minimal on purpose — depth comes from the faint edge volumes.
    public static final int BG_WIN_TOP = 0xFF0C1320; // top edge (faintly lifted)
    public static final int BG_WIN_BOT = 0xFF090E16; // bottom edge (sinks below base)

    // Two extremely soft accent-tinted volumes that give the window depth — a cool
    // light from the top-left and a fainter one from the bottom-right. Deliberately
    // near-invisible (~4% / ~2.5%): the user should feel depth, never spot a glow.
    public static final int GLOW_TL = 0x0A7CABFF; // top-left  — soft accent, ~4%
    public static final int GLOW_BR = 0x067CABFF; // bottom-right — soft accent, ~2.5%

    // --- Accent (ACCENTS ONLY — never for game/info values) ---
    // Soft blue primary + lighter blue secondary (reference). The gradient is the
    // single decorative motif and is permitted ONLY on the active category
    // underline and the active toggle. Everywhere else the accent is a FLAT colour
    // (slider fill, active segment, active-module dot, active-tab text). It is
    // NEVER used to colour text the player must read (names, HP, armour, effects,
    // descriptions, numbers) and is never a gradient on text.
    public static final int GRAD_A      = 0xFF7CABFF; // accent primary (left / top of the allowed gradient)
    public static final int GRAD_B      = 0xFF78D7FF; // accent secondary (right / bottom of the allowed gradient)
    public static final int ACCENT      = 0xFF7CABFF; // single, flat accent — the default everywhere
    public static final int ACCENT_SOFT = 0xFFA9C4FF; // softened accent for active status text (editor labels)
    public static final int ACCENT_FAINT= 0x1F7CABFF; // faint accent wash (selected dropdown row / primary button)
    public static final int GLOW        = 0x207CABFF; // (legacy) soft halo — no longer drawn under controls
    public static final int ON_ACCENT   = 0xFF0A0F18; // text/icons sitting on a filled accent
    public static final int BRAND_HALO  = 0x207CABFF; // (legacy) soft halo behind the brand dot

    // --- Text ---
    public static final int TEXT       = 0xFFF4F6FA; // primary (reference: main text)
    public static final int TEXT_MUTED = 0xFFA6ADBB; // secondary (reference: muted text)
    public static final int TEXT_DESC  = 0xFF767E8E; // panel description — low contrast, recedes
    public static final int TEXT_FAINT = 0xFF5A6273; // tertiary / labels / inactive icons
    public static final int TEXT_GHOST = 0xFF3A4150; // ghost (hollow dots, dividers tint)

    // --- Hairlines & subtle fills (used sparingly) ---
    // Separators (reference): main ~10% white, inner ~6% white.
    public static final int HAIR        = 0x0FFFFFFF; // ~6% white — inner / window edge
    public static final int DIVIDER     = 0x1AFFFFFF; // ~10% white — section divider
    public static final int HAIR_STRONG = 0x1FFFFFFF; // ~12% white — popover edge
    public static final int FILL_SUBTLE = 0x0DFFFFFF; // ~5% — hover wash / segmented track
    public static final int FILL_OFF    = 0xFF222A38; // toggle OFF — calm grey pill (clearly off, not invisible)
    public static final int FILL_TRACK  = 0xFF1D2536; // slider track — flat technical line

    // --- State indicators (small dots ONLY — never colour the digits) ---
    // Durability / health state is shown by a tiny coloured dot beside white text,
    // never by tinting the number. Vivid reference tones — fine as a small accent,
    // never as a text colour. Flat, never gradients.
    public static final int STATE_GOOD = 0xFF2ECC71; // good   — green
    public static final int STATE_WARN = 0xFFE3C66A; // medium — amber
    public static final int STATE_LOW  = 0xFFE06B6B; // low    — red
    public static final int AMBER      = 0xFFE2B27C; // legacy soft amber (kept for compatibility)

    /** Durability/HP colour by fraction [0..1]: green ≥0.70, amber ≥0.40, else red. */
    public static int stateColor(float frac) {
        if (frac >= 0.70f) return STATE_GOOD;
        if (frac >= 0.40f) return STATE_WARN;
        return STATE_LOW;
    }

    // --- Spacing scale (4px grid) ---
    public static final int PAD_XS = 4;
    public static final int PAD_SM = 8;
    public static final int PAD_MD = 12;
    public static final int PAD_LG = 16;
    public static final int PAD_XL = 24;

    // --- Radius ---
    // Tighter than the old "premium desktop" 16 everywhere — a smaller radius reads
    // as a technical game client. Window/popover 10, boxed controls 6, small fills 4.
    // Pills (toggle / knob) clamp to half their height in the renderer.
    public static final int RADIUS    = 10;
    public static final int RADIUS_SM = 6;
    public static final int RADIUS_XS = 4;

    // --- Animation ---
    public static final float ANIM_SPEED = 0.20f; // lerp factor per frame (~200ms feel)
}
