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

    /**
     * <b>1.21.11 stopped storing the frustum and started returning it</b>, which breaks this mixin in two
     * places at once — the field it shadows and the signature it injects into. Measured:
     *
     * <pre>{@code 1.21.1   private Frustum frustum;   setupFrustum(Vec3d;Matrix4f;Matrix4f)V
     * 1.21.8   private Frustum frustum;   setupFrustum(Vec3d;Matrix4f;Matrix4f)V
     * 1.21.11  (no such field)            setupFrustum(Matrix4f;Matrix4f;Vec3d)Frustum}</pre>
     *
     * <p>Note the parameters were also REORDERED — the Vec3d moved from first to last — so even a mixin that
     * survived the missing field would have captured a matrix as its camera position and quietly culled
     * against nonsense. Two independent breaks, one release.
     *
     * <p><b>@Shadow is the trap here.</b> It names the field in ordinary Java, so it reads as something the
     * compiler checks. It is not: the field lives in the TARGET, and javac only ever sees this declaration.
     * The build was green and 1.21.11 died at startup with "@Shadow field frustum was not located in the
     * target class". The offline checker was silent because it read @Accessor's string and never looked at
     * @Shadow at all — it does now, and it names this exact field when the fix is reverted.
     *
     * <p><b>Boundary, honestly:</b> 1.21.8 has the field, 1.21.11 does not. 1.21.9 and 1.21.10 are UNMEASURED
     * — Club ships neither, so no jar depends on the guess, but anyone adding a node there owes this a javap
     * first.
     */
    //? if <1.21.11 {
    @Shadow private Frustum frustum;

    @Inject(method = "setupFrustum", at = @At("RETURN"))
    private void club$captureMainFrustum(Vec3d pos, Matrix4f view, Matrix4f proj, CallbackInfo ci) {
        if (IrisCompat.inShadowPass()) return;   // the sun's view of the world is not the player's
        MainFrustum.set(this.frustum, pos);
    }
    //?} else {
    /*@com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "setupFrustum", at = @At("RETURN"))
    private Frustum club$captureMainFrustum(Frustum built, Matrix4f view, Matrix4f proj, Vec3d pos) {
        if (!IrisCompat.inShadowPass()) MainFrustum.set(built, pos);   // the sun's view is not the player's
        return built;
    }*/
    //?}
}
