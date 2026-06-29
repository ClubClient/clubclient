package com.club.ui.component.widget;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class ToggleTest {
    @Test void releaseInsideFlipsAndNotifies() {
        Boolean[] last = { null };
        Toggle t = new Toggle(false).onChange(v -> last[0] = v);
        t.layout(0, 0, 36, 20);
        t.mouseClicked(5, 5, 0);
        t.mouseReleased(5, 5, 0);
        assertTrue(t.value());
        assertEquals(Boolean.TRUE, last[0]);
    }

    @Test void releaseOutsideDoesNotFlip() {
        Toggle t = new Toggle(false);
        t.layout(0, 0, 36, 20);
        t.mouseClicked(5, 5, 0);
        t.mouseReleased(100, 100, 0);
        assertFalse(t.value());
    }

    @Test void spaceTogglesWhenFocused() {
        Toggle t = new Toggle(false);
        t.layout(0, 0, 36, 20);
        assertTrue(t.keyPressed(GLFW_KEY_SPACE, 0, 0));
        assertTrue(t.value());
    }

    @Test void disabledDoesNotToggle() {
        Toggle t = new Toggle(false);
        t.enabled = false;
        t.layout(0, 0, 36, 20);
        assertFalse(t.mouseClicked(5, 5, 0));
        assertFalse(t.keyPressed(GLFW_KEY_SPACE, 0, 0));
        assertFalse(t.value());
    }
}
