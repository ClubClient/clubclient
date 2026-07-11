package com.club.modules.fullbright;

/**
 * Fullbright (v0.1 kit, Stage 40): overrides the GAMMA READ, not the option — {@code MixinSimpleOption}
 * answers 15.0 for the gamma option while this is on. Nothing is written to options.txt, video
 * settings show the user's real slider, and disabling is instant with nothing to restore.
 *
 * <p>The state is a static mirror of {@code ClubConfig.fullbright} (seeded in ClubClient, updated by
 * the menu toggle): the hook sits on {@code SimpleOption.getValue} — one of the hottest calls in the
 * client and one that runs during GameOptions construction, long before the config file may load —
 * so it must gate on a plain static boolean, never on a lazy config lookup.</p>
 */
public final class FullbrightModule {
    private FullbrightModule() {}

    /** Vanilla's gamma slider tops out at 1.0; 15 is the community-standard "fullbright" value. */
    public static final double GAMMA = 15.0;

    private static boolean on;

    public static boolean on() { return on; }
    public static void set(boolean v) { on = v; }
}
