package com.club.ui.text;

/** Immutable text style. Use the withers to derive variants. */
public final class TextStyle {
    public final Weight weight; public final float size; public final int color;
    public final Align align; public final TextEffect effect;

    public TextStyle(Weight weight, float size, int color, Align align, TextEffect effect) {
        this.weight = weight; this.size = size; this.color = color;
        this.align = align; this.effect = effect;
    }
    public static TextStyle of(Weight weight, float size, int color) {
        return new TextStyle(weight, size, color, Align.LEFT, TextEffect.NONE);
    }
    public TextStyle align(Align a) { return new TextStyle(weight, size, color, a, effect); }
    public TextStyle color(int c)   { return new TextStyle(weight, size, c, align, effect); }
    public TextStyle effect(TextEffect e) { return new TextStyle(weight, size, color, align, e); }
}
