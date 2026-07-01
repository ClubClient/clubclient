package com.club.ui.hud;

import net.minecraft.client.gui.DrawContext;

/**
 * The one legacy seam the V2 HUD keeps: the current frame's {@link DrawContext}, so {@link ArmorElement} can
 * draw vanilla item sprites (the V2 renderer only knows shapes + text). Set once per frame by the in-world
 * HudManager and by the HUD editor, right after {@code Ui.beginFrame}. Read only by {@code ArmorElement}.
 */
public final class HudSprites {
    private HudSprites() {}
    private static DrawContext ctx;
    public static void set(DrawContext c) { ctx = c; }
    static DrawContext ctx() { return ctx; }
}
