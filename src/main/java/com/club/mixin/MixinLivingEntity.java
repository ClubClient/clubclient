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
        if ((Object) this != MinecraftClient.getInstance().player) return original;
        float speed = AnimationModule.speed();
        if (Math.abs(speed - 1f) <= 0.001f) return original;

        // THE FLOOR IS 2, NOT 1 — and that is the difference between a fast swing and a DEAD one.
        // tickHandSwing() (1.21.1 bytecode @0-57) is:
        //     if (handSwinging) { if (++handSwingTicks >= duration) { handSwingTicks = 0; handSwinging = false; } }
        //     handSwingProgress = handSwingTicks / duration;
        // At duration 1 the counter is zeroed on the very tick it reaches 1, so handSwingTicks is ALWAYS 0
        // by the time the progress is computed: handSwingProgress is pinned at 0.0 forever, every frame's
        // getHandSwingProgress returns 0.0, AnimationModule.pose() answers null, and the hand never animates
        // at all — not ours, not vanilla's. A duration of 1 is not a fast animation, it is no animation.
        //
        // And it is reachable, not theoretical: vanilla returns 6 - (1 + amplifier) under Haste
        // (LivingEntity @0-16), so Haste V gives original == 1, and Math.round(1 / 2.0f) == 1.
        // Two ticks is the smallest duration whose progress ramp still has a non-zero sample.
        // In the ordinary range this changes nothing (original 6, speed 2.0 -> 3 either way).
        return Math.max(2, Math.round(original / speed));
    }
}