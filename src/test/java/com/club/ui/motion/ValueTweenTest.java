package com.club.ui.motion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValueTweenTest {

    @Test void easesTowardTarget() {
        ValueTween v = new ValueTween(0f, 0.2f, Curves.LINEAR);
        v.set(1f, 0f);
        assertEquals(0f, v.get(0f), 1e-4);
        assertEquals(0.5f, v.get(0.1f), 1e-4);
        assertEquals(1f, v.get(0.2f), 1e-4);
        assertTrue(v.animating(0.1f));
        assertFalse(v.animating(0.3f));
    }

    @Test void snapJumpsInstantly() {
        ValueTween v = new ValueTween(0f, 0.2f, Curves.LINEAR);
        v.set(1f, 0f);
        v.snap(0.5f, 0.1f);                 // subject changed mid-tween
        assertEquals(0.5f, v.get(0.1f), 1e-4, "snap is instant, no sweep");
        assertFalse(v.animating(0.1f));
        // a fresh set after snap eases from the snapped value
        v.set(1f, 0.1f);
        assertEquals(0.75f, v.get(0.2f), 1e-4);
    }

    @Test void retargetMidTweenStartsFromCurrent() {
        ValueTween v = new ValueTween(0f, 0.2f, Curves.LINEAR);
        v.set(1f, 0f);
        v.set(0f, 0.1f);                    // reverse from current 0.5
        assertEquals(0.5f, v.get(0.1f), 1e-4);
        assertEquals(0f, v.get(0.3f), 1e-4);
    }
}
