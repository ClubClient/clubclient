package com.club.ui.menu;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the card reflow state machine (Stage 21 choreography) that survived six rounds of fixes —
 * extracted from {@code ClubMenuScreen} in Stage 28 precisely so it could be pinned here.
 */
class TileMotionTest {

    // ---- factories: the three enter languages ----

    @Test void openingIsShownAtOnceRidingTheWindowFade() {
        TileMotion tm = TileMotion.opening();
        assertTrue(tm.shown);
        assertFalse(tm.driftIn);
        assertTrue(tm.scaleIn);
        assertEquals(1f, tm.fadeValue(0f), 1e-4);      // fade sits at 1 from the first frame
        assertEquals(1f, tm.tick(1f), 1e-4);
    }

    @Test void categoryEnterIsFadeOnlyRisingAndStaggered() {
        TileMotion a = TileMotion.categoryEnter(0);
        TileMotion b = TileMotion.categoryEnter(3);
        assertFalse(a.scaleIn, "category cascade is fade-only (no popcorn scale)");
        assertTrue(a.driftIn, "category cards RISE into place");
        assertEquals(0f, a.showDelay, 1e-6);
        assertEquals(3 * TileMotion.CAT_STAGGER, b.showDelay, 1e-6);
        assertTrue(a.driftY(0f) > 0f);                 // drift present at fade 0
        assertEquals(0f, a.driftY(1f), 1e-6);          // gone once landed
        assertEquals(0f, TileMotion.opening().driftY(0f), 1e-6, "open/search never drift");
    }

    @Test void scaleRidesFadeForSearchButNotCategory() {
        TileMotion s = TileMotion.searchEnter();
        assertEquals(TileMotion.TILE_SCALE_FROM, s.scale(0f), 1e-6);
        assertEquals(1f, s.scale(1f), 1e-6);
        TileMotion c = TileMotion.categoryEnter(0);
        assertEquals(1f, c.scale(0f), 1e-6, "category is fade-only → no scale");
    }

    // ---- lazy showAt: a rebuild can run before the ui clock ticks ----

    @Test void lazyShowAtResolvesAgainstTheFirstClock() {
        TileMotion tm = TileMotion.categoryEnter(2);   // showDelay = 2 * CAT_STAGGER
        float t0 = 100f;
        assertEquals(0f, tm.tick(t0), 1e-4, "pre-delay → invisible, showAt just bound");
        assertFalse(tm.shown);
        assertEquals(0f, tm.tick(t0 + TileMotion.CAT_STAGGER), 1e-4, "still before the gate");
        assertFalse(tm.shown);
        float after = t0 + 2 * TileMotion.CAT_STAGGER + 1e-3f;
        tm.tick(after);
        assertTrue(tm.shown, "gate passed → shown, fade starts");
        float mid = tm.tick(after + TileMotion.ENTER_DUR * 0.5f);
        assertTrue(mid > 0f && mid < 1f, "fade climbing after the gate");
        assertEquals(1f, tm.tick(after + TileMotion.ENTER_DUR + 1e-3f), 1e-3);
    }

    // ---- exit cascade: recedes FROM THE TAIL ----

    @Test void exitCascadeRecedesFromTail() {
        float now = 50f;
        TileMotion e0 = shown(now), e1 = shown(now), e2 = shown(now);
        e0.beginExit(now); e1.beginExit(now); e2.beginExit(now);
        e0.scheduleHideFromTail(now, 0, 3);
        e1.scheduleHideFromTail(now, 1, 3);
        e2.scheduleHideFromTail(now, 2, 3);
        assertEquals(now + 2 * TileMotion.EXIT_STAGGER, e0.hideAt, 1e-5, "head waits longest");
        assertEquals(now + TileMotion.EXIT_STAGGER,     e1.hideAt, 1e-5);
        assertEquals(now,                               e2.hideAt, 1e-5, "tail dissolves first");
        assertTrue(e2.hideAt < e1.hideAt && e1.hideAt < e0.hideAt);
        for (TileMotion e : new TileMotion[]{e0, e1, e2}) { assertTrue(e.leaving); assertTrue(e.scaleIn); }
    }

    @Test void hideGateDrainsFadeToZeroForPrune() {
        float now = 10f;
        TileMotion tm = shown(now);
        tm.beginExit(now);
        tm.scheduleHideFromTail(now, 0, 1);            // exitCount 1 → hideAt = now, fires immediately
        assertEquals(1f, tm.tick(now), 1e-3, "gate fires, fade just starting to drain");
        assertTrue(tm.fadeValue(now + TileMotion.EXIT_DUR + 1e-3f) <= 0.001f, "dissolve done → prune");
    }

    // ---- reversal mid-exit: turn around, no restart flash ----

    @Test void reverseMidExitTurnsAroundSeededFromCurrentFade() {
        float now = 10f;
        TileMotion tm = shown(now);
        tm.beginExit(now);
        tm.scheduleHideFromTail(now, 0, 1);
        float gate = now + TileMotion.EXIT_DUR * 0.4f;
        tm.tick(gate);                                 // hide gate fires here (fade.target(0) starts)
        float mid = gate + TileMotion.EXIT_DUR * 0.3f; // sample partway into the actual drain
        float draining = tm.fadeValue(mid);
        assertTrue(draining > 0f && draining < 1f, "caught mid-dissolve");
        tm.reverseToEnter(mid);
        assertFalse(tm.leaving);
        assertTrue(tm.shown);
        assertEquals(-1f, tm.hideAt, 1e-6);
        assertEquals(draining, tm.fadeValue(mid), 1e-3, "seeded from current fade — no jump/flash");
        assertEquals(1f, tm.fadeValue(mid + TileMotion.ENTER_DUR + 1e-3f), 1e-3, "climbs back to shown");
    }

    // ---- survivor move: deferred until the +MOVE_DELAY gate ----

    @Test void firstSightingSnapsNeverFliesIn() {
        TileMotion tm = TileMotion.searchEnter();
        tm.place(10f, 30f, 20f, 100f, 50f);
        assertTrue(tm.hasPos);
        assertEquals(30f, tm.bx, 1e-4, "lands on target, no fly-in from 0");
        assertEquals(20f, tm.by, 1e-4);
    }

    @Test void survivorMoveDefersUntilTheGate() {
        float now = 10f;
        TileMotion tm = TileMotion.searchEnter();
        tm.place(now, 0f, 0f, 100f, 50f);              // first sighting at slot A
        tm.scheduleMove(now);
        assertTrue(tm.movePending);
        assertEquals(now + TileMotion.MOVE_DELAY, tm.moveAt, 1e-6);
        float before = now + TileMotion.MOVE_DELAY * 0.5f;
        tm.place(before, 40f, 0f, 100f, 50f);
        assertTrue(tm.movePending, "still pending before the gate");
        assertEquals(0f, tm.tx, 1e-6, "target not re-aimed yet");
        float afterGate = now + TileMotion.MOVE_DELAY + 1e-3f;
        tm.place(afterGate, 40f, 0f, 100f, 50f);
        assertFalse(tm.movePending);
        assertEquals(40f, tm.tx, 1e-6, "re-aimed to the new slot once the gate opened");
    }

    @Test void nonPendingSlotChangeReaimsImmediately() {
        float now = 10f;
        TileMotion tm = TileMotion.searchEnter();
        tm.place(now, 0f, 0f, 100f, 50f);
        tm.place(now + 0.01f, 25f, 0f, 100f, 50f);     // no scheduleMove → re-aim at once
        assertEquals(25f, tm.tx, 1e-6);
    }

    /** A tile already shown at full fade (open language) and positioned — the base for exit tests. */
    private static TileMotion shown(float now) {
        TileMotion tm = TileMotion.opening();
        tm.place(now, 0f, 0f, 100f, 50f);
        return tm;
    }
}
