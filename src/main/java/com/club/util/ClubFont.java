package com.club.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * CLUB's UI typography (Inter, bundled under {@code assets/club/font/}).
 *
 * Every weight/size pair is a real native font provider, so text is rasterised
 * at its target pixel size and never matrix-scaled — that is what keeps it crisp
 * (matrix scaling is the classic cause of "the font looks pixelated"). Each role
 * maps to one provider:
 *
 * <pre>
 *   cat    SemiBold 16  — panel module title / headings  (lightly letter-spaced)
 *   tab    Medium   15  — category tabs (Combat / Visuals / …)
 *   name   SemiBold 15  — HUD names (target / effect) and armour values
 *   hud    Medium   15  — HUD values (HP, timers)
 *   list   Medium   13  — module list, dropdowns, buttons
 *   desc   Regular  12  — panel descriptions, hints
 *   small  Medium   12  — control labels, values, footnotes
 * </pre>
 *
 * Information text is NEVER drawn as a gradient — there is deliberately no
 * gradient-text helper here. The accent lives only on control fills.
 *
 * HUD text uses a SOFT shadow ({@link #drawNameShadow}/{@link #drawHudShadow}) — a
 * faint dark backing pass instead of vanilla's hard 1px shadow, which on small
 * oversampled glyphs reads as a ragged double edge. Menu text takes no shadow
 * (it sits on the dark window).
 */
public final class ClubFont {
    private ClubFont() {}

    private static final Style CAT   = Style.EMPTY.withFont(Identifier.of("club", "club_cat"));
    private static final Style TAB   = Style.EMPTY.withFont(Identifier.of("club", "club_tab"));
    private static final Style NAME  = Style.EMPTY.withFont(Identifier.of("club", "club_name"));
    private static final Style HUD   = Style.EMPTY.withFont(Identifier.of("club", "club_hud"));
    private static final Style LIST  = Style.EMPTY.withFont(Identifier.of("club", "club_list"));
    private static final Style DESC  = Style.EMPTY.withFont(Identifier.of("club", "club_desc"));
    private static final Style SMALL = Style.EMPTY.withFont(Identifier.of("club", "club_small"));

    /** Letter-spacing applied to headings only (premium feel). */
    private static final int CAT_TRACK = 1;

    /** Soft dark backing for HUD legibility — replaces vanilla's hard 1px shadow.
     *  Kept light (≈55%) so it separates the glyph from any background without
     *  thickening or blurring it. */
    private static final int HUD_SHADOW = 0x8C000000;

    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }
    private static Text t(Style st, String s) { return Text.literal(s).setStyle(st); }

    // -- widths -------------------------------------------------------------
    public static int widthCat(String s)   { return trackedWidth(CAT, s, CAT_TRACK); }
    public static int widthTab(String s)   { return tr().getWidth(t(TAB, s)); }
    public static int widthName(String s)  { return tr().getWidth(t(NAME, s)); }
    public static int widthHud(String s)   { return tr().getWidth(t(HUD, s)); }
    public static int widthList(String s)  { return tr().getWidth(t(LIST, s)); }
    public static int widthDesc(String s)  { return tr().getWidth(t(DESC, s)); }
    public static int widthSmall(String s) { return tr().getWidth(t(SMALL, s)); }

    // -- draws --------------------------------------------------------------
    /** Heading weight (16) with light letter-spacing. Returns the x advance. */
    public static int drawCat(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        return trackedDraw(ctx, CAT, s, x, y, color, shadow, CAT_TRACK);
    }
    public static int drawTab(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        return ctx.drawText(tr(), t(TAB, s), x, y, color, shadow);
    }
    public static int drawName(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        return ctx.drawText(tr(), t(NAME, s), x, y, color, shadow);
    }
    public static int drawHud(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        return ctx.drawText(tr(), t(HUD, s), x, y, color, shadow);
    }

    // -- HUD soft-shadow helpers (faint dark backing, not vanilla's hard shadow) --
    /** HUD name (SemiBold 15) with a soft dark backing for legibility over the world. */
    public static void drawNameShadow(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(tr(), t(NAME, s), x + 1, y + 1, HUD_SHADOW, false);
        ctx.drawText(tr(), t(NAME, s), x, y, color, false);
    }
    /** HUD value (Medium 15) with a soft dark backing for legibility over the world. */
    public static void drawHudShadow(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(tr(), t(HUD, s), x + 1, y + 1, HUD_SHADOW, false);
        ctx.drawText(tr(), t(HUD, s), x, y, color, false);
    }
    public static int drawList(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        return ctx.drawText(tr(), t(LIST, s), x, y, color, shadow);
    }
    public static int drawDesc(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        return ctx.drawText(tr(), t(DESC, s), x, y, color, shadow);
    }
    public static int drawSmall(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        return ctx.drawText(tr(), t(SMALL, s), x, y, color, shadow);
    }

    // -- centred helpers ----------------------------------------------------
    public static void drawListCentered(DrawContext ctx, String s, int x, int w, int y, int color, boolean shadow) {
        drawList(ctx, s, x + (w - widthList(s)) / 2, y, color, shadow);
    }
    public static void drawSmallCentered(DrawContext ctx, String s, int x, int w, int y, int color, boolean shadow) {
        drawSmall(ctx, s, x + (w - widthSmall(s)) / 2, y, color, shadow);
    }

    // -- internals ----------------------------------------------------------
    private static int trackedWidth(Style st, String s, int track) {
        if (s.isEmpty()) return 0;
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += tr().getWidth(t(st, String.valueOf(s.charAt(i))));
        return w + track * (s.length() - 1);
    }

    private static int trackedDraw(DrawContext ctx, Style st, String s, int x, int y, int color, boolean shadow, int track) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            cx = ctx.drawText(tr(), t(st, String.valueOf(s.charAt(i))), cx, y, color, shadow);
            if (i < s.length() - 1) cx += track;
        }
        return cx;
    }
}
