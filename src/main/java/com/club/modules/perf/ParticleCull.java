package com.club.modules.perf;

import com.club.config.ClubConfig;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Stop tessellating the particles nobody can see.
 *
 * <p>VERIFIED, NOT ASSUMED: {@code ParticleManager.renderParticles} walks every live particle and calls
 * {@code buildGeometry} on it. There is not one reference to {@code Frustum} in the class, no distance test,
 * no per-frame cap. The campfire behind your head is fully tessellated, every frame, forever. Sodium does
 * not cull them either (it optimises the quad path; it does not skip anything), and Iris's shadow pass does
 * not render particles at all — which is why this is the one cull in the plan with no shadow risk.
 *
 * <p>THE THREE RULES, EACH ONE PAID FOR:
 *
 * <ul>
 *   <li><b>Only {@link BillboardParticle}.</b> Two vanilla particles are drawn nowhere near their own
 *       coordinates: {@code ElderGuardianAppearanceParticle} draws a full model at a FIXED CAMERA-RELATIVE
 *       offset (the jumpscare), and {@code ItemPickupParticle} lerps between the item and the player. A test
 *       on "where is this particle" culls them at random. Everything off the billboard sheet passes through
 *       untouched — not as a mitigation, as a rule.</li>
 *   <li><b>The frustum must be THIS frame's.</b> It is captured at {@code WorldRenderer.setupFrustum}, whose
 *       first argument is the camera position — so freshness is checkable, and it is checked. A frustum from
 *       a frame ago is a cull that eats particles at the screen edge as you turn. If it is stale, or was
 *       never captured, we fall back to the half-space test, which needs nothing but the camera.</li>
 *   <li><b>Behind the camera is invisible BY CONSTRUCTION.</b> The frustum test culls strictly more (the
 *       side planes too) and is equally invisible; the distance limit and the per-frame cap are NOT, and
 *       are not built here at all. Nothing in this class changes what the player can see.</li>
 * </ul>
 */
public final class ParticleCull {
    private ParticleCull() {}

    /** Slack on the culling volume, in blocks. A particle's box is its COLLISION box, not its art. */
    private static final double MARGIN = 2.0;

    /** Counters — deterministic, machine-independent, and the only thing ClubBench asserts on. */
    private static long considered, skipped;

    public static long considered() { return considered; }
    public static long skipped() { return skipped; }
    public static void resetCounters() { considered = skipped = 0; }

    /** A/B seam and the config toggle in one. Off, the hook is a getstatic and a branch. */
    public static boolean enabled() {
        ClubConfig.Perf p = ClubConfig.get().perf;
        return p != null && p.cullParticles && !forceOff;
    }
    /** Forced off by the benchmark's control arm — never by the player, who has the config toggle. */
    public static volatile boolean forceOff;

    /**
     * True if this particle can be skipped without changing one pixel of what the player sees.
     * Called once per particle per frame, from the {@code buildGeometry} redirect.
     */
    public static boolean skip(Particle p, Camera cam, float tickDelta) {
        if (!enabled()) return false;
        // Iris's shadow pass renders no particles, so this cannot fire there today. It is asked anyway: the
        // guard costs a getstatic, and a future Iris that DID draw particles into the shadow map would
        // otherwise have them culled by the main camera's frustum — i.e. by where the PLAYER is looking.
        if (IrisCompat.inShadowPass()) return false;

        considered++;

        // The CUSTOM sheet draws where the camera is, not where the particle is. Never touch it.
        if (!(p instanceof BillboardParticle bp)) return false;

        // THE TEST HAS TO BE CHEAPER THAN THE WORK IT SKIPS, AND THE FIRST ONE WAS NOT.
        //
        // The first cut of this used the real Frustum: strictly better coverage (the side planes, not just
        // the eye plane) and, on paper, obviously right. The benchmark disagreed. Frustum.isVisible tests
        // EIGHT CORNERS against SIX PLANES and needs an expanded Box, which is an allocation — per particle,
        // per frame, including every particle it then decides to KEEP. That test costs about what
        // buildGeometry costs, so the cull skipped 80% of the particles and made the frame 4.1% SLOWER.
        //
        // What is left is one dot product and no allocation: is this particle behind the eye plane? It culls
        // less. It culls FREE. The reason the frustum version is not kept "for the front particles" is that
        // those are exactly the ones that would pay for it and never benefit.
        Box box = p.getBoundingBox();
        double slack = bp.getSize(tickDelta) + MARGIN;

        Vec3d eye = cam.getPos();
        org.joml.Vector3f look = cam.getHorizontalPlane();   // the look vector, cached by Camera — no allocation
        double dx = (box.minX + box.maxX) * 0.5 - eye.x;
        double dy = (box.minY + box.maxY) * 0.5 - eye.y;
        double dz = (box.minZ + box.maxZ) * 0.5 - eye.z;
        double along = dx * look.x() + dy * look.y() + dz * look.z();

        if (along >= -slack) return false;       // in front of, or near, the eye plane: draw it
        skipped++;
        return true;
    }
}
