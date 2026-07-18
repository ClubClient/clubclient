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

    /** The box colour to draw right now: {@code crosshairOnPlayer ? argbA : argbB}. */
    public static int resolveArgb() {
        return crosshairOnPlayer ? argbA : argbB;
    }

    /** Whether the vanilla junk lines (view vector + eye box) should still be drawn: {@code !cleanLines}. */
    public static boolean showJunk() {
        return !cleanLines;
    }
}
