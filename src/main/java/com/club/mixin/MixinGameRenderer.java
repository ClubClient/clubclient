package com.club.mixin;

import com.club.config.ClubConfig;
import com.club.modules.screenstretch.ScreenStretchModule;
import com.club.modules.zoom.ZoomModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    /** NoHurtCam: skip the damage view tilt entirely. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void club$noHurtCam(CallbackInfo ci) {
        if (ClubConfig.get().noHurtCam) {
            ci.cancel();
        }
    }

    /** NoBobbing: skip walk/idle view bobbing. */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void club$noBobbing(CallbackInfo ci) {
        if (ClubConfig.get().noBobbing) {
            ci.cancel();
        }
    }

    /** Zoom (Stage 39): divide the WORLD fov by the eased zoom divisor. Only the changingFov pass
     *  (world render) zooms — the hand pass keeps its FOV, so the viewmodel doesn't balloon. */
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double club$zoom(double fov, Camera camera, float tickDelta, boolean changingFov) {
        if (!changingFov) return fov;
        float d = ZoomModule.fovDivisor();   // also advances the ease + release-persist state
        return d > 1.0005f ? fov / d : fov;
    }

    /** ScreenStretch: scale the world projection horizontally to fake an aspect ratio. */
    @ModifyReturnValue(method = "getBasicProjectionMatrix", at = @At("RETURN"))
    private Matrix4f club$stretch(Matrix4f original) {
        if (original != null && ScreenStretchModule.isActive()) {
            float sx = ScreenStretchModule.projectionScaleX();
            if (Math.abs(sx - 1f) > 0.001f) {
                original.scale(sx, 1f, 1f);
            }
        }
        return original;
    }
}