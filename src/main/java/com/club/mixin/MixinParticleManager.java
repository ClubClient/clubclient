package com.club.mixin;

import com.club.modules.perf.ParticleCull;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The one call vanilla makes without ever asking whether anyone can see the result.
 *
 * <p>{@code renderParticles} iterates every live particle in every texture sheet and calls
 * {@code buildGeometry} — no frustum, no distance, no cap (verified: zero {@code Frustum} references in the
 * whole class). Wrapping the CALL, not overwriting the method, means Sodium's own particle work
 * ({@code SingleQuadParticleMixin}, which optimises the quad but skips nothing) is untouched, and so is any
 * particle whose class we do not recognise.
 *
 * <p>Note for whoever hooks particles next: Iris SPLITS this method into an opaque pass and a translucent
 * pass, so it runs TWICE a frame with a filtered sheet list. Anything counted here is counted per call, not
 * per frame — which is fine for a skip/considered ratio and would be a bug for a per-frame budget.
 */
@Mixin(ParticleManager.class)
public class MixinParticleManager {

    /**
     * {@link WrapOperation}, NOT {@code @Redirect} (same reasoning as {@code MixinMouse#club$look}):
     * {@code buildGeometry} is the obvious seam for every particle-culling / particle-limiter mod, and two
     * {@code @Redirect}s on one instruction is an EXCLUSIVE claim — with {@code "required": true} in
     * club.mixins.json the loser's mixin fails to apply and the game refuses to launch. A wrap composes:
     * several mods can wrap the same call, and a stranger's client still starts with Club installed.
     */
    @WrapOperation(
            method = "renderParticles",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/particle/Particle;buildGeometry("
                            + "Lnet/minecraft/client/render/VertexConsumer;"
                            + "Lnet/minecraft/client/render/Camera;F)V"))
    private void club$cullInvisibleParticles(Particle particle, VertexConsumer vertices, Camera camera, float tickDelta,
                                             Operation<Void> original) {
        if (ParticleCull.skip(particle, camera, tickDelta)) return;
        original.call(particle, vertices, camera, tickDelta);
    }
}
