package com.club.ui.component;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerTest {
    static class ClickProbe extends Component {
        final boolean consume; int clicks;
        ClickProbe(boolean consume) { this.consume = consume; }
        @Override public void render(UiContext ctx) { }
        @Override public boolean mouseClicked(double mx, double my, int b) { clicks++; return consume; }
    }
    static class Box extends Container {
        void add(Component c) { addChild(c); }
    }
    @Test void clickRoutesToTopmostContainingChild() {
        Box box = new Box(); box.layout(0, 0, 100, 100);
        ClickProbe a = new ClickProbe(true);  a.layout(0, 0, 50, 50);
        ClickProbe b = new ClickProbe(true);  b.layout(0, 0, 50, 50); // overlaps a, added later → on top
        box.add(a); box.add(b);
        assertTrue(box.mouseClicked(10, 10, 0));
        assertEquals(1, b.clicks); assertEquals(0, a.clicks);  // topmost consumed
    }
    @Test void clickFallsThroughWhenNotConsumed() {
        Box box = new Box(); box.layout(0, 0, 100, 100);
        ClickProbe a = new ClickProbe(true);  a.layout(0, 0, 50, 50);
        ClickProbe b = new ClickProbe(false); b.layout(0, 0, 50, 50);
        box.add(a); box.add(b);
        assertTrue(box.mouseClicked(10, 10, 0));
        assertEquals(1, b.clicks); assertEquals(1, a.clicks);  // b declined → a got it
    }
    @Test void clickMissesOutsideChild() {
        Box box = new Box(); box.layout(0, 0, 100, 100);
        ClickProbe a = new ClickProbe(true); a.layout(0, 0, 20, 20);
        box.add(a);
        assertFalse(box.mouseClicked(50, 50, 0));
        assertEquals(0, a.clicks);
    }
    @Test void mouseMovedSetsHover() {
        Box box = new Box(); box.layout(0, 0, 100, 100);
        ClickProbe a = new ClickProbe(true); a.layout(0, 0, 20, 20);
        ClickProbe b = new ClickProbe(true); b.layout(50, 50, 20, 20);
        box.add(a); box.add(b);
        box.mouseMoved(10, 10);
        assertTrue(a.isHovered()); assertFalse(b.isHovered());
    }
}
