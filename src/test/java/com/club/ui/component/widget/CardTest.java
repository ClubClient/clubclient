package com.club.ui.component.widget;

import com.club.ui.layout.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardTest {
    static final class Box extends com.club.ui.component.Component {
        final float pw, ph; Box(float w, float h){ pw=w; ph=h; }
        @Override public Size measure(float a, float b){ return new Size(pw, ph); }
        @Override public void render(com.club.ui.UiContext c){}
    }

    @Test void stacksHeaderContentFooterTopToBottom() {
        Box header = new Box(50, 10), content = new Box(50, 30), footer = new Box(50, 8);
        Card card = new Card(content).header(header).footer(footer).padding(Insets.ZERO);
        card.layout(0, 0, 50, 48);
        assertTrue(header.yTop() < content.yTop());
        assertTrue(content.yTop() < footer.yTop());
    }

    @Test void contentOnlyCardLaysOut() {
        Box content = new Box(50, 30);
        Card card = new Card(content).padding(Insets.ZERO);
        card.layout(0, 0, 50, 30);
        assertEquals(0f, content.yTop(), 1e-6);
    }
}
