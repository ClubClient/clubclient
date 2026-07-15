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
    // Accordion band (menu settings sheet): the sheet drops out of the clicked card and the column's lower
    // cards slide down for it. Only the MEASURED height grows here (so a ScrollArea can reveal a tall sheet).
    // The cards are NOT offset in layout — the screen offsets the affected column's lower cards at RENDER
    // time, rigidly synced with the sheet's reveal, so TileMotion doesn't re-ease the push and make the card
    // arrive after the sheet (owner, pack 6 #4).
    private int splitAfterRow = -1;
    private float splitGap;

    public Grid(int cols, float gap) { this.cols = Math.max(1, cols); this.gap = gap; }

    public Grid cols(int c) { this.cols = Math.max(1, c); return this; }
    public Grid gap(float g) { this.gap = g; return this; }
    public Grid add(Component c) { addChild(c); return this; }
    public void clear() { children.clear(); }

    /** Reserve {@code gap} of extra measured height for a sheet dropped after row {@code afterRow}, so a
     *  ScrollArea can scroll to it. Does NOT move any card. {@code afterRow < 0} clears it. */
    public Grid split(int afterRow, float gap) { this.splitAfterRow = afterRow; this.splitGap = Math.max(0f, gap); return this; }

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
            float cy = y + row * (rh + gap);   // the accordion push is applied by the screen at render time
            children.get(i).layout(cx, cy, cw, rh);
        }
    }

    @Override public void render(UiContext ctx) { super.render(ctx); }
}
