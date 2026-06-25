package com.club.modules.hands;

import com.club.config.ClubConfig;
import com.club.config.ClubConfig.HandSide;
import com.club.util.Mth;

/**
 * Thin accessor for per-side hand scale/offset, clamped to valid ranges.
 * {@code right} selects the visual side (true = right hand, false = left).
 */
public final class HandsModule {
    private HandsModule() {}

    private static HandSide side(boolean right) {
        ClubConfig.Hands h = ClubConfig.get().hands;
        return right ? h.rightHand : h.leftHand;
    }

    public static float scale(boolean right)   { return Mth.clamp(side(right).scale, 0.5f, 2.0f); }
    public static float offsetX(boolean right) { return Mth.clamp(side(right).offsetX, -1.0f, 1.0f); }
    public static float offsetY(boolean right) { return Mth.clamp(side(right).offsetY, -1.0f, 1.0f); }
    public static float offsetZ(boolean right) { return Mth.clamp(side(right).offsetZ, -1.0f, 1.0f); }

    public static boolean enabled() { return ClubConfig.get().hands.enabled; }

    /** True when enabled and the given side's hand modification is non-default. */
    public static boolean isActive(boolean right) {
        return enabled() && (scale(right) != 1.0f || offsetX(right) != 0f || offsetY(right) != 0f || offsetZ(right) != 0f);
    }
}