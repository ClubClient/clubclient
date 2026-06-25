package com.club.poc;

import com.club.poc.render.PocRenderer;
import com.club.poc.render.PocText;
import com.club.util.ClubFont;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * "Real game scale" frame: the live title-screen panorama (a genuine 3D game
 * render) dimmed for contrast, with the two HUD samples drawn at the player's
 * actual GUI scale — LEFT current stack, RIGHT new stack. Shows that the new HUD
 * reads at least as cleanly as the current one in real play. PoC-only.
 */
public class RealScaleScreen extends TitleScreen {

    private static final int TEXT = 0xFFF4F6FA, MUTED = 0xFFA6ADBB, ACCENT = 0xFF7CABFF, FILL_TRACK = 0xFF1D2536;
    private static final int STATE_GOOD = 0xFF2ECC71, STATE_WARN = 0xFFE3C66A, STATE_LOW = 0xFFE06B6B;

    private final ItemStack[] armor = sampleArmor();
    private final float[] frac = {0.92f, 0.87f, 0.79f, 0.54f};

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);          // live panorama + title
        ctx.fill(0, 0, width, height, 0xB3000000);  // dim for a clean game-like backdrop

        int half = width / 2;
        ClubFont.drawCat(ctx, "CURRENT — real scale", 32, 24, TEXT, false);
        PocText.drawCat(ctx, "NEW — real scale", half + 32, 24, TEXT);
        ctx.fill(half, 0, half + 1, height, 0x1AFFFFFF);

        drawHudGroup(ctx, 36, 64, false);
        drawHudGroup(ctx, half + 36, 64, true);
    }

    private void drawHudGroup(DrawContext ctx, int x, int y, boolean n) {
        // Armor sample
        int valW = 0;
        for (int i = 0; i < armor.length; i++) valW = Math.max(valW, (int) widthName(n, pct(frac[i])));
        for (int i = 0; i < armor.length; i++) {
            int ry = y + i * 20;
            ctx.drawItem(armor[i], x, ry);
            nameShadow(ctx, n, pct(frac[i]), x + 22, ry + 3, TEXT);
            roundedRect(ctx, n, x + 22 + valW + 6, ry + 6, 4, 4, 2f, stateColor(frac[i]));
        }
        // Target sample beside the armor
        int tx = x + 120, ty = y + 8, tw = 130;
        nameShadow(ctx, n, "PlayerName", tx, ty, TEXT);
        hudShadow(ctx, n, "18.6 HP", tx, ty + 16, MUTED);
        roundedRect(ctx, n, tx, ty + 32, tw, 2, 1f, FILL_TRACK);
        roundedRect(ctx, n, tx, ty + 32, Math.round(tw * 0.62f), 2, 1f, ACCENT);
    }

    // backend switches
    private void roundedRect(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, int c) {
        if (n) PocRenderer.roundedRect(ctx, x, y, w, h, r, c);
        else RenderHelper.roundedRect(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, c);
    }
    private void nameShadow(DrawContext ctx, boolean n, String s, int x, int y, int c) { if (n) PocText.drawNameShadow(ctx, s, x, y, c); else ClubFont.drawNameShadow(ctx, s, x, y, c); }
    private void hudShadow(DrawContext ctx, boolean n, String s, int x, int y, int c)  { if (n) PocText.drawHudShadow(ctx, s, x, y, c); else ClubFont.drawHudShadow(ctx, s, x, y, c); }
    private float widthName(boolean n, String s) { return n ? PocText.widthName(s) : ClubFont.widthName(s); }

    private static String pct(float f) { return Math.round(f * 100) + "%"; }
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
}
