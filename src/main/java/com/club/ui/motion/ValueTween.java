package com.club.ui.motion;

/**
 * Smooths a <i>data</i> value toward its latest target (HUD numbers: an HP-bar fraction, a smoothed FPS).
 * {@link #set} each frame with the raw value; {@link #get} returns the eased value.
 *
 * <p>Lag is bounded by the chosen {@code duration}: with a {@code fast} (~0.12&nbsp;s) tween the eased value
 * can never trail the raw target by more than that, so it eases without lying about state for long — the
 * HP bar uses this. Values that must be exact (HP <i>text</i>, XYZ coordinates, effect timers) are read raw,
 * never through this. {@link #snap} jumps instantly with no animation — use it when the underlying subject
 * changes (e.g. a new attack target) so the bar re-based rather than sweeping across from the old value.
 */
public final class ValueTween {
    private final float duration;
    private final Easing easing;
    private Transition t;

    public ValueTween(float initial, float duration, Easing easing) {
        this.duration = duration;
        this.easing = easing;
        this.t = new Transition(initial, duration, easing);
    }

    /** Ease toward {@code target} from the current value. */
    public void set(float target, float now) { t.target(target, now); }

    /** Jump to {@code value} immediately with no animation (subject changed). */
    public void snap(float value, float now) { this.t = new Transition(value, duration, easing); }

    public float get(float now) { return t.value(now); }

    public boolean animating(float now) { return t.animating(now); }
}
