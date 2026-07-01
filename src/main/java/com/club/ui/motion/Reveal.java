package com.club.ui.motion;

/**
 * Enter/exit driver for one transient element (a list item, dropdown option row, popover, section).
 * Plays <b>in</b> (0&nbsp;→&nbsp;1) from construction; {@link #close} plays <b>out</b> (→&nbsp;0). The owner
 * keeps it in its map until {@link #gone} reports the out-play has fully collapsed.
 *
 * <p><b>Why not {@code Transition.animating()}:</b> {@code animating()} returns false as soon as
 * {@code from == to}, so an element opened and closed within the same frame — or any element whose
 * exit hasn't visibly progressed yet — would be dropped before it plays out. {@code gone()} instead
 * waits until the eased value is within {@link #EPS} of 0 <i>while closing</i>, which is the correct
 * "safe to remove now" signal.
 *
 * <p>Multiply child alpha and/or a measured height by {@link #progress} to get a combined fade + reveal.
 */
public final class Reveal {
    /** Value at/below which a closing reveal is considered fully collapsed. */
    public static final float EPS = 0.001f;

    private final Transition t;
    private boolean closing;

    /** Begins playing in from 0 toward 1 at {@code now}. */
    public Reveal(float duration, Easing easing, float now) {
        this.t = new Transition(0f, duration, easing);
        this.t.target(1f, now);
    }

    /** Reveal amount in [0,1] — 1 = fully shown. Multiply alpha/height by this. */
    public float progress(float now) { return t.value(now); }

    /** Begin playing out toward 0. Idempotent — safe to call every frame once closing. */
    public void close(float now) {
        if (closing) return;
        closing = true;
        t.target(0f, now);
    }

    public boolean closing() { return closing; }

    /** True once a closing reveal has fully collapsed — the owner should drop it now. */
    public boolean gone(float now) { return closing && t.value(now) <= EPS; }
}
