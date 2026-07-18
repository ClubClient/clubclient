package com.club.ui.tooltip;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Draws a shulker's contents as a plain item grid on the normal tooltip background — no bundle chrome (no dark
 * slot cells, no fill bar, no "Full"). Club draws nothing exotic: every icon goes through vanilla's own
 * {@link DrawContext#drawItem}, which records a normal item-draw command, so none of the 1.21.5+ render
 * inversion (that only bites a hand-rolled pipeline) is in play here.
 *
 * <h2>The version fork, all on ONE boundary (1.21.8), measured not guessed</h2>
 * <ul>
 *   <li>{@code getHeight} gained a {@code TextRenderer} parameter at 1.21.8.</li>
 *   <li>{@code drawItems} gained {@code (width, height)} parameters at 1.21.8.</li>
 *   <li>The count/durability overlay was RENAMED {@code drawItemInSlot -> drawStackOverlay} at 1.21.8.</li>
 * </ul>
 * <p>{@code getWidth(TextRenderer)} and {@code drawItem(stack, x, y)} are identical on all three, so they carry
 * no {@code //?}.</p>
 */
public class ShulkerTooltipComponent implements TooltipComponent {
    /** Slots per row = a shulker's own width, so a full box reads as the familiar 9x3. */
    private static final int COLS = 9;
    /** Slot pitch: a 16px icon plus the 2px gutter inventories use. */
    private static final int CELL = 18;

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
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            int ix = x + (i % COLS) * CELL;
            int iy = y + (i / COLS) * CELL;
            context.drawItem(stack, ix, iy);
            //? if <1.21.8 {
            context.drawItemInSlot(textRenderer, stack, ix, iy);
            //?} else {
            /*context.drawStackOverlay(textRenderer, stack, ix, iy);*/
            //?}
        }
    }
}
