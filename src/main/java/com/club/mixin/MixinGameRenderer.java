package com.club.mixin;

import com.club.config.ClubConfig;
import com.club.modules.screenstretch.ScreenStretchModule;
import com.club.modules.totem.SmallTotem;
import com.club.modules.zoom.ZoomModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    /**
     * Fullbright safety net (Stage 59 audit). The gamma override is scoped to the lightmap window by a
     * flag set at LightmapTextureManager.update's HEAD and cleared at its RETURN — but a RETURN inject
     * does NOT run if another mod (Iris and the shader packs replace the lightmap outright) cancels the
     * method at HEAD. The flag would then latch true for the rest of the session, and the next
     * {@code GameOptions.write()} would serialise gamma as 15.0 and destroy the player's Brightness
     * setting for good. Clearing it once per frame, before anything can read it, makes the leak
     * impossible no matter who cancels what.
     */
    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
    private void club$clearLightmapScope(net.minecraft.client.render.RenderTickCounter counter,
                                         boolean tick, CallbackInfo ci) {
        com.club.modules.fullbright.FullbrightModule.exitLightmap();
    }

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

    /**
     * Zoom (Stage 39): divide the WORLD fov by the eased zoom divisor. Only the changingFov pass
     * (world render) zooms — the hand pass keeps its FOV, so the viewmodel doesn't balloon.
     *
     * <p>1.21.2 narrowed {@code getFov} from double to float, and a {@code @ModifyReturnValue} handler must
     * match its target's return type exactly. Measured: {@code (Camera;FZ)D} in 1.21.1, {@code …)F} from
     * 1.21.2 on. The compiler cannot see this — the handler is only ever wired to its target by NAME — so it
     * builds clean and mixin refuses it at startup ("Found unexpected return type double, expected float").
     * The maths is identical on both sides; only the width of the number differs.</p>
     */
    //? if <1.21.2 {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double club$zoom(double fov, Camera camera, float tickDelta, boolean changingFov) {
        if (!changingFov) return fov;
        float d = ZoomModule.fovDivisor();   // also advances the ease + release-persist state
        return d > 1.0005f ? fov / d : fov;
    }
    //?} else {
    /*@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float club$zoom(float fov, Camera camera, float tickDelta, boolean changingFov) {
        if (!changingFov) return fov;
        float d = ZoomModule.fovDivisor();   // also advances the ease + release-persist state
        return d > 1.0005f ? fov / d : fov;
    }*/
    //?}

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

    // Small Totem — the pop lives in GameRenderer.renderFloatingItem THROUGH 1.21.5; it moved to
    // InGameOverlayRenderer at 1.21.6 (measured across the cached jars, GameRenderer=1 up to 1.21.5,
    // =0 from 1.21.6). So these two handlers exist ONLY on <1.21.6 — on 1.21.6+ the same seam lives in
    // MixinInGameOverlayRenderer, and here the method is gone (a require=1 injector into an absent method
    // would drop the client at startup). Both classes call SmallTotem so the numbers live in one place.
    //? if <1.21.6 {
    /** Lift the pop toward the top of the screen — the single translate that centres it (measured: exactly
     *  one translate(FFF) in the method, so no ordinal is needed). Index 1 = the Y argument. */
    @ModifyArg(method = "renderFloatingItem",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"),
               index = 1)
    private float club$totemLift(float y) {
        return SmallTotem.liftY(y);
    }

    /** Shrink the pop — the single scale(FFF) call. All three components multiplied so the sign (the Y flip
     *  into screen space) is preserved. */
    @ModifyArgs(method = "renderFloatingItem",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"))
    private void club$totemShrink(Args args) {
        args.set(0, SmallTotem.shrink((float) args.get(0)));
        args.set(1, SmallTotem.shrink((float) args.get(1)));
        args.set(2, SmallTotem.shrink((float) args.get(2)));
    }
    //?}
}