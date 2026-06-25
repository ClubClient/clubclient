#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float PxRange;   // distance range the atlas was generated with (px)

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

void main() {
    vec3 msd = texture(Sampler0, texCoord0).rgb;
    float sd = median(msd.r, msd.g, msd.b);

    // resolution-independent screen-space AA (Chlumsky) — crisp at any zoom
    vec2 unitRange = vec2(PxRange) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    float d = screenPxRange * (sd - 0.5);
    float alpha = clamp(d + 0.5, 0.0, 1.0);
    if (alpha <= 0.0) discard;

    vec4 col = vertexColor * ColorModulator;
    fragColor = vec4(col.rgb, col.a * alpha);
}
