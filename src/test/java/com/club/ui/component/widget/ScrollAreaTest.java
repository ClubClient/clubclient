package com.club.ui.component.widget;

import com.club.ui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrollAreaTest {
    static final class Box extends com.club.ui.component.Component {
        final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
        @Override public Size measure(float a, float b){ return new Size(pw, ph); }
        @Override public void render(com.club.ui.UiContext c){}
    }

    @Test void clampOffsetWithinBounds() {
        assertEquals(0f,   ScrollArea.clampOffset(-5,   300, 100), 1e-6f);  // no negative
        assertEquals(200f, ScrollArea.clampOffset(999,  300, 100), 1e-6f);  // max = content-viewport
        assertEquals(0f,   ScrollArea.clampOffset(50,    80, 100), 1e-6f);  // content<viewport → no scroll
    }

    @Test void wheelScrollMovesOffsetAndConsumes() {
        ScrollArea sa = new ScrollArea(new Box(100, 300));
        sa.layout(0, 0, 100, 100);                        // viewport 100, content 300 → scrollable
        boolean consumed = sa.mouseScrolled(10, 10, -1);  // wheel down
        assertTrue(consumed);
        assertTrue(sa.offset() > 0f);
    }

    @Test void noScrollWhenContentFits() {
        ScrollArea sa = new ScrollArea(new Box(100, 50));
        sa.layout(0, 0, 100, 100);
        assertFalse(sa.mouseScrolled(10, 10, -1));        // nothing to scroll → not consumed
        assertEquals(0f, sa.offset(), 1e-6f);
    }
}
