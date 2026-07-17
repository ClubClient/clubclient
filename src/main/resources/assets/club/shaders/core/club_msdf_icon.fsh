#version 150

// GLSL 150 — for Minecraft versions whose own core shaders say 150 (measured: 1.21.5, 1.21.6, 1.21.8).
// 1.21.11 uses the identical twin club_msdf_icon330.fsh. Everything below the #version line is shared;
// see club:shaders/include/msdf.glsl for why the split exists and why dynamictransforms is imported
// rather than declared. com.club.compat.IconPipe picks between the two.
//
// No WEIGHT_BIAS is defined here on purpose: icons want the atlas's nominal weight, and the include
// defaults the knob to 0.0 for exactly this importer.
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <club:msdf.glsl>
