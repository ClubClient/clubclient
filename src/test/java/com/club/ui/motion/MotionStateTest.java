package com.club.ui.motion;

import com.club.ui.theme.Tokens;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotionStateTest {

    private static float fast()   { return Tokens.motion().durations().fast(); }
    private static float normal() { return Tokens.motion().durations().normal(); }

    @Test void defaultsAreRestedAndEnabled() {
        MotionState ms = new MotionState();
        assertEquals(0f, ms.hover(0f), 1e-4);
        assertEquals(0f, ms.press(0f), 1e-4);
        assertEquals(0f, ms.focus(0f), 1e-4);
        assertEquals(1f, ms.enabled(0f), 1e-4, "starts fully enabled");
        assertEquals(0f, ms.disabled(0f), 1e-4);
    }

    @Test void hoverChannelAnimatesInAndOut() {
        MotionState ms = new MotionState();
        ms.update(true, false, false, true, 0f);
        assertEquals(0f, ms.hover(0f), 1e-4);
        assertEquals(1f, ms.hover(fast()), 1e-4, "settles to 1 after the fast duration");
        assertTrue(ms.hover(fast() * 0.5f) > 0f && ms.hover(fast() * 0.5f) < 1f, "mid-flight in (0,1)");
        // hover out
        ms.update(false, false, false, true, fast());
        assertEquals(1f, ms.hover(fast()), 1e-4);
        assertEquals(0f, ms.hover(fast() * 2f), 1e-4);
    }

    @Test void pressAndFocusChannelsTrackFlags() {
        MotionState ms = new MotionState();
        ms.update(false, true, true, true, 0f);
        assertEquals(1f, ms.press(fast()), 1e-4);
        assertEquals(1f, ms.focus(fast()), 1e-4);
    }

    @Test void disabledEasesInOnEnabledFalse() {
        MotionState ms = new MotionState();
        ms.update(false, false, false, false, 0f);
        assertEquals(0f, ms.disabled(0f), 1e-4);
        assertEquals(1f, ms.disabled(normal()), 1e-4, "fully disabled after the normal duration");
        assertEquals(0f, ms.enabled(normal()), 1e-4);
    }

    @Test void animatingReportsAnyActiveChannel() {
        MotionState ms = new MotionState();
        assertFalse(ms.animating(0f), "rested at construction");
        ms.update(true, false, false, true, 0f);
        assertTrue(ms.animating(fast() * 0.5f));
        assertFalse(ms.animating(fast() * 2f), "settled");
    }
}
