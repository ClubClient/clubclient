package com.club.ui.component;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;

/** Position-passive UI element. Bounds are assigned by layout(); render() only reads them. */
public abstract class Component {
    protected float x, y, w, h;
    public boolean enabled = true, visible = true;
    protected boolean hovered, pressed, focused;

    /** Intrinsic desired size given available space. Leaf widgets override. */
    public Size measure(float availW, float availH) { return new Size(0, 0); }

    /** Assigns final bounds. Called by the parent container — never self-invoked for positioning. */
    public void layout(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }

    public abstract void render(UiContext ctx);

    public boolean mouseClicked(double mx, double my, int button)     { return false; }
    public boolean mouseReleased(double mx, double my, int button)    { return false; }
    public void    mouseMoved(double mx, double my)                   { }
    public boolean mouseScrolled(double mx, double my, double amount) { return false; }
    public boolean keyPressed(int key, int scan, int mods)           { return false; }
    public boolean charTyped(char ch, int mods)                       { return false; }

    public boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public boolean isHovered() { return hovered; }
    public boolean isPressed() { return pressed; }
    public boolean isFocused() { return focused; }

    public float xLeft()  { return x; }
    public float yTop()   { return y; }
    public float width()  { return w; }
    public float height() { return h; }
}
