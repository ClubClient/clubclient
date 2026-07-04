package com.club.ui.theme;

/** Brand accent tokens. {@code accentQuiet} is the muted brand for dark utility overlays
 *  (HUD editor chrome): full-strength brand fills scream on a near-black working surface
 *  (Stage 18.3, owner), so utility toggles/pills take this desaturated step instead. */
public record Accent(int accent, int accentHi, int gradientA, int gradientB, int onAccent, int accentQuiet) {}
