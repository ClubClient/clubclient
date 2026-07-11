package com.club.mixin;

import com.club.modules.fullbright.FullbrightModule;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright scope (Stage 45): marks the in-lightmap window so {@code MixinSimpleOption} overrides the
 * gamma READ only here — never during options.txt writes or the settings UI. update() reads two
 * options (DarknessEffectScale, then Gamma); the instance check in MixinSimpleOption keeps the darkness
 * read untouched. Cleared at both RETURN and on a thrown update so the flag can never leak.
 */
@Mixin(LightmapTextureManager.class)
public class MixinLightmapTextureManager {
    @Inject(method = "update", at = @At("HEAD"))
    private void club$enterLightmap(float delta, CallbackInfo ci) {
        FullbrightModule.enterLightmap();
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void club$exitLightmap(float delta, CallbackInfo ci) {
        FullbrightModule.exitLightmap();
    }
}
