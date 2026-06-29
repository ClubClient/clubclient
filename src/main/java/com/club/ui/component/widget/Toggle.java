package com.club.ui.component.widget;

import com.club.ui.Axis;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.theme.Glow;
import com.club.ui.theme.Tokens;

/**
 * On/off switch (bare — label composed externally). Commit-model + keyboard from {@link Control}.
 * ON = accent gradient + restrained active glow (the only sanctioned glow use, §3.3); OFF = surfaceHi track.
 * Knob position is animated via a {@link Transition}.
 */
public final class Toggle extends Control {
    private boolean value;
    private BoolConsumer onChange;
    private final Transition knob =
            new Transition(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());

    public Toggle(boolean value) { this.value = value; knob.target(value ? 1f : 0f, 0f); }
    public Toggle onChange(BoolConsumer cb) { this.onChange = cb; return this; }
    public boolean value() { return value; }

    @Override protected void activate() {
        value = !value;
        if (onChange != null) onChange.accept(value);
    }

    @Override public Size measure(float availW, float availH) {
        float hgt = Tokens.type().body().lineHeight();          // stored Role value — headless-safe
        return new Size(hgt + Tokens.spacing().lg(), hgt);      // width = height + knob travel (token)
    }

    @Override public void render(UiContext ctx) {
        float now = ctx.time();
        knob.target(value ? 1f : 0f, now);
        float k = knob.value(now);
        float r = h / 2f;

        if (value) {
            ctx.renderer().gradient(x, y, w, h, r, Tokens.accent().gradientA(), Tokens.accent().gradientB(), Axis.HORIZONTAL);
            Glow.Preset g = Tokens.glow().active();
            ctx.renderer().glow(x, y, w, h, r, g.size(), g.color());
        } else {
            ctx.renderer().roundedRect(x, y, w, h, r, Tokens.surface().surfaceHi());
            ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), Tokens.border().defaultColor());
        }
        float kr = r - Tokens.border().thickness() * 2f;
        float travel = w - 2f * r;
        ctx.renderer().circle(x + r + travel * k, y + r, kr, Tokens.palette().white());

        WidgetPaint.focusRing(ctx, this, r);
    }
}
