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

    // ---- no-overlap constraint (owner, v0.1.3) ----
    @Test void overlapsDetectsIntersectionButNotTouching() {
        int[] box = {100, 100, 40, 20};
        assertTrue(HudSnap.overlaps(120, 110, 40, 20, box));    // clearly inside
        assertFalse(HudSnap.overlaps(140, 100, 40, 20, box));   // left edge touches right edge → not overlap
        assertFalse(HudSnap.overlaps(100, 120, 40, 20, box));   // top touches bottom → not overlap
        assertFalse(HudSnap.overlaps(200, 200, 40, 20, box));   // far away
    }

    @Test void avoidOverlapPushesOutOnLeastPenetrationAxis() {
        int[][] others = {{100, 100, 40, 40}};
        // Small horizontal overlap (10px) vs large vertical overlap → push horizontally, to the left edge.
        int[] r = HudSnap.avoidOverlap(90, 100, 40, 40, others);   // box right (130) is 30 into the neighbour...
        assertFalse(HudSnap.overlaps(r[0], r[1], 40, 40, others[0]));
        assertEquals(60, r[0]);   // pushed to ox - w = 100 - 40
        assertEquals(100, r[1]);  // untouched axis
    }

    @Test void avoidOverlapPushesVerticallyWhenThatPenetratesLess() {
        int[][] others = {{100, 100, 40, 40}};
        // Deep horizontal overlap, shallow vertical → push up to oy - h.
        int[] r = HudSnap.avoidOverlap(105, 75, 40, 40, others);
        assertFalse(HudSnap.overlaps(r[0], r[1], 40, 40, others[0]));
        assertEquals(60, r[1]);   // oy - h = 100 - 40
    }

    @Test void avoidOverlapSettlesAgainstTwoNeighbours() {
        int[][] others = {{100, 100, 40, 40}, {60, 100, 40, 40}};   // two side-by-side boxes
        int[] r = HudSnap.avoidOverlap(80, 100, 40, 40, others);    // dropped between them
        assertFalse(HudSnap.overlapsAny(r[0], r[1], 40, 40, others));
    }

    @Test void avoidOverlapLeavesAClearBoxUntouched() {
        int[][] others = {{100, 100, 40, 40}};
        int[] r = HudSnap.avoidOverlap(200, 200, 40, 40, others);
        assertEquals(200, r[0]);
        assertEquals(200, r[1]);
    }
}
