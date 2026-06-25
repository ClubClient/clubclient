package com.club.mixin;

import com.club.config.ClubConfig;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class MixinInGameOverlayRenderer {

    /** NoFireOverlay: hide the first-person fire overlay while burning. */
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void club$noFireOverlay(CallbackInfo ci) {
        if (ClubConfig.get().noFireOverlay) {
            ci.cancel();
        }
    }
}