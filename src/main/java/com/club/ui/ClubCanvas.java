package com.club.ui;

import net.minecraft.client.MinecraftClient;

/**
 * The coordinate space Club draws in — its own, not Minecraft's.
 *
 * <p>Minecraft's GUI units are the player's <b>GUI Scale</b> video setting: one unit is 2 physical pixels at
 * scale 2, 4 at scale 4. Anything laid out in those units is therefore RESIZED by a setting that has nothing
 * to do with it. The menu learned this the hard way in Stage 60 — at GUI Scale 4 its 660-unit window wanted
 * 2640px on a 1920px screen, so it clamped, dropped columns and squeezed its own rail; a video setting was
 * quietly redesigning the product. It got its own canvas and the problem went away.</p>
 *
 * <p>The HUD was still living in the old space (owner, Stage 63: "какого хера у нас худы меняют свой размер
 * в зависимости от настроек в игре") — the chips grew and shrank with GUI Scale, and worse, they did it
 * on top of their OWN size slider, so the same 1.0 meant four different things on four machines.</p>
 *
 * <p>The canvas is a fixed <b>540 units tall, always</b> — exactly the space the UI was designed in (GUI
 * Scale 2 at 1080p). Width follows the window's aspect. Everything is drawn in those units through a single
 * matrix scale, so the interface holds the same PROPORTION of the screen at 720p, 1080p, 1440p or 4K, and
 * the player's GUI Scale never reaches it at all. Mouse coordinates arrive in Minecraft units and are
 * converted at the input boundary ({@link #toCanvas}).</p>
 */
public final class ClubCanvas {
    private ClubCanvas() {}

    /** The canvas is this many units tall, on every screen. Everything else is derived. */
    public static final float HEIGHT = 540f;

    /** Minecraft GUI units per Club unit — the matrix scale, and the mouse divisor. */
    public static float scale(MinecraftClient mc) {
        if (mc == null) return 1f;
        var win = mc.getWindow();
        double mcScale = Math.max(0.0001, win.getScaleFactor());
        float clubScale = Math.max(0.1f, win.getFramebufferHeight() / HEIGHT);   // physical px per Club unit
        return (float) (clubScale / mcScale);
    }

    /** Canvas width in Club units for the current window (height is always {@link #HEIGHT}). */
    public static float width(MinecraftClient mc) {
        if (mc == null) return 960f;
        return Math.max(1f, mc.getWindow().getScaledWidth() / scale(mc));
    }

    public static int widthI(MinecraftClient mc)  { return Math.round(width(mc)); }
    public static int heightI()                   { return Math.round(HEIGHT); }

    /** A Minecraft-unit mouse/screen coordinate → Club units. */
    public static double toCanvas(MinecraftClient mc, double mcUnits) { return mcUnits / scale(mc); }
}
