package com.club.ui.hud;

import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;

/**
 * Numeric text for HUD stats with the one problem digit fixed. The atlas font is Inter, whose proportional
 * <b>'1'</b> is an unusually tight <code>0.381</code>em (vs <code>~0.62</code>em for most digits) — so values
 * with 1s ("481/481") come out visibly narrower than others ("592/592") and the stat columns/dots drift.
 *
 * <p>Rather than force every digit to one width (monospaced — which leaves air around all the narrow glyphs),
 * this widens <b>only the '1'</b> to a near-normal digit advance and centres the glyph in it, leaving every
 * other digit natural. The numbers even out and the '1' just reads as a '1' with normal sidebearing. A pixel-
 * perfect fix would be a tabular-figures atlas (font-level: freeze Inter's {@code tnum}), which needs font
 * tooling not available here. Metrics come from the shared {@link Ui#text()}; render-only.
 */
final class HudText {
    private HudText() {}

    /** Widened advance (em) for '1' — near a normal digit so 1-heavy values stop reading narrow, without
     *  going monospaced. Inter's natural '1' is 0.381em; most digits are ~0.62em. */
    private static final float ONE_EM = 0.56f;

    private static boolean isOne(char c) { return c == '1'; }

    /** Unscaled width of {@code s} with the '1' widened. */
    static float width(String s, Weight w, float size) {
        float out = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            out += isOne(ch) ? ONE_EM * size : Ui.text().width(String.valueOf(ch), w, size);
        }
        return out;
    }

    /** Draw {@code s} with the '1' widened; top-left at (x,y). {@code style} supplies weight/size/colour/effect
     *  (its size is {@code baseSize*scale}); {@code baseSize} + {@code scale} drive the (unscaled) metrics and the
     *  baked geometry. Every digit but '1' advances naturally; the '1' is centred in its widened advance. */
    static void draw(UiContext ctx, String s, float x, float y, TextStyle style, float baseSize, float scale) {
        var t = ctx.text();
        float penX = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String ch = String.valueOf(c);
            if (isOne(c)) {
                float adv = ONE_EM * baseSize, gw = Ui.text().width(ch, style.weight, baseSize);
                t.draw(ch, penX + (adv - gw) * 0.5f * scale, y, style);   // centre the tight '1' in its widened advance
                penX += adv * scale;
            } else {
                t.draw(ch, penX, y, style);
                penX += Ui.text().width(ch, style.weight, baseSize) * scale;
            }
        }
    }
}
