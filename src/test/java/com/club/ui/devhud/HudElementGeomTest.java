package com.club.ui.devhud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudElementGeomTest {
    @Test void scaledBoxAppliesScaleAndKeepsOrigin() {
        int[] b = HudElement.scaledBox(30, 40, 100, 20, 1.5f);
        assertArrayEquals(new int[]{30, 40, 150, 30}, b);
    }
    @Test void scaledBoxFloorsToEightPx() {
        int[] b = HudElement.scaledBox(0, 0, 2, 2, 0.5f);   // 2*0.5=1 → floored to 8
        assertArrayEquals(new int[]{0, 0, 8, 8}, b);
    }
    @Test void scaledBoxRoundsHalfUp() {
        int[] b = HudElement.scaledBox(0, 0, 15, 15, 1.1f); // 16.5 → 17 (Math.round)
        assertArrayEquals(new int[]{0, 0, 17, 17}, b);
    }
}
