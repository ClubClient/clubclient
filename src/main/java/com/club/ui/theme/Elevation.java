package com.club.ui.theme;

public record Elevation(Level level0, Level level1, Level level2, Level level3) {
    /** glow may be null (no glow at this level). */
    public record Level(int surface, int border, Shadow.Preset shadow, Glow.Preset glow) {}
}
