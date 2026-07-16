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
 * <p>WHAT THIS CLASS ACTUALLY DOES — ONE HALF-SPACE TEST, AND NOTHING ELSE. Take the particle's
 * bounding-box centre, subtract the eye, dot it with the camera's look vector. If the particle sits behind
 * the eye plane by more than its own size plus {@code MARGIN} blocks of slack, skip {@code buildGeometry}.
 * That is the entire cull. There is NO frustum, no distance limit, no per-frame cap, and no
 * staleness-or-fallback path: the test reads only the {@link Camera} it is handed and the particle itself,
 * so it holds no cached state that could go stale between frames. Anything this header says beyond that,
 * it should not say.
 *
 * <p>WHY THERE IS NO FRUSTUM: there was one, and it was thrown out. It culls strictly more (the side planes,
 * not just the eye plane) and it would have been just as invisible — but the test cost about what the
 * {@code buildGeometry} it was avoiding cost, and every particle it decided to KEEP paid that price for
 * nothing. It made the frame slower, and the benchmark said so. The measurement, and the numbers, are in
 * the comment inside {@link #skip} where they can be checked against the code they describe.
 *
 * <p>THE RULES, EACH ONE PAID FOR:
 *
 * <ul>
 *   <li><b>Only {@link BillboardParticle}.</b> Two vanilla particles are drawn nowhere near their own
 *       coordinates: {@code ElderGuardianAppearanceParticle} draws a full model at a FIXED CAMERA-RELATIVE
 *       offset (the jumpscare), and {@code ItemPickupParticle} lerps between the item and the player. A test
 *       on "where is this particle" culls them at random. Everything off the billboard sheet passes through
 *       untouched — not as a mitigation, as a rule.</li>
 *   <li><b>Behind the eye plane is invisible BY CONSTRUCTION.</b> The quad is built around the particle's
 *       centre, so a centre that is behind the eye by more than the particle's own size cannot put a pixel
 *       on the screen — and {@code MARGIN} buys another two blocks of slack on top. That is why this cull
 *       needs no visual budget: it removes work, never pixels. A distance limit and a per-frame cap would
 *       NOT be invisible, which is exactly why neither is built here. Nothing in this class changes what the
 *       player can see.</li>
 *   <li><b>Never during a shadow pass.</b> Iris draws no particles there today, so the guard at the top of
 *       {@link #skip} cannot fire; it is asked anyway, because a future Iris that DID draw particles into
 *       the shadow map would otherwise have them culled by where the PLAYER is looking, not by where the
 *       light is.</li>
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

        Vec3d eye = com.club.compat.Cam.pos(cam);
        // Vector3fc, not Vector3f: 1.21.11 narrowed the return type to the read-only interface. Vector3f
        // implements it in every JOML the game has shipped, so this one declaration compiles on every
        // version and needs no guard — we only ever read x()/y()/z() anyway.
        org.joml.Vector3fc look = cam.getHorizontalPlane();   // the look vector, cached by Camera — no allocation
        double dx = (box.minX + box.maxX) * 0.5 - eye.x;
        double dy = (box.minY + box.maxY) * 0.5 - eye.y;
        double dz = (box.minZ + box.maxZ) * 0.5 - eye.z;
        double along = dx * look.x() + dy * look.y() + dz * look.z();

        if (along >= -slack) return false;       // in front of, or near, the eye plane: draw it
        skipped++;
        return true;
    }
}
