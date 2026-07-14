package com.club.modules.perf;

import com.club.config.ClubConfig;

/**
 * When the window is not in front, stop drawing 200 frames a second at a wall.
 *
 * <p><b>THIS GIVES YOU ZERO FPS IN THE GAME, AND SAYING OTHERWISE IS A LIE.</b> It is a battery feature, a
 * fan-noise feature, a second-monitor feature. Minecraft 1.21.1 has no background throttle at all (verified:
 * {@code getFramerateLimit} returns 60 only on the title screen — in-world, alt-tabbed, it hands back the
 * player's own max-FPS setting), so this is worth 40 lines. Dynamic FPS does it more thoroughly, and the
 * docs say so.
 *
 * <p>TWO RULES, BOTH FROM THE AUDIT, BOTH LOAD-BEARING:
 *
 * <ul>
 *   <li><b>{@code Math.min}, never a set.</b> Vanilla's Max Framerate slider bottoms out at 10 fps
 *       ({@code Codec.intRange(10, 260)}) — below our cap. "Set the limit to 15 when unfocused" would RAISE
 *       the limit of a player who deliberately chose 10. A performance mod does not get to increase the work
 *       the user asked for, and this is the only item in the whole plan that could have made someone's
 *       machine worse.</li>
 *   <li><b>Hysteresis.</b> {@code isWindowFocused()} is a raw GLFW edge with no debounce anywhere in the
 *       chain, and alt-tabbing THROUGH windows flips it repeatedly. Without a delay the cap would oscillate
 *       while the player is still deciding where to go.</li>
 * </ul>
 *
 * <p>WHY THE FLOOR IS 15 AND NOT 2. The brief said "never below 15, because at fewer frames the client
 * cannot hold 20 ticks a second and keepalives will lag". Both halves are wrong, and the bytecode says so:
 * {@code render()} runs up to TEN ticks per frame, so 2 fps is enough for 20 TPS, and the keepalive interval
 * is 15 seconds while packets drain once per frame. The real reason is a different line entirely —
 * {@code RenderSystem.limitDisplayFPS} wakes on input and then GOES STRAIGHT BACK TO SLEEP until the frame
 * deadline. So the first frame after you alt-tab back costs up to 1/cap: 67 ms at 15 fps, half a second at
 * 2. The floor is about how fast the window answers you, not about ticks or the network.
 */
public final class BackgroundThrottle {
    private BackgroundThrottle() {}

    /** Below this the window feels broken when you come back to it — 1/cap is the wake-up latency. */
    public static final int FLOOR = 15;
    /** How long the window must stay unfocused before we believe it. Alt-tab passes through windows. */
    public static final long HYSTERESIS_MS = 500;

    private static long unfocusedSince;

    /** The live hook. A thin shell: it reads the config and the clock, and decides nothing. */
    public static int limit(int vanillaLimit) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        ClubConfig.Perf p = ClubConfig.get().perf;
        if (p == null) return vanillaLimit;
        return limit(vanillaLimit, mc == null || mc.isWindowFocused(), System.currentTimeMillis(),
                p.throttleWhenUnfocused, p.backgroundFps);
    }

    /**
     * The whole decision, as a pure function of (what vanilla wanted, is the window in front, what time it
     * is, is the feature on, what cap was asked for). Nothing here reads a config or a clock, which is the
     * only reason its two traps can be guarded by a test instead of by hope.
     */
    public static int limit(int vanillaLimit, boolean focused, long nowMs, boolean on, int askedCap) {
        if (!on) { unfocusedSince = 0; return vanillaLimit; }

        if (focused) { unfocusedSince = 0; return vanillaLimit; }   // back in front: full speed, immediately

        if (unfocusedSince == 0) unfocusedSince = nowMs;
        if (nowMs - unfocusedSince < HYSTERESIS_MS) return vanillaLimit;

        int cap = Math.max(FLOOR, askedCap);
        return Math.min(vanillaLimit, cap);   // ONLY EVER TIGHTEN. Never raise what the player chose.
    }

    /** For tests: forget how long we have been in the background. */
    public static void reset() { unfocusedSince = 0; }
}
