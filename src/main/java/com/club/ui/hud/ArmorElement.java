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
 * Armor (Stage 15): MASSIVE solid piece icons tinted by MATERIAL association (diamond cyan, gold
 * amber, netherite mauve…), each with an exact value and a LIVE LINE directly under the ICON —
 * the same stripe-under-icon gesture as the menu cards, so it reads as that piece's gauge, never
 * as a divider (owner: full-width lines read as element separators). NO capsule in-world — clear
 * icons don't need a ground (owner pt. 4); the editor still shows the rounded placeholder box.
 * Digits stay exact (truth); the tint names the material, it never grades the number.
 *
 * <p>Two layouts ({@code armorLayout}, owner Stage 18 — the old horizontal-cells view is retired):
 * 0 = COLUMN, rows of [icon  value] with the gauge under each icon; 1 = LINE, icons in a row with
 * the exact value ABOVE each icon (label size, centered) and the gauge below — value stays
 * available in both (Percent/Count). Empty pieces are skipped. On LEGACY the icon glyphs are
 * skipped (values/lines remain).</p>
 */
public final class ArmorElement extends HudElement {
    private static final int ICON = 16, GAP = 5;             // icon size; icon ↔ value gap (COLUMN)
    private static final float BAR_H = 2f, BAR_GAP = 1f;     // per-piece live line + gap above it
    private static final float ROW_BLOCK = ICON + BAR_GAP + BAR_H;   // icon + gap + line = 19
    // Row pitch 25 → 21 (owner: the column read ~15-20% too long) — the gauge hugs its icon.
    private static final float ROW_GAP = 2f;                 // COLUMN row spacing
    private static final float LINE_GAP = 8f, VAL_GAP = 2f;  // LINE cell spacing; value ↔ icon gap
    // Centering the value by lineHeight leaves the DIGITS ~1px above the icon's optical middle
    // (digits have no descender) — owner sees it. Nudge the baseline down.
    private static final float VAL_NUDGE = 1f;

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
    private int valueWidth(ItemStack[] ps, boolean percent, float size) {
        float w = 0;
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

    // Stage 15: no ground at all in-world (clear massive icons carry themselves); editor keeps its box.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }
    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) { }

    /** 0 = column, 1 = line; old configs may hold 2 (the retired horizontal-cells view) — clamp. */
    private int layout() { return Math.min(1, Math.max(0, h().armorLayout)); }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        ItemStack[] ps = stacks(mc, live);
        int count = count(ps);
        if (count == 0) return new int[]{0, 0};
        Typography ty = Tokens.type();
        boolean percent = h().armorPercent;
        if (layout() == 0) {
            int cell = ICON + GAP + valueWidth(ps, percent, ty.body().size());
            return new int[]{ cell, Math.round(count * ROW_BLOCK + (count - 1) * ROW_GAP) };
        }
        int cell = Math.max(ICON, valueWidth(ps, percent, ty.label().size()));
        return new int[]{ Math.round(count * cell + (count - 1) * LINE_GAP),
                          Math.round(ty.label().lineHeight() + VAL_GAP + ROW_BLOCK) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        Typography ty = Tokens.type();
        ItemStack[] ps = stacks(mc, live);
        int layout = layout();
        boolean percent = h().armorPercent;
        boolean line = layout == 1;
        float vSize = line ? ty.label().size() : ty.body().size();   // LINE: value shrinks to label size
        float vlh = ty.label().lineHeight();
        int cell = line ? Math.max(ICON, valueWidth(ps, percent, vSize)) : ICON + GAP + valueWidth(ps, percent, vSize);
        TextStyle style = TextStyle.of(Weight.SEMIBOLD, vSize * s, Color.scaleAlpha(Tokens.palette().textHi(), alpha))
                .effect(HudPaint.textShadow(alpha));

        int i = 0;
        for (int slot = 0; slot < ps.length; slot++) {
            ItemStack st = ps[slot];
            if (st.isEmpty()) continue;
            float f = frac(st);
            String v = value(st, percent);
            float tw = HudText.width(v, Weight.SEMIBOLD, vSize);

            float iconX, iconY;
            if (line) {
                // LINE: [value] over [icon] over [gauge], each piece one centered column
                float cellX = ox + i * (cell + LINE_GAP) * s;
                HudText.draw(ctx, v, cellX + (cell - tw) * 0.5f * s, oy, style, vSize, s);
                iconX = cellX + (cell - ICON) * 0.5f * s;
                iconY = oy + (vlh + VAL_GAP) * s;
            } else {
                // COLUMN: [icon  value] rows, tabular values right-aligned down the stack
                iconX = ox;
                iconY = oy + i * (ROW_BLOCK + ROW_GAP) * s;
                HudText.draw(ctx, v, ox + (cell - tw) * s,
                        iconY + ((ICON - ty.body().lineHeight()) * 0.5f + VAL_NUDGE) * s, style, vSize, s);
            }
            // duotone vanilla item icon (Stage 17, owner pick A); SDF silhouette is the fallback
            if (!com.club.hud.PixelIcons.draw(itemTexture(st), iconX, iconY, ICON * s, 16, materialTint(st), alpha))
                icon(slot, st).draw(ctx, iconX, iconY, ICON * s, Color.scaleAlpha(materialTint(st), alpha));
            // the piece's live line — UNDER THE ICON only (its gauge, echoing the menu card stripe;
            // spanning the whole cell read as an element divider)
            HudPaint.rowBar(ctx, iconX, iconY + (ICON + BAR_GAP) * s, ICON * s, BAR_H, f, stateColor(f), s, alpha);
            i++;
        }
    }

    /** The item's own flat texture — resource packs and modded armor come for free. */
    private static net.minecraft.util.Identifier itemTexture(ItemStack st) {
        var id = net.minecraft.registry.Registries.ITEM.getId(st.getItem());
        return net.minecraft.util.Identifier.of(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
    }

    /** Slot → piece silhouette (fallback when the item texture can't bake); elytra gets its own wings. */
    private static IconGlyph icon(int slot, ItemStack st) {
        if (st.getItem() instanceof ElytraItem) return IconGlyph.ARMOR_ELYTRA;
        return switch (slot) {
            case 0  -> IconGlyph.ARMOR_HELMET;
            case 1  -> IconGlyph.ARMOR_CHEST;
            case 2  -> IconGlyph.ARMOR_LEGS;
            default -> IconGlyph.ARMOR_BOOTS;
        };
    }

    /** Icon tint = MATERIAL association (identity only — never grades the durability number).
     *  Deliberately juicier than the raw item colors — a full set in one muted tone read as
     *  tasteless gray (owner), so every material gets a clearly voiced hue. */
    private static int materialTint(ItemStack st) {
        if (st.getItem() instanceof ElytraItem) return 0xFFB3A6DE;          // phantom-membrane lilac
        if (!(st.getItem() instanceof ArmorItem ai)) return 0xFFAEB9C9;
        String m = ai.getMaterial().getKey().map(k -> k.getValue().getPath()).orElse("");
        return switch (m) {
            case "leather"   -> 0xFFC1976B;
            case "chainmail" -> 0xFFACB8C6;
            case "iron"      -> 0xFFD8DEE6;
            case "gold"      -> 0xFFF2CE72;
            case "diamond"   -> 0xFF7FE0E6;
            case "netherite" -> 0xFFAA849B;   // a step darker (owner) — still voiced, not gray
            case "turtle"    -> 0xFF8FD0AC;
            default          -> 0xFFAEB9C9;   // unknown/modded — neutral steel
        };
    }
}
