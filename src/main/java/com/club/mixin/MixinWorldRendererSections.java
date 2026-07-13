package com.club.mixin;

import com.club.modules.perf.EntityCull;
import com.club.modules.perf.IrisCompat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every frame, {@code applyFrustum} refills {@code builtChunks} with exactly the chunk sections the
 * occlusion graph decided are visible. That list is a ready-made answer to "can this entity be seen at all",
 * and it costs nothing to copy the section origins out of it.
 *
 * <p>Guarded on the shadow pass, and it must be: Iris runs a SECOND setupTerrain -> applyFrustum with the
 * SUN's frustum, which would silently overwrite the player's visible-section set with the sun's. The cull
 * would then hide everything the sun cannot see, from the player who is looking straight at it.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRendererSections {

    @Shadow @Final private ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks;

    @Inject(method = "applyFrustum", at = @At("RETURN"))
    private void club$captureVisibleSections(Frustum frustum, CallbackInfo ci) {
        if (IrisCompat.inShadowPass()) return;
        EntityCull.sections(this.builtChunks);
    }
}
