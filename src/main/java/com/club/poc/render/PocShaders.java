package com.club.poc.render;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * PoC-only registration of the two custom core shaders used by the NEW render
 * stack. Files live under {@code assets/club/shaders/core/}; Fabric loads them by
 * the namespaced id ({@code club:<name>}). Registration is invoked from
 * {@link com.club.poc.PocBootstrap} and is entirely gated behind {@code CLUB_POC=1},
 * so nothing here touches the live client.
 */
public final class PocShaders {
    private PocShaders() {}

    /** Analytic SDF rounded-box: fill / border / glow / gradient (resolution-independent). */
    public static ShaderProgram SDF_SHAPE;
    /** MSDF text with screen-space AA — crisp at any zoom. */
    public static ShaderProgram MSDF_TEXT;

    /** Register both shaders. Call once during client init (when the PoC is enabled). */
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(ctx -> {
            ctx.register(Identifier.of("club", "club_sdf_shape"),
                    VertexFormats.POSITION_TEXTURE_COLOR, p -> SDF_SHAPE = p);
            ctx.register(Identifier.of("club", "club_msdf_text"),
                    VertexFormats.POSITION_TEXTURE_COLOR, p -> MSDF_TEXT = p);
        });
    }

    public static boolean ready() {
        return SDF_SHAPE != null && MSDF_TEXT != null;
    }
}
