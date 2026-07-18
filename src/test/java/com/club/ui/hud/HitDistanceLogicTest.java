package com.club.ui.hud;

import com.club.hud.HitDistanceTracker;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Hit Distance readout's pure pieces: the "d.dd" number formatting, the eye→nearest-hitbox-point
 * distance (the reach of the hit), and the 10-second visibility window.
 */
class HitDistanceLogicTest {

    // --- number formatting: two decimals, ROOT locale (a dot, never a comma) ---
    @Test void numberTwoDecimals() {
        assertEquals("2.34", HitDistanceElement.numberText(2.34));
    }
    @Test void numberRootLocaleUsesDot() {
        assertEquals("4.20", HitDistanceElement.numberText(4.2));
    }
    @Test void numberZero() {
        assertEquals("0.00", HitDistanceElement.numberText(0));
    }

    // --- nearest-hitbox distance: component-wise clamp of the eye into the box ---
    @Test void nearestStraightAhead() {                 // box 2 blocks ahead on X, eye level with it
        double d = HitDistanceTracker.nearestDistance(new Vec3d(0, 0.5, 0.5), new Box(2, 0, 0, 3, 1, 1));
        assertEquals(2.0, d, 1e-9);
    }
    @Test void nearestIsZeroWhenEyeInsideBox() {
        double d = HitDistanceTracker.nearestDistance(new Vec3d(0.5, 0.5, 0.5), new Box(0, 0, 0, 1, 1, 1));
        assertEquals(0.0, d, 1e-9);
    }
    @Test void nearestDiagonal() {                       // clamps on X and Z, level on Y → sqrt(3^2+3^2)
        double d = HitDistanceTracker.nearestDistance(new Vec3d(0, 3.5, 0), new Box(3, 3, 3, 4, 4, 4));
        assertEquals(Math.sqrt(18), d, 1e-9);
    }

    // --- visibility window: live for 10 s after the hit, hidden before any hit ---
    @Test void withinRightAfterHit() {
        assertTrue(HitDistanceTracker.within(1_000_000, 1_000_000));
    }
    @Test void withinJustBeforeTenSeconds() {
        assertTrue(HitDistanceTracker.within(1_000_000 + 9_999, 1_000_000));
    }
    @Test void notWithinAtTenSeconds() {
        assertFalse(HitDistanceTracker.within(1_000_000 + 10_000, 1_000_000));
    }
    @Test void notWithinBeforeAnyHit() {                 // lastHitAt == 0 → never shown
        assertFalse(HitDistanceTracker.within(1_000_000, 0));
    }
}
