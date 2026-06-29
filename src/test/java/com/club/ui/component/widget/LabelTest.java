package com.club.ui.component.widget;

import com.club.ui.text.Align;
import com.club.ui.theme.Tokens;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LabelTest {
    @Test void buildersAreFluentAndStoreState() {
        Label l = new Label("Hi").align(Align.CENTER).color(0xFF112233);
        assertSame(l, l.text("Bye"));           // fluent returns this
        assertEquals("Bye", l.textValue());
        assertEquals(Align.CENTER, l.alignValue());
        assertEquals(0xFF112233, l.colorValue());
    }

    @Test void defaultRoleIsBodyAndDefaultColorIsTextHi() {
        Label l = new Label("X");
        assertEquals(Tokens.type().body().size(), l.role().size(), 1e-6);
        assertEquals(Tokens.palette().textHi(), l.colorValue());   // default = textHi until color() set
    }
}
