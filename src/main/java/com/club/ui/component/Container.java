package com.club.ui.component;
import com.club.ui.UiContext;
import java.util.ArrayList;
import java.util.List;

/** Holds and dispatches to children. Subclasses (layout/UI containers) decide how children are laid out. */
public abstract class Container extends Component {
    protected final List<Component> children = new ArrayList<>();

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
            if (c.visible && c.enabled && c.contains(mx, my) && c.mouseClicked(mx, my, button)) return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        for (int i = children.size() - 1; i >= 0; i--) {
            Component c = children.get(i);
            if (c.visible && c.contains(mx, my) && c.mouseScrolled(mx, my, amount)) return true;
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
