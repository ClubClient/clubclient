package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.theme.Tokens;

/**
 * On/off switch (bare — label composed externally). Commit-model + keyboard from {@link Control}.
 *
 * <p>Design language: a pill track + a white-puck handle (shared with {@link Slider}). ON = flat accent
 * track; OFF = inset control surface ({@link WidgetPaint#controlTrack}). No gradient/glow — coherent with
 * every other accent surface. Knob slides via a {@link Transition} and grows on hover/press (shared
 * handle-grow constants). Focus = offset accent ring around the pill.
 */
public final class Toggle extends Control {

    // Shape geometry (proportions, NOT design tokens).
    //
    // 30×16, down from 40×22 (owner: the switch must relate to the text beside it). A settings row's label is
    // MEDIUM 12 on a 16px line box, so a 22px pill stood 1.4× the height of the words it belonged to and read
    // as the row's subject rather than its control. At 16 the pill IS the line box. 30×16 is also exactly the
    // pill ParticlesPane draws by hand, so the whole menu now speaks one switch size instead of two.
    // KNOB_INSET drops 3 → 2.5 so the puck keeps its presence at the smaller height (r = 16/2 − 2.5 = 5.5).
    private static final float W = 30f, H = 16f, KNOB_INSET = 2.5f;

    private boolean value;
    private BoolConsumer onChange;
    private int accent;   // 0 = theme accent; set for category-tinted contexts (Stage 11.9)
    private final Transition knob =
            new Transition(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
    private final Transition grow =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());

    public Toggle(boolean value) { this.value = value; knob.target(value ? 1f : 0f, 0f); }
    public Toggle onChange(BoolConsumer cb) { this.onChange = cb; return this; }
    /** Overrides the accent colour (ON track + focus ring); 0 restores the theme accent. */
    public Toggle accent(int color) { this.accent = color; return this; }
    public boolean value() { return value; }

    @Override protected void activate() {
        value = !value;
        if (onChange != null) onChange.accept(value);
    }

    @Override public Size measure(float availW, float availH) { return new Size(W, H); }

    @Override public void render(UiContext ctx) {
        float now = ctx.time();
        knob.target(value ? 1f : 0f, now);
        grow.target(pressed ? WidgetPaint.HANDLE_PRESS_GROW : (hovered ? WidgetPaint.HANDLE_HOVER_GROW : 0f), now);
        float k = knob.value(now);
        float r = h / 2f;

        // Track: flat accent (ON) vs inset control surface (OFF) — one language, no gradient/glow.
        if (value) WidgetPaint.surface(ctx, x, y, w, h, r, WidgetPaint.acc(accent), 0);
        else       WidgetPaint.controlTrack(ctx, x, y, w, h, r);

        // White-puck handle; travel uses a stable base radius so hover/press grow doesn't shift position.
        float baseKr = h / 2f - KNOB_INSET;
        float kr = baseKr + grow.value(now);
        float cx = (x + KNOB_INSET + baseKr) + (w - 2f * (KNOB_INSET + baseKr)) * k;
        WidgetPaint.whitePuck(ctx, cx, y + r, kr, 0, 0f);

        WidgetPaint.focusRing(ctx, this, r, WidgetPaint.acc(accent));
    }
}
