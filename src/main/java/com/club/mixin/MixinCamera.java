package com.club.mixin;

import com.club.modules.freelook.FreelookModule;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Freelook seam (Stage 42): while freelook is held, Camera.update rotates by the FREE yaw/pitch
 *  instead of the player's — the third-person arm/clip math downstream stays vanilla. */
@Mixin(Camera.class)
public class MixinCamera {
    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void club$freelook(Args args) {
        if (FreelookModule.active()) {
            args.set(0, FreelookModule.camYaw());
            args.set(1, FreelookModule.camPitch());
        }
    }
}
