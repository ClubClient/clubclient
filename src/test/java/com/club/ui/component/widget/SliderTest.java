package com.club.ui.component.widget;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class SliderTest {
    @Test void quantizeClampsAndSnaps() {
        assertEquals(0f,   Slider.quantize(-3,   0, 10, 1), 1e-6f);
        assertEquals(10f,  Slider.quantize(99,   0, 10, 1), 1e-6f);
        assertEquals(4f,   Slider.quantize(4.2f, 0, 10, 1), 1e-6f);   // snap to step 1
        assertEquals(4.2f, Slider.quantize(4.2f, 0, 10, 0), 1e-4f);   // step 0 = continuous
    }

    @Test void formatIntegerVsFractional() {
        assertEquals("4",   Slider.format(4f, 1f));
        assertEquals("4.2", Slider.format(4.2f, 0.1f));
    }

    @Test void dragMapsMouseToValue() {
        float[] last = { -1 };
        Slider s = new Slider(0, 0, 100, 0).showValue(false).onChange(v -> last[0] = v);
        s.layout(0, 0, 100, 16);                 // track spans full width (no value label)
        s.mouseClicked(0, 8, 0);                  // grab at left → ~min
        s.mouseDragged(50, 8, 0, 50, 0);          // drag to mid → ~50
        assertEquals(50f, s.value(), 1.5f);
        assertEquals(s.value(), last[0], 1e-6f);
    }

    @Test void arrowKeysStep() {
        Slider s = new Slider(5, 0, 10, 1).showValue(false);
        s.layout(0, 0, 100, 16);
        assertTrue(s.keyPressed(GLFW_KEY_RIGHT, 0, 0));
        assertEquals(6f, s.value(), 1e-6f);
        s.keyPressed(GLFW_KEY_LEFT, 0, 0);
        assertEquals(5f, s.value(), 1e-6f);
    }
}
