package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.theme.Tokens;

/**
 * Boolean checkbox (bare — label composed externally). Commit-model + keyboard from {@link Control}.
 *
 * <p>Design language: OFF = inset control surface ({@link WidgetPaint#controlTrack}); ON = flat accent fill
 * + a 2px check (dark {@code onAccent}). Box radius = sm (small-control radius). Hover wash + offset focus
 * ring are the shared state visuals. Sized to body line-height so it aligns optically with adjacent text.
 */
public final class Checkbox extends Control {
    // Checkmark glyph geometry (proportions of the box, NOT design tokens): left point, vertex, right point.
    private static final float CK_LX = 0.26f, CK_LY = 0.50f;
    private static final float CK_VX = 0.42f, CK_VY = 0.62f;
    private static final float CK_RX = 0.74f, CK_RY = 0.32f;
    private static final float CK_STROKE = 2f;                 // check stroke (px) — was 1.5, read thin

    private boolean value;
    private BoolConsumer onChange;
    private int accent;   // 0 = theme accent; set for category-tinted contexts (Stage 11.9)
    private final Transition check =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
    private final Transition hover =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());

    public Checkbox(boolean value) { this.value = value; check.target(value ? 1f : 0f, 0f); }
    public Checkbox onChange(BoolConsumer cb) { this.onChange = cb; return this; }
    /** Overrides the accent colour (ON fill + focus ring); 0 restores the theme accent. */
    public Checkbox accent(int color) { this.accent = color; return this; }
    public boolean value() { return value; }

    @Override protected void activate() {
        value = !value;
        if (onChange != null) onChange.accept(value);
    }

    @Override public Size measure(float availW, float availH) {
        float s = Tokens.type().body().lineHeight();          // stored Role value — headless-safe
        return new Size(s, s);
    }

    @Override public void render(UiContext ctx) {
        float now = ctx.time();
        check.target(value ? 1f : 0f, now);
        hover.target(hovered ? 1f : 0f, now);
        float c = check.value(now);
        float r = Tokens.radius().sm();

        if (c < 1f) WidgetPaint.controlTrack(ctx, x, y, w, h, r);
        if (c > 0f) {
            WidgetPaint.surface(ctx, x, y, w, h, r, WidgetPaint.acc(accent), 0);
            int col = Tokens.accent().onAccent();
            ctx.renderer().line(x + w * CK_LX, y + h * CK_LY, x + w * CK_VX, y + h * CK_VY, CK_STROKE, col);
            ctx.renderer().line(x + w * CK_VX, y + h * CK_VY, x + w * CK_RX, y + h * CK_RY, CK_STROKE, col);
        }
        WidgetPaint.hoverWash(ctx, x, y, w, h, r, hover.value(now));
        WidgetPaint.focusRing(ctx, this, r, WidgetPaint.acc(accent));
    }
}
