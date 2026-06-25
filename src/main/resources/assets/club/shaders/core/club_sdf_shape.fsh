#version 150

uniform vec4 ColorModulator;
uniform vec2 HalfSize;   // half width/height of the rounded box, pixels
uniform float Radius;    // corner radius, pixels
uniform float Softness;  // AA softness multiplier (1.0 = crisp)
uniform int Mode;        // 0 fill, 1 border, 2 glow, 3 gradient fill
uniform int GradAxis;    // 0 horizontal, 1 vertical (gradient mode)
uniform float Feather;   // glow falloff distance, pixels (glow mode)
uniform float Thickness; // border thickness, pixels (border mode)
uniform vec4 ColorB;     // gradient colour B (gradient mode)

in vec2 localPos;
in vec4 vertexColor;

out vec4 fragColor;

// Signed distance to a rounded box (Inigo Quilez).
float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    float r = min(Radius, min(HalfSize.x, HalfSize.y));
    float d = sdRoundBox(localPos, HalfSize, r);
    float aa = max(fwidth(d), 1e-4) * Softness;

    vec4 col;
    float cov;

    if (Mode == 1) {
        // 1px-ish anti-aliased border ring hugging the edge
        float outer = 1.0 - smoothstep(-aa, aa, d);
        float inner = 1.0 - smoothstep(-aa, aa, d + Thickness);
        cov = clamp(outer - inner, 0.0, 1.0);
        col = vertexColor;
    } else if (Mode == 2) {
        // continuous outward glow — no quad stamps, true falloff
        float g = 1.0 - clamp(max(d, 0.0) / Feather, 0.0, 1.0);
        g = g * g * (3.0 - 2.0 * g);   // smoothstep shoulder
        cov = g;
        col = vertexColor;
    } else if (Mode == 3) {
        float t = (GradAxis == 0)
            ? (localPos.x + HalfSize.x) / (2.0 * HalfSize.x)
            : (localPos.y + HalfSize.y) / (2.0 * HalfSize.y);
        t = clamp(t, 0.0, 1.0);
        col = mix(vertexColor, ColorB, t);
        cov = 1.0 - smoothstep(-aa, aa, d);
    } else {
        col = vertexColor;
        cov = 1.0 - smoothstep(-aa, aa, d);
    }

    vec4 outc = col * ColorModulator;
    outc.a *= cov;
    // tiny ordered dither to kill gradient / alpha banding
    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    outc.rgb += dither;
    if (outc.a <= 0.0) discard;
    fragColor = outc;
}
