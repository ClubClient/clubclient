package com.club.mixin;

import com.club.modules.perf.EntityCull;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * THE SEAM THE BRIEF'S OWN AUDIT HAD TO CORRECT, AND WHY IT MATTERS.
 *
 * <p>The obvious place to cancel an entity is {@code WorldRenderer.renderEntity}. It is also useless:
 * {@code regularEntityCount++} sits at offset 918 and the {@code renderEntity} call at 1074, so a culler
 * hooked there would work perfectly and be INVISIBLE TO ITS OWN TEST — the counter would never move, and
 * the only noise-free proof we have would report zero forever.
 *
 * <p>{@code shouldRender} is called at offset 796, BEFORE the increment. Cancel here and vanilla's own
 * number moves, which is how the benchmark can say "150 entities rendered became 75" with no statistics in
 * it at all. It is also the single funnel: one call site in vanilla, no overrides to slip through
 * ({@code EntityRenderer.shouldRender} IS overridden — EndCrystal, Guardian, Shulker — which is why the
 * dispatcher, not the renderer, is the target).
 *
 * <p>The one thing it is NOT is main-pass-only: Iris's {@code ShadowRenderer.renderEntities} calls this
 * exact method with the SHADOW frustum. {@link EntityCull} asks {@code IrisCompat} first, and fails closed.
 */
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void club$cullHiddenEntities(
            E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (EntityCull.skip(entity)) cir.setReturnValue(false);
    }
}
