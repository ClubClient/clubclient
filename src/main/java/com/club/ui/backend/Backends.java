package com.club.ui.backend;

import net.minecraft.client.gui.DrawContext;

/**
 * The two render backends, held as singletons.
 *
 * <p><b>MODERN exists only where it compiles.</b> The shader path is built on an API Minecraft deleted in
 * 1.21.5 — {@code BufferRenderer}, {@code VertexFormat} and most of {@code RenderSystem} went at once, and
 * {@code CoreShaderRegistrationCallback} went with them. Until that path is rewritten on {@code RenderPipeline},
 * versions from 1.21.5 on build the LEGACY path only, and {@link com.club.ui.Ui#backend()} routes to it by
 * the same rule it has always used: {@code modernAvailable() ? MODERN : LEGACY}. That fallback is a designed
 * behaviour, not a workaround for this port — it is what already happens when a shader fails to load.</p>
 *
 * <p>The boundary is 1.21.5 and it is measured: {@code BufferRenderer} and {@code VertexFormat} are present
 * in the 1.21.4 mappings and gone from 1.21.5. It is NOT the same boundary as the matrix change in
 * {@code com.club.compat.Mtx} (1.21.6) — two different rewrites, one release apart, which is exactly why
 * each one gets its own measured condition.</p>
 */
public final class Backends {
    private Backends() {}

    //? if <1.21.5 {
    public static final ModernBackend MODERN_R = new ModernBackend();
    public static final ModernText    MODERN_T = new ModernText();
    //?}
    public static final LegacyBackend LEGACY_R = new LegacyBackend();
    public static final LegacyText    LEGACY_T = new LegacyText();

    public static void begin(DrawContext ctx) {
        //? if <1.21.5 {
        MODERN_R.begin(ctx); MODERN_T.begin(ctx);
        //?}
        LEGACY_R.begin(ctx); LEGACY_T.begin(ctx);
    }
}
