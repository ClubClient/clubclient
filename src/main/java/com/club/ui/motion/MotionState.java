package com.club.ui.motion;

import com.club.ui.theme.Tokens;

/**
 * Shared per-component interaction animator: the four universal interaction channels
 * (hover / press / focus / enabled), each a {@link Transition} built from the active Motion tokens.
 *
 * <p><b>Opt-in.</b> A component (or a hand-drawn screen sub-element) holds one {@code MotionState},
 * calls {@link #update} once at the top of its {@code render()} (where {@code now = ctx.time()} is
 * available, so no input-handler signatures change), then reads {@link #hover}/{@link #press}/
 * {@link #focus}/{@link #disabled} to drive state visuals smoothly instead of hard booleans. This is
 * the mechanism by which a NEW widget/HUD element gets hover/press/focus/disabled motion "for free".
 *
 * <p>Channel timings mirror the shipped widget language (Button/Checkbox): hover &amp; focus =
 * {@code fast/standard}, press = {@code fast/decelerate}, enabled = {@code normal/standard}. It does
 * <b>not</b> modify the already-animated widgets (they keep their own hand-tuned Transitions); it only
 * gives the hand-drawn screens and future widgets the same motion. Semantic per-widget state (a module's
 * on/off, a row's selected) stays a bespoke {@link Transition} — {@code MotionState} is interaction only.
 */
public final class MotionState {
    private final Transition hover;
    private final Transition press;
    private final Transition focus;
    private final Transition enable;   // 1 = enabled, 0 = disabled

    public MotionState() {
        var m = Tokens.motion();
        hover  = new Transition(0f, m.durations().fast(),   m.easings().standard());
        press  = new Transition(0f, m.durations().fast(),   m.easings().decelerate());
        focus  = new Transition(0f, m.durations().fast(),   m.easings().standard());
        enable = new Transition(1f, m.durations().normal(), m.easings().standard());
    }

    /** Target every channel from the element's current interaction flags. Call once per render(); {@code now = ctx.time()}. */
    public void update(boolean hovered, boolean pressed, boolean focused, boolean enabled, float now) {
        hover.target(hovered  ? 1f : 0f, now);
        press.target(pressed  ? 1f : 0f, now);
        focus.target(focused  ? 1f : 0f, now);
        enable.target(enabled ? 1f : 0f, now);
    }

    public float hover(float now)   { return hover.value(now); }
    public float press(float now)   { return press.value(now); }
    public float focus(float now)   { return focus.value(now); }
    /** Eased enabled amount: 1 = fully enabled, 0 = fully disabled. */
    public float enabled(float now) { return enable.value(now); }
    /** Eased disabled amount: 0 = fully enabled, 1 = fully disabled (for opacity/tint toward disabledAlpha). */
    public float disabled(float now) { return 1f - enable.value(now); }

    /** True while any channel is mid-animation (lets a caller skip work when fully settled). */
    public boolean animating(float now) {
        return hover.animating(now) || press.animating(now) || focus.animating(now) || enable.animating(now);
    }
}
