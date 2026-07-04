package com.club.modules.animations;

import com.club.util.Mth;

/**
 * Custom first-person attack animations.
 *
 * Each animation is a full {@link Pose}: a translation arc (blocks) <b>and</b> a
 * rotation (degrees), applied about the held item's grip — exactly the space
 * vanilla swings in. Pairing rotation with a compensating arc is what keeps the
 * blade from dropping/orbiting; every animation returns cleanly to rest at
 * {@code swing == 0} and {@code swing == 1}.
 *
 * {@code arm} is +1 for a right hand, -1 for a left hand, so sweeps mirror
 * correctly. {@code amp} scales the motion (0.5 subtle … 1.5 punchy).
 */
public enum AnimationType {
    /** Stock Minecraft swing (no override — fully vanilla). Uses the identity default of
     *  {@link #sample}; it is never reached anyway ({@code AnimationModule.pose} short-circuits
     *  VANILLA to null before sampling). */
    VANILLA("Vanilla"),
    /** Clean diagonal chop — snappy, returns to rest. The default showcase. */
    CLASSIC("Classic") {
        @Override public Pose sample(Pose p, float s, float amp, int arm) {
            float e = front(s), b = bell(s);
            return p.set(arm * -0.18f * e * amp, -0.05f * e * amp, -0.12f * b * amp,
                         -52f * e * amp, arm * 16f * e * amp, arm * -15f * e * amp);
        }
    },
    /** Forward stab — drives the tip out and back. */
    THRUST("Thrust") {
        @Override public Pose sample(Pose p, float s, float amp, int arm) {
            float e = front(s);
            return p.set(arm * 0.03f * e * amp, 0.05f * e * amp, -0.36f * e * amp,
                         14f * e * amp, arm * -4f * e * amp, 0);
        }
    },
    /** Overhead chop: a quick wind-up, then a hard downward strike. */
    OVERHEAD("Overhead") {
        @Override public Pose sample(Pose p, float s, float amp, int arm) {
            float chop = front(s);
            float pre = bell(s) * (1f - s); // early raise that fades out
            return p.set(0, 0.05f * pre * amp, -0.08f * chop * amp,
                         (30f * pre - 92f * chop) * amp, 0, arm * -6f * chop * amp);
        }
    },
    /** Wide horizontal sweep (big yaw + roll). */
    SHORT_SLASH("Short Slash") {
        @Override public Pose sample(Pose p, float s, float amp, int arm) {
            float e = front(s), side = bell(s);
            return p.set(arm * -0.22f * e * amp, -0.02f * e * amp, -0.10f * e * amp,
                         -18f * e * amp, arm * 50f * side * amp, arm * -14f * e * amp);
        }
    },
    /**
     * Full blade spin, flat to the screen. Rotation is pure roll about the camera
     * axis (Z) — the same axis vanilla uses for its in-plane swing tilt — so the
     * blade sweeps a clean circle facing the player and never dives into depth
     * (no Z-translate "pump"). Direction mirrors per hand. Amplitude drives the spin
     * on both axes it honestly can: the turn count (quantized — only FULL turns land
     * back at rest at {@code s == 1}) and the launch snap (continuous, so the slider
     * always does something: low = even glide, high = hard front-loaded whip that
     * eases into the landing — the family's curve language).
     */
    SPIN("Spin") {
        @Override public Pose sample(Pose p, float s, float amp, int arm) {
            int turns = Math.max(1, Math.round(amp));
            float e = 1f - (float) Math.pow(1f - s, 1f + amp);   // monotonic 0→1, soft landing
            return p.set(0, 0, 0, 0, 0, arm * -360f * turns * e);
        }
    };

    private final String label;

    AnimationType(String label) { this.label = label; }

    public String label() { return label; }

    /** Front-loaded bell (snaps out fast, eases back): 0 → 1 → 0. */
    protected static float front(float swing) {
        return (float) Math.sin(Math.sqrt(Mth.clamp(swing, 0f, 1f)) * Math.PI);
    }

    /** Symmetric bell: 0 → 1 → 0. */
    protected static float bell(float swing) {
        return (float) Math.sin(Math.PI * Mth.clamp(swing, 0f, 1f));
    }

    /** Fill {@code out} with this animation's pose at swing progress {@code swing}.
     *  Default: identity (rest) — every non-passthrough constant overrides. */
    public Pose sample(Pose out, float swing, float amplitude, int arm) {
        return out.set(0, 0, 0, 0, 0, 0);
    }

    public static AnimationType fromName(String name) {
        try { return valueOf(name); } catch (Exception e) { return CLASSIC; }
    }
}