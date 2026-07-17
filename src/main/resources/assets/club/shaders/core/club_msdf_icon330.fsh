#version 330

// GLSL 330 — for Minecraft versions whose own core shaders say 330 (measured: 1.21.11). Versions from
// 1.21.5 to 1.21.8 use the identical twin club_msdf_icon.fsh. The #version line is the ONLY difference
// between the two files: it must be a literal first line, so it is the one thing that cannot be shared.
// See club:shaders/include/msdf.glsl. com.club.compat.IconPipe picks between the two.
//
// No WEIGHT_BIAS is defined here on purpose: icons want the atlas's nominal weight, and the include
// defaults the knob to 0.0 for exactly this importer.
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <club:msdf.glsl>
