package com.club.ui.component.widget;

import com.club.ui.UiContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class ControlTest {
    /** Minimal concrete Control that counts activations. */
    static final class Btn extends Control {
        int activations = 0;
        @Override protected void activate() { activations++; }
        @Override public void render(UiContext ctx) {}
    }

    @Test void releaseInsideActivates() {
        Btn b = new Btn();
        b.layout(0, 0, 80, 24);
        assertTrue(b.mouseClicked(10, 10, 0));   // press inside → capture
        b.mouseReleased(10, 10, 0);              // release inside → fire
        assertEquals(1, b.activations);
    }

    @Test void releaseOutsideCancels() {
        Btn b = new Btn();
        b.layout(0, 0, 80, 24);
        b.mouseClicked(10, 10, 0);
        b.mouseReleased(200, 200, 0);            // release outside → cancel
        assertEquals(0, b.activations);
    }

    @Test void rightButtonDoesNotCapture() {
        Btn b = new Btn();
        b.layout(0, 0, 80, 24);
        assertFalse(b.mouseClicked(10, 10, 1));  // non-primary button ignored
    }

    @Test void enterActivates() {
        Btn b = new Btn();
        b.layout(0, 0, 80, 24);
        assertTrue(b.keyPressed(GLFW_KEY_ENTER, 0, 0));
        assertEquals(1, b.activations);
    }

    @Test void spaceActivates() {
        Btn b = new Btn();
        b.layout(0, 0, 80, 24);
        assertTrue(b.keyPressed(GLFW_KEY_SPACE, 0, 0));
        assertEquals(1, b.activations);
    }

    @Test void disabledIgnoresKeyboard() {
        Btn b = new Btn();
        b.enabled = false;
        b.layout(0, 0, 80, 24);
        assertFalse(b.keyPressed(GLFW_KEY_ENTER, 0, 0));
        assertEquals(0, b.activations);
    }

    @Test void disabledDoesNotCapturePress() {
        Btn b = new Btn();
        b.enabled = false;
        b.layout(0, 0, 80, 24);
        assertFalse(b.mouseClicked(10, 10, 0));   // disabled control does not consume the press
    }
}
