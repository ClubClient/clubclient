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

    // Tone/level/percentile math lives in the pure {@link PixelMath} (unit-tested, Stage 28).

    /** A baked mask + the art's tight-bounds center (texels) — vanilla sprites pad unevenly, so
     *  icons must center on their VISIBLE art, not the texture square (owner: «цифры не встают»). */
    private record Baked(Identifier tex, float cx, float cy) {}

    private static DrawContext dc;
    private static final Map<Identifier, Baked> baked = new HashMap<>();
    private static final Set<Identifier> failed = new HashSet<>();

    /** Icons drawn since the last reset. Each one is a REAL GL draw the V2 backend never sees: it goes out
     *  through vanilla's immediate path, and every icon carries its own texture, so every icon forces its
     *  own RenderLayer and its own flush. The profiler counted only the backend's batches and therefore
     *  under-reported the HUD's draw calls by exactly this many (Stage 64). Reset per frame by HudManager. */
    public static int DRAWS;

    /** The current frame's DrawContext (HudManager / HudEditorScreen, right after Ui.beginFrame). */
    public static void set(DrawContext c) { dc = c; }

    /** Resource reload → drop every baked mask so pack-switched textures rebake lazily. */
    public static void reload() {
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (Baked b : baked.values()) tm.destroyTexture(b.tex());
        baked.clear(); failed.clear();
    }

    /**
     * Draws {@code src} (a square {@code srcSize}px texture) duotoned into {@code tint} with its
     * content box at (x, y)..(x+sizePx, y+sizePx). Returns false when the texture can't be baked
     * or no DrawContext is set this frame — the caller should draw its SDF fallback instead.
     */
    public static boolean draw(Identifier src, float x, float y, float sizePx, int srcSize, int tint, float alpha) {
        boolean prof = com.club.modules.perf.HudProfiler.armed();
        long t0 = prof ? System.nanoTime() : 0L;
        try {
            return draw0(src, x, y, sizePx, srcSize, tint, alpha);
        } finally {
            if (prof) com.club.modules.perf.HudProfiler.addIconNs(System.nanoTime() - t0);
        }
    }

    private static boolean draw0(Identifier src, float x, float y, float sizePx, int srcSize, int tint, float alpha) {
        if (dc == null || alpha <= 0f || failed.contains(src)) return false;
        Baked bk = baked.get(src);
        if (bk == null) {
            bk = bake(src);
            if (bk == null) { failed.add(src); return false; }
            baked.put(src, bk);
        }
        // clamp(tint·LIFT): the mask's white level is 1/LIFT, so highlight = tint·LIFT, base = tint,
        // shadow/outline scale down — the exact tones of the approved board (same per-channel clamp).
        float r = PixelMath.shaderChannel((tint >> 16) & 0xFF);
        float g = PixelMath.shaderChannel((tint >> 8) & 0xFF);
        float b = PixelMath.shaderChannel(tint & 0xFF);
        RenderSystem.setShaderColor(r, g, b, alpha);
        var m = dc.getMatrices();
        m.push();
        float k = sizePx / srcSize;
        // land the ART's center on the box center — sprites pad their square unevenly
        m.translate(x + (srcSize * 0.5f - bk.cx()) * k, y + (srcSize * 0.5f - bk.cy()) * k, 0f);
        m.scale(k, k, 1f);
        dc.drawTexture(bk.tex(), 0, 0, 0f, 0f, srcSize, srcSize, srcSize, srcSize);
        m.pop();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        DRAWS++;
        return true;
    }

    /** Bakes the source texture into its grayscale factor mask (NEAREST, alpha preserved). */
    private static Baked bake(Identifier src) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            var res = mc.getResourceManager().getResource(src);
            if (res.isEmpty()) return null;
            NativeImage img;
            try (var in = res.get().getInputStream()) { img = NativeImage.read(in); }
            // Pass 1 — auto-levels: the tritone must split THIS texture's own tonal range. Fixed
            // absolute thresholds turned dark art (netherite!) into an all-shadow field with random
            // speckle ("баганные" icons); percentile normalization keeps every sprite's structure.
            int w = img.getWidth(), hgt = img.getHeight();
            int[] hist = new int[256];
            int opaque = 0;
            int minX = w, minY = hgt, maxX = -1, maxY = -1;   // the art's tight bounds
            for (int py = 0; py < hgt; py++)
                for (int px = 0; px < w; px++) {
                    int abgr = img.getColor(px, py);
                    if (((abgr >>> 24) & 0xFF) < 96) continue;
                    hist[PixelMath.lum(abgr)]++; opaque++;
                    if (px < minX) minX = px; if (px > maxX) maxX = px;
                    if (py < minY) minY = py; if (py > maxY) maxY = py;
                }
            if (opaque == 0) { img.close(); return null; }
            float lo = PixelMath.percentile(hist, opaque, 0.10f), hi = PixelMath.percentile(hist, opaque, 0.90f);
            boolean flat = PixelMath.flat(lo, hi);   // near-uniform sprite → single base tone, no fake contrast

            NativeImage out = new NativeImage(w, hgt, true);
            for (int py = 0; py < hgt; py++) {
                for (int px = 0; px < w; px++) {
                    int abgr = img.getColor(px, py);
                    int a = (abgr >>> 24) & 0xFF;
                    if (a < 96) { out.setColor(px, py, 0); continue; }
                    // quadtone: the darkest band becomes crisp OUTLINE linework (flat sprite → base only)
                    float f = flat ? 1f : PixelMath.quadFactor(PixelMath.normLum(PixelMath.lum(abgr), lo, hi));
                    int level = PixelMath.maskLevel(f);
                    out.setColor(px, py, (a << 24) | (level << 16) | (level << 8) | level);
                }
            }
            img.close();
            NativeImageBackedTexture tex = new NativeImageBackedTexture(out);
            tex.setFilter(false, false);   // NEAREST both ways — crisp pixels at any HUD scale
            Identifier id = Identifier.of("club",
                    "pixel/" + src.getNamespace() + "/" + src.getPath().replace(".png", "").replace('/', '_'));
            mc.getTextureManager().registerTexture(id, tex);
            return new Baked(id, PixelMath.tightCenter(minX, maxX), PixelMath.tightCenter(minY, maxY));
        } catch (Exception e) {
            return null;   // missing/broken texture → caller falls back to its SDF glyph
        }
    }
}
