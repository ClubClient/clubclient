package com.club.ui.motion;

/** One animated scalar. Driven by an external clock (UiContext.time(), seconds). Held as a component field. */
public final class Transition {
    private final float duration;
    private final Easing easing;
    private float from, to, start;

    public Transition(float initial, float duration, Easing easing) {
        this.from = this.to = initial;
        this.duration = Math.max(0f, duration);
        this.easing = easing;
        this.start = 0f;
    }

    public void target(float v, float now) {
        if (v == to) return;
        this.from = value(now);
        this.to = v;
        this.start = now;
    }

    public float value(float now) {
        if (duration <= 0f) return to;
        float t = (now - start) / duration;
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        return from + (to - from) * easing.apply(t);
    }

    public boolean animating(float now) { return duration > 0f && (now - start) < duration && from != to; }

    public float target() { return to; }
}
