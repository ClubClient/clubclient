package com.club.ui.component.widget;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.UiRenderer;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;

import java.util.function.IntConsumer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Colour PALETTE control — a wrapping grid of flat rounded swatches; the picked one wears an accent ring.
 *
 * <p>The value is the INDEX of the chosen swatch into the colour array — the SAME int-index model the old
 * "On player" / "Default" dropdowns stored, so swapping a dropdown for this widget needs no config change.
 * Mouse selects a swatch on click; when the grid holds keyboard focus, the arrow keys walk the selection
 * (Left/Right by one, Up/Down by a row). Flat by design: each swatch is a fill + 1px hairline, the pick is
 * an offset accent ring (the panel background shows through the gap), and there is no glow — the frozen
 * Club design language, matching the {@code .sp-sw} swatch grid in the settings-panel mockup.</p>
 */
public final class Swatches extends Component {

    // Grid geometry (shape proportions, NOT design tokens). Six columns fills the ~126px panel body, so
    // the cell width is derived from the laid-out width (an equal-fraction column, like the mockup's
    // repeat(6,1fr)); everything else is fixed to the mockup's swatch metrics.
    private static final int   COLS     = 6;
    private static final float GAP      = 5f;    // space between swatches (.sp-swgrid gap:5px)
    private static final float RAD      = 4f;    // swatch corner radius (.sp-sw border-radius:4px)
    private static final float MIN_CELL = 8f;    // never collapse a swatch to nothing on a squeezed panel
    private static final float RING_GAP = 1.5f;  // panel-toned gap between the picked swatch and its ring
    private static final float RING_THK = 1.5f;  // accent ring thickness (.sp-sw.sel outer ring)
    private static final int   HOVER_RING_ALPHA = 0x88;

    private final int[] colors;
    private int value;
    private IntConsumer onChange;
    private int accent;        // 0 → theme accent (WidgetPaint.acc); a category tint otherwise (Stage 11.9)
    private int hoverIdx = -1;

    public Swatches(int[] colors, int value) {
        this.colors = colors;
        this.value = clamp(value);
    }
    public Swatches onChange(IntConsumer cb) { this.onChange = cb; return this; }
    /** Overrides the ring accent (0 restores the theme accent) — used for category-tinted panels. */
    public Swatches accent(int color) { this.accent = color; return this; }
    public int value() { return value; }

    private int clamp(int i) { return i < 0 ? 0 : (i >= colors.length ? colors.length - 1 : i); }
    private float cell(float width) { return Math.max(MIN_CELL, (width - (COLS - 1) * GAP) / COLS); }
    private int rows() { return (colors.length + COLS - 1) / COLS; }

    @Override public Size measure(float availW, float availH) {
        int rows = rows();
        return new Size(availW, rows * cell(availW) + (rows - 1) * GAP);
    }

    @Override public void render(UiContext ctx) {
        UiRenderer r = ctx.renderer();
        float cell = cell(w);
        int acc = WidgetPaint.acc(accent);
        for (int i = 0; i < colors.length; i++) {
            float px = x + (i % COLS) * (cell + GAP);
            float py = y + (i / COLS) * (cell + GAP);
            r.roundedRect(px, py, cell, cell, RAD, colors[i]);
            // A flat 1px hairline frames every swatch so a light colour (White) still reads as a tile
            // against the dark panel — no shadow, no glow (mockup: border 1px white@9%).
            r.border(px, py, cell, cell, RAD, Tokens.border().thickness(), Tokens.border().subtle());
            if (i == value) {
                // Picked: an accent ring offset by a small gap; the panel surface showing through the gap
                // is the mockup's window-toned separator between the swatch and its ring.
                r.border(px - RING_GAP, py - RING_GAP, cell + 2 * RING_GAP, cell + 2 * RING_GAP,
                        RAD + RING_GAP, RING_THK, acc);
            } else if (i == hoverIdx) {
                r.border(px - 1f, py - 1f, cell + 2f, cell + 2f, RAD + 1f, Tokens.border().thickness(),
                        Color.withAlpha(acc, HOVER_RING_ALPHA));
            }
        }
        // Whole-grid keyboard-focus halo (arrow keys drive the selection) — only when reached via Tab.
        WidgetPaint.focusRing(ctx, this, RAD, acc);
    }

    private int indexAt(double mx, double my) {
        float cell = cell(w);
        for (int i = 0; i < colors.length; i++) {
            float px = x + (i % COLS) * (cell + GAP);
            float py = y + (i / COLS) * (cell + GAP);
            if (mx >= px && mx < px + cell && my >= py && my < py + cell) return i;
        }
        return -1;
    }

    @Override public void mouseMoved(double mx, double my) { hoverIdx = indexAt(mx, my); }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || button != 0) return false;
        int i = indexAt(mx, my);
        if (i < 0) return false;
        select(i);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (!enabled) return false;
        int v;
        switch (key) {
            case GLFW_KEY_LEFT  -> v = Math.max(0, value - 1);
            case GLFW_KEY_RIGHT -> v = Math.min(colors.length - 1, value + 1);
            case GLFW_KEY_UP    -> v = Math.max(0, value - COLS);
            case GLFW_KEY_DOWN  -> v = Math.min(colors.length - 1, value + COLS);
            default -> { return false; }
        }
        if (v != value) select(v);
        return true;   // an arrow inside the grid is consumed even at an edge, so it never leaks to the page
    }

    private void select(int i) {
        value = i;
        if (onChange != null) onChange.accept(i);
    }
}
