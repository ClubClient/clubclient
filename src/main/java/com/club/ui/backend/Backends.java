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
    //?} else {
    /*// The same renderer, re-expressed for the other side of the 1.21.5 seam: ModernBackend draws now and
    // owns its flush; ModernShapes records a GuiElementRenderState and lets vanilla's GuiRenderer flush it.
    // Exactly one of the two is in the source set on any version (build.gradle enforces it), so they never
    // meet and neither needs to know the other exists.
    public static final ModernShapes  MODERN_S = new ModernShapes();*/
    //?}
    public static final LegacyBackend LEGACY_R = new LegacyBackend();
    public static final LegacyText    LEGACY_T = new LegacyText();

    /** The DrawContext of the pass in flight — see {@link #current()}. */
    private static DrawContext current;

    /**
     * The DrawContext of the pass currently being rendered, or null outside one.
     *
     * <p>{@link com.club.ui.UiContext} deliberately exposes only a renderer and a text backend: a component
     * is not supposed to know Minecraft is underneath it. {@link SpriteIcons} is the one caller that must,
     * because from 1.21.5 an icon is drawn by {@code DrawContext.drawTexture} rather than by either of
     * those two. Both backends already stash this same reference from {@link #begin}; this exposes the one
     * they were all handed instead of inventing a second way to get it.</p>
     */
    public static DrawContext current() { return current; }

    public static void begin(DrawContext ctx) {
        current = ctx;
        //? if <1.21.5 {
        MODERN_R.begin(ctx); MODERN_T.begin(ctx);
        //?} else {
        /*MODERN_S.begin(ctx);*/
        //?}
        LEGACY_R.begin(ctx); LEGACY_T.begin(ctx);
    }
}
