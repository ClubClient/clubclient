// Club — UI shape vertex stage, shared by club:core/club_ui_shape (GLSL 150) and
// club:core/club_ui_shape330 (GLSL 330).
//
// WHY THIS FILE IS AN INCLUDE AND NOT A SHADER. A shader's #version must be a literal on its own first
// line, and Minecraft's own GLSL moved 150 -> 330 (measured: position_tex_color.vsh says 150 on 1.21.8
// and 330 on 1.21.11 — read out of each jar on 2026-07-17). That line cannot be #defined or imported
// around, so it is the only thing the two .vsh files carry. Same split, same reason, as
// club:msdf.glsl.
//
// WHAT THE IMPORTER MUST PROVIDE, and in this order:
//   #moj_import <minecraft:dynamictransforms.glsl>  — ModelViewMat. Imported rather than hand-declared
//       because the std140 block's LAYOUT changes per version and a mismatched layout does not warn, it
//       links wrong: `float LineWidth` is PRESENT on 1.21.8 and ABSENT on 1.21.11 (measured, both jars,
//       2026-07-17).
//   #moj_import <minecraft:projection.glsl>         — ProjMat, for the same reason.
//
// WHY EVERY SHAPE PARAMETER RIDES THE VERTEX. From 1.21.5 the GUI does not draw, it RECORDS: a shape is
// queued as a GuiElementRenderState and vanilla's GuiRenderer decides later when to flush it. A uniform
// set at record time would be long overwritten by the time the draw happens, and eight scalar uniforms
// are not expressible anyway — UniformType offers only UNIFORM_BUFFER/TEXEL_BUFFER (measured, both jars).
// So the geometry travels where it cannot go stale: on the vertices themselves. All four vertices of a
// quad carry identical values, so the interpolated result is exact.
//
// CARRIERS (see com.club.compat.ShapePipe, which is the only writer):
//   UV0 — local position relative to the shape's centre, in Club px  (float2)
//   UV1 — half size (x, y), in 1/32 px                               (short2, so <= ~1023 px)
//   UV2 — corner radius, thickness, in 1/64 px                       (short2, so <= ~511 px)
//   Color — per-vertex, which is what makes a gradient free: the old ui_sdf_shape mixed vertexColor
//       toward a ColorB UNIFORM by a t it recomputed from localPos; hardware interpolation of two
//       vertex colours across the quad is the same linear ramp with nothing to upload.
//
// UV2.y IS A SIGNED MODE TAG, not just a magnitude. 0 = fill, > 0 = border of that thickness,
// < 0 = glow whose feather is its absolute value. A shape is never both a border and a glow, so one
// signed carrier says which of the three it is and how much — and that is what let the whole shape
// vocabulary fit in the vanilla element set (VertexConsumer can only write POSITION/COLOR/UV0/UV1/UV2;
// a custom element has no writer and BufferBuilder would reject the vertex as incomplete).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec2  localPos;
out vec4  vertexColor;
out vec2  halfSize;
out float radius;
out float thickness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    localPos    = UV0;
    vertexColor = Color;
    halfSize    = vec2(UV1) / 32.0;        // SIZE_SCALE — ShapePipe.SIZE_SCALE
    radius      = float(UV2.x) / 64.0;     // EDGE_SCALE — ShapePipe.EDGE_SCALE
    thickness   = float(UV2.y) / 64.0;     // EDGE_SCALE, signed: see the mode-tag note above
}
