package com.club.ui.backend;

import com.club.ui.text.MsdfMetrics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * One MSDF weight: parsed glyph metrics + lazily-loaded GL texture.
 *
 * <p>Texture registration is deferred to the first render call ({@link #ensureTexture()})
 * so that {@link MsdfAtlas#load} is safe to call off the render thread or during
 * resource-manager availability checks.</p>
 */
public final class MsdfAtlas {

    /** Parsed glyph metrics (advance, plane-bounds, atlas UVs, line metrics). */
    public final MsdfMetrics metrics;

    /** Registered texture identifier — pass to {@code RenderSystem.setShaderTexture}. */
    public final Identifier textureId;

    /** Distance range in pixels, forwarded verbatim to the PxRange shader uniform. */
    public final float pxRange;

    private final Identifier pngId;
    private boolean textureLoaded;

    private MsdfAtlas(MsdfMetrics metrics, Identifier pngId, Identifier textureId) {
        this.metrics   = metrics;
        this.pngId     = pngId;
        this.textureId = textureId;
        this.pxRange   = metrics.distanceRange;
    }

    /**
     * Loads metrics from the embedded JSON resource for {@code weight}
     * ("regular", "medium", or "semibold"). The PNG is not decoded here.
     *
     * @throws RuntimeException if the JSON resource cannot be found or parsed.
     */
    public static MsdfAtlas load(String weight) {
        if ("1".equals(System.getProperty("club.ui.breakAtlas"))) throw new RuntimeException("simulated missing atlas: " + weight);
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        Identifier jsonId = Identifier.of("club", "ui/font/msdf/inter_" + weight + ".json");
        try (InputStream in = rm.getResourceOrThrow(jsonId).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            MsdfMetrics m = MsdfMetrics.parse(json);
            Identifier pngId    = Identifier.of("club", "ui/font/msdf/inter_" + weight + ".png");
            Identifier texId    = Identifier.of("club", "ui_atlas_" + weight);
            return new MsdfAtlas(m, pngId, texId);
        } catch (Exception e) {
            throw new RuntimeException("MsdfAtlas: failed to load weight '" + weight + "'", e);
        }
    }

    /**
     * Registers the atlas PNG as a GL texture on the first call; subsequent calls are no-ops.
     * Must be called from the render thread.
     *
     * @throws RuntimeException if the PNG resource cannot be found or decoded.
     */
    public void ensureTexture() {
        if (textureLoaded) return;
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        try (InputStream in = rm.getResourceOrThrow(pngId).getInputStream()) {
            NativeImage img = NativeImage.read(in);
            boolean owned = false;
            try {
                NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                // NativeImageBackedTexture took ownership of img; don't close it on success
                owned = true;
                tex.setFilter(true, false);   // bilinear, no mip — correct for SDF
                MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, tex);
                textureLoaded = true;
            } finally {
                if (!owned) img.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("MsdfAtlas: failed to load texture for '" + pngId + "'", e);
        }
    }
}
