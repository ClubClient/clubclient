package com.club.ui;
/** Per-corner radii (top-left, top-right, bottom-right, bottom-left), px. */
public record Radii(float tl, float tr, float br, float bl) {
    public static Radii all(float r) { return new Radii(r, r, r, r); }
}
