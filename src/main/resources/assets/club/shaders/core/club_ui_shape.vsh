#version 150

// GLSL 150 — for Minecraft versions whose own core shaders say 150 (measured from each jar's
// position_tex_color.vsh on 2026-07-17: 150 on 1.21.5/1.21.6/1.21.8, 330 on 1.21.11). The identical
// twin is club:core/club_ui_shape330.vsh. The #version line is the ONLY difference between the two files: it must be a
// literal first line, so it is the one thing that cannot be shared. 1.21.9 and 1.21.10 are UNMEASURED
// (no jar here) — anyone adding a node between them owes this line a look inside that jar first.
// com.club.compat.ShapePipe picks between the two; the vertex maths lives in the include.
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <club:club_shape_vert.glsl>
