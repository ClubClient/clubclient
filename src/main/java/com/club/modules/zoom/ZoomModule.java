package com.club.modules.zoom;

import com.club.config.ClubConfig;
import com.club.ui.motion.Curves;
import com.club.ui.motion.Transition;
import net.minecraft.client.MinecraftClient;

/**
 * Hold-to-zoom (v0.1 kit, Stage 39): while the zoom key is held, the WORLD FOV divides by an eased
 * factor — decelerate, the same motion language as the UI; instant when Smooth is off. The ease
 * duration is {@code smoothness * MAX_SMOOTH} seconds — 0 → instant, 1 → 0.45s — so the Smooth
 * slider IS the duration. The scroll wheel adjusts the factor mid-zoom (consumed, so the
 * hotbar never switches under a zoom); a scroll-adjusted factor persists ONCE on release, not per
 * notch. Data/state only: the FOV hook lives in {@code MixinGameRenderer} (world pass only — the
 * hand keeps its FOV), the wheel seam in {@code MixinMouse}.
 */
public final class ZoomModule {
    private ZoomModule() {}

    public static final float MIN_FACTOR = 2f, MAX_FACTOR = 8f;
    private static final float STEP = 0.5f;      // one wheel notch
    // Ease duration (s) at smoothness = 1. Kept at 0.45 across the log-curve fix, deliberately: under the
    // old curve the FOV was visually done in the first ~third of the duration and crawled after, so 0.45s
    // never read as 0.45s of motion. Now the whole duration is visible motion — the same number will FEEL
    // longer. It is the ceiling of a slider the user sets (default smoothness 0.5 → 0.225s), and "1 = very
    // smooth" should mean genuinely slow, so the ceiling stays. Revisit only if the owner says max is sluggish.
    private static final float MAX_SMOOTH = 0.45f;

    private static final long START = System.nanoTime();
    // Ease is recreated when the smoothness setting changes (Transition duration is fixed at construction).
    private static Transition ease = new Transition(0f, 0.22f, Curves.DECELERATE);
    private static float curDur = 0.22f;
    private static boolean wasActive;    // release edge → persist a scroll-adjusted factor
    private static boolean factorDirty;
    private static float lastDivisor = 1f;   // this frame's divisor, read by the mouse hook (see sensitivityScale)

    private static float now() { return (System.nanoTime() - START) / 1_000_000_000f; }

    /** The module is on, the zoom key is physically held, and no screen owns the keyboard. Uses the
     *  RAW key state (Keys.held) so zoom works even if its key is also bound to something else. */
    public static boolean active() {
        return ClubConfig.get().zoom.enabled
                && com.club.util.Keys.held(com.club.ClubClient.zoomKey)
                && MinecraftClient.getInstance().currentScreen == null;
    }

    /** World-FOV divisor for this frame (1 = no zoom). Advances the ease and the release-persist
     *  edge — called once per frame from the world getFov pass. */
    public static float fovDivisor() {
        ClubConfig.Zoom cfg = ClubConfig.get().zoom;
        boolean active = active();
        float t = now();
        float dur = cfg.smoothness * MAX_SMOOTH;   // 0 → instant
        if (dur != curDur) { ease = new Transition(ease.value(t), dur, Curves.DECELERATE); curDur = dur; }
        ease.target(active ? 1f : 0f, t);
        float v = ease.value(t);                    // duration 0 → returns the target instantly
        if (wasActive && !active && factorDirty) { factorDirty = false; ClubConfig.save(); }
        wasActive = active;
        lastDivisor = divisorFor(cfg.factor, v);
        return lastDivisor;
    }

    /**
     * Pure FOV divisor for a factor and eased amount {@code v} in [0,1] (1 = full zoom).
     *
     * <p>The ease is applied in LOG space — {@code factor^v}, not {@code 1 + (factor-1)*v}. This is not
     * a taste choice, it is what made the zoom lurch (owner: "плавность работает не на всю силу").
     * The eye does not see the divisor, it sees the FOV, and FOV = base/divisor is a HYPERBOLA in the
     * divisor: easing the divisor linearly dumps most of the visible motion into the first few frames
     * and crawls through the rest. At factor 8 / base 70°, the old curve put the FOV at 41° by v=0.10
     * — 47% of the whole FOV travel, and DECELERATE reaches v=0.10 inside the first ~5% of the duration
     * — and then spent the entire back half (v=0.6→1) moving the FOV just 4.7°. Worse, the violence of
     * that opening scaled with the factor: at the SAME smoothness, 2× was almost linear and 8× was a
     * jump, because the opening rate goes as (factor-1). One slider, two behaviours.</p>
     *
     * <p>{@code factor^v} makes every equal slice of time multiply the magnification by an equal RATIO,
     * which is what a perceptually even zoom is, and it behaves identically at 2× and at 8×. Endpoints
     * are unchanged and exact — Math.pow returns the base for exponent 1 and 1.0 for exponent 0 — so
     * v=0 still means no zoom and v=1 still divides the FOV by exactly the factor.</p>
     */
    public static float divisorFor(float factor, float v) {
        // Exact endpoints, and no pow() on the idle path — fovDivisor() runs every frame, zoomed or not.
        if (v <= 0f) return 1f;
        if (v >= 1f) return factor;
        return (float) Math.pow(factor, v);
    }

    /**
     * Look-sensitivity multiplier for this frame (1 = untouched). Zoomed in, the mouse must turn the
     * view SLOWER — otherwise a 4× view flings around at 4× the on-screen speed and is unusable for
     * the thing zoom exists for (owner, Stage 58; every mainstream zoom does this).
     *
     * <p>The ratio is the tangent of the half-FOVs, not a flat 1/divisor: that keeps the world moving
     * at a CONSTANT speed across the screen at any zoom level, which is what "aiming feels the same"
     * actually means. It rides the same eased divisor, so the slow-down ramps in with the zoom instead
     * of snapping. Floored so an 8× zoom at a low FOV never reads as a dead mouse.</p>
     */
    public static float sensitivityScale() {
        return scaleFor(lastDivisor, baseFov());
    }

    /** The player's FOV setting in degrees (the zoom divides it). */
    private static float baseFov() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return 70f;
        return mc.options.getFov().getValue();
    }

    /** Pure: sensitivity multiplier for a divisor and a base FOV (degrees). Unit-testable, no GL. */
    public static float scaleFor(float divisor, float baseFovDeg) {
        if (divisor <= 1.0005f || baseFovDeg <= 0f) return 1f;
        double half = Math.toRadians(baseFovDeg / 2.0);
        double zoomedHalf = Math.toRadians(baseFovDeg / divisor / 2.0);
        double s = Math.tan(zoomedHalf) / Math.tan(half);
        return (float) Math.max(0.05, Math.min(1.0, s));
    }

    /** Wheel while zooming: adjust the factor, consume the scroll. Returns true when consumed. */
    public static boolean onScroll(double vertical) {
        if (vertical == 0 || !active()) return false;
        ClubConfig.Zoom cfg = ClubConfig.get().zoom;
        cfg.factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, cfg.factor + (vertical > 0 ? STEP : -STEP)));
        factorDirty = true;
        return true;
    }
}
