package com.club.ui.theme;

/** Ink-ramp surfaces + the Stage-22 composition wells: {@code well} = the deep content well
 *  (Δ≈3 tone steps below {@code surface}), {@code wellShallow} = the barely-recessed category
 *  tray (Δ≈1) — zones separate by panel edges and depth, not hairlines (owner-approved board). */
public record Surface(int bg0, int bg1, int bg2, int surface, int surfaceHi, int well, int wellShallow) {}
