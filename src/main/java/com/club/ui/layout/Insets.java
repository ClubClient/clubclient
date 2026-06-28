package com.club.ui.layout;
public record Insets(float top, float right, float bottom, float left) {
    public static final Insets ZERO = new Insets(0, 0, 0, 0);
    public static Insets all(float v)               { return new Insets(v, v, v, v); }
    public static Insets symmetric(float h, float v) { return new Insets(v, h, v, h); }
    public float horizontal() { return left + right; }
    public float vertical()   { return top + bottom; }
}
