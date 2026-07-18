package com.club.mixin;

import com.club.config.ClubConfig;
import net.minecraft.component.type.ContainerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the vanilla contents text ("Slime Block (64), … and 22 more") while the shulker grid is on, so a
 * box shows ONE thing, not two: the grid when the feature is enabled, the plain text list when it is off.
 *
 * <p>This is the 1.21.8+ half — from 1.21.2 on the container's own {@code ContainerComponent} appends that
 * text as a {@code TooltipAppender}. On 1.21.1 the same lines come from the block instead, handled by
 * {@code MixinShulkerBoxBlockTooltip}; that split is why this mixin sits ONLY in the modern override configs
 * and its sibling only in the shared (1.21.1) one. Both compile on every node — only the registration differs.</p>
 *
 * <p>HEAD cancel gated on {@code config.shulkerTooltip}: exactly when the grid replaces the text. An empty box
 * appends nothing anyway, so cancelling is harmless there. The handler captures no target args — a bare
 * {@code CallbackInfo} is all a HEAD cancel needs.</p>
 */
@Mixin(ContainerComponent.class)
public class MixinContainerComponentTooltip {
    @Inject(method = "appendTooltip", at = @At("HEAD"), cancellable = true)
    private void club$suppressWhenGridShows(CallbackInfo ci) {
        if (ClubConfig.get().shulkerTooltip) ci.cancel();
    }
}
