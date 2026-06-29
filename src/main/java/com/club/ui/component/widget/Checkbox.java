package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.theme.Tokens;

/**
 * Boolean checkbox (bare — label composed externally). Commit-model + keyboard from {@link Control}.
 * OFF = surfaceHi box + border; ON = accent fill + checkmark (two lines). Check-in animated via {@link Transition}.
 */
public final class Checkbox extends Control {
    // Checkmark glyph geometry (shape proportions of the box, NOT design tokens): left point, vertex, right point.
    private static final float CK_LX = 0.26f, CK_LY = 0.50f;   // left end
    private static final float CK_VX = 0.42f, CK_VY = 0.62f;   // bottom vertex
    private static final float CK_RX = 0.74f, CK_RY = 0.32f;   // right end
    private static final float CK_STROKE = 1.5f;               // stroke = thickness * CK_STROKE

    private boolean value;
    private BoolConsumer onChange;
    private final Transition check =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());

    public Checkbox(boolean value) { this.value = value; check.target(value ? 1f : 0f, 0f); }
    public Checkbox onChange(BoolConsumer cb) { this.onChange = cb; return this; }
    public boolean value() { return value; }

    @Override protected void activate() {
        value = !value;
        if (onChange != null) onChange.accept(value);
    }

    @Override public Size measure(float availW, float availH) {
        float s = Tokens.type().body().lineHeight();           // stored Role value — headless-safe
        return new Size(s, s);
    }

    @Override public void render(UiContext ctx) {
        float now = ctx.time();
        check.target(value ? 1f : 0f, now);
        float c = check.value(now);
        float r = Tokens.radius().xs();

        if (c < 1f) {
            ctx.renderer().roundedRect(x, y, w, h, r, Tokens.surface().surfaceHi());
            ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), Tokens.border().defaultColor());
        }
        if (c > 0f) {
            ctx.renderer().roundedRect(x, y, w, h, r, Tokens.accent().accent());
            float t = Tokens.border().thickness() * CK_STROKE;
            int col = Tokens.accent().onAccent();
            ctx.renderer().line(x + w * CK_LX, y + h * CK_LY, x + w * CK_VX, y + h * CK_VY, t, col);
            ctx.renderer().line(x + w * CK_VX, y + h * CK_VY, x + w * CK_RX, y + h * CK_RY, t, col);
        }
        WidgetPaint.focusRing(ctx, this, r);
    }
}
