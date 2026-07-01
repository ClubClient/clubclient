package com.club.ui.hud;

import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;

/**
 * Tabular (fixed-width) numeric text for HUD stats. The atlas font is proportional — a "1" is narrower than a
 * "5" — so equal-length numbers ("481/481" vs "592/592") don't share a width and their columns/dots drift. This
 * renders every <b>digit</b> in a cell of the widest digit's advance, with the glyph <b>centered</b> in that cell,
 * so equal-length numbers become pixel-identical and every column (value edge, dot) lines up regardless of which
 * digits appear — exactly what premium HUDs get from a font's tabular-figures feature, done here in layout since
 * the atlas is proportional. Non-digits (<code>/ % . :</code> space, letters) keep their natural width.
 *
 * <p>Centering (not left/right cell-alignment) is what makes it read as balanced monospaced numerals rather than
 * numbers with a stray gap. Metrics come from the shared {@link Ui#text()}; render-only.
 */
final class HudText {
    private HudText() {}

    /** Widest digit advance (unscaled) at this weight/size — the tabular cell width. */
    private static float digitCell(Weight w, float size) {
        float c = 0;
        for (char d = '0'; d <= '9'; d++) c = Math.max(c, Ui.text().width(String.valueOf(d), w, size));
        return c;
    }

    /** Unscaled width of {@code s} rendered with tabular digits. */
    static float width(String s, Weight w, float size) {
        float cell = digitCell(w, size), out = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            out += Character.isDigit(ch) ? cell : Ui.text().width(String.valueOf(ch), w, size);
        }
        return out;
    }

    /** Draw {@code s} with tabular digits; top-left at (x,y). {@code style} supplies weight/size/colour/effect
     *  (its size is {@code baseSize*scale}); {@code baseSize} + {@code scale} drive the (unscaled) metrics and
     *  the baked geometry. Each digit is centred in the shared cell; non-digits advance naturally. */
    static void draw(UiContext ctx, String s, float x, float y, TextStyle style, float baseSize, float scale) {
        float cell = digitCell(style.weight, baseSize);
        var t = ctx.text();
        float penX = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            if (Character.isDigit(s.charAt(i))) {
                float gw = Ui.text().width(ch, style.weight, baseSize);
                t.draw(ch, penX + (cell - gw) * 0.5f * scale, y, style);   // centre the digit in its cell
                penX += cell * scale;
            } else {
                t.draw(ch, penX, y, style);
                penX += Ui.text().width(ch, style.weight, baseSize) * scale;
            }
        }
    }
}
