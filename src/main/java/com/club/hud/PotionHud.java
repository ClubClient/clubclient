package com.club.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Active-effects DATA source for the V2 Effects HUD ({@code com.club.ui.hud.EffectsElement}). Provides the
 * effect list (sorted expiring-first) plus the display name + countdown text. Rendering lives in V2; this
 * class is data-only (the legacy render layer was removed once the V2 HUD landed).
 */
public final class PotionHud {
    private PotionHud() {}

    public static List<StatusEffectInstance> effects(MinecraftClient mc) {
        List<StatusEffectInstance> list = new ArrayList<>(mc.player.getStatusEffects());
        list.sort((a, b) -> Integer.compare(a.getDuration(), b.getDuration())); // expiring first
        return list;
    }

    public static String title(StatusEffectInstance e) {
        String name = e.getEffectType().value().getName().getString();
        int amp = e.getAmplifier();
        return amp > 0 ? name + " " + roman(amp + 1) : name;
    }

    public static String time(StatusEffectInstance e) {
        if (e.isInfinite()) return "—";   // Onest has no ∞ glyph; em dash reads as "no countdown / permanent"
        int s = e.getDuration() / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static String roman(int n) {
        switch (n) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(n);
        }
    }
}
