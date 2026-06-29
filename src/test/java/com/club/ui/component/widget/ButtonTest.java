package com.club.ui.component.widget;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class ButtonTest {

    @Test void releaseInsideFiresOnClick() {
        int[] n = {0};
        Button b = new Button("Ok").onClick(() -> n[0]++);
        b.layout(0, 0, 80, 24);
        assertTrue(b.mouseClicked(10, 10, 0));   // press inside → capture
        b.mouseReleased(10, 10, 0);              // release inside → fire
        assertEquals(1, n[0]);
    }

    @Test void releaseOutsideCancels() {
        int[] n = {0};
        Button b = new Button("Ok").onClick(() -> n[0]++);
        b.layout(0, 0, 80, 24);
        b.mouseClicked(10, 10, 0);
        b.mouseReleased(200, 200, 0);            // release outside → cancel
        assertEquals(0, n[0]);
    }

    @Test void disabledIgnoresClick() {
        int[] n = {0};
        Button b = new Button("Ok").onClick(() -> n[0]++);
        b.enabled = false;
        b.layout(0, 0, 80, 24);
        assertFalse(b.mouseClicked(10, 10, 0));
        assertEquals(0, n[0]);
    }

    @Test void keyboardEnterActivates() {
        int[] n = {0};
        Button b = new Button("Ok").onClick(() -> n[0]++);
        b.layout(0, 0, 80, 24);
        assertTrue(b.keyPressed(GLFW_KEY_ENTER, 0, 0));
        assertEquals(1, n[0]);
    }

    @Test void variantDefaultIsPrimary() {
        Button b = new Button("Hi");
        assertEquals(Button.Variant.PRIMARY, b.variantValue());
    }

    @Test void variantFluentSetter() {
        Button b = new Button("Hi").variant(Button.Variant.GHOST);
        assertEquals(Button.Variant.GHOST, b.variantValue());
    }

    @Test void nullOnClickIsSafe() {
        // onClick not set — release-inside must not throw
        Button b = new Button("Ok");
        b.layout(0, 0, 80, 24);
        b.mouseClicked(10, 10, 0);
        assertDoesNotThrow(() -> b.mouseReleased(10, 10, 0));
    }
}
