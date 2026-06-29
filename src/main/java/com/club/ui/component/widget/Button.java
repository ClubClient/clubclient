package com.club.ui.component.widget;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.text.Align;
import com.club.ui.theme.Tokens;

/**
 * Action button. Composes a centered {@link Label}; commit-model + keyboard come from {@link Control}.
 * State-visuals route through {@link WidgetPaint} (hover/press/focus) — no hand-rolled overlays.
 *
 * <p>PRIMARY: filled accent (hover lightens toward accentHi), text = {@code accent().onAccent()},
 * pressed → press overlay. GHOST: transparent + default border, text textHi→accent on hover, hover wash.
 */
public final class Button extends Control {
    public enum Variant { PRIMARY, GHOST }

    private final Label label;
    private Variant variant = Variant.PRIMARY;
    private Runnable onClick;
    private final Transition hover =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());

    public Button(String text) { this.label = new Label(text).align(Align.CENTER); }

    public Button variant(Variant v) { this.variant = v; return this; }
    public Button onClick(Runnable r) { this.onClick = r; return this; }

    /** Exposed for tests/gallery (no text metrics). */
    public Variant variantValue() { return variant; }

    @Override protected void activate() { if (onClick != null) onClick.run(); }

    @Override public Size measure(float availW, float availH) {     // text-width: not headless-tested
        Size t = label.measure(availW, availH);
        return new Size(t.w() + Tokens.spacing().md() * 2f, t.h() + Tokens.spacing().sm() * 2f);
    }

    @Override public void layout(float x, float y, float w, float h) { super.layout(x, y, w, h); } // bounds only

    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().sm();
        float now = ctx.time();
        hover.target(hovered ? 1f : 0f, now);
        float hv = hover.value(now);

        if (variant == Variant.PRIMARY) {
            int fill = Color.lerp(Tokens.accent().accent(), Tokens.accent().accentHi(), hv);
            ctx.renderer().roundedRect(x, y, w, h, r, fill);
            if (pressed) WidgetPaint.pressOverlay(ctx, x, y, w, h, r);
            label.color(Tokens.accent().onAccent());
        } else { // GHOST
            ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), Tokens.border().defaultColor());
            WidgetPaint.hoverWash(ctx, x, y, w, h, r, hv);
            label.color(Color.lerp(Tokens.palette().textHi(), Tokens.accent().accent(), hv));
        }

        // Center the label within bounds (text metrics here, where ctx is ready — never in layout()).
        float lh = label.measure(w, h).h();
        label.layout(x, y + (h - lh) / 2f, w, h);
        label.render(ctx);

        WidgetPaint.focusRing(ctx, this, r);
    }
}
