package com.club.ui.backend;

import com.club.ClubMod;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public final class UiShaders {
    private UiShaders() {}
    public static ShaderProgram SDF, SDF_BATCH, TEXT, ICON;

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
            // Clear BEFORE registering, and not only on failure. This callback runs inside
            // GameRenderer.loadPrograms(), which then calls clearPrograms() — closing the programs these
            // fields point at — and only AFTER that runs the load callbacks below that re-assign them. So a
            // field surviving from the previous load is a CLOSED GL program, while ready() reads non-null as
            // "MODERN is usable". If a resource pack breaks one of our shaders on a reload, catching the
            // failure without clearing would leave MODERN up, drawing through a closed program every frame:
            // silent, undiagnosable, worse than the crash we are fixing. From here on, a field is non-null
            // only if THIS load actually produced it.
            //
            // The MODERN path is unaffected: on success the load callbacks re-assign all four inside this
            // same synchronous loadPrograms() call, before a single frame is drawn.
            SDF = SDF_BATCH = TEXT = ICON = null;

            // Not short-circuited: every program is attempted, so one failure reports the rest too, and the
            // outcome does not depend on the order of these lines.
            boolean sdf   = load(ctx, "ui_sdf_shape", VertexFormats.POSITION_TEXTURE_COLOR, p -> SDF = p);
            boolean batch = load(ctx, "ui_sdf_batch", SHAPE_FORMAT,                         p -> SDF_BATCH = p);
            boolean text  = load(ctx, "ui_msdf_text", VertexFormats.POSITION_TEXTURE_COLOR, p -> TEXT = p);
            // Deliberately NOT part of the LEGACY decision — a missing icon program has its own, narrower
            // fallback to the per-sprite DrawContext path. See iconReady().
            load(ctx, "ui_icon", VertexFormats.POSITION_TEXTURE_COLOR, p -> ICON = p);

            // Mirrors ready(): those three are what MODERN cannot draw without.
            if (!(sdf && batch && text)) {
                ClubMod.LOGGER.warn("[Club] UI core shaders incomplete -> falling back to the LEGACY backend. "
                        + "Usual cause: a resource pack overriding assets/club/shaders/, or a corrupt jar.");
            }
        });
    }

    /**
     * Register one core shader, surviving its failure. Returns true if the program was handed to Minecraft.
     *
     * <p>A core shader that fails to compile used to take the game down: the exception escaped the
     * resource-reload listener and became {@code RuntimeException("could not reload shaders")}. That made the
     * LEGACY backend unreachable by the exact failure it exists for. Now the failure is contained here — the
     * field stays null, {@link #ready()} turns false, and {@code Ui.backend()} resolves to LEGACY through the
     * mechanism that was already there (the same one behind {@code ModernBackend.healthy()}), which is what
     * puts up the LegacyNotice warning and plaque.
     *
     * <p>Catching {@link Exception} rather than {@code Throwable} is the point of the choice, and rather than
     * {@code IOException} alone: a broken shader arrives as EITHER — {@code IOException} for a missing file or
     * a GLSL compile error, but an unchecked {@code JsonSyntaxException}/{@code IllegalArgumentException} for a
     * malformed shader JSON, which vanilla's own {@code catch (IOException)} does not even cover. Both are "this
     * shader is bad", both are recoverable, both belong in LEGACY. {@code Error} is not: an OutOfMemoryError or
     * a LinkageError means the JVM or the installation is broken, not the shader, and swallowing it would hide a
     * fatal condition behind a cosmetic fallback.
     */
    private static boolean load(CoreShaderRegistrationCallback.RegistrationContext ctx,
                                String path, VertexFormat format, Consumer<ShaderProgram> sink) {
        try {
            ctx.register(Identifier.of("club", path), format, sink);
            return true;
        } catch (Exception e) {
            // Once per failing program per resource reload — this callback is not on the frame path.
            ClubMod.LOGGER.error("[Club] core shader 'club:{}' failed to load", path, e);
            return false;
        }
    }
    public static boolean ready() { return SDF != null && SDF_BATCH != null && TEXT != null; }
    /** The batched duotone-icon program (Stage 67). Separate from {@link #ready()}: a missing icon shader
     *  must fall back to the old per-sprite DrawContext path, not take the whole UI down to LEGACY. */
    public static boolean iconReady() { return ICON != null; }
}
