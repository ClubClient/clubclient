package com.club.combat;

/**
 * The shared, version-blind holder the hitbox render mixins (Tasks 2/3) read every frame. {@link HitboxModule}
 * writes it once per client tick; the mixins only READ, and the colour/junk decision is taken ONLY through the
 * two pure helpers below — so it is a plain function of these fields, and ClubHarness can unit-assert it.
 *
 * <p>Deliberately MC-import-free: no Minecraft on this class's dependency graph, so the harness can exercise
 * {@link #resolveArgb()}/{@link #showJunk()} as ordinary logic. Fields are {@code volatile} — written on the
 * client thread each tick, read on the render thread each frame.</p>
 */
public final class HitboxState {
    private HitboxState() {}

    /** Master switch. When false every render hook is a no-op and vanilla draws its hitboxes unchanged. */
    public static volatile boolean enabled;
    /** True = strip the vanilla "junk" lines (blue view vector + red eye box); the default clean look. */
    public static volatile boolean cleanLines = true;
    /** Resolved once per tick: is a player under the crosshair right now? Chooses {@link #argbA} vs {@link #argbB}. */
    public static volatile boolean crosshairOnPlayer;
    /** Packed {@code 0xAARRGGBB} drawn when the crosshair IS on a player ("On player"). */
    public static volatile int argbA;
    /** Packed {@code 0xAARRGGBB} drawn otherwise ("Default"). */
    public static volatile int argbB;
    /** Outline thickness multiplier, 1.0..4.0 (clamped in config). 1.0 == the vanilla look on every version;
     *  above 1.0 the mixins widen the box outline (natively on 1.21.11, concentric outlines on 1.21.1/1.21.8).
     *  Initialised to 1.0 so a render read before the first tick pushes is the harmless no-change default. */
    public static volatile float lineWidth = 1.0f;

    /** The box colour to draw right now: {@code crosshairOnPlayer ? argbA : argbB}. */
    public static int resolveArgb() {
        return crosshairOnPlayer ? argbA : argbB;
    }

    /** Whether the vanilla junk lines (view vector + eye box) should still be drawn: {@code !cleanLines}. */
    public static boolean showJunk() {
        return !cleanLines;
    }

    /** World-space gap between the concentric outlines the 1.21.1/1.21.8 mixins stack to fake a wider line —
     *  small enough that adjacent (already shader-expanded ~2.5px) edges read as one thicker band at combat range. */
    public static final double RING_STEP = 0.005;

    /** How many concentric box outlines the 1.21.1/1.21.8 render path draws for the current {@link #lineWidth}:
     *  {@code 1} at width 1.0 (⇒ the single vanilla box, pixel-identical to today), rising one ring per 0.5 step
     *  up to {@code 7} at 4.0. Pure function of {@link #lineWidth} so ClubHarness can assert the mapping. The
     *  1.21.11 path does NOT use this — it widens natively via {@code DrawStyle.stroked(color, 2.5*lineWidth)}. */
    public static int rings() {
        float w = lineWidth;
        if (w <= 1.0f) return 1;
        int n = 1 + Math.round((w - 1.0f) / 0.5f);
        return Math.max(1, Math.min(7, n));
    }
}
