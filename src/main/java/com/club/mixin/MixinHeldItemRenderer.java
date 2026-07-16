package com.club.mixin;

import com.club.modules.animations.AnimationModule;
import com.club.modules.animations.Pose;
import com.club.modules.hands.HandsModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 * our own pose STRICTLY per-hand — vanilla gates the swing by {@code player.preferredHand},
 * so only the hand that actually swung plays it (an off-hand block placement animates
 * the off-hand, never the main hand). The pose is rotated about the grip — the same
 * space vanilla swings in — and paired with a translation arc, so the blade never
 * orbits a stray pivot or drops on an air-hit. Items in use (eat/drink/block/bow)
 * are left to vanilla.
 */
@Mixin(HeldItemRenderer.class)
public class MixinHeldItemRenderer {

    /**
     * The call our pose brackets. Two independent changes hide in this one string, and BOTH are invisible to
     * the compiler — an {@code @At} target is text, and text does not fail to build. It fails at startup, and
     * {@code "required": true} makes that fatal (owner's 1.21.8 run: "Scanned 0 target(s)").
     *
     * <p>Measured out of the mappings, because the class moved AND was renamed, at different releases:</p>
     * <table>
     *   <tr><td>1.21.1</td><td>{@code client/render/model/json/ModelTransformationMode}</td><td>+ boolean</td></tr>
     *   <tr><td>1.21.2 – 1.21.4</td><td>{@code item/ModelTransformationMode} (moved)</td><td>+ boolean</td></tr>
     *   <tr><td>1.21.5 +</td><td>{@code item/ItemDisplayContext} (renamed)</td><td>boolean dropped</td></tr>
     * </table>
     *
     * <p>A single guess would have been wrong on two of the three rows, and silently: this reads as "the
     * sword animation just doesn't play" long before anyone suspects a string.</p>
     */
    //? if <1.21.2 {
    private static final String RENDER_ITEM =
            "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    //?} elif <1.21.5 {
    /*private static final String RENDER_ITEM =
            "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";*/
    //?} else {
    /*private static final String RENDER_ITEM =
            "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";*/
    //?}

    private float club$swing;
    private final Pose club$pose = new Pose();   // out-param, reused — no per-frame allocation

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

    /**
     * THE OTHER HALF OF THE EQUIP DIP — the one that fires when you HIT SOMETHING (owner, v0.1.3: "при
     * попадании по игроку меч продолжает моментами опускаться вниз"). {@link #club$noCooldownDip} kills the
     * cooldown-driven dip, but {@code updateHeldItems} has a SECOND path to it.
     *
     * <p>The bytecode: {@code if (ItemStack.areEqual(this.mainHand, current)) this.mainHand = current;} —
     * the field is refreshed only when the two are equal. Later, {@code equipProgress += clamp((this.mainHand
     * == current ? f³ : 0.0) - equipProgress, ...)} dips toward 0 when the references differ. And in 1.21 an
     * item's DAMAGE is a component, so {@code areEqual} returns false the instant your sword loses a point of
     * durability — which is exactly when you land a hit. The field is not refreshed, the reference check
     * fails, and the hand dips. It looks intermittent because it tracks connecting hits, not swings.
     *
     * <p>Fix: while our animation owns the swing, treat a stack that is the SAME ITEM (ignoring damage,
     * count, any component) as equal, so the field refreshes and the dip never arms. A genuine item switch
     * (sword → pickaxe) is a different item, still returns the real {@code false}, and still plays vanilla's
     * equip animation — which is correct. Main hand only (ordinal 0): the report is the sword, and the
     * off-hand does not carry our attack pose.
     */
    /**
     * <b>1.21.4 moved the call this wraps one level down.</b> {@code updateHeldItems} no longer asks
     * {@code ItemStack.areEqual} itself — it asks its own {@code shouldSkipHandAnimationOnSwap(a, b)}, which
     * asks {@code areEqual} and then falls back to whether the item's MODEL declares a swap animation:
     *
     * <pre>{@code if (ItemStack.areEqual(a, b)) return true;
     * return !itemModelManager.hasHandAnimationOnSwap(b);}</pre>
     *
     * <p>So the injection point vanished while the method it lived in stayed put, and the mod compiled and
     * died at startup with "Scanned 0 target(s)" — the same shape of failure as the held-item target above,
     * for a completely different reason. Measured: absent in 1.21.3, present from 1.21.4.</p>
     *
     * <p>The wrap simply moves down with it. The new method takes the SAME two stacks and means the same
     * thing — true = do not play the swap animation — so the body below is unchanged, and on both sides of
     * the boundary we are answering exactly the question the dip is decided by.</p>
     */
    //? if <1.21.4 {
    @WrapOperation(
            method = "updateHeldItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;areEqual(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z",
                    ordinal = 0))
    private boolean club$noSwapDipOnDamage(ItemStack a, ItemStack b, Operation<Boolean> original) {
        if (club$sameItemMidSwing(a, b)) return true;
        return original.call(a, b);
    }
    //?} else {
    /*@WrapOperation(
            method = "updateHeldItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;shouldSkipHandAnimationOnSwap(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z",
                    ordinal = 0))
    private boolean club$noSwapDipOnDamage(HeldItemRenderer self, ItemStack a, ItemStack b, Operation<Boolean> original) {
        // The receiver is in the signature because the call we now wrap is an INSTANCE method — areEqual was
        // static, shouldSkipHandAnimationOnSwap is not, and @WrapOperation hands the wrapper everything the
        // original invoke consumed, receiver first. Nothing else changes.
        if (club$sameItemMidSwing(a, b)) return true;
        return original.call(self, a, b);
    }*/
    //?}

    /** The decision itself, shared by both wrappers above: the same item mid-swing is not a swap. */
    @Unique
    private boolean club$sameItemMidSwing(ItemStack a, ItemStack b) {
        return AnimationModule.overridesVanillaSwing()
                && a != null && b != null && !a.isEmpty() && !b.isEmpty() && a.isOf(b.getItem());
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

        // Custom attack pose — only on the hand that actually swung (vanilla gates the swing it
        // hands out by player.preferredHand; the captured progress belongs to that hand alone),
        // and not while VANILLA IS ALREADY DRAWING ITS OWN item-use pose for THIS hand.
        //
        // That last clause used to read `!player.isUsingItem()`, and it is not the complement of vanilla's
        // condition — the gap is a hole, not a rounding error. Vanilla takes its use-pose branch only when
        // ALL THREE hold (renderFirstPersonItem, 1.21.1 bytecode @643-662, and @199-218 for the crossbow):
        //     isUsingItem() && getItemUseTimeLeft() > 0 && getActiveHand() == hand
        // Two consequences of the old, broader gate, both of which SILENCE the animation entirely:
        //
        //  • Off-hand use. Shield up, food or a bow in the OFF hand: isUsingItem() is true, but
        //    getActiveHand() != MAIN_HAND, so vanilla renders the MAIN hand through its ORDINARY branch —
        //    with the swing club$captureSwing already zeroed. Vanilla therefore draws the rest pose, and
        //    the old gate refused to draw ours on top. The sword swung with NO animation at all.
        //  • Latency. isUsingItem() is not a local fact: it reads the LIVING_FLAGS DataTracker bit, and
        //    setCurrentHand only writes that bit when !world.isClient (LivingEntity @36-42). For the LOCAL
        //    player it is server-authoritative and lags by a round trip in BOTH directions, so for ~1 RTT
        //    after releasing right-click the gate still said "using" and ate the first swing after a block.
        //
        // Zeroing the swing is unconditional on overridesVanillaSwing(); the pose must be too, minus exactly
        // the frames vanilla is posing the hand itself. Anything wider is a frame with no animation from
        // either side — and "zero must be able to mean broken".
        Hand swingHand = player.preferredHand != null ? player.preferredHand : Hand.MAIN_HAND;
        boolean vanillaUsePose = player.isUsingItem()
                && player.getItemUseTimeLeft() > 0
                && player.getActiveHand() == hand;
        if (hand == swingHand && !vanillaUsePose && AnimationModule.overridesVanillaSwing()) {
            int arm = rightSide ? 1 : -1;
            Pose pose = AnimationModule.pose(club$pose, club$swing, arm);
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