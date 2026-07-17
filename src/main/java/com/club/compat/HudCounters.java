package com.club.compat;

/**
 * Whether the HUD profiler is FED on this version — the one fact the harness needs before it may print a
 * perf number, stated once.
 *
 * <p><b>What is true here.</b> Below 1.21.5 the HUD's GL draws are counted by the shader path itself
 * ({@code ModernBackend.SHAPE_DRAWS}, {@code ModernText}'s, {@code IconBatch.DRAWS}) — Club submits every
 * primitive, so Club can count them. From 1.21.5 those three classes are not in the build at all (see
 * build.gradle): shapes, text and icons are RECORDED into vanilla's GuiRenderer, which groups them by
 * pipeline and issues the draws itself. Nobody on our side of that boundary knows how many draws a frame
 * cost, and a count we cannot take is not a count we may estimate.
 *
 * <p>So {@code HudManager} does not feed {@code HudProfiler} there, deliberately — reporting zeros for draws
 * that plainly happen is the exact failure this project has retracted three times. The profiler is a dev
 * instrument, and it comes back with the shaders.
 *
 * <p><b>Why this constant exists rather than a version check at the call site.</b> The consequence of that
 * decision is silent and remote: every snapshot on 1.21.5+ reports zero frames, so anything asked of the
 * profiler answers 0. The harness read that 0 as a measurement and printed
 * {@code FAIL perf: the icon draws are counted, not invisible (0/frame)} on every run of every version past
 * 1.21.4 — a permanently red line that describes the build, not the mod, and a permanently red line is one
 * nobody reads when it finally means something.
 *
 * <p><b>This constant and {@code HudManager}'s {@code //? if <1.21.5} feed are one decision written twice,
 * and they must not drift.</b> They cannot be merged: the feed names classes that do not exist past 1.21.4,
 * so its guard has to be resolved by Stonecutter at build time, while the harness needs the same fact as a
 * value it can branch on. If the profiler is ever fed on a new version, this flips in the same commit.
 */
public final class HudCounters {
    private HudCounters() {}

    /** True where {@code HudManager} feeds {@code HudProfiler} — i.e. where the GL draw counters exist. */
    //? if <1.21.5 {
    public static final boolean AVAILABLE = true;
    //?} else {
    /*public static final boolean AVAILABLE = false;*/
    //?}
}
