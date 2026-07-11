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
    private boolean compact;   // pin to the 24px control lane (popover value fields, Stage 34)
    private float minWidth;    // width floor — an armed/confirm label swap must not shrink the box (Stage 38)
    private int accent;   // 0 = theme accent; set for category-tinted contexts (Stage 11.9)
    private final Transition hover =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());
    private boolean styleInit, lastHovered;   // discrete label-color state → no per-frame TextStyle rebuild

    public Button(String text) { this.label = new Label(text).align(Align.CENTER); }

    public Button variant(Variant v) { this.variant = v; this.styleInit = false; return this; }
    public Button onClick(Runnable r) { this.onClick = r; return this; }
    /** Overrides the accent colour (PRIMARY fill / GHOST hover tint / focus ring); 0 = theme accent. */
    public Button accent(int color) { this.accent = color; this.styleInit = false; return this; }
    /** Pin the height to the 24px control lane (spacing.xl — the Slider's lane): an inline value field
     *  in a settings row must not stretch the row taller than the slider rows around it (Stage 34). */
    public Button compact() { this.compact = true; return this; }
    /** Swap the label text in place (armed/confirm states) — bounds are kept, callers pass a narrower
     *  or equal label so no re-layout is needed. */
    public Button label(String text) { this.label.text(text); return this; }
    /** Width floor in px: a state-swapped label (e.g. armed "Sure? Reset") keeps the idle box, so the
     *  click target never shrinks under the cursor between the two clicks of a confirmation. */
    public Button minWidth(float px) { this.minWidth = px; return this; }

    /** Package-private for same-package tests (NOT public §3 API). */
    Variant variantValue() { return variant; }

    @Override protected void activate() { if (onClick != null) onClick.run(); }

    @Override public Size measure(float availW, float availH) {     // text-width: not headless-tested
        Size t = label.measure(availW, availH);
        // Generous horizontal padding (lg/side) + a min-width floor (xxl*3) so a row of buttons
        // aligns to a common width instead of hugging each label — the key "designed" signal.
        float w = Math.max(Math.max(t.w() + Tokens.spacing().lg() * 2f, Tokens.spacing().xxl() * 3f), minWidth);
        return new Size(w, compact ? Tokens.spacing().xl() : t.h() + Tokens.spacing().sm() * 2f);
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        float lh = label.role().lineHeight();          // token lineHeight — no text-measure, no alloc
        label.layout(x, y + (h - lh) / 2f, w, lh);     // vertical-center; Label.align handles horizontal
    }

    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().md();                 // control-radius language (md=10); sm(6) read too boxy
        float now = ctx.time();
        hover.target(hovered ? 1f : 0f, now);
        float hv = hover.value(now);                    // animated via int colors only (alloc-free)

        if (variant == Variant.PRIMARY) {
            ctx.renderer().roundedRect(x, y, w, h, r, Color.lerp(WidgetPaint.acc(accent), WidgetPaint.accHi(accent), hv));
            if (pressed) WidgetPaint.pressOverlay(ctx, x, y, w, h, r);
        } else { // GHOST — premium outline: a visible strong hairline that tints toward accent on hover.
            WidgetPaint.hoverWash(ctx, x, y, w, h, r, hv);
            ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(),
                    Color.lerp(Tokens.border().strong(), WidgetPaint.acc(accent), hv));
            if (pressed) WidgetPaint.pressOverlay(ctx, x, y, w, h, r);
        }

        // Discrete label color: Label rebuilds its TextStyle only on a state change, never per-frame (alloc-free).
        if (!styleInit || hovered != lastHovered) {
            label.color(variant == Variant.PRIMARY ? Tokens.accent().onAccent()
                        : (hovered ? WidgetPaint.acc(accent) : Tokens.palette().textHi()));
            lastHovered = hovered;
            styleInit = true;
        }
        label.render(ctx);                              // bounds set in layout(); no per-frame measure/alloc
        WidgetPaint.focusRing(ctx, this, r, WidgetPaint.acc(accent));
    }
}
