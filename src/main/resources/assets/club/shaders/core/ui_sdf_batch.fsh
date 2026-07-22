#version 150

// Fill / border rounded box, with every parameter arriving per-vertex (see ui_sdf_batch.vsh).
// The maths is IDENTICAL to ui_sdf_shape's fill and border branches — including the dither, so a
// batched shape is pixel-for-pixel the same as the one-draw-per-shape path it replaces.

uniform vec4 ColorModulator;

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
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = sdRoundBox(localPos, halfSize, r);
    // One-pixel box filter: aa is d's travel across a single pixel, so the ramp runs -aa/2 .. +aa/2.
    // The old smoothstep(-aa, aa, d) spanned TWO pixels. Identical to ui_sdf_shape.fsh by construction.
    float aa = max(fwidth(d), 1e-4);

    float cov;
    if (thickness > 0.0) {                       // BORDER
        float outer = clamp(0.5 - d / aa, 0.0, 1.0);
        float inner = clamp(0.5 - (d + thickness) / aa, 0.0, 1.0);
        cov = clamp(outer - inner, 0.0, 1.0);
    } else {                                     // FILL
        cov = clamp(0.5 - d / aa, 0.0, 1.0);
    }

    vec4 o = vertexColor * ColorModulator;
    o.a *= cov;
    o.rgb += (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    if (o.a <= 0.0) discard;
    fragColor = o;
}
