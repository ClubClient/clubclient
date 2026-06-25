package com.club.gui.components;

import com.club.gui.Icons;
import com.club.gui.Theme;
import com.club.util.ClubFont;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/**
 * Dropdown / select. The header is a normal widget; the open option list is a
 * popup rendered and click-routed by the owning screen (so it draws on top of
 * sibling widgets).
 */
public class DropdownWidget extends ClickableWidget {
    private final String[] options;
    private int selected;
    private final IntConsumer onSelect;
    public boolean open = false;

    public DropdownWidget(int x, int y, int width, int height, String[] options, int selected, IntConsumer onSelect) {
        super(x, y, width, height, Text.literal("dropdown"));
        this.options = options;
        this.selected = Math.max(0, Math.min(selected, options.length - 1));
        this.onSelect = onSelect;
    }

    private int itemH() { return Math.max(18, height); }
    private static final int CHEV = 6, GAP = 7;

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();
        boolean hover = inHeader(mouseX, mouseY);
        // Boxed control surface (element fill + border) — the reference dropdown.
        RenderHelper.controlSurface(ctx, x, y, width, height, Theme.RADIUS_SM,
                hover || open ? Theme.SURFACE_HI : Theme.SURFACE, Theme.BORDER);

        // Value stays white/muted; only the chevron picks up the accent when open,
        // so the accent never colours information text.
        int textCol = open || hover ? Theme.TEXT : Theme.TEXT_MUTED;
        int chevCol = open ? Theme.ACCENT : hover ? Theme.TEXT : Theme.TEXT_MUTED;

        int chX = x + width - 8 - CHEV;
        Icons.chevron(ctx, chX, y + height / 2 - (open ? 1 : 2), CHEV, chevCol, !open);
        ClubFont.drawList(ctx, options[selected], x + 9, y + (height - 13) / 2, textCol, false);
    }

    /** Drawn by the screen after all widgets so it overlaps siblings. */
    public void renderPopup(DrawContext ctx, int mouseX, int mouseY) {
        if (!open) return;
        int ih = itemH();
        int pw = popupW();
        int x = getX() + width - pw; // right-aligned under the value
        int y = getY() + height + 4;
        int h = options.length * ih + 6;
        RenderHelper.controlSurface(ctx, x, y, pw, h, Theme.RADIUS_SM, Theme.BG_2, Theme.BORDER);
        for (int i = 0; i < options.length; i++) {
            int iy = y + 3 + i * ih;
            boolean hover = mouseX >= x && mouseX <= x + pw && mouseY >= iy && mouseY <= iy + ih;
            boolean sel = i == selected;
            if (sel) RenderHelper.roundedRect(ctx, x + 3, iy, pw - 6, ih, 5, Theme.ACCENT_FAINT);
            else if (hover) RenderHelper.roundedRect(ctx, x + 3, iy, pw - 6, ih, 5, Theme.FILL_SUBTLE);
            // Text stays white/muted; the accent only tints the selected row wash.
            int color = sel || hover ? Theme.TEXT : Theme.TEXT_MUTED;
            ClubFont.drawList(ctx, options[i], x + 10, iy + (ih - 13) / 2, color, false);
        }
    }

    private int popupW() {
        int w = 0;
        for (String o : options) w = Math.max(w, ClubFont.widthList(o));
        return Math.max(width, w + 28);
    }

    public boolean inHeader(double mx, double my) {
        return mx >= getX() && mx <= getX() + width && my >= getY() && my <= getY() + height;
    }

    /** Returns clicked option index, or -1 if click was outside the popup. */
    public int optionAt(double mx, double my) {
        if (!open) return -1;
        int ih = itemH();
        int pw = popupW();
        int x = getX() + width - pw;
        int y = getY() + height + 4;
        for (int i = 0; i < options.length; i++) {
            int iy = y + 3 + i * ih;
            if (mx >= x && mx <= x + pw && my >= iy && my <= iy + ih) return i;
        }
        return -1;
    }

    public void select(int i) {
        if (i < 0 || i >= options.length) return;
        selected = i;
        if (onSelect != null) onSelect.accept(i);
    }

    public int getSelected() { return selected; }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}