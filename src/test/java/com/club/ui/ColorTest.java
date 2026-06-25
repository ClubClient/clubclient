package com.club.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorTest {
    @Test void packsArgb() { assertEquals(0xFF112233, Color.rgba(0x11, 0x22, 0x33, 0xFF)); }
    @Test void withAlphaReplaces() { assertEquals(0x80112233, Color.withAlpha(0xFF112233, 0x80)); }
    @Test void scaleAlphaHalves() { assertEquals(0x80, (Color.scaleAlpha(0xFF112233, 0.5f) >>> 24) & 0xFF); }
    @Test void lerpMidpoint() { assertEquals(0xFF808080, Color.lerp(0xFF000000, 0xFFFFFFFF, 0.5f) & 0xFFFFFFFF | 0xFF000000); }
    @Test void floatComponents() { assertEquals(1.0f, Color.af(0xFF000000), 1e-6); }
}
