package com.club.ui.motion;

public final class Curves {
    private Curves() {}
    private static float c(float t) { return t < 0f ? 0f : (t > 1f ? 1f : t); }
    public static final Easing LINEAR     = t -> c(t);
    public static final Easing STANDARD   = t -> { float x = c(t); return x * x * (3f - 2f * x); }; // smoothstep
    public static final Easing DECELERATE = t -> { float x = c(t); return 1f - (1f - x) * (1f - x); }; // ease-out
    public static final Easing ACCELERATE = t -> { float x = c(t); return x * x; };                    // ease-in
}
