package com.club.ui.layout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpacerTest {
    @Test void fixedCarriesLength() {
        Spacer s = Spacer.fixed(8);
        assertEquals(8, s.length());
        assertTrue(s.sizing() instanceof Sizing.Fixed);
    }
    @Test void fillAndWeight() {
        assertTrue(Spacer.fill().sizing() instanceof Sizing.Fill);
        Spacer w = Spacer.weight(3);
        assertTrue(w.sizing() instanceof Sizing.Weight);
        assertEquals(3f, ((Sizing.Weight) w.sizing()).value());
        assertEquals(0, w.length());
    }
    @Test void measureIsZero() { assertEquals(0, Spacer.fixed(8).measure(100, 100).w()); }
}
