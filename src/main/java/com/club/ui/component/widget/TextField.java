package com.club.ui.component.widget;

import com.club.ui.Icon;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

import java.util.function.Consumer;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Single-line text field with a leading search icon (used as the menu search). Inset surface (bg1 + hairline);
 * border goes accent while focused. Typing requires focus (the screen routes char/key events to the focused
 * component). Emits {@link #onChange} on every edit.
 */
public final class TextField extends Component {
    private final StringBuilder text = new StringBuilder();
    private final String placeholder;
    private Consumer<String> onChange;
    private TextStyle styleHi, stylePh;

    public TextField(String placeholder) { this.placeholder = placeholder; }
    public TextField onChange(Consumer<String> cb) { this.onChange = cb; return this; }
    public String text() { return text.toString(); }

    /** Clears the field without firing {@link #onChange} (the caller already owns the reset state). */
    public void clear() { text.setLength(0); }

    @Override public Size measure(float availW, float availH) {
        Typography.Role r = Tokens.type().body();
        return new Size(Tokens.spacing().xxl() * 6f, r.lineHeight() + Tokens.spacing().md());
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        return enabled && contains(mx, my);   // consume → FocusManager focuses us
    }

    @Override public boolean charTyped(char ch, int mods) {
        if (!focused || ch < 32) return false;
        text.append(ch);
        if (onChange != null) onChange.accept(text.toString());
        return true;
    }

    @Override public boolean keyPressed(int k, int scan, int mods) {
        if (focused && k == GLFW_KEY_BACKSPACE && text.length() > 0) {
            text.deleteCharAt(text.length() - 1);
            if (onChange != null) onChange.accept(text.toString());
            return true;
        }
        return false;
    }

    @Override public void render(UiContext ctx) {
        float rad = Tokens.radius().md();
        int bcol = focused ? Tokens.accent().accent() : Tokens.border().defaultColor();
        WidgetPaint.surface(ctx, x, y, w, h, rad, Tokens.surface().bg1(), bcol);
        float pad = Tokens.spacing().md(), is = 14f;
        Icon.SEARCH.draw(ctx.renderer(), x + pad, y + (h - is) / 2f, is, Tokens.palette().textDesc(), 1.5f);
        Typography.Role r = Tokens.type().body();
        boolean empty = text.length() == 0;
        if (styleHi == null) {
            styleHi = TextStyle.of(r.weight(), r.size(), Tokens.palette().textHi());
            stylePh = TextStyle.of(r.weight(), r.size(), Tokens.palette().textDesc());
        }
        float tx = x + pad + is + Tokens.spacing().sm();
        ctx.text().draw(empty ? placeholder : text.toString(), tx, y + (h - r.lineHeight()) / 2f,
                empty ? stylePh : styleHi);
    }
}
