package com.club.hud;

import com.club.config.ClubConfig;
import com.club.gui.Icons;
import com.club.gui.Theme;
import com.club.util.ClubFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Armor durability — the vanilla item icon and a value, no card, no bars, no
 * labels. The value is the primary element: white SemiBold, instantly legible.
 * Durability state is shown by a small coloured dot beside the value (green /
 * amber / red), never by tinting the digits and never as a gradient. Vertical
 * (default) is a column of {@code [icon] value ●}; horizontal is a row of
 * {@code icon / value ●} cells. Empty pieces are skipped.
 */
public final class ArmorHud {
    private ArmorHud() {}

    // Tight icon→value gap so each piece reads as one unit; a small state dot trails.
    private static final int ICON = 16, GAP = 6, ROW = 20, DOT = 4, DOTGAP = 6;

    private static ItemStack[] pieces(MinecraftClient mc) {
        return new ItemStack[]{
            mc.player.getInventory().getArmorStack(3), // head
            mc.player.getInventory().getArmorStack(2), // chest
            mc.player.getInventory().getArmorStack(1), // legs
            mc.player.getInventory().getArmorStack(0), // feet
        };
    }

    /** Representative pieces for the editor preview (when nothing is equipped). */
    private static ItemStack[] sampleStacks() {
        float[] f = {0.92f, 0.87f, 0.79f, 0.54f};
        ItemStack[] s = {
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS),
        };
        for (int i = 0; i < s.length; i++) s[i].setDamage(Math.round((1 - f[i]) * s[i].getMaxDamage()));
        return s;
    }

    private static float frac(ItemStack s) {
        int max = s.getMaxDamage();
        return max > 0 ? (float) (max - s.getDamage()) / max : 1f;
    }

    private static String value(ItemStack s, boolean percent) {
        if (percent) return Math.round(frac(s) * 100) + "%";
        int max = s.getMaxDamage();
        return (max - s.getDamage()) + "/" + max;
    }

    private static int valueWidth(ItemStack[] ps, boolean percent) {
        int w = 0;
        for (ItemStack s : ps) if (!s.isEmpty()) w = Math.max(w, ClubFont.widthName(value(s, percent)));
        return w;
    }

    private static int count(ItemStack[] ps) {
        int n = 0; for (ItemStack s : ps) if (!s.isEmpty()) n++; return n;
    }

    private static int[] sizeOf(ClubConfig.Hud cfg, ItemStack[] ps) {
        int count = count(ps);
        if (count == 0) return new int[]{0, 0};
        int valW = valueWidth(ps, cfg.armorPercent);
        if (cfg.armorVertical) return new int[]{ICON + GAP + valW + DOTGAP + DOT, (count - 1) * ROW + ICON};
        int cell = Math.max(ICON, valW + DOTGAP + DOT);
        return new int[]{count * cell + (count - 1) * GAP, ICON + 2 + 12};
    }

    /** Content [width, height] for the live armor (0,0 if none) — used by the editor. */
    public static int[] size(ClubConfig.Hud cfg, MinecraftClient mc) { return sizeOf(cfg, pieces(mc)); }

    /** Content [width, height] for the editor preview. */
    public static int[] sampleSize(ClubConfig.Hud cfg) { return sizeOf(cfg, sampleStacks()); }

    /** Draws the rows at the local origin (matrix already translated/scaled). */
    private static void drawRows(DrawContext ctx, ClubConfig.Hud cfg, ItemStack[] ps) {
        int valW = valueWidth(ps, cfg.armorPercent);
        if (cfg.armorVertical) {
            int row = 0;
            for (ItemStack s : ps) {
                if (s.isEmpty()) continue;
                int ry = row * ROW;
                ctx.drawItem(s, 0, ry);
                // value: white SemiBold (the main element), soft dark shadow.
                ClubFont.drawNameShadow(ctx, value(s, cfg.armorPercent), ICON + GAP, ry + (ICON - 11) / 2, Theme.TEXT);
                // small state dot, aligned in a column after the widest value.
                Icons.dot(ctx, ICON + GAP + valW + DOTGAP, ry + (ICON - DOT) / 2, DOT, Theme.stateColor(frac(s)));
                row++;
            }
        } else {
            int cell = Math.max(ICON, valW + DOTGAP + DOT);
            int col = 0;
            for (ItemStack s : ps) {
                if (s.isEmpty()) continue;
                int cx = col * (cell + GAP);
                ctx.drawItem(s, cx + (cell - ICON) / 2, 0);
                String v = value(s, cfg.armorPercent);
                int vw = ClubFont.widthName(v);
                int sx = cx + (cell - (vw + DOTGAP + DOT)) / 2;
                ClubFont.drawNameShadow(ctx, v, sx, ICON + 2, Theme.TEXT);
                Icons.dot(ctx, sx + vw + DOTGAP, ICON + 2 + (11 - DOT) / 2, DOT, Theme.stateColor(frac(s)));
                col++;
            }
        }
    }

    private static void renderAt(DrawContext ctx, ClubConfig.Hud cfg, ItemStack[] ps) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cfg.armorX, cfg.armorY, 0);
        ctx.getMatrices().scale(cfg.armorScale, cfg.armorScale, 1f);
        drawRows(ctx, cfg, ps);
        ctx.getMatrices().pop();
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        ItemStack[] ps = pieces(mc);
        if (count(ps) == 0) return;
        renderAt(ctx, ClubConfig.get().hud, ps);
    }

    /** Editor preview at the configured position/scale. */
    public static void drawSample(DrawContext ctx) {
        renderAt(ctx, ClubConfig.get().hud, sampleStacks());
    }
}
