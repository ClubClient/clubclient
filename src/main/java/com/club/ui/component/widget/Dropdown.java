package com.club.ui.component.widget;

import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

import java.util.function.IntConsumer;

/**
 * Compact value dropdown. v1 commits by cycling to the next option on click/Enter (a popup menu is a
 * later enhancement). Flat field (surfaceHi + hairline) with the current value and a small caret mark.
 */
public final class Dropdown extends Control {
    private final String[] options;
    private int index;
    private IntConsumer onChange;
    private TextStyle style;

    public Dropdown(String[] options, int index) {
        this.options = options;
        this.index = Math.max(0, Math.min(options.length - 1, index));
    }

    public Dropdown onChange(IntConsumer cb) { this.onChange = cb; return this; }
    public int index() { return index; }
    public String value() { return options[index]; }

    @Override protected void activate() {
        index = (index + 1) % options.length;
        if (onChange != null) onChange.accept(index);
    }

    @Override public Size measure(float availW, float availH) {
        Typography.Role r = Tokens.type().body();
        float w = 0f;
        for (String o : options) w = Math.max(w, Ui.text().width(o, r.weight(), r.size()));
        return new Size(w + Tokens.spacing().md() * 2f + 12f, r.lineHeight() + Tokens.spacing().md());
    }

    @Override public void render(UiContext ctx) {
        float rad = Tokens.radius().sm();
        WidgetPaint.surface(ctx, x, y, w, h, rad, Tokens.surface().surfaceHi(), Tokens.border().defaultColor());
        if (pressed) WidgetPaint.pressOverlay(ctx, x, y, w, h, rad);
        Typography.Role r = Tokens.type().body();
        if (style == null) style = TextStyle.of(r.weight(), r.size(), Tokens.palette().textHi());
        float ty = y + (h - r.lineHeight()) / 2f;
        ctx.text().draw(options[index], x + Tokens.spacing().md(), ty, style);
        float cs = 3f;   // small caret mark (axis-aligned square — avoids diagonal artefacts)
        ctx.renderer().rect(x + w - Tokens.spacing().md() - cs, y + h / 2f - cs / 2f, cs, cs, Tokens.palette().textDesc());
        WidgetPaint.focusRing(ctx, this, rad);
    }
}
