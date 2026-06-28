package com.club.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorTest {
    @Test void packsArgb() { assertEquals(0xFF112233, Color.rgba(0x11, 0x22, 0x33, 0xFF)); }
    @Test void withAlphaReplaces() { assertEquals(0x80112233, Color.withAlpha(0xFF112233, 0x80)); }
    @Test void scaleAlphaHalves() { assertEquals(0x80, (Color.scaleAlpha(0xFF112233, 0.5f) >>> 24) & 0xFF); }
    @Test void lerpMidpoint() {
        // Lerp two fully-opaque mid-grey colors (unchanged baseline)
        assertEquals(0xFF808080, Color.lerp(0xFF000000, 0xFFFFFFFF, 0.5f) & 0xFFFFFFFF | 0xFF000000);
        // Lerp colors with DIFFERENT alphas: 0x00FF0000 (a=0x00,r=0xFF,g=0,b=0)
        //                               and  0xFF0000FF (a=0xFF,r=0x00,g=0,b=0xFF) at t=0.5
        // Expected per-channel: a=round(127.5)=128=0x80, r=round(127.5)=128=0x80, g=0, b=round(127.5)=128=0x80
        // rgba(r=0x80,g=0x00,b=0x80,a=0x80) = 0x80800080 — test raw so an alpha-lerp regression is caught (risk R5)
        int result = Color.lerp(0x00FF0000, 0xFF0000FF, 0.5f);
        assertEquals(0x80800080, result,
            "alpha must be interpolated; raw packed = 0x" + Integer.toHexString(result));
    }
    @Test void floatComponents() { assertEquals(1.0f, Color.af(0xFF000000), 1e-6); }
}
