package com.club.ui.backend;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class UiShaders {
    private UiShaders() {}
    public static ShaderProgram SDF, SDF_BATCH, TEXT;

    /**
     * Vertex format of the BATCHED shape shader: the per-shape parameters that used to be uniforms
     * (half size, corner radius, border thickness) ride the vertex instead, so every plain rounded
     * rect in a frame can go out in a single draw call.
     *
     * <p>UV1/UV2 are SHORT pairs — the only integer carriers Minecraft's BufferBuilder can write —
     * hence the 1/16 px fixed point (see PARAM_SCALE).</p>
     */
    public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color",    VertexFormatElement.COLOR)
            .add("UV0",      VertexFormatElement.UV_0)
            .add("UV1",      VertexFormatElement.UV_1)
            .add("UV2",      VertexFormatElement.UV_2)
            .build();

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(ctx -> {
            ctx.register(Identifier.of("club", "ui_sdf_shape"), VertexFormats.POSITION_TEXTURE_COLOR, p -> SDF = p);
            ctx.register(Identifier.of("club", "ui_sdf_batch"), SHAPE_FORMAT, p -> SDF_BATCH = p);
            ctx.register(Identifier.of("club", "ui_msdf_text"), VertexFormats.POSITION_TEXTURE_COLOR, p -> TEXT = p);
        });
    }
    public static boolean ready() { return SDF != null && SDF_BATCH != null && TEXT != null; }
}
