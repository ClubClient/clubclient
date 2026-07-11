package com.club.mixin;

import com.club.modules.fullbright.FullbrightModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fullbright seam (Stage 40): the GAMMA option answers 15.0 while the module is on. Read-side only —
 * options.txt keeps the user's real value and video settings stay honest. The gate is a single static
 * boolean first (this is one of the hottest calls in the client, and it runs during GameOptions
 * construction), the gamma-instance check second, null-safe for the construction window.
 */
@Mixin(SimpleOption.class)
public class MixinSimpleOption {
    @ModifyReturnValue(method = "getValue", at = @At("RETURN"))
    private Object club$fullbright(Object original) {
        if (FullbrightModule.on()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.options != null && (Object) this == mc.options.getGamma())
                return FullbrightModule.GAMMA;
        }
        return original;
    }
}
