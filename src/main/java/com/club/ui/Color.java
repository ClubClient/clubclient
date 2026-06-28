package com.club.ui;

/** Pure ARGB (0xAARRGGBB) helpers. No Minecraft/GL dependencies. */
public final class Color {
    private Color() {}
    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
    public static int withAlpha(int c, int a) { return (c & 0x00FFFFFF) | ((a & 0xFF) << 24); }
    public static int scaleAlpha(int c, float mul) {
        int a = Math.round(((c >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, mul)));
        return (c & 0x00FFFFFF) | (a << 24);
    }
    public static int lerp(int x, int y, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int xa = (x >>> 24) & 0xFF, xr = (x >>> 16) & 0xFF, xg = (x >>> 8) & 0xFF, xb = x & 0xFF;
        int ya = (y >>> 24) & 0xFF, yr = (y >>> 16) & 0xFF, yg = (y >>> 8) & 0xFF, yb = y & 0xFF;
        return rgba(Math.round(xr + (yr - xr) * t), Math.round(xg + (yg - xg) * t),
                    Math.round(xb + (yb - xb) * t), Math.round(xa + (ya - xa) * t));
    }
    public static float af(int c) { return ((c >>> 24) & 0xFF) / 255f; }
    public static float rf(int c) { return ((c >>> 16) & 0xFF) / 255f; }
    public static float gf(int c) { return ((c >>> 8) & 0xFF) / 255f; }
    public static float bf(int c) { return (c & 0xFF) / 255f; }
}
