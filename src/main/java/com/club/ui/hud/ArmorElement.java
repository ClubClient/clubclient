package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
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
 * Armor durability on the V2 stack: per equipped piece a vanilla item sprite + its value (white SemiBold) +
 * a small durability dot (green ≥70% / amber ≥40% / red &lt;40%). Vertical column (default) or a row of cells.
 * The sprite is the one thing the V2 renderer can't draw, so it goes through the frame's {@link HudSprites}
 * DrawContext (matrix-scaled to the element's scale); value/dot/panel are pure V2. Empty pieces are skipped.
 */
public final class ArmorElement extends HudElement {
    private static final int ICON = 16, GAP = 6, DOT = 4, DOTGAP = 6;   // value text is body-size (smaller than the 16px icon); row height = shared LIST_ROW

    public ArmorElement() { super("armor"); }

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
        for (ItemStack s : ps) if (!s.isEmpty()) w = Math.max(w, tabularWidth(value(s, percent), size));
        return Math.round(w);
    }

    // --- tabular figures: digits render in a fixed-width cell (the widest digit's advance) so equal-length
    // values are pixel-identical and every column — icon↔value, value↔dot, and the dots themselves — lines up
    // regardless of which digits appear (e.g. "481/481" no longer drifts from "592/592"). Non-digits keep their
    // natural width. This is why premium HUDs use tabular numerals for stats.
    private static float digitCell(float size) {
        float w = 0;
        for (char c = '0'; c <= '9'; c++) w = Math.max(w, Ui.text().width(String.valueOf(c), Weight.SEMIBOLD, size));
        return w;
    }
    /** Unscaled width of {@code v} rendered with tabular digits. */
    private static float tabularWidth(String v, float size) {
        float cell = digitCell(size), w = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            w += Character.isDigit(c) ? cell : Ui.text().width(String.valueOf(c), Weight.SEMIBOLD, size);
        }
        return w;
    }
    /** Draw {@code v} with tabular digits, top-left at (x,y); each digit is right-aligned inside its cell. */
    private static void drawTabular(UiContext ctx, String v, float x, float y, TextStyle style, float s) {
        float size = Tokens.type().body().size(), cell = digitCell(size), penX = x;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            String ch = String.valueOf(c);
            if (Character.isDigit(c)) {
                float gw = Ui.text().width(ch, Weight.SEMIBOLD, size);
                ctx.text().draw(ch, penX + (cell - gw) * s, y, style);   // right-align the digit in its cell
                penX += cell * s;
            } else {
                ctx.text().draw(ch, penX, y, style);
                penX += Ui.text().width(ch, Weight.SEMIBOLD, size) * s;
            }
        }
    }
    /** Durability dot colour — green/amber/red with a smooth crossing at the 0.70 / 0.40 thresholds
     *  (a narrow lerp band each side) so a draining piece shifts colour instead of snapping. */
    private static int stateColor(float f) {
        int good = Tokens.palette().stateGood(), warn = Tokens.palette().stateWarn(), low = Tokens.palette().stateLow();
        if (f >= 0.73f) return good;
        if (f >= 0.67f) return Color.lerp(warn, good, (f - 0.67f) / 0.06f);
        if (f >= 0.43f) return warn;
        if (f >= 0.37f) return Color.lerp(low, warn, (f - 0.37f) / 0.06f);
        return low;
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        ItemStack[] ps = live ? pieces(mc) : sampleStacks();
        int count = count(ps);
        if (count == 0) return new int[]{0, 0};
        int valW = valueWidth(ps, h().armorPercent);
        if (h().armorVertical) return new int[]{ ICON + GAP + valW + DOTGAP + DOT, (count - 1) * LIST_ROW + ICON };
        int cell = Math.max(ICON, valW + DOTGAP + DOT);
        return new int[]{ count * cell + (count - 1) * GAP, ICON + 2 + Math.round(Tokens.type().body().lineHeight()) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var r = ctx.renderer(); Typography ty = Tokens.type();
        ItemStack[] ps = live ? pieces(mc) : sampleStacks();
        boolean percent = h().armorPercent;
        int valW = valueWidth(ps, percent);
        float lh = ty.body().lineHeight();
        TextStyle style = TextStyle.of(Weight.SEMIBOLD, ty.body().size() * s, Color.scaleAlpha(Tokens.palette().textHi(), alpha))
                .effect(HudPaint.textShadow(alpha));
        DrawContext dc = HudSprites.ctx();

        if (h().armorVertical) {
            // tabular values right-aligned in the value column: the dots form one straight column at the tile's
            // right edge, and equal-length values (407/407, 481/481) share icon↔value + value↔dot spacing exactly.
            int row = 0;
            for (ItemStack st : ps) {
                if (st.isEmpty()) continue;
                float ry = oy + row * LIST_ROW * s;
                drawSprite(dc, st, ox, ry, s);
                String v = value(st, percent);
                float tw = tabularWidth(v, ty.body().size());
                drawTabular(ctx, v, ox + (ICON + GAP + valW - tw) * s, ry + (ICON - lh) * 0.5f * s, style, s);
                float dcx = ox + (ICON + GAP + valW + DOTGAP + DOT * 0.5f) * s;
                r.circle(dcx, ry + ICON * 0.5f * s, DOT * 0.5f * s, Color.scaleAlpha(stateColor(frac(st)), alpha));
                row++;
            }
        } else {
            int cell = Math.max(ICON, valW + DOTGAP + DOT);
            int col = 0;
            for (ItemStack st : ps) {
                if (st.isEmpty()) continue;
                float cx = ox + col * (cell + GAP) * s;
                drawSprite(dc, st, cx + (cell - ICON) * 0.5f * s, oy, s);
                String v = value(st, percent);
                float vw = tabularWidth(v, ty.body().size());
                float sx = cx + (cell - (vw + DOTGAP + DOT)) * 0.5f * s;
                drawTabular(ctx, v, sx, oy + (ICON + 2) * s, style, s);
                r.circle(sx + (vw + DOTGAP + DOT * 0.5f) * s, oy + (ICON + 2) * s + lh * 0.5f * s, DOT * 0.5f * s,
                        Color.scaleAlpha(stateColor(frac(st)), alpha));
                col++;
            }
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
