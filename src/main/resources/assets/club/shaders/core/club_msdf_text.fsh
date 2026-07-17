#version 150

// GLSL 150 — for Minecraft versions whose own core shaders say 150 (measured: 1.21.5, 1.21.6, 1.21.8).
// 1.21.11 uses the identical twin club_msdf_text330.fsh. Everything below the #version line is shared;
// see club:shaders/include/msdf.glsl for why the split exists and why dynamictransforms is imported
// rather than declared. com.club.compat.TextPipe picks between the two.
//
// This file is byte-for-byte its icon counterpart apart from these comments, and that is not an
// oversight worth collapsing: com.club.compat.IconPipe names club:core/club_msdf_icon and is frozen, so
// the text pipeline needs a name of its own. Sharing one .fsh between them would mean an edit made for
// icons silently changed the font, and vice versa — the coupling this include already exists to avoid.
// WEIGHT_BIAS is supplied per pipeline by TextPipe; the include defaults it when it is absent.
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <club:msdf.glsl>
