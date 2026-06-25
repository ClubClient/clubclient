package com.club.mixin;

import com.club.modules.animations.AnimationModule;
import com.club.modules.animations.Pose;
import com.club.modules.hands.HandsModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands (scale/offset) + custom attack animation.
 *
 * <p><b>Hands</b> are decoupled: scale/offset apply to the <i>main hand only</i>
 * so the off-hand keeps its natural position (they no longer move together).
 *
 * <p><b>Animations</b>: unless the Vanilla passthrough is selected we capture the
 * real hand-swing once (and zero it so vanilla never swings the hand), then apply
 * our own pose. The pose is rotated about the grip — the same space vanilla swings
 * in — and paired with a translation arc, so the blade never orbits a stray pivot
 * or drops on an air-hit. Items in use (eat/drink/block/bow) are left to vanilla.
 */
@Mixin(HeldItemRenderer.class)
public class MixinHeldItemRenderer {

    private static final String RENDER_ITEM =
            "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    private float club$swing;

    /**
     * Kill the vanilla "equip dip" on air/entity hits. {@code updateHeldItems}
     * drives {@code equipProgressMainHand} from {@code getAttackCooldownProgress};
     * a fresh attack resets the cooldown, so the main hand sinks ~0.6 blocks and
     * floats back up. When our animation owns the swing we pin the progress to 1
     * (cooldown finished) so only our pose plays — block hits already read 1 here.
     * Item-switch equip animations are unaffected (they don't go through this call).
     */
    @ModifyExpressionValue(
            method = "updateHeldItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getAttackCooldownProgress(F)F"))
    private float club$noCooldownDip(float original) {
        return AnimationModule.overridesVanillaSwing() ? 1.0f : original;
    }

    /** Capture the real swing; zero it so vanilla never swings (unless Vanilla mode). */
    @ModifyExpressionValue(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getHandSwingProgress(F)F"))
    private float club$captureSwing(float original) {
        club$swing = original;
        return AnimationModule.overridesVanillaSwing() ? 0f : original;
    }

    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = RENDER_ITEM, shift = At.Shift.BEFORE))
    private void club$preItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                              float swingProgress, ItemStack item, float equipProgress,
                              MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                              CallbackInfo ci) {
        matrices.push();

        boolean main = hand == Hand.MAIN_HAND;
        // Visual side of THIS hand: main hand renders on the player's main arm,
        // the off-hand on the opposite side. Each side is configured independently.
        boolean rightSide = (player.getMainArm() == Arm.RIGHT) == main;

        // Hand position/scale — applied per visual side, so both hands are independent.
        if (HandsModule.isActive(rightSide)) {
            matrices.translate(HandsModule.offsetX(rightSide), HandsModule.offsetY(rightSide), HandsModule.offsetZ(rightSide));
            float sc = HandsModule.scale(rightSide);
            matrices.scale(sc, sc, sc);
        }

        // Custom attack pose — main hand, not while using an item.
        if (main && !player.isUsingItem() && AnimationModule.overridesVanillaSwing()) {
            int arm = player.getMainArm() == Arm.RIGHT ? 1 : -1;
            Pose pose = AnimationModule.pose(new Pose(), club$swing, arm);
            if (pose != null && !pose.isIdentity()) {
                // rotate about the grip (current origin), paired with the arc translate
                matrices.translate(pose.tx, pose.ty, pose.tz);
                if (pose.rz != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.rz));
                if (pose.ry != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.ry));
                if (pose.rx != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.rx));
            }
        }
    }

    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = RENDER_ITEM, shift = At.Shift.AFTER))
    private void club$postItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                               float swingProgress, ItemStack item, float equipProgress,
                               MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                               CallbackInfo ci) {
        matrices.pop();
    }
}