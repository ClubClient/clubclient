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
 *
 * <h2>1.21.11 does this itself, so this mixin is not built there</h2>
 *
 * <p>The class is absent from {@code versions/1.21.11/.../club.mixins.json}, and that is not a gap left open
 * — it is the feature arriving upstream. Measured in the 1.21.11 jar:
 *
 * <pre>{@code ParticleManager.addToBatch(SubmittableBatch, Frustum, Camera, float)
 *   -> ParticleRenderer.render(Frustum, Camera, float)
 *      -> BillboardParticleRenderer.render calls Frustum.intersectPoint(D,D,D)}</pre>
 *
 * <p>A frustum now reaches the particle renderer and it culls against it. Vanilla's test is STRICTLY better
 * than ours: we only ever skipped what was behind the camera, and a full frustum also drops everything to
 * either side. Wrapping it would buy nothing and cost a second visibility test per particle per frame.
 *
 * <p>The third thing Mojang has absorbed from this mod — after the background frame cap (1.21.2's
 * {@code InactivityFpsLimiter}) and the equip dip (1.21.4's {@code shouldSkipHandAnimationOnSwap}). The rule
 * the owner set for the first two applies here: when the game does it, we stop doing it. The player on
 * 1.21.11 loses nothing.
 *
 * <p>Unaffected and still built on every version: {@code MixinParticleManagerVisibility}, which is the
 * Particles category — choosing which particle TYPES exist at all. That is a different question from whether
 * a particle that exists is on screen, and no version of Minecraft answers it.
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
    private void club$cullInvisibleParticles(Particle particle, VertexConsumer vertices, Camera camera, float tickDelta,
                                             Operation<Void> original) {
        if (ParticleCull.skip(particle, camera, tickDelta)) return;
        original.call(particle, vertices, camera, tickDelta);
    }
    //?} else {
    /*// THREE changes, all landing on this one injection, all arriving in 1.21.4:
    //   1. Particle.buildGeometry was renamed to render. Same method (method_3074), same descriptor — only
    //      the name moved, and a name is all an @At target is.
    //   2. renderParticles split into three: a public entry point plus two workers. The call we wrap lives
    //      in the SHEET worker, so the bare name is now BOTH ambiguous and aimed at the overload that does
    //      not contain the call. Spelled out in full below.
    //   3. That worker is STATIC — so this handler must be static too. Mixin says so plainly at startup
    //      ("non-static callback method targets a static method which is not supported") and nothing says
    //      it earlier: not javac, and not our own offline checker, which read names and call sites but
    //      never compared modifiers. It does now.
    @WrapOperation(
            method = "renderParticles(Lnet/minecraft/client/render/Camera;F"
                   + "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;"
                   + "Lnet/minecraft/client/particle/ParticleTextureSheet;Ljava/util/Queue;)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/particle/Particle;render("
                            + "Lnet/minecraft/client/render/VertexConsumer;"
                            + "Lnet/minecraft/client/render/Camera;F)V"))
    private static void club$cullInvisibleParticles(Particle particle, VertexConsumer vertices, Camera camera,
                                                    float tickDelta, Operation<Void> original) {
        if (ParticleCull.skip(particle, camera, tickDelta)) return;
        original.call(particle, vertices, camera, tickDelta);
    }*/
    //?}
}
