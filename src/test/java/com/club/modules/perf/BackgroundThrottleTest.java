package com.club.modules.perf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two traps the audit found in this feature, and the reason the decision is a pure function: both of
 * them are invisible in a game and obvious in a test.
 */
class BackgroundThrottleTest {

    @BeforeEach void fresh() { BackgroundThrottle.reset(); }

    /** The feature ON, with the default 15 fps cap. */
    private static int lim(int vanilla, boolean focused, long now) {
        return BackgroundThrottle.limit(vanilla, focused, now, true, 15);
    }

    @Test void aPlayerWhoCappedAtTenIsNeverRaisedToFifteen() {
        // THE ONE ITEM IN THE WHOLE PLAN THAT COULD MAKE SOMEONE'S MACHINE WORSE. Vanilla's Max Framerate
        // slider bottoms out at 10 (Codec.intRange(10, 260)) — below our 15. "Set the cap to 15 when
        // unfocused" would RAISE the limit of a player who deliberately chose 10. A performance mod does not
        // get to increase the work the user asked for.
        long t = 1_000_000;
        lim(10, false, t);                       // focus lost
        assertEquals(10, lim(10, false, t + 5_000),
                "the throttle may only ever TIGHTEN a limit, never raise it");
    }

    @Test void anUncappedGameIsThrottledToTheBackgroundCap() {
        long t = 1_000_000;
        lim(260, false, t);
        assertEquals(15, lim(260, false, t + 5_000));
    }

    @Test void theCapIsNeverBelowTheFloorHoweverTheConfigIsEdited() {
        // 1 fps in the background means the first frame after alt-tabbing back costs a whole second, because
        // limitDisplayFPS wakes on input and then goes straight back to sleep until the frame deadline.
        long t = 1_000_000;
        BackgroundThrottle.limit(260, false, t, true, 1);
        assertEquals(BackgroundThrottle.FLOOR, BackgroundThrottle.limit(260, false, t + 5_000, true, 1));
    }

    @Test void alettTabPassingThroughWindowsDoesNotOscillateTheCap() {
        // isWindowFocused() is a raw GLFW edge with no debounce. Without hysteresis the cap would flap while
        // the player is still deciding where they are going.
        long t = 1_000_000;
        assertEquals(260, lim(260, false, t), "the first unfocused frame must not throttle");
        assertEquals(260, lim(260, false, t + BackgroundThrottle.HYSTERESIS_MS - 1),
                "…nor any frame inside the hysteresis window");
        assertEquals(15, lim(260, false, t + BackgroundThrottle.HYSTERESIS_MS + 1),
                "…and it must engage once the window has really been left behind");
    }

    @Test void comingBackReleasesInstantly() {
        long t = 1_000_000;
        lim(260, false, t);
        assertEquals(15, lim(260, false, t + 5_000));
        assertEquals(260, lim(260, true, t + 5_001),
                "release is immediate — a player alt-tabbing back must not wait out a hysteresis window");
        // …and the clock restarts, so a quick out-and-back does not throttle at once
        assertEquals(260, lim(260, false, t + 5_002));
    }

    @Test void switchedOffItDoesNothingAtAll() {
        long t = 1_000_000;
        BackgroundThrottle.limit(260, false, t, false, 15);
        assertEquals(260, BackgroundThrottle.limit(260, false, t + 60_000, false, 15));
    }
}
