package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.IconGlyph;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Armor on the V4 "Chips" language (Stage 13.6/14): ONE capsule for the whole set — separate
 * per-piece chips read as choppy slivers (owner). Each row inside is [piece icon + exact value]
 * with its own LIVE LINE underneath (the in-capsule sibling of the edge bar, echoing the menu
 * card's stripe): state-coloured durability, smooth threshold crossings. Digits stay exact (truth).
 * Vertical rows (default) or horizontal cells. Empty pieces are skipped.
 *
 * <p>Stage 14: pieces are OUR SDF icons (helmet/chest/legs/boots/elytra), tinted by MATERIAL
 * association (diamond cyan, gold amber, netherite mauve…) — vanilla item sprites clashed with the
 * flat language, and dropping them removes the HUD's last legacy seam (HudSprites is gone; the
 * element is now pure V2 renderer). The value + line stay neutral/state-coloured — the tint names
 * the material, it never grades the number. On LEGACY the icon column is skipped (glyphs only).</p>
 */
public final class ArmorElement extends HudElement {
    private static final int ICON = 16, GAP = 5;             // sprite size; sprite ↔ value gap
    private static final float PAD_X = 8f, PAD_Y = 5f;       // capsule padding
    private static final float BAR_H = 2f, BAR_GAP = 2f;     // per-row live line + gap above it
    private static final float ROW_BLOCK = ICON + BAR_GAP + BAR_H;   // sprite + gap + line = 20
    private static final float ROW_GAP = 5f, CELL_GAP = 12f; // vertical row spacing / horizontal cell spacing

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

    // Snapshot of the last non-empty set: when the final piece is removed the canvas fades the
    // element out — during that fade we draw this frozen frame instead of popping to nothing.
    private ItemStack[] lastLive;

    /** The stacks to draw: live set, frozen last frame during the exit fade, or the editor sample. */
    private ItemStack[] stacks(MinecraftClient mc, boolean live) {
        if (!live) return sampleStacks();
        ItemStack[] ps = pieces(mc);
        if (count(ps) == 0) return lastLive != null ? lastLive : ps;
        lastLive = ps;
        return ps;
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
        // count mode = REMAINING durability only (owner 2026-07-03): "407", not "407/407" —
        // the maximum is implied by the live edge, the chip stays as compact as the percent mode
        return String.valueOf(s.getMaxDamage() - s.getDamage());
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

    /** Inner cell width: sprite + gap + value column (uniform → values right-align down the stack). */
    private int cellW(ItemStack[] ps, boolean percent) {
        return Math.round(ICON + GAP + valueWidth(ps, percent));
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        ItemStack[] ps = stacks(mc, live);
        int count = count(ps);
        if (count == 0) return new int[]{0, 0};
        int cell = cellW(ps, h().armorPercent);
        if (h().armorVertical)
            return new int[]{ Math.round(2 * PAD_X + cell),
                              Math.round(2 * PAD_Y + count * ROW_BLOCK + (count - 1) * ROW_GAP) };
        return new int[]{ Math.round(2 * PAD_X + count * cell + (count - 1) * CELL_GAP),
                          Math.round(2 * PAD_Y + ROW_BLOCK) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        Typography ty = Tokens.type();
        float base = ty.body().size();
        ItemStack[] ps = stacks(mc, live);
        boolean percent = h().armorPercent;
        boolean vertical = h().armorVertical;
        int cell = cellW(ps, percent);
        float lh = ty.body().lineHeight();
        TextStyle style = TextStyle.of(Weight.SEMIBOLD, base * s, Color.scaleAlpha(Tokens.palette().textHi(), alpha))
                .effect(HudPaint.textShadow(alpha));

        // ONE capsule for the whole set — the rows inside carry their own live lines.
        int[] cs = contentSize(mc, live);
        HudPaint.chip(ctx, ox, oy, cs[0] * s, cs[1] * s, HudPaint.CHIP_RAD * s, alpha);

        int i = 0;
        for (int slot = 0; slot < ps.length; slot++) {
            ItemStack st = ps[slot];
            if (st.isEmpty()) continue;
            float cx = ox + (PAD_X + (vertical ? 0 : i * (cell + CELL_GAP))) * s;
            float cy = oy + (PAD_Y + (vertical ? i * (ROW_BLOCK + ROW_GAP) : 0)) * s;
            float f = frac(st);

            icon(slot, st).draw(ctx, cx, cy, ICON * s, Color.scaleAlpha(materialTint(st), alpha));
            // tabular value right-aligned in the shared column: equal-length values are pixel-identical
            String v = value(st, percent);
            float tw = HudText.width(v, Weight.SEMIBOLD, base);
            HudText.draw(ctx, v, cx + (cell - tw) * s, cy + (ICON - lh) * 0.5f * s, style, base, s);
            // the row's live line — durability, state-coloured
            HudPaint.rowBar(ctx, cx, cy + (ICON + BAR_GAP) * s, cell * s, BAR_H, f, stateColor(f), s, alpha);
            i++;
        }
    }

    /** Slot → piece silhouette; elytra gets its own wings in the chest slot. */
    private static IconGlyph icon(int slot, ItemStack st) {
        if (st.getItem() instanceof ElytraItem) return IconGlyph.ARMOR_ELYTRA;
        return switch (slot) {
            case 0  -> IconGlyph.ARMOR_HELMET;
            case 1  -> IconGlyph.ARMOR_CHEST;
            case 2  -> IconGlyph.ARMOR_LEGS;
            default -> IconGlyph.ARMOR_BOOTS;
        };
    }

    /** Icon tint = MATERIAL association (identity only — never grades the durability number). */
    private static int materialTint(ItemStack st) {
        if (st.getItem() instanceof ElytraItem) return 0xFFA99EC2;          // phantom-membrane mauve
        if (!(st.getItem() instanceof ArmorItem ai)) return 0xFFA9B4C4;
        String m = ai.getMaterial().getKey().map(k -> k.getValue().getPath()).orElse("");
        return switch (m) {
            case "leather"   -> 0xFFB39070;
            case "chainmail" -> 0xFF9AA6B4;
            case "iron"      -> 0xFFC9D0D8;
            case "gold"      -> 0xFFE4C87A;
            case "diamond"   -> 0xFF7CD0DC;
            case "netherite" -> 0xFFA28A96;
            case "turtle"    -> 0xFF7FBFA6;
            default          -> 0xFFA9B4C4;   // unknown/modded — neutral steel
        };
    }
}
