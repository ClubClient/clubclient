package com.club.mixin;

import com.club.modules.fullbright.FullbrightModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright seam (Stage 40; scoped Stage 45): the GAMMA option answers 15.0 while the module is on —
 * but ONLY while the client is inside {@code LightmapTextureManager.update} ({@link FullbrightModule#inLightmap}).
 * Outside that window (GameOptions.write, the Video-settings slider, construction) it returns the real
 * value, so options.txt and the UI stay honest. Gates on cheap static booleans first (getValue is one
 * of the hottest calls in the client), the gamma-instance check last, null-safe for construction.
 */
@Mixin(SimpleOption.class)
public class MixinSimpleOption {
    @ModifyReturnValue(method = "getValue", at = @At("RETURN"))
    private Object club$fullbright(Object original) {
        if (FullbrightModule.on() && FullbrightModule.inLightmap()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.options != null && (Object) this == mc.options.getGamma())
                return FullbrightModule.GAMMA;
        }
        return original;
    }
}
