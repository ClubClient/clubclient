package com.club.ui.theme;

import com.club.ui.motion.Easing;

public record Motion(Durations durations, Easings easings) {
    public record Durations(float instant, float fast, float normal, float slow) {}
    public record Easings(Easing standard, Easing decelerate, Easing accelerate, Easing linear) {}
}
