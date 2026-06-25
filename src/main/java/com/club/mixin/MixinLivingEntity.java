package com.club.mixin;

import com.club.modules.animations.AnimationModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Animation "speed": scale the local player's hand-swing duration. */
@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"))
    private int club$swingDuration(int original) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            float speed = AnimationModule.speed();
            if (Math.abs(speed - 1f) > 0.001f) {
                return Math.max(1, Math.round(original / speed));
            }
        }
        return original;
    }
}