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
    // Accordion band (menu settings sheet): the settings popover drops out of the clicked card, which is
    // ONE column wide. So only the cards in THAT column, below the clicked row, slide down (owner, pack 4:
    // "зачем отъезжают строки которые поповером не закрывают ничего"). Other columns stay put; the measured
    // height still grows by the band so a ScrollArea can reveal a sheet taller than the grid.
    private int splitCol = -1, splitAfterRow = -1;
    private float splitGap;

    public Grid(int cols, float gap) { this.cols = Math.max(1, cols); this.gap = gap; }

    public Grid cols(int c) { this.cols = Math.max(1, c); return this; }
    public Grid gap(float g) { this.gap = g; return this; }
    public Grid add(Component c) { addChild(c); return this; }
    public void clear() { children.clear(); }

    /** Push the cards in column {@code col} that sit below row {@code afterRow} down by {@code gap} — the room
     *  the settings sheet drops into. The measured height grows by {@code gap} so a ScrollArea can reveal it.
     *  {@code afterRow < 0} clears the split; {@code col < 0} would push every column (unused now). */
    public Grid split(int col, int afterRow, float gap) {
        this.splitCol = col; this.splitAfterRow = afterRow; this.splitGap = Math.max(0f, gap); return this;
    }

    private float bandAt(int row, int col) {
        return (splitAfterRow >= 0 && row > splitAfterRow && (splitCol < 0 || col == splitCol)) ? splitGap : 0f;
    }
    private float bandTotal() { return splitAfterRow >= 0 ? splitGap : 0f; }

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
        return new Size(availW, rows * rh + gap * (rows - 1) + bandTotal());
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        float cw = cellW(w);
        float rh = rowHeight(cw);
        for (int i = 0; i < children.size(); i++) {
            int col = i % cols, row = i / cols;
            float cx = x + col * (cw + gap);
            float cy = y + row * (rh + gap) + bandAt(row, col);   // only the split column's lower cards slide down
            children.get(i).layout(cx, cy, cw, rh);
        }
    }

    @Override public void render(UiContext ctx) { super.render(ctx); }
}
