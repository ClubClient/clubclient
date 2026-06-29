package com.club.ui.component.widget;

import com.club.ui.layout.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PanelTest {
    static final class Box extends com.club.ui.component.Component {
        final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
        @Override public Size measure(float a, float b){ return new Size(pw, ph); }
        @Override public void render(com.club.ui.UiContext c){}
    }

    @Test void measureAddsPadding() {
        Panel p = new Panel(new Box(40, 20)).padding(Insets.all(8));
        Size s = p.measure(200, 200);
        assertEquals(40 + 16, s.w(), 1e-6);
        assertEquals(20 + 16, s.h(), 1e-6);
    }

    @Test void layoutPlacesChildInInnerRect() {
        Box b = new Box(40, 20);
        Panel p = new Panel(b).padding(Insets.all(8));
        p.layout(10, 10, 100, 60);
        assertEquals(18f, b.xLeft(), 1e-6);   // 10 + left padding
        assertEquals(18f, b.yTop(),  1e-6);
        assertEquals(84f, b.width(), 1e-6);    // 100 - 16
        assertEquals(44f, b.height(),1e-6);
    }

    @Test void emptyPanelMeasuresPaddingOnly() {
        assertEquals(16f, new Panel().padding(Insets.all(8)).measure(50,50).w(), 1e-6);
    }
}
