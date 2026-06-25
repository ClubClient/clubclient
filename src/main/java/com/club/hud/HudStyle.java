package com.club.hud;

import com.club.gui.Theme;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared bits for the redesigned HUD: the low-value threshold (value shifts to
 * soft amber) and the editor's settings popover surface — the only "panel"
 * the HUD system draws. HUD elements themselves use no cards, bars or borders.
 */
public final class HudStyle {
    private HudStyle() {}

    /** True when a fraction is low enough to warn (value shifts to soft amber). */
    public static boolean isLow(float frac) { return frac < 0.40f; }

    /** Editor popover surface: deep fill, soft shadow, hairline. */
    public static void popover(DrawContext ctx, int x, int y, int w, int h) {
        RenderHelper.dropShadow(ctx, x, y, w, h, Theme.RADIUS, 6);
        RenderHelper.gradientRoundedRectV(ctx, x, y, w, h, Theme.RADIUS, Theme.BG_2, Theme.BG_1);
        RenderHelper.roundedBorder(ctx, x, y, w, h, Theme.RADIUS, Theme.HAIR_STRONG);
    }
}
