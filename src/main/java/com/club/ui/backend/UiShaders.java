package com.club.ui.backend;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class UiShaders {
    private UiShaders() {}
    public static ShaderProgram SDF, TEXT;

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(ctx -> {
            ctx.register(Identifier.of("club", "ui_sdf_shape"), VertexFormats.POSITION_TEXTURE_COLOR, p -> SDF = p);
            ctx.register(Identifier.of("club", "ui_msdf_text"), VertexFormats.POSITION_TEXTURE_COLOR, p -> TEXT = p);
        });
    }
    public static boolean ready() { return SDF != null && TEXT != null; }
}
