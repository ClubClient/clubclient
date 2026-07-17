package com.club.mixin;

import com.club.config.ClubConfig;
import com.club.modules.totem.SmallTotem;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameOverlayRenderer.class)
public class MixinInGameOverlayRenderer {

    /** NoFireOverlay: hide the first-person fire overlay while burning. */
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void club$noFireOverlay(CallbackInfo ci) {
        if (ClubConfig.get().noFireOverlay) {
            ci.cancel();
        }
    }

    // Small Totem — the pop moved HERE at 1.21.6 (measured: InGameOverlayRenderer.renderFloatingItem=0
    // through 1.21.5, =1 from 1.21.6). So these handlers exist ONLY on >=1.21.6; on <1.21.6 the same seam
    // lives in MixinGameRenderer, and here the method does not exist yet. Identical to that copy — same
    // single translate(FFF)/scale(FFF) shape, measured on 1.21.8 and 1.21.11 — and both call SmallTotem so
    // the numbers live in one place. renderFloatingItem gained an OrderedRenderCommandQueue param at 1.21.11,
    // but the injection targets the INTERNAL translate/scale calls, whose descriptors did not change, so the
    // name-only method selector binds on both 1.21.8 and 1.21.11 without splitting them.
    //? if >=1.21.6 {
    /*@ModifyArg(method = "renderFloatingItem",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"),
               index = 1)
    private float club$totemLift(float y) {
        return SmallTotem.liftModernY(y);
    }

    @ModifyArgs(method = "renderFloatingItem",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"))
    private void club$totemShrink(Args args) {
        args.set(0, SmallTotem.shrink((float) args.get(0)));
        args.set(1, SmallTotem.shrink((float) args.get(1)));
        args.set(2, SmallTotem.shrink((float) args.get(2)));
    }*/
    //?}
}