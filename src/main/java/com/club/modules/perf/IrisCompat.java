package com.club.modules.perf;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * "Am I inside Iris's shadow pass right now?" — asked without ever mentioning Iris to the classloader.
 *
 * <p>WHY THIS EXISTS. The recon said Iris renders shadow-map entities through its own ShadowRenderer, so our
 * hooks would leave shadows alone. That is FALSE, and the bytecode says so: iris-1.8.8's
 * {@code ShadowRenderer.renderEntities} calls {@code EntityRenderDispatcher.shouldRender} with the SHADOW
 * frustum and draws through an {@code @Invoker} on {@code WorldRenderer.renderEntity}. Both of the seams the
 * brief sends us to are shared with the shadow pass. A cull keyed to the main camera's visibility, injected
 * at either, deletes the shadows of everything off-screen.
 *
 * <p>WHY IT IS SHAPED LIKE THIS. Iris is a {@code modRuntimeOnly} dependency — it is not on the compile
 * classpath, so a direct call does not compile, and a hard reference in a hot path would be a
 * NoClassDefFoundError on the first culled entity for the majority of players, who do not run Iris. So the
 * lookup happens once, reflectively, and only if the mod is actually loaded. Absent Iris, this is a
 * {@code getstatic} of {@code null} and a {@code false}.
 *
 * <p>Particles, verified, do NOT need this: Iris's shadow pass renders none at all. It is here for them
 * anyway — one guard used by every cull is one guard to get right, and the day someone adds a shadow-pass
 * particle path is not the day we want three copies of this to find.
 */
public final class IrisCompat {
    private IrisCompat() {}

    private static MethodHandle inShadowPass;
    private static boolean resolved;

    private static void resolve() {
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("iris")) return;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState");
            inShadowPass = MethodHandles.lookup()
                    .findStatic(c, "areShadowsCurrentlyBeingRendered", MethodType.methodType(boolean.class));
        } catch (Throwable t) {
            // A version of Iris that moved the class is not a crash — it is a mod that gets no cull.
            System.err.println("[club.perf] Iris is present but its shadow-pass flag was not found ("
                    + t + "). Culls that could corrupt shadows will stay OFF.");
            inShadowPass = null;
            irisPresentButOpaque = FabricLoader.getInstance().isModLoaded("iris");
        }
    }

    private static boolean irisPresentButOpaque;

    /**
     * True while Iris is drawing the shadow map. Any cull that decides visibility from the MAIN camera must
     * do nothing while this is true — the shadow pass sees the world from the sun.
     *
     * <p>Fails CLOSED: if Iris is installed but we could not find its flag, this returns true, so the cull
     * simply never fires. A mod that quietly deletes shadows is worse than a mod that quietly does nothing.
     */
    public static boolean inShadowPass() {
        if (!resolved) resolve();
        if (inShadowPass == null) return irisPresentButOpaque;
        try { return (boolean) inShadowPass.invokeExact(); }
        catch (Throwable t) { return true; }
    }
}
