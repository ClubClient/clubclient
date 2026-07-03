package com.club.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * DUOTONE pixel icons (Stage 17, owner pick "A" from the approach board): HUD icons are the game's
 * own textures — armor item icons, mob-effect sprites, resource packs and modded content included —
 * flattened into THREE TONES of one hue and tinted at draw time with the association/material color.
 * Vanilla recognizability, our palette.
 *
 * <p>How: on first use a source texture is baked into a grayscale "factor mask" (per pixel:
 * luminance → bright 1.28 / base 1.0 / shadow 0.52, stored as level·255/1.28 so the shader-color
 * multiply reproduces the exact tritone of the approved prototype), registered as a NEAREST-filtered
 * texture (pixel art must stay crisp under HUD scale) and drawn through the frame's
 * {@link DrawContext} with {@code setShaderColor(clamp(tint·1.28), alpha)}. One bake per texture —
 * any tint is free. The cache drops on resource reload (pack switches rebake).</p>
 *
 * <p>This is a deliberate DrawContext seam (like the retired HudSprites): pixel sprites are the one
 * thing the V2 renderer doesn't draw — which is also why this class lives in {@code com.club.hud}
 * (the vanilla-integration layer), NOT in {@code com.club.ui}: it talks RenderSystem/TextureManager,
 * and low-level render calls are banned outside the V2 backend (ArchitectureRuleTest). Set once per
 * frame by HudManager and the HUD editor. A failed bake returns {@code false} — callers fall back
 * to their SDF glyph.</p>
 */
public final class PixelIcons {
    private PixelIcons() {}

    /** The prototype's tritone: lift for highlights, unity for base, deep cut for shadow. */
    private static final float LIFT = 1.28f, SHADOW = 0.52f;

    private static DrawContext dc;
    private static final Map<Identifier, Identifier> baked = new HashMap<>();
    private static final Set<Identifier> failed = new HashSet<>();

    /** The current frame's DrawContext (HudManager / HudEditorScreen, right after Ui.beginFrame). */
    public static void set(DrawContext c) { dc = c; }

    /** Resource reload → drop every baked mask so pack-switched textures rebake lazily. */
    public static void reload() {
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : baked.values()) tm.destroyTexture(id);
        baked.clear(); failed.clear();
    }

    /**
     * Draws {@code src} (a square {@code srcSize}px texture) duotoned into {@code tint} with its
     * content box at (x, y)..(x+sizePx, y+sizePx). Returns false when the texture can't be baked
     * or no DrawContext is set this frame — the caller should draw its SDF fallback instead.
     */
    public static boolean draw(Identifier src, float x, float y, float sizePx, int srcSize, int tint, float alpha) {
        if (dc == null || alpha <= 0f || failed.contains(src)) return false;
        Identifier tex = baked.get(src);
        if (tex == null) {
            tex = bake(src);
            if (tex == null) { failed.add(src); return false; }
            baked.put(src, tex);
        }
        // clamp(tint·LIFT): the mask's white level is 1/LIFT, so highlight = tint·LIFT, base = tint,
        // shadow = tint·SHADOW — the exact tones of the approved board (same per-channel clamp).
        float r = Math.min(1f, ((tint >> 16) & 0xFF) / 255f * LIFT);
        float g = Math.min(1f, ((tint >> 8) & 0xFF) / 255f * LIFT);
        float b = Math.min(1f, (tint & 0xFF) / 255f * LIFT);
        RenderSystem.setShaderColor(r, g, b, alpha);
        var m = dc.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        float k = sizePx / srcSize;
        m.scale(k, k, 1f);
        dc.drawTexture(tex, 0, 0, 0f, 0f, srcSize, srcSize, srcSize, srcSize);
        m.pop();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        return true;
    }

    /** Bakes the source texture into its grayscale factor mask (NEAREST, alpha preserved). */
    private static Identifier bake(Identifier src) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            var res = mc.getResourceManager().getResource(src);
            if (res.isEmpty()) return null;
            NativeImage img;
            try (var in = res.get().getInputStream()) { img = NativeImage.read(in); }
            NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), true);
            for (int py = 0; py < img.getHeight(); py++) {
                for (int px = 0; px < img.getWidth(); px++) {
                    int abgr = img.getColor(px, py);
                    int a = (abgr >>> 24) & 0xFF;
                    if (a < 96) { out.setColor(px, py, 0); continue; }
                    int b = (abgr >> 16) & 0xFF, g = (abgr >> 8) & 0xFF, r = abgr & 0xFF;
                    float l = 0.299f * r + 0.587f * g + 0.114f * b;
                    float f = l > 168f ? LIFT : (l > 100f ? 1f : SHADOW);
                    int level = Math.round(255f * f / LIFT);
                    out.setColor(px, py, (a << 24) | (level << 16) | (level << 8) | level);
                }
            }
            img.close();
            NativeImageBackedTexture tex = new NativeImageBackedTexture(out);
            tex.setFilter(false, false);   // NEAREST both ways — crisp pixels at any HUD scale
            Identifier id = Identifier.of("club",
                    "pixel/" + src.getNamespace() + "/" + src.getPath().replace(".png", "").replace('/', '_'));
            mc.getTextureManager().registerTexture(id, tex);
            return id;
        } catch (Exception e) {
            return null;   // missing/broken texture → caller falls back to its SDF glyph
        }
    }
}
