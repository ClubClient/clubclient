package com.club.ui.component.widget;

import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.text.Align;
import com.club.ui.text.TextEffect;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

/** Text primitive. Single-line in M2.2 (wrap → M2.3). measure/render use text metrics; not headless-tested. */
public final class Label extends Component {
    private String text;
    private Typography.Role role;
    private Align align = Align.LEFT;
    private int color;
    private boolean colorSet;
    private TextEffect effect = TextEffect.NONE;
    private TextStyle style;        // cached; rebuilt only on change (alloc-free in render)
    private boolean styleDirty = true;
    // Disabled dimming (Stage 9.8): eases the text alpha toward Interaction.disabledAlpha on enabled=false.
    // Text can't use pushOpacity (the backend ignores it for glyphs), so we scale the colour alpha directly.
    private final Transition enableT =
            new Transition(1f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());

    public Label(String text) { this(text, Tokens.type().body()); }
    public Label(String text, Typography.Role role) { this.text = text; this.role = role; }

    public Label text(String s)          { this.text = s; return this; }
    public Label role(Typography.Role r) { this.role = r; styleDirty = true; return this; }
    public Label align(Align a)          { this.align = a; styleDirty = true; return this; }
    public Label color(int c)            { this.color = c; this.colorSet = true; styleDirty = true; return this; }
    public Label effect(TextEffect e)    { this.effect = e; styleDirty = true; return this; }

    // package-private for same-package tests/widgets (NOT public §3 API)
    String textValue()       { return text; }
    Align alignValue()       { return align; }
    int colorValue()         { return colorSet ? color : Tokens.palette().textHi(); }
    Typography.Role role()   { return role; }

    @Override public Size measure(float availW, float availH) {   // text-width: not headless-testable
        return new Size(Ui.text().width(text, role.weight(), role.size()),
                        Ui.text().lineHeight(role.weight(), role.size()));
    }

    @Override public void render(UiContext ctx) {
        enableT.target(enabled ? 1f : 0f, ctx.time());
        float e = enableT.value(ctx.time());
        TextStyle s;
        if (e >= 0.999f) {   // fully enabled: use the cached style (alloc-free common path)
            if (styleDirty) {
                style = TextStyle.of(role.weight(), role.size(), colorValue()).align(align).effect(effect);
                styleDirty = false;
            }
            s = style;
        } else {             // disabled / animating: scale alpha toward disabledAlpha (per-frame style)
            float da = Tokens.interaction().disabledAlpha();
            s = TextStyle.of(role.weight(), role.size(), Color.scaleAlpha(colorValue(), da + (1f - da) * e))
                    .align(align).effect(effect);
        }
        float tx = switch (align) { case LEFT -> x; case CENTER -> x + w / 2f; case RIGHT -> x + w; };
        ctx.text().draw(text, tx, y, s);
    }
}
