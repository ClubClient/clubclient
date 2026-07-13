package com.club.modules.perf;

import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.Vec3d;

/**
 * This frame's main-camera frustum — and a way to know it IS this frame's.
 *
 * <p>Reading {@code WorldRenderer.frustum} directly at cull time would have been simpler and wrong twice
 * over: Sodium shadows that field, and Iris runs a whole second pass from the SUN's point of view. So the
 * frustum is taken at the moment vanilla builds it, in {@code setupFrustum}, together with the camera
 * position it was built from — and that position is the freshness check. If another mod owns the terrain
 * path and setupFrustum never runs, the frustum simply ages out, {@link #current} returns null, and every
 * cull that depends on it does NOTHING. Failing open is the only acceptable failure for a renderer.
 */
public final class MainFrustum {
    private MainFrustum() {}

    private static Frustum frustum;
    private static Vec3d builtAt;

    /** Called from the setupFrustum mixin, and never during Iris's shadow pass. */
    public static void set(Frustum f, Vec3d cameraPos) { frustum = f; builtAt = cameraPos; }

    /** The frustum, if it was built for THIS camera position; null if it cannot be trusted. */
    public static Frustum current(Vec3d cameraPos) {
        if (frustum == null || builtAt == null || cameraPos == null) return null;
        return builtAt.squaredDistanceTo(cameraPos) < 0.25 ? frustum : null;
    }
}
