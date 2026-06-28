package com.club.ui.layout;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinearTest {
    /** Fixed-size leaf: measures to (w,h). */
    static class Tile extends Component {
        final float dw, dh;
        Tile(float dw, float dh) { this.dw = dw; this.dh = dh; }
        @Override public Size measure(float aw, float ah) { return new Size(dw, dh); }
        @Override public void render(UiContext ctx) { }
    }
    @Test void columnStacksFixedWithGapAndPadding() {
        Column col = new Column().padding(Insets.all(10)).gap(5).crossAlign(CrossAlign.START);
        Tile a = new Tile(40, 12), b = new Tile(40, 20);
        col.add(a); col.add(b);
        col.layout(0, 0, 100, 200);
        assertEquals(10, a.yTop()); assertEquals(10, a.xLeft());          // padding
        assertEquals(10 + 12 + 5, b.yTop());                              // a.h + gap
        assertEquals(12, a.height()); assertEquals(20, b.height());       // fixed keep measured
    }
    @Test void fillTakesLeftover() {
        Column col = new Column().padding(Insets.ZERO).gap(0);
        Tile fixed = new Tile(40, 30);
        Tile fill  = new Tile(40, 10);
        col.add(fixed);
        col.add(fill, Sizing.fill());
        col.layout(0, 0, 100, 100);
        assertEquals(30, fixed.height());
        assertEquals(70, fill.height());                                 // 100 - 30
        assertEquals(30, fill.yTop());
    }
    @Test void weightSplitsLeftoverProportionally() {
        Column col = new Column();
        Tile one = new Tile(10, 0), two = new Tile(10, 0);
        col.add(one, Sizing.weight(1));
        col.add(two, Sizing.weight(3));
        col.layout(0, 0, 100, 80);
        assertEquals(20, one.height());                                  // 1/4 of 80
        assertEquals(60, two.height());                                  // 3/4 of 80
    }
    @Test void spacerFillPushesNextToEnd() {
        Column col = new Column();
        Tile top = new Tile(10, 10), bottom = new Tile(10, 10);
        col.add(top);
        col.add(Spacer.fill());
        col.add(bottom);
        col.layout(0, 0, 50, 100);
        assertEquals(0, top.yTop());
        assertEquals(90, bottom.yTop());                                 // pushed to bottom
    }
    @Test void crossAlignStretchAndCenter() {
        Column stretch = new Column().crossAlign(CrossAlign.STRETCH);
        Tile t1 = new Tile(30, 10); stretch.add(t1);
        stretch.layout(0, 0, 100, 100);
        assertEquals(0, t1.xLeft()); assertEquals(100, t1.width());      // stretched to inner width

        Column center = new Column().crossAlign(CrossAlign.CENTER);
        Tile t2 = new Tile(30, 10); center.add(t2);
        center.layout(0, 0, 100, 100);
        assertEquals(35, t2.xLeft()); assertEquals(30, t2.width());      // (100-30)/2
    }
    @Test void mainAlignCenterOffsetsBlockWhenNoFlex() {
        Column col = new Column().mainAlign(MainAlign.CENTER);
        Tile a = new Tile(10, 20); col.add(a);
        col.layout(0, 0, 50, 100);
        assertEquals(40, a.yTop());                                      // (100-20)/2
    }
    @Test void rowIsHorizontalTranspose() {
        Row row = new Row().padding(Insets.ZERO).gap(5).crossAlign(CrossAlign.START);
        Tile a = new Tile(12, 40), b = new Tile(20, 40);
        row.add(a); row.add(b);
        row.layout(0, 0, 200, 100);
        assertEquals(0, a.xLeft());
        assertEquals(12 + 5, b.xLeft());                                 // a.w + gap
    }
}
