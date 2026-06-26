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

    /** Call at the start of every screen/HUD render pass. */
    public static void beginFrame(DrawContext ctx) { Backends.begin(ctx); }

    public static boolean modernAvailable() { return UiShaders.ready(); }
    public static Backend backend() {
        if (forced != null) return forced;
        return modernAvailable() ? Backend.MODERN : Backend.LEGACY;
    }
    public static void setBackend(Backend b) { forced = b; }
    public static void setAuto() { forced = null; }

    public static UiRenderer renderer() { return backend() == Backend.MODERN ? Backends.MODERN_R : Backends.LEGACY_R; }
    public static UiText text() { return backend() == Backend.MODERN ? Backends.MODERN_T : Backends.LEGACY_T; }
}
