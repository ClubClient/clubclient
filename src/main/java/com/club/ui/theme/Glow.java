package com.club.ui.theme;

public record Glow(Preset subtle, Preset active) {
    public record Preset(float size, int color) {}
}
