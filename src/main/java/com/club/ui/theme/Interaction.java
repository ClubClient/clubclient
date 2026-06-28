package com.club.ui.theme;

/**
 * Interaction state values not covered by other token groups (§3.9). Deliberately minimal (G2): colors
 * reference Palette/Accent (composed in the theme file), not new hex; only the genuinely-missing scalars live here.
 */
public record Interaction(
    int   hoverWash,       // hover overlay color  (palette.white + low alpha)
    int   pressOverlay,    // press overlay color  (palette.ink0 + low alpha)
    float disabledAlpha,   // opacity multiplier for disabled components (ctx.renderer().pushOpacity)
    int   focusRing,       // focus-ring color (= palette.accent — reference, not a new color)
    float focusRingWidth   // focus-ring thickness in px
) {}
