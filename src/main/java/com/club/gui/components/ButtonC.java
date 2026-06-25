package com.club.gui.components;

import com.club.gui.Theme;
import com.club.util.ClubFont;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Premium Dark Glass button. Default = subtle glass; primary = accent-filled. */
public class ButtonC extends ClickableWidget {
    private final Runnable onPress;
    private boolean primary = false;

    public ButtonC(int x, int y, int width, int height, String label, Runnable onPress) {
        super(x, y, width, height, Text.literal(label));
        this.onPress = onPress;
    }

    /** Accent-filled call-to-action style. */
    public ButtonC primary() { this.primary = true; return this; }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean hover = isHovered();
        int x = getX(), y = getY();
        // A bordered pill — the reference button. primary() reads as the CTA via a
        // faint accent wash + accent label; default stays neutral. No gradient fill.
        int fill = primary
                ? (hover ? Theme.ACCENT_FAINT : Theme.FILL_SUBTLE)
                : (hover ? Theme.SURFACE_HI : Theme.FILL_SUBTLE);
        RenderHelper.controlSurface(ctx, x, y, width, height, Theme.RADIUS_SM, fill, Theme.BORDER);

        String s = getMessage().getString();
        int tw = ClubFont.widthList(s);
        int col = primary ? Theme.ACCENT : hover ? Theme.TEXT : Theme.TEXT_MUTED;
        ClubFont.drawList(ctx, s, x + (width - tw) / 2, y + (height - 13) / 2, col, false);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (onPress != null) onPress.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}