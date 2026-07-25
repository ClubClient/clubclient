package com.club.modules.saturation;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;

/**
 * The numbers behind the Saturation module (AppleSkin's food/hunger info). Two halves:
 *
 * <ul>
 *   <li><b>Reading a food item</b> — {@link #isFood}, {@link #nutrition}, {@link #saturationIncrement}. These
 *       touch Minecraft classes but only through accessors measured identical on all four versions
 *       (1.21.1/1.21.6/1.21.8/1.21.11): {@code ItemStack.contains/get(DataComponentTypes.FOOD)} and the
 *       {@code FoodComponent} record accessors {@code nutrition():I} / {@code saturation():F}. Detection keys
 *       on {@code FOOD} alone, deliberately: {@code DataComponentTypes.CONSUMABLE} does not exist on 1.21.1,
 *       so AppleSkin's {@code FOOD && CONSUMABLE} test would not compile there.</li>
 *   <li><b>The natural-regen estimate</b> — {@link #estimatedHealthIncrement}, a pure function of primitives
 *       so it is unit-tested without a running client (see {@code FoodHelperTest}). It reproduces AppleSkin's
 *       {@code getEstimatedHealthIncrement}, itself a simulation of vanilla {@code PlayerEntity} natural
 *       regen.</li>
 * </ul>
 *
 * <p><b>The saturation the increment uses is only as true as its input.</b> {@code exhaustion} is
 * server-authoritative and unsynced to a remote client — see {@link com.club.mixin.MixinHungerManagerAccessor}
 * — so this estimate is exact in single-player and approximate on a vanilla server, exactly as AppleSkin is
 * without its server companion.
 */
public final class FoodHelper {
    private FoodHelper() {}

    // Vanilla natural-regen constants (net.minecraft.entity.player.PlayerEntity), reproduced from AppleSkin.
    private static final float MAX_EXHAUSTION = 4.0f;
    private static final float REGEN_EXHAUSTION_INCREMENT = 6.0f;

    /** True when the stack carries a FOOD component — the one detection key present on all four versions. */
    public static boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
    }

    /** Hunger points the food restores, or 0 if the stack is not food. */
    public static int nutrition(ItemStack stack) {
        FoodComponent f = food(stack);
        return f == null ? 0 : f.nutrition();
    }

    /**
     * Saturation the food GRANTS (vanilla: {@code nutrition * saturationModifier * 2}, exposed by the record
     * as {@code saturation()}). Capped at the food level the way vanilla caps it is the caller's job.
     */
    public static float saturationIncrement(ItemStack stack) {
        FoodComponent f = food(stack);
        return f == null ? 0f : f.saturation();
    }

    private static FoodComponent food(ItemStack stack) {
        return isFood(stack) ? stack.get(DataComponentTypes.FOOD) : null;
    }

    /**
     * Health (in half-heart-inclusive HP units) that natural regen would restore given a food/saturation/
     * exhaustion state — AppleSkin's estimate, pure so it can be tested off-thread.
     *
     * <p>Two regen paths, mirroring vanilla:
     * <ul>
     *   <li><b>Saturation regen</b> ({@code food == 20 && saturation > 0}): heals fast, 1 HP per cycle, each
     *       cycle spending {@code min(saturation, REGEN_EXHAUSTION_INCREMENT)/REGEN_EXHAUSTION_INCREMENT} of a
     *       heart's worth — summed until saturation or the health gap runs out.</li>
     *   <li><b>Food regen</b> ({@code food >= 18}): heals 1 HP per full cycle, no saturation needed.</li>
     *   <li>Below 18: no natural regen, returns 0.</li>
     * </ul>
     *
     * @param food       current food level (0..20)
     * @param saturation current saturation (0..food)
     * @param exhaustion current exhaustion (0..4) — the value whose truthfulness the sync caveat governs
     * @param health     current health
     * @param maxHealth  max health
     * @return estimated HP that would be regained, clamped to the missing-health gap, never negative
     */
    public static float estimatedHealthIncrement(int food, float saturation, float exhaustion,
                                                 float health, float maxHealth) {
        float gap = maxHealth - health;
        if (gap <= 0f || food < 18) return 0f;

        // Each regen tick first pays down exhaustion; only whole REGEN_EXHAUSTION_INCREMENT chunks past
        // MAX_EXHAUSTION-worth of accumulation actually heal. AppleSkin models the steady state: from the
        // current exhaustion, how much healing the remaining food/saturation buys before food drops below 18.
        float healed = 0f;
        float sat = saturation;
        float exh = exhaustion;
        int foodLeft = food;

        // Bound the loop hard — a cycle always removes at least REGEN_EXHAUSTION_INCREMENT of budget or ends.
        for (int guard = 0; guard < 256 && healed < gap && foodLeft >= 18; guard++) {
            if (foodLeft >= 20 && sat > 0f) {
                // Saturation regen: heal a fraction of a heart per step, consuming saturation as exhaustion.
                float step = Math.min(sat, REGEN_EXHAUSTION_INCREMENT) / REGEN_EXHAUSTION_INCREMENT;
                if (step <= 0f) break;
                healed += step;
                sat -= REGEN_EXHAUSTION_INCREMENT;
                if (sat <= 0f) { sat = 0f; }
            } else {
                // Food regen: 1 HP per cycle, each cycle costs 4 food (vanilla spends exhaustion that in turn
                // drains food+saturation). Model it as one heart per cycle until food would fall below 18.
                healed += 1f;
                exh += MAX_EXHAUSTION;
                if (exh >= MAX_EXHAUSTION) { exh -= MAX_EXHAUSTION; foodLeft -= 1; }
                if (foodLeft < 18) break;
            }
        }
        return Math.min(healed, gap);
    }
}
