package com.club.modules.zoom;

import com.club.config.ClubConfig;
import com.club.ui.motion.Curves;
import com.club.ui.motion.Transition;
import net.minecraft.client.MinecraftClient;

/**
 * Hold-to-zoom (v0.1 kit, Stage 39): while the zoom key is held, the WORLD FOV divides by an eased
 * factor — 0.18s decelerate, the same motion language as the UI; instant when Smooth is off. The
 * scroll wheel adjusts the factor mid-zoom (consumed, so the hotbar never switches under a zoom);
 * a scroll-adjusted factor persists ONCE on release, not per notch. Data/state only: the FOV hook
 * lives in {@code MixinGameRenderer} (world pass only — the hand keeps its FOV), the wheel seam in
 * {@code MixinMouse}.
 */
public final class ZoomModule {
    private ZoomModule() {}

    public static final float MIN_FACTOR = 2f, MAX_FACTOR = 8f;
    private static final float STEP = 0.5f;      // one wheel notch
    private static final float MAX_SMOOTH = 0.45f;   // ease duration (s) at smoothness = 1

    private static final long START = System.nanoTime();
    // Ease is recreated when the smoothness setting changes (Transition duration is fixed at construction).
    private static Transition ease = new Transition(0f, 0.22f, Curves.DECELERATE);
    private static float curDur = 0.22f;
    private static boolean wasActive;    // release edge → persist a scroll-adjusted factor
    private static boolean factorDirty;

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
        return divisorFor(cfg.factor, v);
    }

    /** Pure FOV divisor for a factor and eased amount {@code v} in [0,1] (1 = full zoom). */
    public static float divisorFor(float factor, float v) { return 1f + (factor - 1f) * v; }

    /** Wheel while zooming: adjust the factor, consume the scroll. Returns true when consumed. */
    public static boolean onScroll(double vertical) {
        if (vertical == 0 || !active()) return false;
        ClubConfig.Zoom cfg = ClubConfig.get().zoom;
        cfg.factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, cfg.factor + (vertical > 0 ? STEP : -STEP)));
        factorDirty = true;
        return true;
    }
}
