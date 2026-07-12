package com.club.mixin;

import com.club.modules.freelook.FreelookModule;
import com.club.modules.zoom.ZoomModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse seams: the wheel adjusts the zoom factor while zooming (consumed — the hotbar must never
 *  switch under a zoom, Stage 39); look deltas are damped by the zoom level and swing the freelook
 *  camera instead of the player while freelook is held (Stage 42/58). */
@Mixin(Mouse.class)
public class MixinMouse {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void club$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ZoomModule.onScroll(vertical)) ci.cancel();
    }

    /**
     * The single look seam: scale the delta by the zoom (a magnified view must turn slower — the world
     * then moves at the same speed ACROSS THE SCREEN at any zoom, Stage 58), then route it to the
     * freelook camera or the player. Damping applies to both: freelook can be held while zoomed.
     *
     * <p>{@link WrapOperation}, NOT {@code @Redirect} (Stage 59 audit): {@code changeLookDirection} is
     * the single most contested call in the client — every zoom / freelook / freecam / shoulder-surfing
     * mod hooks it. Two {@code @Redirect}s on one instruction is an EXCLUSIVE claim: with
     * {@code "required": true} the loser's mixin fails to apply and the game refuses to launch. A wrap
     * composes: several mods can wrap the same call and each one's transform is applied.</p>
     */
    @WrapOperation(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void club$look(ClientPlayerEntity player, double dx, double dy, Operation<Void> original) {
        float s = ZoomModule.sensitivityScale();
        double sdx = dx * s, sdy = dy * s;
        if (FreelookModule.active()) FreelookModule.onLook(sdx, sdy);   // camera only — the player never turns
        else original.call(player, sdx, sdy);
    }
}
