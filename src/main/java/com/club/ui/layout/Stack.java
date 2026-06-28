package com.club.ui.layout;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import java.util.ArrayList;
import java.util.List;

/** Overlays children; each is placed at its measured size, anchored within the Stack bounds. */
public final class Stack extends Container {
    private final List<Anchor> anchors = new ArrayList<>();

    public Stack add(Component c, Anchor a) { addChild(c); anchors.add(a); return this; }

    @Override public Size measure(float availW, float availH) {
        float mw = 0, mh = 0;
        for (Component c : children) { Size s = c.measure(availW, availH); mw = Math.max(mw, s.w()); mh = Math.max(mh, s.h()); }
        return new Size(mw, mh);
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        for (int i = 0; i < children.size(); i++) {
            Component c = children.get(i);
            Anchor a = anchors.get(i);
            Size s = c.measure(w, h);
            float cw = s.w(), ch = s.h();
            float cx = x + hFactor(a) * (w - cw);
            float cy = y + vFactor(a) * (h - ch);
            c.layout(cx, cy, cw, ch);
        }
    }

    private static float hFactor(Anchor a) {
        return switch (a) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT       -> 0f;
            case TOP, CENTER, BOTTOM               -> 0.5f;
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT    -> 1f;
        };
    }

    private static float vFactor(Anchor a) {
        return switch (a) {
            case TOP_LEFT, TOP, TOP_RIGHT          -> 0f;
            case LEFT, CENTER, RIGHT               -> 0.5f;
            case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> 1f;
        };
    }
}
