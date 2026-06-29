package com.club.ui.component.widget;

import com.club.ui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowTest {
    static final class Box extends com.club.ui.component.Component {
        final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
        @Override public Size measure(float a, float b){ return new Size(pw, ph); }
        @Override public void render(com.club.ui.UiContext c){}
    }

    @Test void titleBarDragMovesWindowByReference() {
        Window win = new Window("Settings").content(new Box(180, 120)).size(200, 160).position(40, 40);
        win.layout(40, 40, 200, 160);
        // press in title-bar (top strip), then drag — capture routes to the title-bar handle
        assertTrue(win.mouseClicked(60, 46, 0));         // consumes → captured
        win.mouseDragged(85, 71, 0, 25, 25);
        assertEquals(65f, win.xLeft(), 1e-6);            // moved by (25,25)
        assertEquals(65f, win.yTop(),  1e-6);
        win.mouseReleased(85, 71, 0);
    }

    @Test void pressOnContentDoesNotMoveWindow() {
        Box content = new Box(180, 120);
        Window win = new Window("S").content(content).size(200, 160).position(0, 0);
        win.layout(0, 0, 200, 160);
        win.mouseClicked(100, 120, 0);                   // inside content area (below title bar)
        win.mouseDragged(120, 140, 0, 20, 20);
        assertEquals(0f, win.xLeft(), 1e-6);             // window not moved (content has no drag)
    }
}
