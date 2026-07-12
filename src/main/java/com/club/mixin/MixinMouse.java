package com.club.mixin;

import com.club.modules.freelook.FreelookModule;
import com.club.modules.zoom.ZoomModule;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /** The single look seam: scale the delta by the zoom (a magnified view must turn slower — the
     *  world then moves at the same speed ACROSS THE SCREEN at any zoom, Stage 58), then route it to
     *  the freelook camera or the player. Damping applies to both: freelook can be held while zoomed. */
    @Redirect(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void club$look(ClientPlayerEntity player, double dx, double dy) {
        float s = ZoomModule.sensitivityScale();
        double sdx = dx * s, sdy = dy * s;
        if (FreelookModule.active()) FreelookModule.onLook(sdx, sdy);
        else player.changeLookDirection(sdx, sdy);
    }
}
