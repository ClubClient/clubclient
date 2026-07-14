package com.club.mixin;

import com.club.modules.perf.ParticleCull;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The one call vanilla makes without ever asking whether anyone can see the result.
 *
 * <p>{@code renderParticles} iterates every live particle in every texture sheet and calls
 * {@code buildGeometry} — no frustum, no distance, no cap (verified: zero {@code Frustum} references in the
 * whole class). Redirecting the CALL, not overwriting the method, means Sodium's own particle work
 * ({@code SingleQuadParticleMixin}, which optimises the quad but skips nothing) is untouched, and so is any
 * particle whose class we do not recognise.
 *
 * <p>Note for whoever hooks particles next: Iris SPLITS this method into an opaque pass and a translucent
 * pass, so it runs TWICE a frame with a filtered sheet list. Anything counted here is counted per call, not
 * per frame — which is fine for a skip/considered ratio and would be a bug for a per-frame budget.
 */
@Mixin(ParticleManager.class)
public class MixinParticleManager {

    @Redirect(
            method = "renderParticles",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/particle/Particle;buildGeometry("
                            + "Lnet/minecraft/client/render/VertexConsumer;"
                            + "Lnet/minecraft/client/render/Camera;F)V"))
    private void club$cullInvisibleParticles(Particle particle, VertexConsumer vertices, Camera camera, float tickDelta) {
        if (ParticleCull.skip(particle, camera, tickDelta)) return;
        particle.buildGeometry(vertices, camera, tickDelta);
    }
}
