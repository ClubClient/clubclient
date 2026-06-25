package com.club.util;

/** Small math utilities used across modules and the GUI. */
public final class Mth {
    private Mth() {}

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Smoothstep easing, t in [0,1]. */
    public static float smooth(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Round to a step (e.g. 0.01). */
    public static float snap(float v, float step) {
        return Math.round(v / step) * step;
    }
}