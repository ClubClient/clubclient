#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float PxRange;
uniform float OutlineWidth;   // px, 0 = none
uniform vec4 OutlineColor;
uniform float GlowRange;       // px, 0 = none
uniform vec4 GlowColor;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

float median(float r, float g, float b) { return max(min(r, g), min(max(r, g), b)); }
void main() {
    vec3 msd = texture(Sampler0, texCoord0).rgb;
    float sd = median(msd.r, msd.g, msd.b);
    vec2 unitRange = vec2(PxRange) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    float spr = max(0.5 * dot(unitRange, screenTexSize), 1.0);
    float d = spr * (sd - 0.5);

    float fillA = clamp(d + 0.5, 0.0, 1.0);
    vec4 col = vertexColor * ColorModulator;
    vec4 outc = vec4(col.rgb, col.a * fillA);

    if (OutlineWidth > 0.0) {
        float oa = clamp(d + 0.5 + OutlineWidth, 0.0, 1.0);
        vec4 o = vec4(OutlineColor.rgb, OutlineColor.a * oa);
        outc = mix(o, outc, fillA);     // fill over outline
    }
    if (GlowRange > 0.0) {
        float ga = clamp((d + GlowRange) / GlowRange, 0.0, 1.0);
        ga = ga * ga;
        vec4 g = vec4(GlowColor.rgb, GlowColor.a * ga);
        outc = mix(g, outc, outc.a);    // content over glow
    }
    if (outc.a <= 0.0) discard;
    fragColor = outc;
}
