package com.club.ui.component;
import com.club.ui.UiContext;
import com.club.ui.layout.Size;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentTest {
    static class Probe extends Component {
        @Override public Size measure(float aw, float ah) { return new Size(40, 12); }
        @Override public void render(UiContext ctx) { }
    }
    @Test void layoutAssignsBounds() {
        Probe p = new Probe();
        p.layout(5, 7, 40, 12);
        assertTrue(p.contains(5, 7));
        assertTrue(p.contains(44.9, 18.9));
        assertFalse(p.contains(45, 19));   // exclusive right/bottom
        assertFalse(p.contains(4, 7));
    }
    @Test void measureReturnsDesired() {
        assertEquals(40, new Probe().measure(100, 100).w());
    }
    @Test void inputDefaultsAreInert() {
        Probe p = new Probe();
        assertFalse(p.mouseClicked(0, 0, 0));
        assertFalse(p.keyPressed(0, 0, 0));
        assertFalse(p.charTyped('a', 0));
        assertFalse(p.mouseScrolled(0, 0, 1));
        assertTrue(p.enabled); assertTrue(p.visible);
        assertFalse(p.isHovered());
    }
}
