package com.club.gui.components;

import com.club.gui.Theme;
import com.club.util.ClubFont;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/** Pill-style on/off toggle. Accent when active. */
public class ToggleWidget extends ClickableWidget {
    private final String label;
    private boolean value;
    private final Consumer<Boolean> onChange;

    public ToggleWidget(int x, int y, int width, int height, String label, boolean value, Consumer<Boolean> onChange) {
        super(x, y, width, height, Text.literal(label));
        this.label = label;
        this.value = value;
        this.onChange = onChange;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // ~34x18 pill (spec): a control, never the focal point. Right-aligned in
        // the row, vertically centred; an optional label sits at the left.
        int pillH = Math.max(16, Math.min(20, height));
        int pillW = (label != null && !label.isEmpty()) ? 34 : Math.max(30, Math.min(36, width));
        if (label != null && !label.isEmpty()) {
            int textY = getY() + (height - 12) / 2;
            // The whole row recedes when off, so the surface stays calm.
            ClubFont.drawSmall(ctx, label, getX(), textY, value ? Theme.TEXT : Theme.TEXT_MUTED, false);
        }

        int px = getX() + width - pillW;
        int py = getY() + (height - pillH) / 2;
        if (value) {
            // ON: the one place (with the active tab) a gradient is allowed — a soft
            // blue→light-blue accent. No glow, no neon.
            RenderHelper.gradientRoundedRect(ctx, px, py, pillW, pillH, pillH / 2, Theme.GRAD_A, Theme.GRAD_B);
        } else {
            // OFF: a calm grey pill — clearly off, not invisible.
            RenderHelper.roundedRect(ctx, px, py, pillW, pillH, pillH / 2, Theme.FILL_OFF);
        }

        int kn = pillH - 6;
        int knobX = value ? px + pillW - kn - 3 : px + 3;
        // White knob when on; dim when off — never a harsh bright dot.
        RenderHelper.roundedRect(ctx, knobX, py + 3, kn, kn, kn / 2, value ? Theme.TEXT : Theme.TEXT_MUTED);
    }

    public boolean getValue() { return value; }

    @Override
    public void onClick(double mouseX, double mouseY) {
        value = !value;
        if (onChange != null) onChange.accept(value);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}