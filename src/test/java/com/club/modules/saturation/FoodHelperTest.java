package com.club.modules.saturation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The natural-regen estimate is a fact about vanilla, not about us — so it is pinned here, off-thread, the
 * way the shulker grid size and the clip arithmetic are. {@link FoodHelper#estimatedHealthIncrement} is the
 * one piece of the Saturation module that is pure math (the rest reads live components or draws), which is
 * exactly why it is the piece a test can guard: the ghost-hearts preview is only as honest as this number.
 */
class FoodHelperTest {

    private static final float EPS = 1e-4f;

    /** Below the regen floor (food &lt; 18) nothing regenerates, so nothing is previewed. */
    @Test
    void noRegenBelow18() {
        assertEquals(0f, FoodHelper.estimatedHealthIncrement(17, 17f, 0f, 10f, 20f), EPS);
        assertEquals(0f, FoodHelper.estimatedHealthIncrement(0, 0f, 0f, 1f, 20f), EPS);
    }

    /** A full health bar has nothing to regain, whatever the food state. */
    @Test
    void nothingToHealWhenFull() {
        assertEquals(0f, FoodHelper.estimatedHealthIncrement(20, 20f, 0f, 20f, 20f), EPS);
    }

    /** The estimate never exceeds the missing-health gap. */
    @Test
    void clampedToTheGap() {
        float gap = 2f;
        float est = FoodHelper.estimatedHealthIncrement(20, 20f, 0f, 18f, 20f);
        assertTrue(est <= gap + EPS, "estimate " + est + " must not exceed the 2 HP gap");
    }

    /** Saturation regen (food 20, saturation present) heals; more saturation never heals less. */
    @Test
    void saturationRegenIsMonotonic() {
        float low = FoodHelper.estimatedHealthIncrement(20, 6f, 0f, 0f, 20f);
        float high = FoodHelper.estimatedHealthIncrement(20, 18f, 0f, 0f, 20f);
        assertTrue(low > 0f, "some healing with saturation present");
        assertTrue(high >= low - EPS, "more saturation heals at least as much: " + high + " vs " + low);
    }

    /** Food regen (18..19, no saturation) still previews healing — the food path, not the saturation path. */
    @Test
    void foodRegenWithoutSaturation() {
        float est = FoodHelper.estimatedHealthIncrement(18, 0f, 0f, 0f, 20f);
        assertTrue(est > 0f, "food>=18 regenerates even with zero saturation");
    }

    /** The estimate is never negative — a guard against the loop math underflowing. */
    @Test
    void neverNegative() {
        for (int food = 0; food <= 20; food++) {
            for (float sat = 0; sat <= 20; sat += 2.5f) {
                float est = FoodHelper.estimatedHealthIncrement(food, sat, 0f, 5f, 20f);
                assertTrue(est >= 0f, "food=" + food + " sat=" + sat + " gave " + est);
            }
        }
    }
}
