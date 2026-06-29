package com.club.ui.component.widget;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class CheckboxTest {
    @Test void releaseInsideTogglesAndNotifies() {
        Boolean[] last = { null };
        Checkbox c = new Checkbox(false).onChange(v -> last[0] = v);
        c.layout(0, 0, 18, 18);
        c.mouseClicked(4, 4, 0);
        c.mouseReleased(4, 4, 0);
        assertTrue(c.value());
        assertEquals(Boolean.TRUE, last[0]);
    }

    @Test void releaseOutsideCancels() {
        Checkbox c = new Checkbox(false);
        c.layout(0, 0, 18, 18);
        c.mouseClicked(4, 4, 0);
        c.mouseReleased(99, 99, 0);
        assertFalse(c.value());
    }

    @Test void spaceToggles() {
        Checkbox c = new Checkbox(true);
        c.layout(0, 0, 18, 18);
        assertTrue(c.keyPressed(GLFW_KEY_SPACE, 0, 0));
        assertFalse(c.value());
    }
}
