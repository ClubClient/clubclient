package com.club.ui.layout;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import java.util.ArrayList;
import java.util.List;

/** Axis-generic linear layout (package-private internal engine — external consumers use Column/Row). */
abstract class Linear extends Container {
    private final boolean horizontal;
    private final List<Sizing> sizings = new ArrayList<>();
    private Insets padding = Insets.ZERO;
    private float gap = 0f;
    private CrossAlign crossAlign = CrossAlign.START;
    private MainAlign mainAlign = MainAlign.START;

    // reused layout scratch (alloc-free across frames once child count is stable)
    private float[] mainExt = new float[8];
    private float[] crossDes = new float[8];

    protected Linear(boolean horizontal) { this.horizontal = horizontal; }

    protected void setPadding(Insets p)    { this.padding = p; }
    protected void setGap(float g)         { this.gap = g; }
    protected void setCrossAlign(CrossAlign a) { this.crossAlign = a; }
    protected void setMainAlign(MainAlign a)   { this.mainAlign = a; }
    protected void addItem(Component c, Sizing s) {
        Sizing eff = (c instanceof Spacer sp) ? sp.sizing() : s;
        addChild(c);
        sizings.add(eff);
    }
    protected void addItem(Component c) { addItem(c, Sizing.fixed()); }

    private float mainOf(float w, float h) { return horizontal ? w : h; }
    private float crossOf(float w, float h) { return horizontal ? h : w; }

    /**
     * Intrinsic CONTENT size. A Fill/Weight child contributes its own measured main-extent
     * (a Fixed Spacer contributes its length; a flexible Spacer contributes 0). Flex expansion
     * happens only in {@link #layout} when an explicit main-axis budget exists, so a
     * size-to-content parent wraps its children tightly. (Pinned: measureReportsIntrinsicSizeIgnoringFlex.)
     */
    @Override public Size measure(float availW, float availH) {
        float innerW = availW - padding.horizontal(), innerH = availH - padding.vertical();
        float mainSum = 0f, crossMax = 0f; int n = children.size();
        for (int i = 0; i < n; i++) {
            Component c = children.get(i);
            float len; float cross;
            if (c instanceof Spacer sp && sp.sizing() instanceof Sizing.Fixed) { len = sp.length(); cross = 0; }
            else { Size s = c.measure(innerW, innerH); len = mainOf(s.w(), s.h()); cross = crossOf(s.w(), s.h()); }
            mainSum += len; crossMax = Math.max(crossMax, cross);
        }
        mainSum += gap * Math.max(0, n - 1);
        float mainTotal = mainSum + (horizontal ? padding.horizontal() : padding.vertical());
        float crossTotal = crossMax + (horizontal ? padding.vertical() : padding.horizontal());
        return horizontal ? new Size(mainTotal, crossTotal) : new Size(crossTotal, mainTotal);
    }

    /**
     * The largest extent ≤ {@code budget} that ends in a GAP BETWEEN children, never inside one.
     *
     * <p>Why this exists: a scrolling container capped at an arbitrary pixel height cuts whatever child
     * happens to straddle that pixel — and the popover was doing exactly that, slicing "Reset to Default"
     * through the middle of its letters (owner, v0.1.3 item 10: "что с надписью у нижнего края? почему это
     * так убого смотрится"). It is not a taste problem and it is not a clipping bug in the renderer: it is a
     * height chosen without asking the content where its seams are.
     *
     * <p>A half-drawn row is the worst of both worlds — it is neither shown nor hidden, so the eye reads it
     * as damage rather than as "there is more below". Snapping the viewport to a child boundary makes that
     * state UNREACHABLE, not merely unlikely: the scrollbar still says there is more, and everything the
     * player can see is whole.
     *
     * <p>Returns {@code budget} unchanged when not even the first child fits — a sliver of one row is ugly,
     * but zero rows is unusable, and there is nothing better to do with the space.
     */
    public float snapToChild(float availW, float availH, float budget) {
        float innerW = availW - padding.horizontal(), innerH = availH - padding.vertical();
        float pad = horizontal ? padding.horizontal() : padding.vertical();
        float acc = 0f, best = 0f;
        int n = children.size();
        for (int i = 0; i < n; i++) {
            Component c = children.get(i);
            float len;
            if (c instanceof Spacer sp && sp.sizing() instanceof Sizing.Fixed) len = sp.length();
            else { Size s = c.measure(innerW, innerH); len = mainOf(s.w(), s.h()); }
            if (i > 0) acc += gap;
            acc += len;
            if (acc + pad <= budget) best = acc + pad; else break;
        }
        return best > 0f ? best : budget;
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        int n = children.size();
        if (n == 0) return;
        ensureScratch(n);
        float innerX = x + padding.left(), innerY = y + padding.top();
        float innerW = w - padding.horizontal(), innerH = h - padding.vertical();
        float mainAvail = horizontal ? innerW : innerH;
        float crossAvail = horizontal ? innerH : innerW;

        // pass 1: measure, gather fixed main, total weight (Fill counts as weight 1)
        float fixedMain = 0f, totalWeight = 0f;
        for (int i = 0; i < n; i++) {
            Component c = children.get(i);
            Sizing s = sizings.get(i);
            if (s instanceof Sizing.Fixed) {
                float len;
                if (c instanceof Spacer sp) { len = sp.length(); crossDes[i] = 0f; }
                else { Size m = c.measure(innerW, innerH); len = mainOf(m.w(), m.h()); crossDes[i] = crossOf(m.w(), m.h()); }
                mainExt[i] = len; fixedMain += len;
            } else {
                float weight = (s instanceof Sizing.Weight wt) ? wt.value() : 1f;
                totalWeight += weight;
                mainExt[i] = -weight;                       // marker: resolve after leftover known
                Size m = c.measure(innerW, innerH);
                crossDes[i] = (c instanceof Spacer) ? 0f : crossOf(m.w(), m.h());
            }
        }
        float totalGap = gap * (n - 1);
        float leftover = Math.max(0f, mainAvail - fixedMain - totalGap);
        for (int i = 0; i < n; i++) if (mainExt[i] < 0f) mainExt[i] = leftover * (-mainExt[i]) / totalWeight;

        // pass 2: main-axis start offset (only meaningful when nothing flexes)
        float cursor = horizontal ? innerX : innerY;
        if (totalWeight == 0f) {
            float used = fixedMain + totalGap;
            if (mainAlign == MainAlign.CENTER) cursor += (mainAvail - used) / 2f;
            else if (mainAlign == MainAlign.END) cursor += (mainAvail - used);
        }

        // pass 3: place
        for (int i = 0; i < n; i++) {
            Component c = children.get(i);
            float mext = mainExt[i];
            float cext = (crossAlign == CrossAlign.STRETCH) ? crossAvail : crossDes[i];
            float crossStart = horizontal ? innerY : innerX;
            float coff;
            switch (crossAlign) {
                case CENTER  -> coff = (crossAvail - cext) / 2f;
                case END     -> coff = (crossAvail - cext);
                default      -> coff = 0f;                  // START and STRETCH
            }
            float cpos = crossStart + coff;
            if (horizontal) c.layout(cursor, cpos, mext, cext);
            else            c.layout(cpos, cursor, cext, mext);
            cursor += mext + gap;
        }
    }

    private void ensureScratch(int n) {
        if (mainExt.length < n) { mainExt = new float[n]; crossDes = new float[n]; }
    }
}
