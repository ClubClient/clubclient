package com.club.config;

/**
 * Armor HUD layout (Stage 29 — replaces the int clamps that were scattered across ArmorElement and
 * HudEditorScreen with one enum + a single sanitize, mirroring {@code AnimationType}'s safe-fallback
 * pattern). {@code COLUMN} = rows of [icon value] with the gauge under each icon; {@code LINE} = value
 * above icon, gauge below. The retired horizontal-cells view (legacy stored value 2) folds into LINE.
 */
public enum ArmorLayout {
    COLUMN, LINE;

    /** Map the stored int to a layout: any value ≥ 1 (incl. the retired 2) → LINE, else COLUMN. Total
     *  (never throws) so a hand-edited / old config degrades safely. */
    public static ArmorLayout fromIndex(int i) { return i >= 1 ? LINE : COLUMN; }

    /** Canonical stored int (0 = COLUMN, 1 = LINE). */
    public int index() { return ordinal(); }
}
