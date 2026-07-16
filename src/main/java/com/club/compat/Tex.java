package com.club.compat;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import java.util.function.Supplier;

/**
 * A {@link NativeImageBackedTexture} with the filtering we asked for, across Minecraft versions.
 *
 * <p><b>Why this class exists.</b> Two unrelated changes land on the same three call sites, and one of them
 * has no like-for-like replacement. 1.21.5 added a debug label ({@code Supplier<String>}) to the constructor.
 * Then 1.21.11 deleted {@code AbstractTexture.setFilter(boolean, boolean)} — along with {@code setClamp} and
 * {@code setUseMipmaps} — and replaced the whole idea: a texture now holds a {@code GpuSampler}, taken from
 * {@code RenderSystem.getSamplerCache()}, and the {@code sampler} field is {@code protected} with <b>no
 * setter</b>. There is no call to rename. The only way to give a texture a sampler is to BE the texture, so
 * the 1.21.11 branch below is a subclass whose constructor assigns the field — which is not a trick, it is
 * exactly what vanilla's own {@code GlyphAtlasTexture} does, verbatim, for exactly this reason.</p>
 *
 * <p><b>The filter translation is measured, not approximated.</b> {@code SamplerCache.getRepeated(mode)}
 * resolves to {@code get(REPEAT, REPEAT, mode, mode, false)} — both min and mag set to {@code mode}, no
 * mipmaps — which is precisely what the old {@code setFilter(bilinear, false)} meant. {@code getRepeated} and
 * not {@code get(mode)}: the single-argument {@code get} forces {@code CLAMP_TO_EDGE} addressing, whereas the
 * old {@code setFilter} never touched addressing at all (that was {@code setClamp}'s job, and no caller here
 * ever called it). {@code REPEAT} is also what 1.21.11's {@code AbstractTexture} constructor already defaults
 * to, so these textures keep the addressing they have always had and change only what they asked to change.</p>
 *
 * <p><b>The default is not good enough — this is not a no-op.</b> 1.21.11's default sampler is min=NEAREST,
 * mag=LINEAR. That matches NEITHER caller: {@code PixelIcons} needs NEAREST both ways or pixel art blurs the
 * moment the HUD scales it up, and {@code MsdfAtlas} needs LINEAR both ways or the SDF falls apart when
 * minified. Leaving the default in place would compile, run, and look wrong.</p>
 *
 * <p><b>Boundaries measured, not assumed</b> (Yarn mappings, all eleven versions 1.21.1..1.21.11, 2026-07-16):
 * {@code AbstractTexture.setFilter(ZZ)V} is present 1.21.1..1.21.10 and absent in 1.21.11 — one release LATER
 * than the input refactor at 1.21.9 and one later than the 4x4-matrix change at 1.21.6. Three boundaries,
 * three different releases, none of them where a reasonable person would have guessed.</p>
 */
public final class Tex {
    private Tex() {}

    /** A texture that stays crisp: NEAREST magnification and minification. For pixel art. */
    public static NativeImageBackedTexture nearest(Supplier<String> label, NativeImage img) {
        //? if <1.21.5 {
        NativeImageBackedTexture t = new NativeImageBackedTexture(img);
        t.setFilter(false, false);
        return t;
        //?} elif <1.21.11 {
        /*NativeImageBackedTexture t = new NativeImageBackedTexture(label, img);
        t.setFilter(false, false);
        return t;*/
        //?} else {
        /*return new Filtered(label, img, com.mojang.blaze3d.textures.FilterMode.NEAREST);*/
        //?}
    }

    /** A texture that interpolates: LINEAR magnification and minification, no mipmaps. Correct for SDF. */
    public static NativeImageBackedTexture linear(Supplier<String> label, NativeImage img) {
        //? if <1.21.5 {
        NativeImageBackedTexture t = new NativeImageBackedTexture(img);
        t.setFilter(true, false);
        return t;
        //?} elif <1.21.11 {
        /*NativeImageBackedTexture t = new NativeImageBackedTexture(label, img);
        t.setFilter(true, false);
        return t;*/
        //?} else {
        /*return new Filtered(label, img, com.mojang.blaze3d.textures.FilterMode.LINEAR);*/
        //?}
    }

    //? if >=1.21.11 {
    /*/^*
     * The only way to set a texture's sampler in 1.21.11: {@code AbstractTexture.sampler} is protected and has
     * no setter, and a protected field is reachable from a subclass in another package through {@code this}.
     * Vanilla's {@code GlyphAtlasTexture} is this same class for this same reason.
     *
     * {@code RenderSystem.getSamplerCache()} is safe to call here: {@code AbstractTexture}'s own constructor —
     * which runs via {@code super(...)} before this line — already calls it to install the default sampler, so
     * a cache that was not ready would have failed one frame earlier, on every texture the game makes.
     *^/
    private static final class Filtered extends NativeImageBackedTexture {
        Filtered(Supplier<String> label, NativeImage img, com.mojang.blaze3d.textures.FilterMode mode) {
            super(label, img);
            this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getRepeated(mode);
        }
    }*/
    //?}
}
