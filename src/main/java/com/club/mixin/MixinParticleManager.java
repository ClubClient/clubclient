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
    //? if <1.21.4 {
    @WrapOperation(
            method = "renderParticles",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/particle/Particle;buildGeometry("
                            + "Lnet/minecraft/client/render/VertexConsumer;"
                            + "Lnet/minecraft/client/render/Camera;F)V"))
    //?} else {
    /*// Two changes, one release apart in cause but both landing here:
    //   1.21.4 renamed Particle.buildGeometry -> render. Same method (method_3074), same descriptor; the
    //          name is all that moved, and a name is all an @At target is.
    //   1.21.8 split renderParticles into three: a public entry point and two private static workers. The
    //          call we wrap is in the SHEET worker, not the entry point — so `method = "renderParticles"`
    //          is now both ambiguous AND pointed at the overload that does not contain the call. It is
    //          spelled out in full below; ambiguity here would either fail loudly or silently pick wrong,
    //          and neither is something to leave to luck.
    @WrapOperation(
            method = "renderParticles(Lnet/minecraft/client/render/Camera;F"
                   + "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;"
                   + "Lnet/minecraft/client/particle/ParticleTextureSheet;Ljava/util/Queue;)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/particle/Particle;render("
                            + "Lnet/minecraft/client/render/VertexConsumer;"
                            + "Lnet/minecraft/client/render/Camera;F)V"))*/
    //?}
    private void club$cullInvisibleParticles(Particle particle, VertexConsumer vertices, Camera camera, float tickDelta,
                                             Operation<Void> original) {
        if (ParticleCull.skip(particle, camera, tickDelta)) return;
        original.call(particle, vertices, camera, tickDelta);
    }
}
