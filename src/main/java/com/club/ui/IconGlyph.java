package com.club.ui;

import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;

/**
 * Stage-11 SDF icon glyphs — one monochrome atlas icon per value, mapped to a PUA code point
 * (kept in sync with {@code tools/icons/src/<HHHH>_<name>.svg}; regenerate the atlas with
 * {@code ./gradlew genIconAtlas} after adding/editing an SVG).
 *
 * <p>An icon IS a glyph: it renders through the normal text pipeline (MSDF shader), so it is
 * resolution-independent and tinted by the draw color — one monochrome asset covers every
 * category color, and state colors ease smoothly via {@link Color#lerp}. The ghost underlay is
 * this same glyph drawn large at low alpha. Unlike the retired procedural Icon enum, diagonals
 * are fully supported (no R8 limitation on this path).</p>
 *
 * <p>On the LEGACY backend the glyph path cannot draw ({@link #draw} no-ops, no vanilla '?'
 * boxes). Surfaces that would lose identity check {@link #available()} and fall back to a
 * LETTER initial (Stage 26): menu card chips, Effects rows, Armor slots, the footer chip;
 * the header simply stops reserving the logo's width.</p>
 */
public enum IconGlyph {
    // categories (rail)
    COMBAT(0xE000),
    VISUALS(0xE001),
    PLAYER(0xE002),
    MISC(0xE003),
    // modules
    ANIMATIONS(0xE010),
    SCREEN_STRETCH(0xE011),
    HANDS(0xE012),
    HUD_EDITOR(0xE013),
    NO_HURT_CAM(0xE014),
    NO_FIRE_OVERLAY(0xE015),
    NO_BOBBING(0xE016),
    HIDE_EFFECTS(0xE017),
    ZOOM(0xE018),
    FULLBRIGHT(0xE019),
    TOGGLE_SPRINT(0xE01A),
    FREELOOK(0xE01B),
    ITEM_SCROLL(0xE01C),
    // service
    SEARCH(0xE020),
    /** The CLUB mark — the club card suit (trefoil), solid. Header wordmark + editor watermark. */
    LOGO(0xE021),
    // HUD: armor pieces (Stage 14 — replaces vanilla item sprites; tinted by material)
    ARMOR_HELMET(0xE030),
    ARMOR_CHEST(0xE031),
    ARMOR_LEGS(0xE032),
    ARMOR_BOOTS(0xE033),
    ARMOR_ELYTRA(0xE034),
    // HUD: status effects (Stage 14 — icon-only Effects element; tinted by association)
    FX_SPEED(0xE040),
    FX_SLOWNESS(0xE041),
    FX_HASTE(0xE042),
    FX_MINING_FATIGUE(0xE043),
    FX_STRENGTH(0xE044),
    FX_JUMP_BOOST(0xE045),
    FX_NAUSEA(0xE046),
    FX_REGENERATION(0xE047),
    FX_RESISTANCE(0xE048),
    FX_FIRE_RESISTANCE(0xE049),
    FX_WATER_BREATHING(0xE04A),
    FX_INVISIBILITY(0xE04B),
    FX_BLINDNESS(0xE04C),
    FX_NIGHT_VISION(0xE04D),
    FX_HUNGER(0xE04E),
    FX_POISON(0xE04F),
    FX_WITHER(0xE050),
    FX_HEALTH_BOOST(0xE051),
    FX_ABSORPTION(0xE052),
    FX_WEAKNESS(0xE053),
    FX_LEVITATION(0xE054),
    FX_SLOW_FALLING(0xE055),
    FX_GLOWING(0xE056),
    FX_DARKNESS(0xE057),
    FX_LUCK(0xE058),
    /** Fallback for effects without a drawn icon (rare/modded) — generic test tube. */
    FX_GENERIC(0xE059);

    public final int codePoint;
    private final String str;

    IconGlyph(int cp) { this.codePoint = cp; this.str = String.valueOf((char) cp); }

    /** The glyph as a 1-char string (PUA is BMP) — for direct text-pipeline composition. */
    public String str() { return str; }

    /** Whether the SDF glyph path can draw at all (MODERN backend). Callers that must keep an
     *  identity on LEGACY branch to a letter fallback when this is false. */
    public static boolean available() { return Ui.backend() == Ui.Backend.MODERN; }

    /** Draws the icon with its 24-grid content box at (x, y)..(x+size, y+size), tinted {@code color}. */
    public void draw(UiContext ctx, float x, float y, float size, int color) {
        if (Ui.backend() != Ui.Backend.MODERN) return;
        // The text pipeline places glyphs from the baseline (yTop + ascent). The icon plane puts the
        // content top exactly 1em above the baseline, so shifting by (size - ascent) pins the content
        // box's top-left to (x, y) regardless of the font's ascender value.
        float yTop = y + size - ctx.text().ascent(Weight.MEDIUM, size);
        ctx.text().draw(str, x, yTop, TextStyle.of(Weight.MEDIUM, size, color));
    }
}
