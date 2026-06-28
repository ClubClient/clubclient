package com.club.ui.layout;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StackTest {
    static class Tile extends Component {
        final float dw, dh;
        Tile(float dw, float dh) { this.dw = dw; this.dh = dh; }
        @Override public Size measure(float aw, float ah) { return new Size(dw, dh); }
        @Override public void render(UiContext ctx) { }
    }
    @Test void anchorsPlaceChildren() {
        Stack st = new Stack();
        Tile tl = new Tile(10, 10), center = new Tile(20, 20), br = new Tile(10, 10);
        st.add(tl, Anchor.TOP_LEFT);
        st.add(center, Anchor.CENTER);
        st.add(br, Anchor.BOTTOM_RIGHT);
        st.layout(0, 0, 100, 100);
        assertEquals(0, tl.xLeft());   assertEquals(0, tl.yTop());
        assertEquals(40, center.xLeft()); assertEquals(40, center.yTop());   // (100-20)/2
        assertEquals(90, br.xLeft());  assertEquals(90, br.yTop());          // 100-10
    }
}
