// Club — MSDF coverage, shared by all four of Club's MSDF shaders:
//   club:core/club_msdf_icon  (GLSL 150) / club:core/club_msdf_icon330  (GLSL 330) — com.club.compat.IconPipe
//   club:core/club_msdf_text  (GLSL 150) / club:core/club_msdf_text330  (GLSL 330) — com.club.compat.TextPipe
//
// An icon and a glyph are the SAME maths over different atlases — that is why this file is shared rather
// than copied, and why it is called msdf.glsl and not msdf_icon.glsl. The text path adds exactly one knob
// (WEIGHT_BIAS) and nothing else.
//
// WHY THIS FILE IS AN INCLUDE AND NOT A SHADER. A shader's #version must be a literal on its own first
// line, and Minecraft's own GLSL moved 150 -> 330 (measured: position_tex_color.fsh says 150 on 1.21.5,
// 1.21.6 and 1.21.8, and 330 on 1.21.11 — read out of each version's jar on 2026-07-17). That one line
// cannot be #defined, imported, or branched around, so it is the only thing the four .fsh files carry.
// The maths lives here and is written once: a fix to the coverage curve lands on every version and on
// both icons and text, or on none of them, which is the entire point.
//
// WHAT THE IMPORTER MUST PROVIDE, and in this order:
//   #moj_import <minecraft:dynamictransforms.glsl>  — supplies ColorModulator. Imported rather than
//       hand-declared because the block's std140 LAYOUT changes per version and a mismatched layout does
//       not warn, it links wrong: `float LineWidth` is ABSENT in 1.21.5, PRESENT in 1.21.6 and 1.21.8,
//       and ABSENT again in 1.21.11 (measured, same jars, same day). Importing vanilla's own copy means
//       this file never has to know which shape today's version has.
//   PX_RANGE — RenderPipeline.Builder.withShaderDefine, fed from the atlas's own `distanceRange` so the
//       constant has ONE home (the atlas JSON) instead of two that can drift. The two atlases genuinely
//       disagree — measured 2026-07-17: 6 in ui/font/msdf/inter_*.json, 8 in ui/icon/msdf/icons.json —
//       so this is a per-atlas number, never a global one.
//
// WHAT THE IMPORTER MAY PROVIDE:
//   WEIGHT_BIAS — optical weight, in coverage units: >0 renders the glyph a touch thinner, <0 heavier,
//       0 = the atlas's nominal weight. Defaulted below, so an importer that says nothing (the icon
//       shaders) gets `- 0.0` and is constant-folded back to exactly the curve it had before this knob
//       existed. A DEFINE and not a uniform because the only thing DrawContext.drawTexture carries per
//       draw is the tint — see com.club.compat.TextPipe for why that is the whole API here, and for the
//       pipeline-per-value cache and its bound.
//
// Sampler0 must be LINEAR-filtered both ways or the field falls apart under minification — that is
// com.club.compat.Tex.linear's job, not this shader's.

// Defines are injected by GlImportProcessor.addDefines, which splits the source at its FIRST newline and
// inserts them there — i.e. directly after the #version line, and therefore ALWAYS above this guard
// (bytecode, 1.21.8, 2026-07-17). So an importer's -DWEIGHT_BIAS wins and this default only fires when
// nobody set one.
#ifndef WEIGHT_BIAS
#define WEIGHT_BIAS 0.0
#endif

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float club_median(float r, float g, float b) { return max(min(r, g), min(max(r, g), b)); }

void main() {
    // Median of the three channels = the true signed distance; that is what makes it MULTI-channel and
    // is why corners stay sharp where a plain SDF rounds them off.
    vec3 msd = texture(Sampler0, texCoord0).rgb;
    float sd = club_median(msd.r, msd.g, msd.b);

    // Screen-space width of one distance unit. fwidth() asks the GPU how fast the UV is moving across
    // this pixel, so the glyph anti-aliases correctly at ANY scale without being told the scale.
    // The floor of 1.0 keeps a heavily-minified icon from thinning into nothing.
    vec2 unitRange = vec2(PX_RANGE) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    // WEIGHT_BIAS shifts the 50%-coverage contour: subtracting it moves the edge inward (thinner). It is
    // applied here, to the coverage, and NOWHERE else — a glyph's advance, plane box and cell are all
    // untouched by it, which is what lets com.club.compat.TextPipe fall back to a nominal-weight pipeline
    // without moving a single glyph.
    float fillA = clamp(screenPxRange * (sd - 0.5) + 0.5 - WEIGHT_BIAS, 0.0, 1.0);

    // The tint rides the vertex colour (drawTexture's `tint` argument); ColorModulator is vanilla's own
    // GUI-wide factor and is applied for the same reason GUI_TEXTURED applies it — so this pipeline
    // differs from vanilla's in the coverage maths and in nothing else.
    vec4 col = vertexColor * ColorModulator;
    vec4 outc = vec4(col.rgb, col.a * fillA);
    if (outc.a <= 0.0) discard;
    fragColor = outc;
}
