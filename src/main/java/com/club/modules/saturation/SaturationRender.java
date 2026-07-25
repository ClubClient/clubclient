package com.club.modules.saturation;

import com.club.mixin.MixinHungerManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Draws AppleSkin's food/hunger information onto the vanilla food and health bars — the SATURATION reserve,
 * the ghost preview of what a held food would restore, and the single-player exhaustion line.
 *
 * <p><b>Flat by choice, not by limitation.</b> AppleSkin ships its own pixel-art icon sheet; Club's design is
 * frozen flat-dark, and copying another mod's texture is a licence question besides. So the behaviour is
 * reproduced faithfully — same anchors, same proportions, same right-to-left alignment as the vanilla bar —
 * but drawn with {@link DrawContext#fill}, which is identical on all four versions and needs no
 * {@code //?} split. The exact look against AppleSkin's sprites is the owner's in-game (EYE) call; the numbers
 * and positions behind it are not.</p>
 *
 * <p>Every method here assumes the caller ({@link com.club.mixin.MixinInGameHud}) has already checked
 * {@code ClubConfig.saturation} — off means these are never reached and the vanilla bar is untouched.</p>
 */
public final class SaturationRender {
    private SaturationRender() {}

    private static final int ROW_W = 81;       // 10 icons: right − i*8 − 9, i∈0..9 ⇒ 81px wide
    private static final int ICON_H = 9;

    // Flat palette. Saturation reserve reads as a light film over the food it backs; the restore preview uses
    // Club's flat accent (#7CABFF) so it is unmistakably "what you would gain", not "what you have".
    private static final int SATURATION_ARGB = 0x8CFFFFFF;   // white, ~55% — the reserve line
    private static final int EXHAUSTION_ARGB = 0x593C3C3C;   // faint dark — the receding exhaustion bar
    private static final int ACCENT_RGB      = 0x7CABFF;      // Club accent, alpha added per-frame (flash)

    /**
     * Called at the TAIL of {@code InGameHud.renderFood}. {@code y} is the top of the food row, {@code right}
     * its right edge (the measured method arguments).
     */
    public static void onRenderFood(DrawContext ctx, PlayerEntity player, int y, int right) {
        HungerManager hunger = player.getHungerManager();
        int food = hunger.getFoodLevel();
        float saturation = hunger.getSaturationLevel();

        // 1. Saturation reserve — a thin line along the top of the food row, its width the fraction of the bar
        //    the current saturation backs (right-aligned, the way vanilla fills the food bar).
        if (saturation > 0f) {
            int w = Math.round(clamp01(saturation / 20f) * ROW_W);
            if (w > 0) ctx.fill(right - w, y - 1, right, y, SATURATION_ARGB);
        }

        // 2. Held-food restore preview — a flat accent film over the shanks a held food would fill, flashing.
        ItemStack held = heldFood(player);
        if (held != null && food < 20) {
            int restored = Math.min(food + FoodHelper.nutrition(held), 20);
            int fillFrom = right - Math.round(clamp01(restored / 20f) * ROW_W);
            int fillTo   = right - Math.round(clamp01(food / 20f) * ROW_W);
            if (fillTo > fillFrom) ctx.fill(fillFrom, y, fillTo, y + ICON_H, flashAccent(player));
        }

        // 3. Exhaustion — a receding faint bar, ONLY where the value is real (integrated server). Vanilla never
        //    syncs exhaustion to a remote client, so on someone else's server this would always read empty; a
        //    bar that is silently empty is a lie, so it is simply not drawn there.
        if (isDataAuthoritative()) {
            float exhaustion = exhaustionOf(hunger);
            float ratio = clamp01(exhaustion / 4.0f);
            int w = Math.round(ratio * ROW_W);
            if (w > 0) ctx.fill(right - w, y, right, y + 1, EXHAUSTION_ARGB);
        }
    }

    /**
     * Called at the TAIL of {@code InGameHud.renderHealthBar}. {@code x} is the left edge of the heart row,
     * {@code y} its top. Shows a ghost preview of the health natural regen would restore if a food is held —
     * AppleSkin's condition set: not peaceful, no poison/wither/active regen, food ≥ 18.
     */
    public static void onRenderHealth(DrawContext ctx, PlayerEntity player, int x, int y,
                                      float maxHealth, int health) {
        if (!shouldPreviewHealth(player)) return;
        HungerManager hunger = player.getHungerManager();
        float heal = FoodHelper.estimatedHealthIncrement(
                hunger.getFoodLevel(), hunger.getSaturationLevel(), exhaustionOf(hunger), health, maxHealth);
        if (heal <= 0f) return;

        // Hearts fill left-to-right, 8px pitch, 2 HP each — the ghost region sits to the RIGHT of current
        // health (single row; multi-row absorption stacks are left to the owner's eye). Flat accent, flashing.
        float rowHp = Math.min(maxHealth, 20f);
        int fromX = x + Math.round(clamp01(health / rowHp) * ROW_W);
        int toX   = x + Math.round(clamp01(Math.min(health + heal, rowHp) / rowHp) * ROW_W);
        if (toX > fromX) ctx.fill(fromX, y, toX, y + ICON_H, flashAccent(player));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Main hand first, then off hand — the first that is food, or null. */
    private static ItemStack heldFood(PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (FoodHelper.isFood(main)) return main;
        ItemStack off = player.getOffHandStack();
        if (FoodHelper.isFood(off)) return off;
        return null;
    }

    /** AppleSkin's health-preview gate: alive-in-a-mode-that-regens, no conflicting effect, fed enough. */
    private static boolean shouldPreviewHealth(PlayerEntity player) {
        if (player.getHungerManager().getFoodLevel() < 18) return false;
        if (player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON)) return false;
        if (player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER)) return false;
        if (player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION)) return false;
        return true;
    }

    /** Exhaustion is only truthful on an integrated server (single-player) — see MixinHungerManagerAccessor. */
    private static boolean isDataAuthoritative() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.getServer() != null;
    }

    private static float exhaustionOf(HungerManager hunger) {
        return ((MixinHungerManagerAccessor) (Object) hunger).club$exhaustion();
    }

    /** Deterministic flash from the player's tick age — no wall clock, so it never breaks a replay. */
    private static int flashAccent(PlayerEntity player) {
        double phase = 0.5 + 0.5 * Math.sin(player.age * 0.35);
        int alpha = (int) ((0.35 + 0.35 * phase) * 255f + 0.5f);
        return (alpha << 24) | ACCENT_RGB;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
