package com.club.poc;

import com.club.poc.render.PocRenderer;
import com.club.poc.render.PocText;
import com.club.util.ClubFont;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * PoC compare screen: a vertical split rendering the SAME six elements twice —
 * LEFT through the current stack ({@code RenderHelper} + {@code ClubFont}, i.e.
 * {@code DrawContext.fill} + bitmap font), RIGHT through the NEW stack
 * ({@code PocRenderer} + {@code PocText}, i.e. SDF shapes + MSDF text). Geometry is
 * identical by construction; only the rendering backend differs, so any visual
 * difference is the renderer, not the layout.
 *
 * Elements: heading · toggle · slider · card · Armor-HUD sample · Target-HUD sample.
 * {@link #zoom} drives the close-up capture (matrix scale about the screen centre);
 * the current stack pixelates while MSDF/SDF stay crisp.
 *
 * Not wired into the client — reachable only via {@link PocBootstrap} (CLUB_POC=1).
 */
public class RenderCompareScreen extends Screen {

    // local palette (current design tokens) — self-contained, no coupling to Theme
    private static final int BG        = 0xFF0B111A;
    private static final int PANEL     = 0xFF0F1624;
    private static final int SURFACE   = 0xFF131B2A;
    private static final int BORDER    = 0xFF1D2536;
    private static final int TEXT      = 0xFFF4F6FA;
    private static final int MUTED     = 0xFFA6ADBB;
    private static final int DESC      = 0xFF767E8E;
    private static final int ACCENT    = 0xFF7CABFF;
    private static final int ACCENT2   = 0xFF78D7FF;
    private static final int FILL_OFF  = 0xFF222A38;
    private static final int FILL_TRACK= 0xFF1D2536;
    private static final int DIVIDER   = 0x1AFFFFFF;
    private static final int STATE_GOOD= 0xFF2ECC71, STATE_WARN = 0xFFE3C66A, STATE_LOW = 0xFFE06B6B;

    private float zoom = 1f;
    private float zoomCx, zoomCy;

    private final ItemStack[] armor = sampleArmor();
    private final float[] armorFrac = {0.92f, 0.87f, 0.79f, 0.54f};

    public RenderCompareScreen() { super(Text.literal("Render Compare")); }

    public void setZoom(float z, float cx, float cy) { this.zoom = z; this.zoomCx = cx; this.zoomCy = cy; }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, BG);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        boolean zoomed = zoom != 1f;
        if (zoomed) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(zoomCx, zoomCy, 0);
            ctx.getMatrices().scale(zoom, zoom, 1f);
            ctx.getMatrices().translate(-zoomCx, -zoomCy, 0);
        }

        int half = width / 2;
        // centre divider
        ctx.fill(half, 0, half + 1, height, DIVIDER);

        // column headers
        ClubFont.drawCat(ctx, "CURRENT", 24, 18, TEXT, false);
        PocText.drawCat(ctx, "NEW", half + 24, 18, TEXT);

        renderColumn(ctx, 0, half, false);
        renderColumn(ctx, half, half, true);

        if (zoomed) ctx.getMatrices().pop();
    }

    /** Renders the six elements in one column; {@code useNew} swaps only the backend. */
    private void renderColumn(DrawContext ctx, int colX, int colW, boolean useNew) {
        int p = 24;
        int x = colX + p;
        int contentW = colW - 2 * p;
        int y = 64;

        // 1 — heading
        drawCat(ctx, useNew, "Premium UI", x, y, TEXT);
        drawDesc(ctx, useNew, "MSDF text · SDF shapes · glow · gradient", x, y + 22, DESC);
        y += 48;

        // 2 — toggle (ON: accent gradient pill + soft glow + white knob)
        drawList(ctx, useNew, "Enable", x, y + 3, MUTED);
        int tgX = x + contentW - 36, tgY = y, tgW = 34, tgH = 16;
        glow(ctx, useNew, tgX, tgY, tgW, tgH, 8, withA(ACCENT, 0x99), 11f);
        gradient(ctx, useNew, tgX, tgY, tgW, tgH, 8, ACCENT, ACCENT2);
        int knob = tgH - 4;
        roundedRect(ctx, useNew, tgX + tgW - knob - 2, tgY + 2, knob, knob, knob / 2f, 0xFFFFFFFF);
        y += 34;

        // 3 — slider (flat accent fill + glow under fill + white knob + value)
        drawList(ctx, useNew, "Strength", x, y + 3, MUTED);
        int slX = x + 64, slW = contentW - 64 - 40, slY = y + 8;
        roundedRect(ctx, useNew, slX, slY, slW, 4, 2, FILL_TRACK);
        int fillW = Math.round(slW * 0.70f);
        glow(ctx, useNew, slX, slY, fillW, 4, 6, withA(ACCENT, 0x88), 9f);
        roundedRect(ctx, useNew, slX, slY, fillW, 4, 2, ACCENT);
        roundedRect(ctx, useNew, slX + fillW - 5, slY - 3, 10, 10, 5f, 0xFFFFFFFF);
        String val = "70%";
        drawSmall(ctx, useNew, val, colX + colW - p - (int) textW(useNew, val, 's'), y + 3, TEXT);
        y += 34;

        // 4 — card (surface + border + inner gradient bar to show gradient quality)
        int cardH = 76;
        roundedRect(ctx, useNew, x, y, contentW, cardH, 10, SURFACE);
        roundedBorder(ctx, useNew, x, y, contentW, cardH, 10, 1f, BORDER);
        drawList(ctx, useNew, "Card title", x + 14, y + 14, TEXT);
        drawDesc(ctx, useNew, "Subtitle and supporting copy.", x + 14, y + 32, MUTED);
        // glowing accent status dot — the clearest glow A/B (rings vs continuous halo)
        int gdX = x + contentW - 20, gdY = y + 16;
        glow(ctx, useNew, gdX, gdY, 6, 6, 3, withA(ACCENT, 0xCC), 10f);
        roundedRect(ctx, useNew, gdX, gdY, 6, 6, 3f, ACCENT);
        gradient(ctx, useNew, x + 14, y + cardH - 18, contentW - 28, 6, 3, ACCENT, ACCENT2);
        y += cardH + 22;

        // 5 — Armor HUD sample
        int valW = 0;
        for (int i = 0; i < armor.length; i++) valW = Math.max(valW, (int) textW(useNew, pct(armorFrac[i]), 'n'));
        for (int i = 0; i < armor.length; i++) {
            int ry = y + i * 20;
            ctx.drawItem(armor[i], x, ry);
            drawNameShadow(ctx, useNew, pct(armorFrac[i]), x + 22, ry + 3, TEXT);
            int dotX = x + 22 + valW + 6, dotY = ry + 6;
            roundedRect(ctx, useNew, dotX, dotY, 4, 4, 2f, stateColor(armorFrac[i]));
        }

        // 6 — Target HUD sample (name + HP + thin HP bar)
        int tx = colX + colW - p - 150, ty = y;
        int tw = 150;
        drawNameShadow(ctx, useNew, "PlayerName", tx, ty, TEXT);
        drawHudShadow(ctx, useNew, "18.6 HP", tx, ty + 16, MUTED);
        roundedRect(ctx, useNew, tx, ty + 32, tw, 2, 1f, FILL_TRACK);
        roundedRect(ctx, useNew, tx, ty + 32, Math.round(tw * 0.62f), 2, 1f, ACCENT);
    }

    // ---- backend-switching primitives (LEFT = current, RIGHT = new) ----------

    private void roundedRect(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, int c) {
        if (n) PocRenderer.roundedRect(ctx, x, y, w, h, r, c);
        else RenderHelper.roundedRect(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, c);
    }
    private void roundedBorder(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, float th, int c) {
        if (n) PocRenderer.roundedBorder(ctx, x, y, w, h, r, th, c);
        else RenderHelper.roundedBorder(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, c);
    }
    private void glow(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, int c, float feather) {
        if (n) PocRenderer.glow(ctx, x, y, w, h, r, feather, c);
        else RenderHelper.glow(ctx, (int) x, (int) y, (int) w, (int) h, c);
    }
    private void gradient(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, int a, int b) {
        if (n) PocRenderer.gradientRoundedRect(ctx, x, y, w, h, r, a, b, false);
        else RenderHelper.gradientRoundedRect(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, a, b);
    }

    private void drawCat(DrawContext ctx, boolean n, String s, int x, int y, int c)  { if (n) PocText.drawCat(ctx, s, x, y, c); else ClubFont.drawCat(ctx, s, x, y, c, false); }
    private void drawList(DrawContext ctx, boolean n, String s, int x, int y, int c) { if (n) PocText.drawList(ctx, s, x, y, c); else ClubFont.drawList(ctx, s, x, y, c, false); }
    private void drawDesc(DrawContext ctx, boolean n, String s, int x, int y, int c) { if (n) PocText.drawDesc(ctx, s, x, y, c); else ClubFont.drawDesc(ctx, s, x, y, c, false); }
    private void drawSmall(DrawContext ctx, boolean n, String s, int x, int y, int c){ if (n) PocText.drawSmall(ctx, s, x, y, c); else ClubFont.drawSmall(ctx, s, x, y, c, false); }
    private void drawNameShadow(DrawContext ctx, boolean n, String s, int x, int y, int c) { if (n) PocText.drawNameShadow(ctx, s, x, y, c); else ClubFont.drawNameShadow(ctx, s, x, y, c); }
    private void drawHudShadow(DrawContext ctx, boolean n, String s, int x, int y, int c)  { if (n) PocText.drawHudShadow(ctx, s, x, y, c); else ClubFont.drawHudShadow(ctx, s, x, y, c); }

    private float textW(boolean n, String s, char role) {
        if (role == 'n') return n ? PocText.widthName(s) : ClubFont.widthName(s);
        return n ? PocText.widthSmall(s) : ClubFont.widthSmall(s);
    }

    // ---- helpers -------------------------------------------------------------

    private static String pct(float f) { return Math.round(f * 100) + "%"; }
    private static int withA(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }
    private static int stateColor(float f) { return f >= 0.70f ? STATE_GOOD : f >= 0.40f ? STATE_WARN : STATE_LOW; }

    private static ItemStack[] sampleArmor() {
        float[] f = {0.92f, 0.87f, 0.79f, 0.54f};
        ItemStack[] s = {
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS),
        };
        for (int i = 0; i < s.length; i++) s[i].setDamage(Math.round((1 - f[i]) * s[i].getMaxDamage()));
        return s;
    }

    @Override
    public boolean shouldPause() { return false; }
}
