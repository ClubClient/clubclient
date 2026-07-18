package com.club.tooltip;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Draws a shulker's contents as a chest-style item grid — recessed slots, then the icons — so it reads like
 * the box's own inventory (owner: "take the chest menu and put it in the tooltip") rather than icons floating
 * on the dark tooltip. Club draws nothing exotic: slots via {@link DrawContext#fill} and icons via
 * {@link DrawContext#drawItem}, both normal record-a-command calls, so none of the 1.21.5+ render inversion
 * (which only bites a hand-rolled pipeline) is in play.
 *
 * <p>Lives in {@code com.club.tooltip}, outside {@code com.club.ui}, because it is a vanilla {@code TooltipComponent}
 * drawn by vanilla's tooltip renderer — the Club backend is neither available nor appropriate here, and the
 * design-system's "no low-level render" rule ({@code ArchitectureRuleTest}) rightly does not reach it.</p>
 *
 * <h2>The version fork, all on ONE boundary (1.21.8), measured not guessed</h2>
 * <ul>
 *   <li>{@code getHeight} gained a {@code TextRenderer} parameter at 1.21.8.</li>
 *   <li>{@code drawItems} gained {@code (width, height)} parameters at 1.21.8.</li>
 *   <li>The count/durability overlay was RENAMED {@code drawItemInSlot -> drawStackOverlay} at 1.21.8.</li>
 * </ul>
 * <p>{@code getWidth(TextRenderer)}, {@code drawItem(stack, x, y)} and {@code fill(x,y,x,y,argb)} are identical
 * on all three, so they carry no {@code //?}.</p>
 */
public class ShulkerTooltipComponent implements TooltipComponent {
    /** Slots per row = a shulker's own width, so a full box reads as the familiar 9x3. */
    private static final int COLS = 9;
    /** Slot pitch: an 18px recessed cell (a 16px icon inset by the 1px bevel each side). */
    private static final int CELL = 18;

    // The vanilla container slot, so the grid reads as a chest's own inventory (owner: "take the chest menu")
    // rather than icons floating on the dark tooltip. A recessed cell: dark top-left, light bottom-right, grey
    // face — the three greys the GUI uses, drawn with DrawContext.fill (identical on all three, so no //?).
    private static final int SLOT_SHADOW = 0xFF373737;   // top + left edge
    private static final int SLOT_LIGHT  = 0xFFFFFFFF;   // bottom + right edge
    private static final int SLOT_FACE   = 0xFF8B8B8B;   // interior

    // The panel the slots sit in — a RAISED bevel (the inverse of the slots), the chest GUI's own body grey.
    // It is drawn OVER the tooltip's dark padding so no black ring shows around the grid: the tooltip insets
    // its content by PAD=3 (measured in TooltipBackgroundRenderer), so the panel reaches out that far to meet
    // the frame. Owner: "why is the border black — make the interface proper."
    private static final int PAD        = 3;
    private static final int PANEL_HI   = 0xFFFFFFFF;   // top + left edge (raised)
    private static final int PANEL_LO   = 0xFF555555;   // bottom + right edge
    private static final int PANEL_FACE = 0xFFC6C6C6;   // body

    private final List<ItemStack> items;

    public ShulkerTooltipComponent(List<ItemStack> items) { this.items = items; }

    private int cols() { return Math.max(1, Math.min(items.size(), COLS)); }
    private int rows() { return Math.max(1, (items.size() + COLS - 1) / COLS); }

    // getWidth's signature is the SAME on all three versions (measured), so no fork.
    @Override public int getWidth(TextRenderer textRenderer) { return cols() * CELL; }

    //? if <1.21.8 {
    @Override public int getHeight() { return rows() * CELL; }
    //?} else {
    /*@Override public int getHeight(TextRenderer textRenderer) { return rows() * CELL; }*/
    //?}

    //? if <1.21.8 {
    @Override public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext context) { paint(textRenderer, x, y, context); }
    //?} else {
    /*@Override public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext context) { paint(textRenderer, x, y, context); }*/
    //?}

    /** The one place the grid is drawn. Both {@code drawItems} arities land here. */
    private void paint(TextRenderer textRenderer, int x, int y, DrawContext context) {
        int c = cols(), r = rows();
        // Panel first, reaching PAD into the tooltip's padding on every side so the dark bg never rings the grid.
        panel(context, x - PAD, y - PAD, x + c * CELL + PAD, y + r * CELL + PAD);
        // A slot for EVERY cell of the c*r rectangle — the trailing cells of the last row are empty slots, not
        // blank panel, so a part-full box still reads as a chest and not a ragged strip.
        for (int i = 0; i < c * r; i++) {
            int cx = x + (i % c) * CELL;
            int cy = y + (i / c) * CELL;
            slot(context, cx, cy);
            if (i >= items.size()) continue;
            ItemStack stack = items.get(i);
            int ix = cx + 1, iy = cy + 1;   // the 16px icon sits inside the 1px bevel
            context.drawItem(stack, ix, iy);
            //? if <1.21.8 {
            context.drawItemInSlot(textRenderer, stack, ix, iy);
            //?} else {
            /*context.drawStackOverlay(textRenderer, stack, ix, iy);*/
            //?}
        }
    }

    /** One recessed 18x18 container slot at {@code (cx, cy)}. */
    private void slot(DrawContext context, int cx, int cy) {
        context.fill(cx, cy, cx + CELL, cy + CELL, SLOT_SHADOW);               // dark base (top + left show)
        context.fill(cx + 1, cy + 1, cx + CELL, cy + CELL, SLOT_LIGHT);        // light base (bottom + right show)
        context.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, SLOT_FACE); // grey face inside both bevels
    }

    /** The raised chest-body panel behind the slots, from {@code (x0,y0)} to {@code (x1,y1)}. */
    private void panel(DrawContext context, int x0, int y0, int x1, int y1) {
        context.fill(x0, y0, x1, y1, PANEL_HI);              // light base (top + left show)
        context.fill(x0 + 1, y0 + 1, x1, y1, PANEL_LO);      // dark base (bottom + right show)
        context.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL_FACE); // body inside both bevels
    }
}
