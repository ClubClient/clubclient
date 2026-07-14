#version 150
// Duotone pixel icons, batched (Stage 67).
//
// Every icon used to be its own GL draw: DrawContext.drawTexturedQuad does Tessellator.begin -> 4 vertices
// -> BufferBuilder.end -> drawWithGlobalProgram, immediately, per sprite. Four sprites a frame at ~43 us
// each — 35% of everything the Club HUD costs. They now ride one buffer and one draw.
//
// THE TINT MUST NOT LOSE PRECISION. The old path put the LIFTED tint in a float uniform
// (min(1, c/255 * LIFT)) — batching means the tint has to travel per-vertex, and an 8-bit vertex colour
// cannot hold that float. So the vertex carries the RAW tint byte instead (c/255 is exact in 8 bits), and
// the lift + clamp move HERE, into the same two float operations the CPU used to do. The colour is the
// same number, not a rounded one.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Lift;            // PixelMath.LIFT — the mask stores level*255/LIFT, so *LIFT restores the tone
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 mask = texture(Sampler0, texCoord0);          // grayscale factor mask + source alpha
    vec3 tint = min(vec3(1.0), vertexColor.rgb * Lift);
    vec4 outc = vec4(mask.rgb * tint, mask.a * vertexColor.a) * ColorModulator;
    if (outc.a <= 0.0) discard;
    fragColor = outc;
}
