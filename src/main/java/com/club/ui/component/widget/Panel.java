package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.Insets;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;

/**
 * Flat grouping surface (no shadow — unlike Card). Holds exactly one child in an inner rect reduced by padding.
 * render: flatSurface (via WidgetPaint) → pushRoundedClip → child → popClip.
 */
public final class Panel extends Container {
    private Component child;
    private Insets padding = Insets.ZERO;

    public Panel() {}

    public Panel(Component c) { child(c); }

    public Panel child(Component c) {
        this.child = c;
        children.clear();
        if (c != null) addChild(c);
        return this;
    }

    public Panel padding(Insets p) { this.padding = p; return this; }

    @Override public Size measure(float availW, float availH) {
        if (child == null) return new Size(padding.horizontal(), padding.vertical());
        Size cs = child.measure(availW - padding.horizontal(), availH - padding.vertical());
        return new Size(cs.w() + padding.horizontal(), cs.h() + padding.vertical());
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        if (child != null) {
            child.layout(
                x + padding.left(),
                y + padding.top(),
                w - padding.horizontal(),
                h - padding.vertical()
            );
        }
    }

    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().md();
        WidgetPaint.flatSurface(ctx, x, y, w, h, r, Tokens.surface().surface());
        if (child != null) {
            ctx.renderer().pushRoundedClip(x, y, w, h, r);
            child.render(ctx);
            ctx.renderer().popClip();
        }
    }
}
