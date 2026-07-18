package com.club.mixin;

import com.club.config.ClubConfig;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hide the vanilla status-effect HUD overlay so it doesn't clash with our Potion HUD.
 *
 * <p>Only while ours is actually drawing: gated on {@code hud.potions} alone. It USED to also require a
 * {@code hideVanillaEffects} toggle, but the owner cut that card (baked in, always on) — a replacement that
 * is on screen is always entitled to hide the original, and the toggle only ever let the two clash. The one
 * rule that still matters is the other half: the Club Effects chip OFF means {@code hud.potions} is false, so
 * the game's own overlay comes back rather than leaving the player with no effect display at all.</p>
 */
@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void club$hideVanillaEffects(CallbackInfo ci) {
        if (ClubConfig.get().hud.potions) {
            ci.cancel();
        }
    }
}