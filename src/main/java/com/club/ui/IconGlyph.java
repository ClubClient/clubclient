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
 * this same glyph drawn large at low alpha. Unlike the procedural {@link Icon}, diagonals are
 * fully supported (no R8 limitation on this path).</p>
 *
 * <p>On the LEGACY backend icons are skipped silently — module names stay readable and no
 * vanilla '?' boxes appear.</p>
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
    // service
    SEARCH(0xE020),
    /** The CLUB mark — the club card suit (trefoil), solid. Header wordmark + editor watermark. */
    LOGO(0xE021);

    public final int codePoint;
    private final String str;

    IconGlyph(int cp) { this.codePoint = cp; this.str = String.valueOf((char) cp); }

    /** The glyph as a 1-char string (PUA is BMP) — for direct text-pipeline composition. */
    public String str() { return str; }

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
