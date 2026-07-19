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

    /** Display names, parallel to {@link #ARGB}. English (project convention). Kept in lock-step with the
     *  colour array — the palette swatch grid draws {@link #ARGB} and never reads a name, but ClubHarness
     *  still asserts the two arrays are parallel, and a name is the honest label for each index. */
    public static final String[] NAMES = {
        "White", "Accent", "Cyan", "Teal", "Green", "Lime", "Yellow", "Orange",
        "Coral", "Red", "Pink", "Purple", "Indigo", "Blue", "Slate", "Gray"
    };

    /** Packed {@code 0xAARRGGBB}, full alpha, parallel to {@link #NAMES}. Flat, slightly desaturated tones
     *  that sit with the neutral-dark palette — no neon, no glow (the frozen design). Index 0 (White) and
     *  index 1 (Accent) are pinned: the config defaults reference {@link #WHITE} and {@link #ACCENT}. */
    public static final int[] ARGB = {
        0xFFFFFFFF, // White  — vanilla's own hitbox colour
        0xFF7CABFF, // Accent — Club's flat accent (#7CABFF)
        0xFF78D7FF, // Cyan
        0xFF4FC4B0, // Teal
        0xFF6BD08B, // Green
        0xFFB6E06B, // Lime
        0xFFF2D06B, // Yellow
        0xFFF2A65A, // Orange
        0xFFF08267, // Coral
        0xFFE0655F, // Red
        0xFFE68FB8, // Pink
        0xFFB98CE0, // Purple
        0xFF8C8FE0, // Indigo
        0xFF6E9BE0, // Blue
        0xFF8C9BB5, // Slate
        0xFFAAB2C0  // Gray
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
