package com.club.ui.layout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayoutValueTypesTest {
    @Test void insetsAllAndSymmetric() {
        Insets a = Insets.all(4);
        assertEquals(4, a.top()); assertEquals(4, a.left()); assertEquals(8, a.horizontal()); assertEquals(8, a.vertical());
        Insets s = Insets.symmetric(10, 6); // h=10, v=6
        assertEquals(6, s.top()); assertEquals(10, s.right()); assertEquals(6, s.bottom()); assertEquals(10, s.left());
        assertEquals(0, Insets.ZERO.horizontal());
    }
    @Test void sizingVariants() {
        assertTrue(Sizing.fixed() instanceof Sizing.Fixed);
        assertTrue(Sizing.fill()  instanceof Sizing.Fill);
        Sizing w = Sizing.weight(2.5f);
        assertTrue(w instanceof Sizing.Weight);
        assertEquals(2.5f, ((Sizing.Weight) w).value());
    }
    @Test void sizeFields() { Size sz = new Size(3, 7); assertEquals(3, sz.w()); assertEquals(7, sz.h()); }
}
