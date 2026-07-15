package com.club.mixin;

import com.club.modules.particles.ParticleVisibility;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drop, at spawn, the particle types the player chose to hide (the Particles category — all on by default).
 *
 * <p>WHY THIS ENTRY, AND NOT THE RENDER SEAM: a built {@link Particle} no longer carries its registry id, so
 * the only place the TYPE is still known is {@code addParticle(ParticleEffect, 6 doubles)} — the one method
 * that constructs a particle from its {@link ParticleEffect}. HEAD + cancellable, returning {@code null}: the
 * method is already {@code @Nullable} and every caller (world.addParticle, the network handler) discards the
 * result, so a hidden particle simply never exists — no tick, no geometry, no cost of any kind.
 *
 * <p>A SEPARATE mixin from {@code MixinParticleManager} (the perf cull) on purpose: that one WRAPS
 * {@code buildGeometry} to skip drawing what the camera cannot see and changes nothing the player would
 * notice; this one CANCELS the spawn of a type the player deliberately turned off. Different method, different
 * intent — and a wrong-headed reader should not find "hide particles" living inside a class that promises it
 * only ever removes invisible work.
 */
@Mixin(ParticleManager.class)
public class MixinParticleManagerVisibility {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void club$hideParticle(ParticleEffect parameters, double x, double y, double z,
                                   double velocityX, double velocityY, double velocityZ,
                                   CallbackInfoReturnable<Particle> cir) {
        if (ParticleVisibility.hidden(parameters.getType())) cir.setReturnValue(null);
    }
}
