package com.club.modules.fullbright;

/**
 * Fullbright (v0.1 kit, Stage 40; scoped in Stage 45): overrides the gamma value ONLY where the
 * lightmap consumes it — {@code MixinSimpleOption} answers 15.0 for the gamma option, but only while
 * {@code MixinLightmapTextureManager} has marked the client inside {@code LightmapTextureManager.update}.
 *
 * <p>Scoping matters: {@code GameOptions.write()} and the vanilla Video-settings slider ALSO call
 * {@code SimpleOption.getValue()} on gamma. An unscoped override poisoned options.txt (gamma serialized
 * as 15.0 → the entry is dropped on encode → the user's real brightness is lost on next launch) and
 * rendered the Brightness slider at a nonsense position. Gating on the in-lightmap window keeps
 * options.txt, the codec write, and the settings UI completely honest — only the world lightmap sees 15.</p>
 *
 * <p>The state is a static mirror of {@code ClubConfig.fullbright} (seeded in ClubClient, updated by
 * the menu toggle and by per-module keybinds) — the getValue hook is one of the hottest calls in the
 * client, so it must gate on plain static booleans, never a lazy config lookup.</p>
 */
public final class FullbrightModule {
    private FullbrightModule() {}

    /** Vanilla's gamma slider tops out at 1.0; 15 is the community-standard "fullbright" value. */
    public static final double GAMMA = 15.0;

    private static boolean on;
    private static boolean inLightmap;   // true only during LightmapTextureManager.update

    public static boolean on() { return on; }
    public static void set(boolean v) { on = v; }

    /** True only while the client is computing the world lightmap — the one place gamma may read 15. */
    public static boolean inLightmap() { return inLightmap; }
    public static void enterLightmap() { inLightmap = true; }
    public static void exitLightmap() { inLightmap = false; }
}
