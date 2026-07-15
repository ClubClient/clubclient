package com.club.ui.layout;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;

/**
 * Simple fixed-column grid: children flow left-to-right into {@code cols} equal-width columns, wrapping
 * to new rows. Row height = the tallest child's measured height (uniform cards in practice). Alloc-light.
 */
public final class Grid extends Container {
    private int cols;
    private float gap;
    // Accordion band (menu settings sheet): extra vertical space inserted AFTER row {@code splitAfterRow},
    // so the rows below it slide down to make room for the popover that drops out of the clicked card.
    private int splitAfterRow = -1;
    private float splitGap;

    public Grid(int cols, float gap) { this.cols = Math.max(1, cols); this.gap = gap; }

    public Grid cols(int c) { this.cols = Math.max(1, c); return this; }
    public Grid gap(float g) { this.gap = g; return this; }
    public Grid add(Component c) { addChild(c); return this; }
    public void clear() { children.clear(); }

    /** Insert {@code gap} vertical px after row {@code row}: every row below it shifts down by that much, and
     *  the measured height grows to match so a ScrollArea can reveal the band. {@code row < 0} clears it. */
    public Grid split(int row, float gap) { this.splitAfterRow = row; this.splitGap = Math.max(0f, gap); return this; }

    private float bandAt(int row) { return (splitAfterRow >= 0 && row > splitAfterRow) ? splitGap : 0f; }
    private float bandTotal(int rows) { return (splitAfterRow >= 0 && rows > 0) ? splitGap : 0f; }

    private float cellW(float w) { return (w - gap * (cols - 1)) / cols; }

    private float rowHeight(float cellW) {
        float h = 0f;
        for (Component c : children) h = Math.max(h, c.measure(cellW, 0).h());
        return h;
    }

    @Override public Size measure(float availW, float availH) {
        if (children.isEmpty()) return new Size(availW, 0);
        float cw = cellW(availW);
        int rows = (children.size() + cols - 1) / cols;
        float rh = rowHeight(cw);
        return new Size(availW, rows * rh + gap * (rows - 1) + bandTotal(rows));
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        float cw = cellW(w);
        float rh = rowHeight(cw);
        for (int i = 0; i < children.size(); i++) {
            int col = i % cols, row = i / cols;
            float cx = x + col * (cw + gap);
            float cy = y + row * (rh + gap) + bandAt(row);   // rows below the split slide down by the band
            children.get(i).layout(cx, cy, cw, rh);
        }
    }

    @Override public void render(UiContext ctx) { super.render(ctx); }
}
