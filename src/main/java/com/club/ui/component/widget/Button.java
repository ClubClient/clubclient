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
    /**
     * PRIMARY: accent fill. GHOST: transparent + hairline. TEXT: neither — a label that happens to be
     * clickable.
     *
     * <p>TEXT exists for actions whose WEIGHT should match their FREQUENCY. "Reset to default" was a
     * full-width 36px bordered button, which made the least-used control in the popover its largest object
     * (owner, v0.1.3 item 3.1: "не кажется тебе, что они слишком большие и неаккуратные + выбиваются?").
     * It is pressed once, by a player who has already decided. It does not need to shout, and a button that
     * shouts louder than the sliders above it teaches the eye the wrong hierarchy.
     *
     * <p>Its hover colour comes from {@link #accent(int)} like every other variant, so a destructive action
     * simply passes the palette's own {@code stateLow} and turns red under the cursor for free.
     */
    /**
     * VALUE: a quiet chip — a faint ground, NO border. For a control that displays a setting and is only
     * occasionally clicked to change it (owner, v0.1.3: "зачем клавише столько места и такое большое
     * ОТДЕЛЬНОЕ ОКНО?"). A border is what makes a thing read as a button; a key binding is not a button, it
     * is a value that happens to be editable, and it belongs in the same column as a slider's number.
     */
    public enum Variant { PRIMARY, GHOST, TEXT, VALUE }

    private final Label label;
    private Variant variant = Variant.PRIMARY;
    private Runnable onClick;
    private boolean compact;   // pin to the 24px control lane (popover value fields, Stage 34)
    private boolean hug;       // drop the 96px width floor — for a control that SHOWS rather than ACTS
    private boolean armed;     // confirm/listening state — a SOFT accent chip, not a solid fill (Stage 50)
    private float minWidth;    // width floor — an armed/confirm label swap must not shrink the box (Stage 38)
    private int accent;   // 0 = theme accent; set for category-tinted contexts (Stage 11.9)
    private final Transition hover =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());
    private boolean styleInit, lastHovered;   // discrete label-color state → no per-frame TextStyle rebuild

    public Button(String text) { this.label = new Label(text).align(Align.CENTER); }

    public Button variant(Variant v) {
        this.variant = v; this.styleInit = false;
        // A VALUE chip is a quiet value, not a control: smaller type than a button's label so "Not set" / "Left
        // Alt" stop shouting (owner, pack 5 #4: "почему текст в боксах так сильно выделяется и такой большой").
        if (v == Variant.VALUE) this.label.role(Tokens.type().label());
        return this;
    }
    public Button onClick(Runnable r) { this.onClick = r; return this; }
    /** Overrides the accent colour (PRIMARY fill / GHOST hover tint / focus ring); 0 = theme accent. */
    public Button accent(int color) { this.accent = color; this.styleInit = false; return this; }
    /** Pin the height to the 24px control lane (spacing.xl — the Slider's lane): an inline value field
     *  in a settings row must not stretch the row taller than the slider rows around it (Stage 34). */
    public Button compact() { this.compact = true; return this; }
    /** Width hugs the label (md padding), skipping the 96px alignment floor — see {@link #measure}. */
    public Button hug() { this.hug = true; return this; }
    /** Left-align the label instead of centring it. A TEXT button pinned to a width floor (so an
     *  arm/confirm swap cannot shift it) reads as INDENTED when its shorter label is centred in the wider
     *  box — "Reset to default" floated away from the left edge (owner, v0.1.3: "почему ресет так съехано
     *  выглядит"). Flush-left, both labels start on the same pixel: no shift, no float. */
    public Button labelLeft() { this.label.align(com.club.ui.text.Align.LEFT); return this; }
    /** Centre the label — the counterpart to {@link #labelLeft()}, for the moment a TEXT button GROWS A
     *  GROUND. Flush-left is right while there is nothing behind the text, but {@link #armed(boolean)} paints
     *  a rounded chip + border around the same box, and a chip whose text hugs one edge while all its padding
     *  piles up on the other looks crooked, not confirmatory (owner: the armed "Confirm reset?" pill). Armed
     *  → centre, decayed → back to flush-left. */
    public Button labelCenter() { this.label.align(com.club.ui.text.Align.CENTER); return this; }
    /** Armed / listening state (confirm-reset, keybind capture): a SOFT accent chip — subtle tinted
     *  fill + accent border + accent text — instead of a solid PRIMARY fill, which read as a garish
     *  pastel block against the flat dark UI (owner, Stage 50). Distinct and urgent, still premium. */
    public Button armed(boolean v) { this.armed = v; this.styleInit = false; return this; }
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
        // Generous horizontal padding (lg/side) + a min-width floor (xxl*3 = 96) so a row of buttons
        // aligns to a common width instead of hugging each label — the key "designed" signal.
        //
        // HUG opts out of BOTH, and it exists because that floor is the wrong instinct for a control that
        // shows a VALUE rather than offering an ACTION (owner, v0.1.3: "зачем клавише столько места и такое
        // большое отдельное окно?"). A key binding is read constantly and rebound roughly once in a lifetime;
        // padding it out to 96px made it the heaviest object in a 236px sheet, heavier than the sliders the
        // player actually drags. A value hugs its content and sits in the same column as the numbers beside it.
        float w = hug
                ? Math.max(t.w() + Tokens.spacing().sm() * 2f, minWidth)   // sm, not md: a key chip is a tag, not a button (owner, v0.1.3 #8)
                : Math.max(Math.max(t.w() + Tokens.spacing().lg() * 2f, Tokens.spacing().xxl() * 3f), minWidth);
        // A VALUE chip hugs its text vertically too — the 24px control lane made "Left Alt" a chunky pill for a
        // one-word value ("для чего им столько места", owner #8). It drops to line-height + sm; PRIMARY/GHOST
        // fields keep the lane so they still align with the sliders beside them.
        float h = compact
                ? (variant == Variant.VALUE ? t.h() + Tokens.spacing().sm() : Tokens.spacing().xl())
                : t.h() + Tokens.spacing().sm() * 2f;
        return new Size(w, h);
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

        if (armed) {   // soft accent chip: tinted fill + accent border + accent text (Stage 50)
            int a = WidgetPaint.acc(accent);
            ctx.renderer().roundedRect(x, y, w, h, r, Color.withAlpha(a, 0x2B));
            ctx.renderer().border(x, y, w, h, r, Tokens.border().thickness(), a);
            if (pressed) WidgetPaint.pressOverlay(ctx, x, y, w, h, r);
        } else if (variant == Variant.TEXT) {
            // No ground and no edge at all — the row IS the label. Nothing to paint but the press feedback,
            // and even that stays inside the text box rather than inventing a plate under it.
            if (pressed) WidgetPaint.pressOverlay(ctx, x, y, w, h, r);
        } else if (variant == Variant.VALUE) {
            // A ground, and NOTHING else. The chip is LIGHTER than the sheet it sits on (white at 6%), never
            // darker: the old key field was painted on ink0 — blacker than the popover itself — which is what
            // made the sheet's footer read as a second, heavier interface glued underneath ("слишком вычерны",
            // owner). Depth in this language goes UP for interactive things and DOWN for grounds, and a value
            // the player can click is an interactive thing.
            ctx.renderer().roundedRect(x, y, w, h, Tokens.radius().sm(),
                    Color.withAlpha(0xFFFFFFFF, hovered ? 0x14 : 0x0D));
            if (pressed) WidgetPaint.pressOverlay(ctx, x, y, w, h, Tokens.radius().sm());
        } else if (variant == Variant.PRIMARY) {
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
            label.color(armed ? WidgetPaint.acc(accent)
                        : variant == Variant.PRIMARY ? Tokens.accent().onAccent()
                        // TEXT rests QUIET (textFaint), not textHi: it is a footnote, and a footnote that is
                        // as bright as the rows above it is not a footnote. GHOST keeps textHi — it has a
                        // border to sit in and reads as a control.
                        : variant == Variant.TEXT
                            ? (hovered ? WidgetPaint.acc(accent) : Tokens.palette().textFaint())
                        // VALUE rests in textMuted, not textHi (owner, pack 5 #4): the chip already has a
                        // ground, so bright text on top double-emphasised the least-used control in the sheet.
                        // It does NOT tint on hover — a value that recolours under the cursor looks like it acted.
                        : variant == Variant.VALUE ? Tokens.palette().textMuted()
                            : (hovered ? WidgetPaint.acc(accent) : Tokens.palette().textHi()));
            lastHovered = hovered;
            styleInit = true;
        }
        label.render(ctx);                              // bounds set in layout(); no per-frame measure/alloc
        WidgetPaint.focusRing(ctx, this, r, WidgetPaint.acc(accent));
    }
}
