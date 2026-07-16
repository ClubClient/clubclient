package com.club.compat;

import net.minecraft.client.texture.NativeImage;

/**
 * Pixels out of a {@link NativeImage}, across Minecraft versions — <b>always in ABGR</b>.
 *
 * <p><b>This class exists to stop a silent colour bug, not to fix a compile error.</b> 1.21.5 did not delete
 * {@code getColor}/{@code setColor} — it made them PRIVATE and offered {@code getColorArgb}/{@code setColorArgb}
 * in their place. The names differ by three letters and so does the byte order: the old pair spoke ABGR, the
 * new pair speaks ARGB. Swap the call and nothing complains — the code compiles, the icons draw, and red and
 * blue are exchanged. A compiler error is a good day; this would have been a bug report about "weird colours"
 * from a player, weeks later.</p>
 *
 * <p>So the seam speaks the order the callers already speak. {@code PixelIcons} names its locals {@code abgr}
 * and {@code PixelMath.lum} reads {@code b = (abgr >> 16), g = (abgr >> 8), r = abgr} — that contract, and the
 * tests over it, stay untouched. The 1.21.5+ branch converts; the shipping version keeps its exact behaviour.</p>
 *
 * <p>Boundary measured, not assumed: {@code getColor} is public in the 1.21.4 mappings and private from
 * 1.21.5 — the same release that took {@code BufferRenderer} and {@code ArmorItem}.</p>
 */
public final class Img {
    private Img() {}

    /** The pixel at {@code (x, y)} as ABGR. */
    public static int abgr(NativeImage img, int x, int y) {
        //? if <1.21.5 {
        return img.getColor(x, y);
        //?} else {
        /*return swapRedBlue(img.getColorArgb(x, y));*/
        //?}
    }

    /** Write the pixel at {@code (x, y)}, given as ABGR. */
    public static void setAbgr(NativeImage img, int x, int y, int abgr) {
        //? if <1.21.5 {
        img.setColor(x, y, abgr);
        //?} else {
        /*img.setColorArgb(x, y, swapRedBlue(abgr));*/
        //?}
    }

    //? if >=1.21.5 {
    /*/^* ARGB <-> ABGR. Alpha and green sit in the same bits either way; only the outer two swap, so one
     *  function converts in both directions. *^/
    private static int swapRedBlue(int c) {
        return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
    }*/
    //?}
}
