package com.club.mixin;

import com.club.modules.perf.IrisCompat;
import com.club.modules.perf.MainFrustum;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the culls THIS FRAME'S main-camera frustum, and nothing else's.
 *
 * <p>Reading {@code WorldRenderer.frustum} at cull time would have been simpler and wrong twice over: Sodium
 * shadows that field, and Iris runs a whole second pass with the SUN's frustum. So the frustum is taken at
 * the moment vanilla builds it, together with the camera position it was built from — and that position is
 * what makes staleness detectable downstream. If another mod owns the terrain path and this never runs, the
 * frustum simply ages out and the cull falls back to the test that needs no frustum at all.
 *
 * <p>The shadow-pass guard is here rather than at the cull: a frustum captured from the sun's point of view
 * must never enter the pipeline in the first place.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRendererFrustum {

    @Shadow private Frustum frustum;

    @Inject(method = "setupFrustum", at = @At("RETURN"))
    private void club$captureMainFrustum(Vec3d pos, Matrix4f view, Matrix4f proj, CallbackInfo ci) {
        if (IrisCompat.inShadowPass()) return;   // the sun's view of the world is not the player's
        MainFrustum.set(this.frustum, pos);
    }
}
