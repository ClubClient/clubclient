package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

import java.util.function.IntConsumer;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Keybind field. Click (or Space/Enter when focused) → "listening"; the next key press becomes the bind
 * (Esc clears). Flat field; border + text go accent while listening. Needs focus to capture key events.
 */
public final class Keybind extends Control {
    private int key;                 // GLFW key code, or -1 = unbound
    private boolean listening;
    private IntConsumer onBind;

    public Keybind(int key) { this.key = key; }
    public Keybind onBind(IntConsumer cb) { this.onBind = cb; return this; }
    public int key() { return key; }

    @Override protected void activate() { listening = true; }

    @Override public boolean keyPressed(int k, int scan, int mods) {
        if (listening) {
            key = (k == GLFW_KEY_ESCAPE) ? -1 : k;
            listening = false;
            if (onBind != null) onBind.accept(key);
            return true;
        }
        return super.keyPressed(k, scan, mods);     // Space/Enter → activate() → listen
    }

    private String label() {
        if (listening) return "Press a key";
        if (key == -1) return "None";
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) return String.valueOf((char) ('A' + (key - GLFW_KEY_A)));
        if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) return String.valueOf((char) ('0' + (key - GLFW_KEY_0)));
        if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) return "Shift";
        if (key == GLFW_KEY_SPACE) return "Space";
        return "Key " + key;
    }

    @Override public Size measure(float availW, float availH) {
        Typography.Role r = Tokens.type().label();
        return new Size(Tokens.spacing().xxl() * 2.5f, r.lineHeight() + Tokens.spacing().md());
    }

    @Override public void render(UiContext ctx) {
        float rad = Tokens.radius().sm();
        int bcol = listening ? Tokens.accent().accent() : Tokens.border().defaultColor();
        WidgetPaint.surface(ctx, x, y, w, h, rad, Tokens.surface().surfaceHi(), bcol);
        int tcol = listening ? Tokens.accent().accent()
                             : (key == -1 ? Tokens.palette().textDesc() : Tokens.palette().textHi());
        Typography.Role r = Tokens.type().label();
        TextStyle st = TextStyle.of(r.weight(), r.size(), tcol).align(Align.CENTER);
        ctx.text().draw(label(), x + w / 2f, y + (h - r.lineHeight()) / 2f, st);
        WidgetPaint.focusRing(ctx, this, rad);
    }
}
