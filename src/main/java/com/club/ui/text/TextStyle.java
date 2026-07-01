package com.club.ui.text;

/** Immutable text style. Use the withers to derive variants. */
public final class TextStyle {
    public final Weight weight; public final float size; public final int color;
    public final Align align; public final TextEffect effect;
    /** MSDF optical-weight bias: &gt;0 renders the glyph a touch thinner, &lt;0 heavier. 0 = nominal weight. */
    public final float weightBias;

    public TextStyle(Weight weight, float size, int color, Align align, TextEffect effect) {
        this(weight, size, color, align, effect, 0f);
    }
    public TextStyle(Weight weight, float size, int color, Align align, TextEffect effect, float weightBias) {
        this.weight = weight; this.size = size; this.color = color;
        this.align = align; this.effect = effect; this.weightBias = weightBias;
    }
    public static TextStyle of(Weight weight, float size, int color) {
        return new TextStyle(weight, size, color, Align.LEFT, TextEffect.NONE, 0f);
    }
    public TextStyle align(Align a) { return new TextStyle(weight, size, color, a, effect, weightBias); }
    public TextStyle color(int c)   { return new TextStyle(weight, size, c, align, effect, weightBias); }
    public TextStyle effect(TextEffect e) { return new TextStyle(weight, size, color, align, e, weightBias); }
    /** Derive a variant with an MSDF optical-weight bias (&gt;0 thinner). */
    public TextStyle weightBias(float wb) { return new TextStyle(weight, size, color, align, effect, wb); }
}
