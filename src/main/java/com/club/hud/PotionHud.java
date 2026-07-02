package com.club.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Active-effects DATA source for the V2 Effects HUD ({@code com.club.ui.hud.EffectsElement}): the
 * effect list, sorted expiring-first. Rendering lives in V2; this class is data-only. (The name +
 * countdown formatters were removed with Stage 14's icon-only Effects element — no text on the HUD.)
 */
public final class PotionHud {
    private PotionHud() {}

    public static List<StatusEffectInstance> effects(MinecraftClient mc) {
        List<StatusEffectInstance> list = new ArrayList<>(mc.player.getStatusEffects());
        list.sort((a, b) -> Integer.compare(a.getDuration(), b.getDuration())); // expiring first
        return list;
    }
}
