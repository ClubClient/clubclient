package com.club.modules.animations;

import com.club.config.ClubConfig;
import com.club.util.Mth;

/**
 * Stateless helper: maps the current vanilla hand-swing progress to a custom
 * {@link Pose} using the configured animation type and amplitude. Animation
 * speed is handled separately by scaling the hand-swing duration.
 */
public final class AnimationModule {
    private AnimationModule() {}

    private static AnimationType type() {
        return AnimationType.fromName(ClubConfig.get().animations.type);
    }

    public static boolean enabled() { return ClubConfig.get().animations.enabled; }

    /** False for the Vanilla passthrough or when disabled — then the stock swing is kept. */
    public static boolean overridesVanillaSwing() {
        return enabled() && type() != AnimationType.VANILLA;
    }

    /**
     * Pose for the given swing progress (0..1), or null when at rest / vanilla / disabled.
     * {@code arm} is +1 for a right hand, -1 for a left hand.
     */
    public static Pose pose(Pose out, float swing, int arm) {
        if (swing <= 0.001f || !enabled()) return null;
        AnimationType t = type();
        if (t == AnimationType.VANILLA) return null;
        float amp = Mth.clamp(ClubConfig.get().animations.amplitude, 0.5f, 1.5f);
        return t.sample(out, Mth.clamp(swing, 0f, 1f), amp, arm);
    }

    /** Swing duration scale from the speed setting (1.0 = vanilla when disabled). */
    public static float speed() {
        if (!enabled()) return 1.0f;
        return Mth.clamp(ClubConfig.get().animations.speed, 0.5f, 2.0f);
    }
}