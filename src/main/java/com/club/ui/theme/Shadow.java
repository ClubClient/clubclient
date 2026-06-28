package com.club.ui.theme;

public record Shadow(Preset sm, Preset md, Preset lg) {
    public record Preset(float dx, float dy, float blur, int color) {}
}
