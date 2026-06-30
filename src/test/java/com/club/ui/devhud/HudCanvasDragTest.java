package com.club.ui.devhud;

import com.club.ui.UiContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudCanvasDragTest {
    /** A headless element backed by plain ints (no ClubConfig / MC). */
    static final class FakeElement extends HudElement {
        int x, y; float scale; final int cw, ch;
        FakeElement(String id, int x, int y, float scale, int cw, int ch) { super(id); this.x=x; this.y=y; this.scale=scale; this.cw=cw; this.ch=ch; }
        @Override public int cfgX() { return x; }
        @Override public int cfgY() { return y; }
        @Override public void cfgX(int v) { x = v; }
        @Override public void cfgY(int v) { y = v; }
        @Override public float cfgScale() { return scale; }
        @Override public boolean cfgEnabled() { return true; }
        @Override public int[] contentSize(net.minecraft.client.MinecraftClient mc, boolean live) { return new int[]{cw, ch}; }
        @Override public void paint(UiContext c, net.minecraft.client.MinecraftClient mc, float ox, float oy, float s, boolean live) {}
        @Override protected boolean live(net.minecraft.client.MinecraftClient mc) { return false; } // sample sizing, no MC
        @Override public int[] box(net.minecraft.client.MinecraftClient mc) { return scaledBox(x, y, cw, ch, scale); }
    }

    @Test void dragMovesElementByGrabOffsetAndPersistsOnRelease() {
        FakeElement e = new FakeElement("t", 100, 100, 1f, 50, 20);
        boolean[] saved = {false};
        HudCanvas c = new HudCanvas(true); c.saver(() -> saved[0] = true); c.add(e); c.setScreen(400, 300);
        e.layout(100, 100, 50, 20);
        assertTrue(c.mouseClicked(110, 105, 0));     // press inside (grab offset 10,5)
        c.mouseDragged(210, 155, 0, 0, 0);           // cursor → (210,155) ⇒ top-left (200,150)
        assertEquals(200, e.x); assertEquals(150, e.y);
        assertFalse(saved[0]);                        // not yet
        c.mouseReleased(210, 155, 0);
        assertTrue(saved[0]);                         // drag commits
    }

    @Test void dragSnapsToCenter() {
        FakeElement e = new FakeElement("t", 0, 0, 1f, 50, 20);
        HudCanvas c = new HudCanvas(true); c.saver(() -> {}); c.add(e); c.setScreen(400, 300);
        e.layout(0, 0, 50, 20);
        c.mouseClicked(0, 0, 0);                      // grab offset (0,0)
        c.mouseDragged(176, 5, 0, 0, 0);             // x near centered target 175
        assertEquals(175, e.x);                       // snapped
        assertEquals(200, c.guideX());                // center guide shown
    }

    @Test void gridSnapQuantizesWhenEnabled() {
        FakeElement e = new FakeElement("t", 0, 0, 1f, 50, 20);
        HudCanvas c = new HudCanvas(true); c.saver(() -> {}); c.add(e); c.setScreen(400, 300);
        c.setGridSnap(true);
        e.layout(0, 0, 50, 20);
        c.mouseClicked(0, 0, 0);
        c.mouseDragged(101, 51, 0, 0, 0);            // 101→104? grid 8: round(101/8)*8=104; 51→48
        assertEquals(104, e.x); assertEquals(48, e.y);
    }

    @Test void rightClickSelectsAndTogglesElement() {
        FakeElement e = new FakeElement("t", 10, 10, 1f, 50, 20);
        HudCanvas c = new HudCanvas(true); c.saver(() -> {}); c.add(e); c.setScreen(400, 300);
        e.layout(10, 10, 50, 20);
        c.mouseClicked(20, 15, 1);                    // RMB → open settings (select)
        assertSame(e, c.selected());
        c.mouseClicked(20, 15, 1);                    // RMB again → toggle off
        assertNull(c.selected());
    }

    @Test void leftTapDoesNotSelect() {
        FakeElement e = new FakeElement("t", 10, 10, 1f, 50, 20);
        HudCanvas c = new HudCanvas(true); c.saver(() -> {}); c.add(e); c.setScreen(400, 300);
        e.layout(10, 10, 50, 20);
        c.mouseClicked(20, 15, 0);                    // LMB tap = move-gesture start; never selects
        c.mouseReleased(20, 15, 0);
        assertNull(c.selected());
    }
}
