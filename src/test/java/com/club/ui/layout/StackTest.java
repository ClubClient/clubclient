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
    @Test void anchorsCoverRemainingSix() {
        // 20x20 child in 100x100: left=0, center=40, right=80; top=0, mid=40, bottom=80
        Stack st = new Stack();
        Tile top = new Tile(20, 20), tr = new Tile(20, 20), left = new Tile(20, 20),
             right = new Tile(20, 20), bl = new Tile(20, 20), bottom = new Tile(20, 20);
        st.add(top, Anchor.TOP);
        st.add(tr, Anchor.TOP_RIGHT);
        st.add(left, Anchor.LEFT);
        st.add(right, Anchor.RIGHT);
        st.add(bl, Anchor.BOTTOM_LEFT);
        st.add(bottom, Anchor.BOTTOM);
        st.layout(0, 0, 100, 100);
        assertEquals(40, top.xLeft());    assertEquals(0,  top.yTop());
        assertEquals(80, tr.xLeft());     assertEquals(0,  tr.yTop());
        assertEquals(0,  left.xLeft());   assertEquals(40, left.yTop());
        assertEquals(80, right.xLeft());  assertEquals(40, right.yTop());
        assertEquals(0,  bl.xLeft());     assertEquals(80, bl.yTop());
        assertEquals(40, bottom.xLeft()); assertEquals(80, bottom.yTop());
    }
}
