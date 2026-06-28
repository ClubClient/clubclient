package com.club.ui.component;
import com.club.ui.UiContext;
import java.util.ArrayList;
import java.util.List;

/** Holds and dispatches to children. Subclasses (layout/UI containers) decide how children are laid out. */
public abstract class Container extends Component {
    protected final List<Component> children = new ArrayList<>();
    /** Child that consumed the active press = this level's capture head; null when no drag is in progress. */
    private Component pressedChild;

    protected void addChild(Component c) { children.add(c); }
    public List<Component> children() { return children; }

    @Override public void render(UiContext ctx) {
        for (int i = 0; i < children.size(); i++) {
            Component c = children.get(i);
            if (c.visible) c.render(ctx);
        }
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Component c = children.get(i);
            if (c.visible && c.enabled && c.contains(mx, my) && c.mouseClicked(mx, my, button)) {
                pressedChild = c;                       // capture the consumer for subsequent drag/release
                return true;
            }
        }
        return false;
    }
    /** Routed only to the capture owner (never hit-test, never broadcast); clears capture at this level. */
    @Override public boolean mouseReleased(double mx, double my, int button) {
        Component p = pressedChild;
        pressedChild = null;
        return p != null && p.mouseReleased(mx, my, button);
    }
    /** Routed only to the capture owner — delivered even when the cursor has left its bounds. */
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return pressedChild != null && pressedChild.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Component c = children.get(i);
            if (c.visible && c.enabled && c.contains(mx, my) && c.mouseScrolled(mx, my, amount)) return true;
        }
        return false;
    }
    @Override public void mouseMoved(double mx, double my) {
        for (int i = 0; i < children.size(); i++) {
            Component c = children.get(i);
            c.hovered = c.visible && c.contains(mx, my);
            c.mouseMoved(mx, my);
        }
    }
}
