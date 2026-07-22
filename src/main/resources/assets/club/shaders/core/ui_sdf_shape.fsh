#version 150
uniform vec4 ColorModulator;
uniform vec2 HalfSize;
uniform vec4 CornerRadii;   // tl, tr, br, bl
uniform int Mode;           // 0 fill, 1 border, 2 glow, 3 gradient
uniform int GradAxis;       // 0 horizontal, 1 vertical
uniform float Feather;
uniform float Thickness;
uniform vec4 ColorB;
in vec2 localPos;
in vec4 vertexColor;
out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}
void main() {
    float r = localPos.x < 0.0
        ? (localPos.y < 0.0 ? CornerRadii.x : CornerRadii.w)
        : (localPos.y < 0.0 ? CornerRadii.y : CornerRadii.z);
    r = min(r, min(HalfSize.x, HalfSize.y));
    float d = sdRoundBox(localPos, HalfSize, r);
    // aa = how far d moves across ONE pixel, so the coverage ramp must span exactly aa — from -aa/2 to
    // +aa/2 around the edge. smoothstep(-aa, aa, d) spans 2*aa and blurred every edge over TWO pixels;
    // clamp(0.5 - d/aa) is the correct 1-pixel box filter. Same change in ui_sdf_batch.fsh and
    // club:club_shape_frag.glsl — they must agree or the versions diverge.
    float aa = max(fwidth(d), 1e-4);
    vec4 col; float cov;
    if (Mode == 1) {
        float outer = clamp(0.5 - d / aa, 0.0, 1.0);
        float inner = clamp(0.5 - (d + Thickness) / aa, 0.0, 1.0);
        cov = clamp(outer - inner, 0.0, 1.0); col = vertexColor;
    } else if (Mode == 2) {
        float g = 1.0 - clamp(max(d, 0.0) / Feather, 0.0, 1.0);
        cov = g * g * (3.0 - 2.0 * g); col = vertexColor;
    } else if (Mode == 3) {
        float t = (GradAxis == 0)
            ? (localPos.x + HalfSize.x) / (2.0 * HalfSize.x)
            : (localPos.y + HalfSize.y) / (2.0 * HalfSize.y);
        col = mix(vertexColor, ColorB, clamp(t, 0.0, 1.0));
        cov = clamp(0.5 - d / aa, 0.0, 1.0);
    } else {
        col = vertexColor; cov = clamp(0.5 - d / aa, 0.0, 1.0);
    }
    vec4 o = col * ColorModulator;
    o.a *= cov;
    o.rgb += (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
    if (o.a <= 0.0) discard;
    fragColor = o;
}
