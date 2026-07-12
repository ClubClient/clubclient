#version 150

// Batched sibling of ui_sdf_shape. Same SDF maths — but every per-shape parameter that used to be a
// UNIFORM now rides the VERTEX, so hundreds of rounded rects can go to the GPU in ONE draw call
// instead of one call each (the old path cost ~23 us per shape in submission overhead alone).
//
// Carriers (all four vertices of a quad carry identical values, so the interpolated result is exact):
//   UV0 — local position relative to the shape's centre, in pixels (as before)
//   UV1 — half size (x, y), in 1/32 px  (signed short: up to ~1023 px)
//   UV2 — corner radius, border thickness, in 1/64 px  (thickness 0 = FILL, > 0 = BORDER)
// Gradients, glow/shadow and per-corner radii do NOT fit here and keep using ui_sdf_shape.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2  localPos;
out vec4  vertexColor;
out vec2  halfSize;
out float radius;
out float thickness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPos    = UV0;
    vertexColor = Color;
    halfSize    = vec2(UV1) / 32.0;   // SIZE_SCALE
    radius      = float(UV2.x) / 64.0;   // EDGE_SCALE
    thickness   = float(UV2.y) / 64.0;   // EDGE_SCALE
}
