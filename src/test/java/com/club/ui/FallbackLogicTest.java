package com.club.ui;

import com.club.ui.text.Charset;
import com.club.ui.text.MsdfMetrics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FallbackLogicTest {
    @Test void missingGlyphIsNullNotCrash() {
        MsdfMetrics m = MsdfMetrics.parse(MsdfMetricsTest.JSON);
        assertNull(m.get(0x4E2D));               // CJK 中 absent
        assertEquals(0f, m.width("中", 16f), 1e-6);
    }
    @Test void charsetExcludesOutOfRange() {
        assertFalse(Charset.contains(0x4E2D));
        assertTrue(Charset.contains(0x0410));    // Cyrillic А
    }
}
