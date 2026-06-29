package com.club.ui.component.widget;

import com.club.ui.Axis;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;

/** 1px separator. Stretches along the parent's main axis (Fill) or cross axis (CrossAlign.STRETCH). */
public final class Divider extends Component {
    private final Axis axis;
    private int color;
    private boolean colorSet;

    public Divider() { this(Axis.HORIZONTAL); }
    public Divider(Axis axis) { this.axis = axis; }

    public Divider color(int c) { this.color = c; this.colorSet = true; return this; }
    private int color() { return colorSet ? color : Tokens.border().subtle(); }

    @Override public Size measure(float availW, float availH) {
        float t = Tokens.border().thickness();
        return axis == Axis.HORIZONTAL ? new Size(0, t) : new Size(t, 0);
    }

    @Override public void render(UiContext ctx) { ctx.renderer().rect(x, y, w, h, color()); }
}
