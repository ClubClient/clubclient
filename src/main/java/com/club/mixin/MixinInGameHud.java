package com.club.mixin;

import com.club.config.ClubConfig;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide the vanilla status-effect HUD overlay so it doesn't clash with our Potion HUD. */
@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void club$hideVanillaEffects(CallbackInfo ci) {
        if (ClubConfig.get().hud.hideVanillaEffects) {
            ci.cancel();
        }
    }
}