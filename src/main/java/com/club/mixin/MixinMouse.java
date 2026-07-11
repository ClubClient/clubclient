package com.club.mixin;

import com.club.modules.zoom.ZoomModule;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scroll-wheel seam (Stage 39): while zooming, the wheel adjusts the zoom factor and is consumed —
 *  the hotbar must never switch under an active zoom. */
@Mixin(Mouse.class)
public class MixinMouse {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void club$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ZoomModule.onScroll(vertical)) ci.cancel();
    }
}
