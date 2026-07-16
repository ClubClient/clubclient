package com.club.mixin;

import com.club.modules.perf.BlockEntityCull;
import net.minecraft.client.util.math.MatrixStack;
//? if <1.21.9 {
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
//?} else {
/*import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;*/
//?}
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
 *
 * <p><b>1.21.9 renamed the target and rewrote the method.</b> The class became
 * {@code BlockEntityRenderManager} (a rename — same {@code class_824} throughout), and {@code render} stopped
 * taking a {@code BlockEntity} in favour of the {@code BlockEntityRenderState} that the same release
 * introduced. The file keeps its old name because {@code club.mixins.json} names it, and that file is shared
 * by every version.
 *
 * <p><b>The injection point is still per-block-entity, and that was checked rather than hoped.</b> In
 * 1.21.11 {@code WorldRenderer} iterates a {@code List<BlockEntityRenderState>} and calls
 * {@code BlockEntityRenderManager.render} once inside the loop for each one — read out of the bytecode — so
 * a cancel here still skips exactly one block entity's draw, as it always did. {@code method = "render"} is
 * also UNambiguous from 1.21.9 on: the second {@code render} overload that lived beside it through 1.21.8
 * ({@code method_23079}) is gone.
 */
//? if <1.21.9 {
@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void club$cullOffscreenBlockEntities(
            E blockEntity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            CallbackInfo ci) {
        if (BlockEntityCull.skip((BlockEntityRenderDispatcher) (Object) this, blockEntity)) ci.cancel();
    }
}
//?} else {
/*@Mixin(BlockEntityRenderManager.class)
public class MixinBlockEntityRenderDispatcher {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void club$cullOffscreenBlockEntities(
            BlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
            CameraRenderState cameraState, CallbackInfo ci) {
        if (BlockEntityCull.skip((BlockEntityRenderManager) (Object) this, state)) ci.cancel();
    }
}*/
//?}
