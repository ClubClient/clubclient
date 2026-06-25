package com.club.gui.components;

import com.club.gui.Theme;
import com.club.util.ClubFont;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/**
 * Segmented control: 2+ mutually-exclusive options on a single subtle track.
 * The active segment is filled with the accent gradient; others are muted text.
 * Used for the Hands Left/Right selector and the editor's Orientation/Value picks.
 */
public class SegmentedWidget extends ClickableWidget {
    private final String[] options;
    private int selected;
    private final IntConsumer onSelect;
    private static final int PAD = 3;

    public SegmentedWidget(int x, int y, int width, int height, String[] options, int selected, IntConsumer onSelect) {
        super(x, y, width, height, Text.literal("segmented"));
        this.options = options;
        this.selected = Math.max(0, Math.min(selected, options.length - 1));
        this.onSelect = onSelect;
    }

    private float segW() { return (width - 2f * PAD) / options.length; }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();
        // Boxed track (element fill + border), one family with the other controls.
        RenderHelper.controlSurface(ctx, x, y, width, height, Theme.RADIUS_SM, Theme.FILL_SUBTLE, Theme.BORDER);

        float sw = segW();
        int segX = x + PAD + Math.round(selected * sw);
        // Active segment — a FLAT accent (no gradient; that is reserved for tab/toggle).
        RenderHelper.roundedRect(ctx, segX, y + PAD, Math.round(sw), height - 2 * PAD, Theme.RADIUS_XS, Theme.ACCENT);

        int textY = y + (height - 12) / 2;
        for (int i = 0; i < options.length; i++) {
            int ox = x + PAD + Math.round(i * sw);
            int ow = Math.round(sw);
            boolean on = i == selected;
            if (on) ClubFont.drawSmallCentered(ctx, options[i], ox, ow, textY, Theme.ON_ACCENT, false);
            else {
                boolean hover = mouseX >= ox && mouseX <= ox + ow && mouseY >= y && mouseY <= y + height;
                ClubFont.drawSmallCentered(ctx, options[i], ox, ow, textY, hover ? Theme.TEXT : Theme.TEXT_MUTED, false);
            }
        }
    }

    public int getSelected() { return selected; }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int i = (int) ((mouseX - (getX() + PAD)) / segW());
        i = Math.max(0, Math.min(i, options.length - 1));
        if (i != selected) {
            selected = i;
            if (onSelect != null) onSelect.accept(i);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
