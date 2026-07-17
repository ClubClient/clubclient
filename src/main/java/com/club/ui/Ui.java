package com.club.ui;

import com.club.ui.backend.Backends;
import net.minecraft.client.gui.DrawContext;

public final class Ui {
    private Ui() {}
    public enum Backend { MODERN, LEGACY }
    private static Backend forced = null; // null = auto

    /** Call once at client init (registers shaders). No-op where the shader path is not built — see
     *  {@link Backends}. */
    public static void init() {
        //? if <1.21.5 {
        com.club.ui.backend.UiShaders.register();
        //?}
    }

    /** Call at the start of every screen/HUD render pass (drawing in Minecraft's GUI units). */
    public static void beginFrame(DrawContext ctx) { beginFrame(ctx, 1f); }

    /**
     * Same, but for a caller drawing in its OWN unit space: {@code k} is how many Minecraft GUI units
     * one caller unit is worth, and the caller has already pushed the matching {@code scale(k)} onto the
     * matrix stack. Shapes and text ride that matrix for free — but the GL SCISSOR does not (it is set in
     * framebuffer pixels, outside the matrix), so the backends need to know the factor to convert clip
     * rects with. The Club menu uses this to keep a FIXED design canvas whatever the player's GUI scale
     * is (Stage 60).
     */
    public static void beginFrame(DrawContext ctx, float k) {
        Backends.begin(ctx);
        //? if <1.21.5 {
        Backends.MODERN_R.unitScale(k);
        //?}
        Backends.LEGACY_R.unitScale(k);
    }

    /**
     * Call at the END of every render pass that called {@link #beginFrame}. Shapes are BATCHED (Stage
     * 61): they sit in a buffer until something forces them out, and the end of the pass is the last
     * such point — miss it and the frame's final shapes are simply never drawn. Cheap and idempotent.
     */
    public static void endFrame() {
        //? if <1.21.5 {
        Backends.MODERN_R.flush();
        Backends.MODERN_T.flush();
        com.club.ui.backend.IconBatch.flush();
        //?}
    }

    /**
     * Submit the pending icons NOW (Stage 67). The icon batch is what turns four GL draws into one, and it
     * does that by outliving the text and shapes interleaved with it — but only INSIDE one HUD element.
     *
     * <p>It may not outlive the element, and the harness is why we know: the order proof went green three
     * runs and then caught a real violation on the fourth. A mob had wandered into the crosshair, the Target
     * chip appeared — and the Target chip is drawn AFTER the Effects chip, so its panel landed on top of an
     * Effects icon. Batching across that boundary would have popped the icon out through the panel. Two HUD
     * elements CAN overlap: the player can drag them onto each other in the editor.
     *
     * <p>So {@code HudCanvas} calls this between elements. Inside an element the reorder is proved harmless;
     * across elements the order is not reordered at all.
     */
    public static void flushIcons() {
        //? if <1.21.5 {
        com.club.ui.backend.IconBatch.flush();
        //?}
    }

    /**
     * Whether the MODERN path is BUILT on this Minecraft at all — a fact about the jar, not about this run.
     *
     * <p>Not the same question as {@link #modernAvailable()}, and the difference is the whole point: there
     * they both answer "false" on 1.21.8, but for opposite reasons. On 1.21.1 a false means something BROKE —
     * a resource pack, a shader that would not load — and the player should be told. On 1.21.5+ it means the
     * shader path was never compiled in, so LEGACY is the road, not a parachute, and there is nothing to
     * report. Telling that player to "check resource packs" is advice about a problem they do not have.
     */
    public static boolean modernSupported() {
        //? if <1.21.5 {
        return true;
        //?} else {
        /*return false;*/
        //?}
    }

    /** False wherever the shader path is not built (1.21.5+, until it is rewritten on RenderPipeline) —
     *  which sends {@link #backend()} down the LEGACY road it already takes when a shader fails to load. */
    public static boolean modernAvailable() {
        //? if <1.21.5 {
        return com.club.ui.backend.UiShaders.ready() && Backends.MODERN_T.healthy() && Backends.MODERN_R.healthy();
        //?} else {
        /*return false;*/
        //?}
    }
    public static Backend backend() {
        if (forced != null) return forced;
        return modernAvailable() ? Backend.MODERN : Backend.LEGACY;
    }
    public static void setBackend(Backend b) { forced = b; }
    public static void setAuto() { forced = null; }

    //? if <1.21.5 {
    public static UiRenderer renderer() { return backend() == Backend.MODERN ? Backends.MODERN_R : Backends.LEGACY_R; }
    public static UiText text() { return backend() == Backend.MODERN ? Backends.MODERN_T : Backends.LEGACY_T; }
    //?} else {
    /*public static UiRenderer renderer() { return Backends.LEGACY_R; }
    public static UiText text() { return Backends.LEGACY_T; }*/
    //?}
}
