package com.club.mixin;

import com.club.config.ClubConfig;
import net.minecraft.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 1.21.1 half of the contents-text suppression: there the shulker's preview lines come from the BLOCK
 * ({@code ShulkerBoxBlock.appendTooltip}), not yet from the container component (which only became a
 * {@code TooltipAppender} at 1.21.2). Modern versions use {@code MixinContainerComponentTooltip} instead, so
 * this mixin is registered ONLY in the shared (1.21.1) config. Both compile on every node — only the
 * registration is per-version.
 *
 * <p>HEAD cancel gated on {@code config.shulkerTooltip}, so the grid and the text never show together. The
 * handler needs no target args — a bare {@code CallbackInfo} cancels.</p>
 */
@Mixin(ShulkerBoxBlock.class)
public class MixinShulkerBoxBlockTooltip {
    @Inject(method = "appendTooltip", at = @At("HEAD"), cancellable = true)
    private void club$suppressWhenGridShows(CallbackInfo ci) {
        if (ClubConfig.get().shulkerTooltip) ci.cancel();
    }
}
