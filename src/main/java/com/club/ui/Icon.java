package com.club.ui;

/**
 * Procedural line-icons drawn via {@link UiRenderer} primitives. The SDF backend approximates DIAGONAL
 * lines as bounding boxes, so every icon here is built only from circles, rings ({@code border}),
 * axis-aligned lines and rounded rects — keeping them crisp. Sized into a square box (x,y,s).
 */
public enum Icon {
    COMBAT, MOVEMENT, RENDER, PLAYER, WORLD, EXPLOIT, HUD, SETTINGS, SEARCH;

    public void draw(UiRenderer r, float x, float y, float s, int color, float t) {
        float cx = x + s / 2f, cy = y + s / 2f;
        switch (this) {
            case COMBAT -> {                                   // crosshair: ring + 4 ticks + dot
                float rad = s * 0.40f;
                r.border(cx - rad, cy - rad, rad * 2, rad * 2, rad, t, color);
                r.line(cx, y, cx, y + s * 0.15f, t, color);
                r.line(cx, y + s - s * 0.15f, cx, y + s, t, color);
                r.line(x, cy, x + s * 0.15f, cy, t, color);
                r.line(x + s - s * 0.15f, cy, x + s, cy, t, color);
                r.circle(cx, cy, s * 0.06f, color);
            }
            case MOVEMENT -> {                                 // speed lines
                float g = s * 0.24f;
                r.line(x + s * 0.12f, cy - g, x + s * 0.74f, cy - g, t, color);
                r.line(x + s * 0.12f, cy,     x + s * 0.90f, cy,     t, color);
                r.line(x + s * 0.12f, cy + g, x + s * 0.62f, cy + g, t, color);
            }
            case RENDER -> {                                   // eye: ring + pupil
                r.border(x + s * 0.10f, cy - s * 0.22f, s * 0.80f, s * 0.44f, s * 0.22f, t, color);
                r.circle(cx, cy, s * 0.12f, color);
            }
            case PLAYER -> {                                   // head + shoulders
                r.border(cx - s * 0.17f, y + s * 0.10f, s * 0.34f, s * 0.34f, s * 0.17f, t, color);
                r.border(cx - s * 0.30f, y + s * 0.52f, s * 0.60f, s * 0.46f, s * 0.20f, t, color);
            }
            case WORLD -> {                                    // globe: ring + cross
                float rad = s * 0.40f;
                r.border(cx - rad, cy - rad, rad * 2, rad * 2, rad, t, color);
                r.line(cx - rad, cy, cx + rad, cy, t, color);
                r.line(cx, cy - rad, cx, cy + rad, t, color);
            }
            case EXPLOIT -> {                                  // bracketed mark
                r.border(x + s * 0.16f, y + s * 0.16f, s * 0.68f, s * 0.68f, s * 0.16f, t, color);
                r.line(cx, y + s * 0.32f, cx, y + s * 0.68f, t, color);
            }
            case HUD -> {                                      // layout
                r.border(x + s * 0.13f, y + s * 0.16f, s * 0.74f, s * 0.68f, s * 0.10f, t, color);
                r.line(x + s * 0.13f, y + s * 0.37f, x + s * 0.87f, y + s * 0.37f, t, color);
                r.line(x + s * 0.39f, y + s * 0.37f, x + s * 0.39f, y + s * 0.84f, t, color);
            }
            case SETTINGS -> {                                 // sliders
                r.line(x + s * 0.14f, cy - s * 0.18f, x + s * 0.86f, cy - s * 0.18f, t, color);
                r.circle(x + s * 0.36f, cy - s * 0.18f, s * 0.10f, color);
                r.line(x + s * 0.14f, cy + s * 0.18f, x + s * 0.86f, cy + s * 0.18f, t, color);
                r.circle(x + s * 0.62f, cy + s * 0.18f, s * 0.10f, color);
            }
            case SEARCH -> {                                   // magnifier: ring + handle nub
                float rad = s * 0.26f, gx = cx - s * 0.06f, gy = cy - s * 0.06f;
                r.border(gx - rad, gy - rad, rad * 2, rad * 2, rad, t, color);
                r.circle(cx + s * 0.26f, cy + s * 0.26f, s * 0.07f, color);
            }
        }
    }
}
