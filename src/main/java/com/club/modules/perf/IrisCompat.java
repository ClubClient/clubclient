package com.club.modules.perf;

import com.club.ClubMod;
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
            // DEBUG on purpose. This fires for EVERY player who has Iris installed — the frustum mixin asks
            // inShadowPass() unconditionally, so resolve() runs even when every perf module is switched off.
            // "The reflection found what it went looking for" is a non-event the player cannot act on, and
            // it is not worth a line in a stranger's console; anyone actually debugging whether the guard is
            // armed can turn DEBUG on. The failure below IS worth interrupting them for.
            ClubMod.LOGGER.debug("[Club] Iris detected — shadow-pass guard armed ({})", c.getName());
        } catch (Throwable t) {
            // A version of Iris that moved the class is not a crash — it is a mod that gets no cull.
            // WARN: the perf modules go silently inert for this player (inShadowPass fails CLOSED, so every
            // cull stops firing), and the only person who can act on it is whoever reads the report — which
            // is why the Iris version and the throwable have to reach the log.
            ClubMod.LOGGER.warn("[Club] Iris is present but its shadow-pass flag was not found — entity and "
                    + "particle culls will stay OFF for this session (culling here would delete Iris shadows). "
                    + "Please report this together with your Iris version.", t);
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

    /**
     * Did the reflective lookup actually find Iris's shadow-pass flag? {@code false} when Iris is absent, and
     * also when Iris is present but has moved the class we look for.
     *
     * <p>This exists because the ONLY evidence that the guard was armed used to be a line a human read in a
     * log — and a human reading a log is not an instrument. The line is DEBUG now (it fires for every player
     * who installs Iris, and they can do nothing with it), so the harness has to ask instead. Under
     * {@code -PclubIris} it asserts this is true: "the guard exists" and "the guard is armed" are different
     * claims, and only the second one saves a shadow.
     */
    public static boolean armed() {
        if (!resolved) resolve();
        return inShadowPass != null;
    }
}
