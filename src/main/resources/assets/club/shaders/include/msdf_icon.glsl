// Club — MSDF icon coverage, shared by club:core/club_msdf_icon (GLSL 150) and
// club:core/club_msdf_icon330 (GLSL 330).
//
// WHY THIS FILE IS AN INCLUDE AND NOT A SHADER. A shader's #version must be a literal on its own first
// line, and Minecraft's own GLSL moved 150 -> 330 (measured: position_tex_color.fsh says 150 on 1.21.5,
// 1.21.6 and 1.21.8, and 330 on 1.21.11 — read out of each version's jar on 2026-07-17). That one line
// cannot be #defined, imported, or branched around, so it is the only thing the two .fsh files carry.
// The maths lives here and is written once: a fix to the coverage curve lands on both versions or on
// neither, which is the entire point.
//
// WHAT THE IMPORTER MUST PROVIDE, and in this order:
//   #moj_import <minecraft:dynamictransforms.glsl>  — supplies ColorModulator. Imported rather than
//       hand-declared because the block's std140 LAYOUT changes per version and a mismatched layout does
//       not warn, it links wrong: `float LineWidth` is ABSENT in 1.21.5, PRESENT in 1.21.6 and 1.21.8,
//       and ABSENT again in 1.21.11 (measured, same jars, same day). Importing vanilla's own copy means
//       this file never has to know which shape today's version has.
//   PX_RANGE — RenderPipeline.Builder.withShaderDefine, fed from the atlas's own `distanceRange` so the
//       constant has ONE home (assets/club/ui/icon/msdf/icons.json) instead of two that can drift.
//
// Sampler0 must be LINEAR-filtered both ways or the field falls apart under minification — that is
// com.club.compat.Tex.linear's job, not this shader's.

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

    float fillA = clamp(screenPxRange * (sd - 0.5) + 0.5, 0.0, 1.0);

    // The tint rides the vertex colour (drawTexture's `tint` argument); ColorModulator is vanilla's own
    // GUI-wide factor and is applied for the same reason GUI_TEXTURED applies it — so this pipeline
    // differs from vanilla's in the coverage maths and in nothing else.
    vec4 col = vertexColor * ColorModulator;
    vec4 outc = vec4(col.rgb, col.a * fillA);
    if (outc.a <= 0.0) discard;
    fragColor = outc;
}
