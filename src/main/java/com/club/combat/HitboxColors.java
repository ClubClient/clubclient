package com.club.combat;

/**
 * Curated FLAT palette for the debug-hitbox outline — neutral tones plus Club's single accent
 * (#7CABFF), no glow and no garish rainbow (the frozen design). Two PARALLEL arrays: {@link #NAMES}
 * feeds the menu dropdown, {@link #ARGB} is the packed {@code 0xAARRGGBB} colour the render hooks draw
 * with. The two "On player" / "Default" dropdowns store an INDEX into these arrays — the same shape as
 * Animations storing an enum ordinal — so the palette stays the single source of truth for both the
 * label a player sees and the colour that is drawn.
 *
 * <p>Version-blind and MC-import-free on purpose: it is read by both the client tick and the
 * per-version render mixins, and ClubHarness can assert against it with no Minecraft on the classpath.</p>
 */
public final class HitboxColors {
    private HitboxColors() {}

    /** Display names, parallel to {@link #ARGB}. English (project convention). */
    public static final String[] NAMES = {
        "White", "Accent", "Red", "Green", "Yellow", "Orange", "Cyan"
    };

    /** Packed {@code 0xAARRGGBB}, full alpha, parallel to {@link #NAMES}. Flat values — no glow. */
    public static final int[] ARGB = {
        0xFFFFFFFF, // White  — vanilla's own hitbox colour
        0xFF7CABFF, // Accent — Club's flat accent (#7CABFF)
        0xFFFF5555, // Red
        0xFF55FF55, // Green
        0xFFFFFF55, // Yellow
        0xFFFFAA55, // Orange
        0xFF55FFFF  // Cyan
    };

    /** Index of White — the "Default" colour when no player is under the crosshair (matches vanilla). */
    public static final int WHITE = 0;
    /** Index of the Club accent — the default "On player" colour. */
    public static final int ACCENT = 1;

    /** The colour at {@code index}, clamped into {@code [0, length)} so a stale or hand-edited config
     *  index can never throw — an out-of-range value falls back to the nearest end of the palette. */
    public static int argb(int index) {
        if (index < 0) index = 0;
        if (index >= ARGB.length) index = ARGB.length - 1;
        return ARGB[index];
    }
}
