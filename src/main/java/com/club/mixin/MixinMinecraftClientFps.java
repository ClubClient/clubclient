package com.club.mixin;

import com.club.modules.perf.BackgroundThrottle;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The frame cap, and the only place vanilla asks for it.
 *
 * <p>{@code getFramerateLimit()} is private with exactly ONE call site — {@code render()} — and the Max
 * Framerate slider in the options screen reads {@code GameOptions.getMaxFps()} instead, so tightening the
 * return value here throttles the game without lying to the player about what they set.
 */
@Mixin(MinecraftClient.class)
public class MixinMinecraftClientFps {

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void club$throttleInBackground(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(BackgroundThrottle.limit(cir.getReturnValueI()));
    }
}
