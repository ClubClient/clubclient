package com.club.ui.component.widget;

import com.club.ui.Axis;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DividerTest {
    @Test void horizontalMeasuresThicknessOnCrossAxis() {
        Size s = new Divider().measure(100, 100);          // default HORIZONTAL
        assertEquals(0f, s.w(), 1e-6);
        assertEquals(Tokens.border().thickness(), s.h(), 1e-6);
    }
    @Test void verticalMeasuresThicknessOnWidth() {
        Size s = new Divider(Axis.VERTICAL).measure(100, 100);
        assertEquals(Tokens.border().thickness(), s.w(), 1e-6);
        assertEquals(0f, s.h(), 1e-6);
    }
}
