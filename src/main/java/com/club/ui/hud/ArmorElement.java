package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Armor on the V4 "Chips" language (Stage 13): each equipped piece is its own capsule
 * [sprite + exact value], and the capsule's LIVE EDGE is that piece's durability — state-coloured
 * (green/amber/red with smooth threshold crossings). The old dot indicator is gone: the edge carries
 * the state, the digits stay exact (truth). Vertical stack (default) or a row of capsules.
 * The sprite is the one thing the V2 renderer can't draw, so it goes through the frame's
 * {@link HudSprites} DrawContext (matrix-scaled). Empty pieces are skipped.
 */
public final class ArmorElement extends HudElement {
    private static final int ICON = 16, GAP = 7;            // sprite size; sprite ↔ value gap
    private static final float CHIP_H = 28f, CHIP_GAP = 4f; // capsule height / stack gap (unscaled)
    private static final float PAD_X = 10f, PAD_TOP = 4f;   // capsule padding (icon band sits high; edge zone below)
    private static final float BAR_H = 2f;                  // live edge height (rows are quieter than Target's 3px)

    public ArmorElement() { super("armor"); }
    @Override public String displayName() { return "Armor"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().armorX; }
    @Override public int   cfgY() { return h().armorY; }
    @Override public void  cfgX(int v) { h().armorX = v; }
    @Override public void  cfgY(int v) { h().armorY = v; }
    @Override public float cfgScale() { return h().armorScale; }
    @Override public boolean cfgEnabled() { return h().armor; }

    /** In-world: show only when at least one piece is equipped (editor shows a diamond sample). */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || count(pieces(mc)) > 0;
    }

    // --- data (ported from legacy hud/ArmorHud) ---
    private static ItemStack[] pieces(MinecraftClient mc) {
        return new ItemStack[]{
            mc.player.getInventory().getArmorStack(3), mc.player.getInventory().getArmorStack(2),
            mc.player.getInventory().getArmorStack(1), mc.player.getInventory().getArmorStack(0),
        };
    }
    private static ItemStack[] sampleStacks() {
        float[] f = {0.92f, 0.87f, 0.79f, 0.54f};
        ItemStack[] s = {
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS),
        };
        for (int i = 0; i < s.length; i++) s[i].setDamage(Math.round((1 - f[i]) * s[i].getMaxDamage()));
        return s;
    }
    private static int count(ItemStack[] ps) { int n = 0; for (ItemStack s : ps) if (!s.isEmpty()) n++; return n; }
    private static float frac(ItemStack s) { int max = s.getMaxDamage(); return max > 0 ? (float) (max - s.getDamage()) / max : 1f; }
    private String value(ItemStack s, boolean percent) {
        if (percent) return Math.round(frac(s) * 100) + "%";
        int max = s.getMaxDamage();
        return (max - s.getDamage()) + "/" + max;
    }
    private int valueWidth(ItemStack[] ps, boolean percent) {
        float size = Tokens.type().body().size(), w = 0;
        for (ItemStack s : ps) if (!s.isEmpty()) w = Math.max(w, HudText.width(value(s, percent), Weight.SEMIBOLD, size));
        return Math.round(w);
    }
    /** Edge colour — green/amber/red with a smooth crossing at the 0.70 / 0.40 thresholds
     *  (a narrow lerp band each side) so a draining piece shifts colour instead of snapping. */
    private static int stateColor(float f) {
        int good = Tokens.palette().stateGood(), warn = Tokens.palette().stateWarn(), low = Tokens.palette().stateLow();
        if (f >= 0.73f) return good;
        if (f >= 0.67f) return Color.lerp(warn, good, (f - 0.67f) / 0.06f);
        if (f >= 0.43f) return warn;
        if (f >= 0.37f) return Color.lerp(low, warn, (f - 0.37f) / 0.06f);
        return low;
    }

    // V4: capsules are drawn per piece in paint(); no shared outer panel.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }
    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) { }

    /** Uniform capsule width (all chips share it → the stack reads as one column). */
    private int chipW(ItemStack[] ps, boolean percent) {
        return Math.round(2 * PAD_X + ICON + GAP + valueWidth(ps, percent));
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        ItemStack[] ps = live ? pieces(mc) : sampleStacks();
        int count = count(ps);
        if (count == 0) return new int[]{0, 0};
        int cw = chipW(ps, h().armorPercent);
        if (h().armorVertical)
            return new int[]{ cw, Math.round(count * CHIP_H + (count - 1) * CHIP_GAP) };
        return new int[]{ Math.round(count * cw + (count - 1) * CHIP_GAP), Math.round(CHIP_H) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        Typography ty = Tokens.type();
        float base = ty.body().size();
        ItemStack[] ps = live ? pieces(mc) : sampleStacks();
        boolean percent = h().armorPercent;
        boolean vertical = h().armorVertical;
        float cw = chipW(ps, percent);
        int valW = valueWidth(ps, percent);
        float lh = ty.body().lineHeight();
        TextStyle style = TextStyle.of(Weight.SEMIBOLD, base * s, Color.scaleAlpha(Tokens.palette().textHi(), alpha))
                .effect(HudPaint.textShadow(alpha));
        DrawContext dc = HudSprites.ctx();

        int i = 0;
        for (ItemStack st : ps) {
            if (st.isEmpty()) continue;
            float cx = vertical ? ox : ox + i * (cw + CHIP_GAP) * s;
            float cy = vertical ? oy + i * (CHIP_H + CHIP_GAP) * s : oy;
            float f = frac(st);

            HudPaint.chip(ctx, cx, cy, cw * s, CHIP_H * s, HudPaint.CHIP_RAD * s, alpha);
            HudPaint.edgeBar(ctx, cx, cy, cw * s, CHIP_H * s, BAR_H, f, stateColor(f), s, alpha);

            drawSprite(dc, st, cx + PAD_X * s, cy + PAD_TOP * s, s);
            // tabular value right-aligned in the shared column: equal-length values are pixel-identical
            String v = value(st, percent);
            float tw = HudText.width(v, Weight.SEMIBOLD, base);
            HudText.draw(ctx, v, cx + (PAD_X + ICON + GAP + valW - tw) * s,
                    cy + (PAD_TOP + (ICON - lh) * 0.5f) * s, style, base, s);
            i++;
        }
    }

    /** Vanilla item sprite via the frame's DrawContext, matrix-scaled to the element scale. */
    private static void drawSprite(DrawContext dc, ItemStack st, float x, float y, float scale) {
        if (dc == null) return;   // no DrawContext this frame (defensive)
        dc.getMatrices().push();
        dc.getMatrices().translate(x, y, 0f);
        dc.getMatrices().scale(scale, scale, 1f);
        dc.drawItem(st, 0, 0);
        dc.getMatrices().pop();
    }
}
