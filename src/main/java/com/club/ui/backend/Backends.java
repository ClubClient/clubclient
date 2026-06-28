package com.club.ui.backend;

import net.minecraft.client.gui.DrawContext;

public final class Backends {
    private Backends() {}
    public static final ModernBackend MODERN_R = new ModernBackend();
    public static final ModernText    MODERN_T = new ModernText();
    public static final LegacyBackend LEGACY_R = new LegacyBackend();
    public static final LegacyText    LEGACY_T = new LegacyText();

    public static void begin(DrawContext ctx) {
        MODERN_R.begin(ctx); MODERN_T.begin(ctx); LEGACY_R.begin(ctx); LEGACY_T.begin(ctx);
    }
}
