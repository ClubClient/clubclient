package com.club.ui.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudSnapTest {
    @Test void snapsToLeftMarginWithinThreshold() {
        HudSnap.Snap s = HudSnap.snapAxis(7, 50, 400);   // |7-4|=3 <= 6
        assertEquals(4, s.pos());
        assertEquals(4, s.guide());                       // guide at MARGIN
    }
    @Test void snapsToCenter() {
        HudSnap.Snap s = HudSnap.snapAxis(176, 50, 400);  // centered target = (400-50)/2 = 175
        assertEquals(175, s.pos());
        assertEquals(200, s.guide());                     // guide at screen/2
    }
    @Test void snapsToRightMargin() {
        HudSnap.Snap s = HudSnap.snapAxis(345, 50, 400);  // far target = 400-50-4 = 346
        assertEquals(346, s.pos());
        assertEquals(396, s.guide());                     // guide at screen-MARGIN
    }
    @Test void noSnapWhenFar() {
        HudSnap.Snap s = HudSnap.snapAxis(100, 50, 400);
        assertEquals(100, s.pos());
        assertEquals(HudSnap.NO_GUIDE, s.guide());
    }
    @Test void gridSnapRoundsToNearestStep() {
        assertEquals(16, HudSnap.snapToGrid(14, 8));
        assertEquals(16, HudSnap.snapToGrid(19, 8));
        assertEquals(-8, HudSnap.snapToGrid(-5, 8));
        assertEquals(13, HudSnap.snapToGrid(13, 0));      // step<=0 → unchanged
    }
    @Test void clampKeepsElementOnScreen() {
        assertEquals(0,   HudSnap.clampAxis(-10, 50, 400));
        assertEquals(350, HudSnap.clampAxis(999, 50, 400)); // screen-size
        assertEquals(100, HudSnap.clampAxis(100, 50, 400));
    }
}
