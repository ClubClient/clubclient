// Club — UI shape coverage, shared by club:core/club_ui_shape (GLSL 150) and
// club:core/club_ui_shape330 (GLSL 330). See club:club_shape_vert.glsl for why the #version split
// exists and what the vertex carriers mean.
//
// WHAT THE IMPORTER MUST PROVIDE:
//   #moj_import <minecraft:dynamictransforms.glsl>  — ColorModulator (vanilla's GUI-wide factor).
//
// THE MATHS IS ui_sdf_shape.fsh's, UNCHANGED. That shader is what 1.21.1 has shipped to players since
// v0.1, and the whole point of this port is "один в один" — so the SDF, the smoothstep coverage, the
// glow falloff curve and the dither are copied rather than re-derived. What changed is only WHERE the
// parameters come from: uniforms there, vertices here (they cannot be uniforms any more — see the
// vertex include). A difference in this file would be a difference the player can see between versions.

in vec2  localPos;
in vec4  vertexColor;
in vec2  halfSize;
in float radius;
in float thickness;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    // A radius bigger than the shape's own half-extent is not a rounder rect, it is an inverted SDF —
    // clamping is what makes circle() (radius == half-size) land as a circle instead of a bowtie.
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = sdRoundBox(localPos, halfSize, r);

    // fwidth() asks the GPU how fast d moves across this pixel, so the edge anti-aliases correctly at
    // any scale and under any matrix — including the rotation line() draws diagonals with — without the
    // shader ever being told the scale. This is why the backend is resolution-independent.
    float aa = max(fwidth(d), 1e-4);

    float cov;
    if (thickness > 0.0) {                       // BORDER — the ring between d and d + thickness
        float outer = 1.0 - smoothstep(-aa, aa, d);
        float inner = 1.0 - smoothstep(-aa, aa, d + thickness);
        cov = clamp(outer - inner, 0.0, 1.0);
    } else if (thickness < 0.0) {                // GLOW / SHADOW — feather = -thickness
        float g = 1.0 - clamp(max(d, 0.0) / (-thickness), 0.0, 1.0);
        cov = g * g * (3.0 - 2.0 * g);           // smoothstep by hand: g is already the 0..1 ramp
    } else {                                     // FILL
        cov = 1.0 - smoothstep(-aa, aa, d);
    }

    vec4 o = vertexColor * ColorModulator;
    o.a *= cov;
    // Ordered-ish dither, +/- half a colour step. Club's panels are large areas of nearly-equal dark
    // greys, and 8-bit output bands them into visible steps; this breaks the band up below the eye's
    // threshold. Same line as ui_sdf_shape.fsh — drop it and 1.21.8 bands where 1.21.1 does not.
    o.rgb += (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    if (o.a <= 0.0) discard;
    fragColor = o;
}
