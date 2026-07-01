package com.club.ui.motion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RevealTest {

    @Test void playsInFromZero() {
        Reveal r = new Reveal(0.2f, Curves.LINEAR, 0f);
        assertEquals(0f, r.progress(0f), 1e-4);
        assertEquals(0.5f, r.progress(0.1f), 1e-4);
        assertEquals(1f, r.progress(0.2f), 1e-4);
        assertFalse(r.closing());
        assertFalse(r.gone(0.2f), "an open reveal is never gone");
    }

    @Test void closePlaysOutThenReportsGone() {
        Reveal r = new Reveal(0.2f, Curves.LINEAR, 0f);
        r.progress(0.2f);               // fully open
        r.close(0.2f);
        assertTrue(r.closing());
        assertEquals(1f, r.progress(0.2f), 1e-4, "starts closing from the current (open) value");
        assertEquals(0.5f, r.progress(0.3f), 1e-4);
        assertFalse(r.gone(0.3f), "still collapsing");
        assertEquals(0f, r.progress(0.4f), 1e-4);
        assertTrue(r.gone(0.4f), "fully collapsed → safe to drop");
    }

    @Test void closeIsIdempotent() {
        Reveal r = new Reveal(0.2f, Curves.LINEAR, 0f);
        r.progress(0.2f);
        r.close(0.2f);
        r.close(0.25f);                 // second call must not re-base the out-play
        r.close(0.3f);
        assertEquals(0f, r.progress(0.4f), 1e-4);
        assertTrue(r.gone(0.4f));
    }

    @Test void closingUsesValueNotAnimatingFlag() {
        // Regression: an element opened and shown, then closed, must play out fully — gone() is
        // driven by value<=EPS, not Transition.animating() (which flips false the moment from==to).
        Reveal r = new Reveal(0.2f, Curves.DECELERATE, 0f);
        assertEquals(1f, r.progress(0.2f), 1e-4);
        r.close(0.2f);
        assertFalse(r.gone(0.25f), "must remain until the out-play collapses");
        assertTrue(r.gone(0.45f));
    }
}
