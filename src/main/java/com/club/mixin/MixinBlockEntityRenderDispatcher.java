package com.club.mixin;

import com.club.modules.perf.BlockEntityCull;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The seam every block entity passes through — the vanilla per-section loop, the no-culling loop, AND Iris's
 * shadow pass. That last one is why {@link BlockEntityCull} asks {@code IrisCompat} before it decides
 * anything: a cull keyed to the main camera, fired while Iris draws the shadow map, deletes shadows.
 *
 * <p>It also survives Sodium: Sodium replaces the block-entity ITERATION but still calls this same
 * dispatcher for each one, and it does no per-BE frustum test of its own.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void club$cullOffscreenBlockEntities(
            E blockEntity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            CallbackInfo ci) {
        if (BlockEntityCull.skip((BlockEntityRenderDispatcher) (Object) this, blockEntity)) ci.cancel();
    }
}
