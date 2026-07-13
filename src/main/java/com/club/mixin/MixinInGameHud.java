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
 * <p>Only while ours is actually drawing (Stage 62). The cancel used to hang off {@code hideVanillaEffects}
 * alone, while the Club element hangs off {@code hud.potions} — two flags for one job, and turning the
 * Club Effects chip OFF left the player with no effect display at all: not ours, and not the game's. A
 * replacement is only entitled to hide the original while it is on screen.</p>
 */
@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void club$hideVanillaEffects(CallbackInfo ci) {
        ClubConfig.Hud hud = ClubConfig.get().hud;
        if (hud.hideVanillaEffects && hud.potions) {
            ci.cancel();
        }
    }
}