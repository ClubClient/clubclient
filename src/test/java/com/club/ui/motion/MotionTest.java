package com.club.ui.motion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotionTest {
    @Test void curvesHitEndpoints() {
        for (Easing e : new Easing[]{Curves.LINEAR, Curves.STANDARD, Curves.DECELERATE, Curves.ACCELERATE}) {
            assertEquals(0f, e.apply(0f), 1e-5);
            assertEquals(1f, e.apply(1f), 1e-5);
            assertTrue(e.apply(0.49f) <= e.apply(0.51f), "easing must be monotonic non-decreasing");
            assertEquals(0f, e.apply(-1f), 1e-5, "input below 0 clamped to 0");
            assertEquals(1f, e.apply(2f), 1e-5, "input above 1 clamped to 1");
        }
    }
    @Test void transitionInterpolatesAndSettles() {
        Transition tr = new Transition(0f, 0.2f, Curves.LINEAR);
        tr.target(1f, 0f);
        assertEquals(0f, tr.value(0f), 1e-5);
        assertEquals(0.5f, tr.value(0.1f), 1e-5);
        assertEquals(1f, tr.value(0.2f), 1e-5);
        assertEquals(1f, tr.value(5f), 1e-5);
        assertTrue(tr.animating(0.1f));
        assertFalse(tr.animating(0.25f));
    }
    @Test void retargetMidFlightStartsFromCurrent() {
        Transition tr = new Transition(0f, 0.2f, Curves.LINEAR);
        tr.target(1f, 0f);
        tr.target(0f, 0.1f);                 // reverse from current 0.5
        assertEquals(0.5f, tr.value(0.1f), 1e-5);
        assertEquals(0f, tr.value(0.3f), 1e-5);
    }
    @Test void zeroDurationIsInstant() {
        Transition tr = new Transition(0f, 0f, Curves.LINEAR);
        tr.target(1f, 0f);
        assertEquals(1f, tr.value(0f), 1e-5);
        assertFalse(tr.animating(0f));
    }
}
