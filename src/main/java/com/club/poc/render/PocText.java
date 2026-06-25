package com.club.poc.render;

import net.minecraft.client.gui.DrawContext;

/**
 * NEW-stack typography — same role API as {@code util.ClubFont} (cat/tab/name/hud/
 * list/desc/small) but rendered with MSDF, so the comparison swaps only the backend.
 * Weights/sizes mirror ClubFont exactly (SemiBold 16 headings, Medium/SemiBold 15
 * HUD, Medium 13 list, Regular 12 desc, Medium 12 small). PoC-only.
 */
public final class PocText {
    private PocText() {}

    private static MsdfFont REG, MED, SEMI;

    private static void init() {
        if (REG == null) {
            REG  = MsdfFont.load("regular");
            MED  = MsdfFont.load("medium");
            SEMI = MsdfFont.load("semibold");
        }
    }

    // -- direct font access for the showcase (arbitrary sizes / weights) -----
    public static MsdfFont regular()  { init(); return REG; }
    public static MsdfFont medium()   { init(); return MED; }
    public static MsdfFont semibold() { init(); return SEMI; }

    /** Draw at an arbitrary size with the named weight. Returns x advance. */
    public static float drawAt(DrawContext ctx, String weight, float size, String s, float x, float y, int color) {
        init();
        MsdfFont f = "regular".equals(weight) ? REG : "semibold".equals(weight) ? SEMI : MED;
        return f.drawString(ctx, s, x, y, size, color);
    }
    public static float widthAt(String weight, float size, String s) {
        init();
        MsdfFont f = "regular".equals(weight) ? REG : "semibold".equals(weight) ? SEMI : MED;
        return f.width(s, size);
    }

    // sizes, matching ClubFont roles
    private static final float CAT = 16, TAB = 15, NAME = 15, HUD = 15, LIST = 13, DESC = 12, SMALL = 12;

    /** Soft dark backing for HUD legibility (same intent as ClubFont.HUD_SHADOW). */
    private static final int HUD_SHADOW = 0x8C000000;

    // -- draws --------------------------------------------------------------
    public static float drawCat(DrawContext ctx, String s, float x, float y, int color)  { init(); return SEMI.drawString(ctx, s, x, y, CAT, color); }
    public static float drawTab(DrawContext ctx, String s, float x, float y, int color)  { init(); return MED.drawString(ctx, s, x, y, TAB, color); }
    public static float drawName(DrawContext ctx, String s, float x, float y, int color) { init(); return SEMI.drawString(ctx, s, x, y, NAME, color); }
    public static float drawHud(DrawContext ctx, String s, float x, float y, int color)  { init(); return MED.drawString(ctx, s, x, y, HUD, color); }
    public static float drawList(DrawContext ctx, String s, float x, float y, int color) { init(); return MED.drawString(ctx, s, x, y, LIST, color); }
    public static float drawDesc(DrawContext ctx, String s, float x, float y, int color) { init(); return REG.drawString(ctx, s, x, y, DESC, color); }
    public static float drawSmall(DrawContext ctx, String s, float x, float y, int color){ init(); return MED.drawString(ctx, s, x, y, SMALL, color); }

    // -- HUD soft-shadow helpers (faint dark backing, same look as ClubFont) --
    public static void drawNameShadow(DrawContext ctx, String s, float x, float y, int color) {
        init();
        SEMI.drawString(ctx, s, x + 1, y + 1, NAME, HUD_SHADOW);
        SEMI.drawString(ctx, s, x, y, NAME, color);
    }
    public static void drawHudShadow(DrawContext ctx, String s, float x, float y, int color) {
        init();
        MED.drawString(ctx, s, x + 1, y + 1, HUD, HUD_SHADOW);
        MED.drawString(ctx, s, x, y, HUD, color);
    }

    // -- widths -------------------------------------------------------------
    public static float widthCat(String s)   { init(); return SEMI.width(s, CAT); }
    public static float widthTab(String s)    { init(); return MED.width(s, TAB); }
    public static float widthName(String s)  { init(); return SEMI.width(s, NAME); }
    public static float widthHud(String s)   { init(); return MED.width(s, HUD); }
    public static float widthList(String s)  { init(); return MED.width(s, LIST); }
    public static float widthDesc(String s)  { init(); return REG.width(s, DESC); }
    public static float widthSmall(String s) { init(); return MED.width(s, SMALL); }
}
