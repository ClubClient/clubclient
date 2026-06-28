package com.club.ui.component;
import com.club.ui.UiContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pointer-capture (G1): drag/release route to the press-consuming child via the pressedChild chain, never hit-test. */
class CaptureTest {
    static class DragProbe extends Component {
        boolean consume = true;
        int presses, drags, releases; double lastDx, lastDy;
        @Override public void render(UiContext ctx) { }
        @Override public boolean mouseClicked(double mx, double my, int b) { presses++; return consume; }
        @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
            drags++; lastDx = dx; lastDy = dy; return true;
        }
        @Override public boolean mouseReleased(double mx, double my, int b) { releases++; return true; }
    }
    static class Box extends Container { void add(Component c) { addChild(c); } }

    @Test void dragRoutesToCapturedChildEvenOffBounds() {
        Box root = new Box(); root.layout(0, 0, 100, 100);
        DragProbe child = new DragProbe(); child.layout(0, 0, 20, 20);
        root.add(child);
        assertTrue(root.mouseClicked(5, 5, 0));
        assertEquals(1, child.presses);
        // cursor far outside child bounds — must still reach it because routing follows capture, not contains()
        assertTrue(root.mouseDragged(500, 500, 0, 7, 9));
        assertEquals(1, child.drags);
        assertEquals(7.0, child.lastDx); assertEquals(9.0, child.lastDy);
    }

    @Test void releaseGoesOnlyToCapturedChildNoBroadcast() {
        Box root = new Box(); root.layout(0, 0, 100, 100);
        DragProbe a = new DragProbe(); a.layout(0, 0, 20, 20);
        DragProbe b = new DragProbe(); b.layout(50, 50, 20, 20);
        root.add(a); root.add(b);
        assertTrue(root.mouseClicked(5, 5, 0));          // a captured
        assertTrue(root.mouseReleased(60, 60, 0));        // cursor over b — release must go to a, not b
        assertEquals(1, a.releases); assertEquals(0, b.releases);
    }

    @Test void captureClearedAfterRelease() {
        Box root = new Box(); root.layout(0, 0, 100, 100);
        DragProbe child = new DragProbe(); child.layout(0, 0, 20, 20);
        root.add(child);
        root.mouseClicked(5, 5, 0);
        root.mouseReleased(5, 5, 0);
        assertEquals(1, child.releases);
        assertFalse(root.mouseDragged(5, 5, 0, 1, 1));    // nothing captured anymore
        assertEquals(0, child.drags);
    }

    @Test void noDragOrReleaseWithoutPress() {
        Box root = new Box(); root.layout(0, 0, 100, 100);
        DragProbe child = new DragProbe(); child.layout(0, 0, 20, 20);
        root.add(child);
        assertFalse(root.mouseDragged(5, 5, 0, 1, 1));
        assertFalse(root.mouseReleased(5, 5, 0));
        assertEquals(0, child.drags); assertEquals(0, child.releases);
    }

    @Test void dragRoutesThroughNestedContainersToLeaf() {
        Box root = new Box();  root.layout(0, 0, 100, 100);
        Box inner = new Box(); inner.layout(0, 0, 50, 50);
        DragProbe leaf = new DragProbe(); leaf.layout(0, 0, 20, 20);
        inner.add(leaf); root.add(inner);
        assertTrue(root.mouseClicked(5, 5, 0));
        assertEquals(1, leaf.presses);
        assertTrue(root.mouseDragged(999, 999, 0, 3, 4)); // off-bounds, through inner, to leaf
        assertEquals(1, leaf.drags);
        assertEquals(3.0, leaf.lastDx); assertEquals(4.0, leaf.lastDy);
    }

    @Test void disabledChildNotCaptured() {
        Box root = new Box(); root.layout(0, 0, 100, 100);
        DragProbe child = new DragProbe(); child.layout(0, 0, 20, 20); child.enabled = false;
        root.add(child);
        assertFalse(root.mouseClicked(5, 5, 0));
        assertEquals(0, child.presses);
        assertFalse(root.mouseDragged(5, 5, 0, 1, 1));
        assertEquals(0, child.drags);
    }

    @Test void declinedPressDoesNotCapture() {
        Box root = new Box(); root.layout(0, 0, 100, 100);
        DragProbe child = new DragProbe(); child.consume = false; child.layout(0, 0, 20, 20);
        root.add(child);
        assertFalse(root.mouseClicked(5, 5, 0));          // child declined the press
        assertEquals(1, child.presses);
        assertFalse(root.mouseDragged(5, 5, 0, 1, 1));    // so no capture established
        assertEquals(0, child.drags);
    }
}
