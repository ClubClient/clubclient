package com.club.ui.component;
import java.util.ArrayList;
import java.util.List;

/** Single focus owner over a flat list of focusable components. Held by the root container. */
public final class FocusManager {
    private final List<Component> focusables = new ArrayList<>();
    private int index = -1;

    public void register(Component c) { focusables.add(c); }
    public void clear() { focusables.clear(); setIndex(-1); }
    public Component focused() { return index < 0 ? null : focusables.get(index); }

    public void focus(Component c) { setIndex(focusables.indexOf(c)); }
    public void next()     { if (!focusables.isEmpty()) setIndex((index + 1 + focusables.size()) % focusables.size()); }
    public void previous() { if (!focusables.isEmpty()) setIndex((index - 1 + focusables.size()) % focusables.size()); }

    public boolean keyPressed(int key, int scan, int mods) {
        Component f = focused();
        return f != null && f.keyPressed(key, scan, mods);
    }
    public boolean charTyped(char ch, int mods) {
        Component f = focused();
        return f != null && f.charTyped(ch, mods);
    }
    public void clickFocus(double mx, double my) {
        for (int i = focusables.size() - 1; i >= 0; i--) {
            Component c = focusables.get(i);
            if (c.visible && c.enabled && c.contains(mx, my)) { setIndex(i); return; }
        }
        setIndex(-1);
    }
    private void setIndex(int i) {
        if (index >= 0 && index < focusables.size()) focusables.get(index).focused = false;
        index = i;
        if (index >= 0) focusables.get(index).focused = true;
    }
}
