package com.club.hud;

/**
 * Pure duotone/quadtone bake math for {@link PixelIcons}, extracted (Stage 28) so the numbers that
 * decide tone banding, auto-levels percentiles, tight-bounds centering and the shader tint can be
 * unit-tested without a Minecraft / NativeImage / RenderSystem bootstrap. No MC types, no rendering.
 *
 * <p>History this guards: fixed absolute thresholds once turned dark art (netherite) into an
 * all-shadow field with random speckle — the percentile auto-levels + flat-sprite guard below are
 * the fix, and are exactly what these tests pin.</p>
 */
final class PixelMath {
    private PixelMath() {}

    /** QUADTONE (Stage 18.4): deep outline, shadow, base, light. The mask stores level·255/LIFT so a
     *  shader multiply by LIFT reproduces the exact tritone of the approved prototype. */
    static final float LIFT = 1.28f, SHADOW = 0.62f, OUTLINE = 0.36f;

    /** Luminance of an ABGR pixel (NativeImage layout: R = low byte, B = bits 16..23) → 0..255. */
    static int lum(int abgr) {
        int b = (abgr >> 16) & 0xFF, g = (abgr >> 8) & 0xFF, r = abgr & 0xFF;
        return Math.round(0.299f * r + 0.587f * g + 0.114f * b);
    }

    /** The luminance below which {@code p} of {@code total} opaque pixels fall (histogram scan). */
    static float percentile(int[] hist, int total, float p) {
        int target = Math.round(total * p), seen = 0;
        for (int i = 0; i < 256; i++) { seen += hist[i]; if (seen >= target) return i; }
        return 255f;
    }

    /** Near-uniform sprite (p10..p90 span &lt; 24) → single base tone, no fake contrast from noise. */
    static boolean flat(float lo, float hi) { return hi - lo < 24f; }

    /** Auto-levels: normalize a luminance into the sprite's own [lo,hi] window, clamped to [0,1]. */
    static float normLum(int lum, float lo, float hi) {
        return Math.max(0f, Math.min(1f, (lum - lo) / (hi - lo)));
    }

    /** Quadtone factor for a normalized luminance: outline / shadow / base / lift bands. */
    static float quadFactor(float ln) {
        return ln > 0.74f ? LIFT : (ln < 0.15f ? OUTLINE : (ln < 0.42f ? SHADOW : 1f));
    }

    /** Grayscale mask level for a tone factor (stored as level·255/LIFT so shader ×LIFT restores it). */
    static int maskLevel(float f) { return Math.round(255f * f / LIFT); }

    /** Tight-bounds center coordinate (texels) from the visible min/max index, inclusive. */
    static float tightCenter(int minVisible, int maxVisible) { return (minVisible + maxVisible + 1) * 0.5f; }

    /** Shader tint channel: a 0..255 byte lifted by LIFT and clamped to [0,1] (the mask's white = 1/LIFT). */
    static float shaderChannel(int channelByte) { return Math.min(1f, channelByte / 255f * LIFT); }
}
