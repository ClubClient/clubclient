package com.club.ui.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Hit Distance readout's two decisions, as pure functions: the green/red hysteresis latch and the
 * "d.dd blocks" formatting. Green means "within reach" (<= 3.0 blocks); the deadband (±0.15 around 3.0)
 * stops a target hovering right at reach from flashing green/red frame to frame.
 */
class HitDistanceLogicTest {

    // --- acquire (rising edge): seed the verdict straight from the distance, no stale carry ---
    @Test void acquireInReachIsGreen() {
        assertTrue(HitDistanceElement.greenLatch(false, false, 2.0));
    }
    @Test void acquireOutOfReachIsRed() {
        assertFalse(HitDistanceElement.greenLatch(true, false, 4.0));
    }
    @Test void acquireExactlyAtReachIsGreen() {   // <= 3.0 is "you can reach"
        assertTrue(HitDistanceElement.greenLatch(false, false, 3.0));
    }

    // --- hysteresis: inside the deadband the previous verdict is held ---
    @Test void greenHeldJustPastReach() {         // 3.10 is >3.0 but < 3.15 → stays green
        assertTrue(HitDistanceElement.greenLatch(true, true, 3.10));
    }
    @Test void greenFlipsRedPastTheBand() {       // 3.20 >= 3.15 → red
        assertFalse(HitDistanceElement.greenLatch(true, true, 3.20));
    }
    @Test void redHeldJustInsideReach() {         // 2.90 is <3.0 but > 2.85 → stays red
        assertFalse(HitDistanceElement.greenLatch(false, true, 2.90));
    }
    @Test void redFlipsGreenBelowTheBand() {      // 2.80 <= 2.85 → green
        assertTrue(HitDistanceElement.greenLatch(false, true, 2.80));
    }

    // --- formatting: always two decimals, ROOT locale (a '.' decimal, never a comma) ---
    @Test void readoutTwoDecimals() {
        assertEquals("2.34 blocks", HitDistanceElement.readout(2.34));
    }
    @Test void readoutZeroIsTheNeutralText() {
        assertEquals("0.00 blocks", HitDistanceElement.readout(0));
    }
    @Test void readoutRootLocaleUsesDot() {
        assertEquals("4.20 blocks", HitDistanceElement.readout(4.2));
    }
}
