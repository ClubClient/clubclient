package com.club.ui;

import com.club.ui.backend.Backends;
import com.club.ui.backend.UiShaders;
import net.minecraft.client.gui.DrawContext;

public final class Ui {
    private Ui() {}
    public enum Backend { MODERN, LEGACY }
    private static Backend forced = null; // null = auto

    /** Call once at client init (registers shaders). */
    public static void init() { UiShaders.register(); }

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
        Backends.MODERN_R.unitScale(k);
        Backends.LEGACY_R.unitScale(k);
    }

    /**
     * Call at the END of every render pass that called {@link #beginFrame}. Shapes are BATCHED (Stage
     * 61): they sit in a buffer until something forces them out, and the end of the pass is the last
     * such point — miss it and the frame's final shapes are simply never drawn. Cheap and idempotent.
     */
    public static void endFrame() { Backends.MODERN_R.flush(); Backends.MODERN_T.flush(); }

    public static boolean modernAvailable() { return UiShaders.ready() && Backends.MODERN_T.healthy() && Backends.MODERN_R.healthy(); }
    public static Backend backend() {
        if (forced != null) return forced;
        return modernAvailable() ? Backend.MODERN : Backend.LEGACY;
    }
    public static void setBackend(Backend b) { forced = b; }
    public static void setAuto() { forced = null; }

    public static UiRenderer renderer() { return backend() == Backend.MODERN ? Backends.MODERN_R : Backends.LEGACY_R; }
    public static UiText text() { return backend() == Backend.MODERN ? Backends.MODERN_T : Backends.LEGACY_T; }
}
