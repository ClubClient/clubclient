package com.club.ui.theme;

import com.club.ui.theme.themes.ClubDark;

/** Active-theme facade. The single access point for all design tokens. */
public final class Tokens {
    private Tokens() {}
    private static Theme active = ClubDark.create();
    public static void setTheme(Theme t) { active = t; }
    public static Theme theme()        { return active; }
    public static Palette    palette()  { return active.palette(); }
    public static Radius     radius()   { return active.radius(); }
    public static Spacing    spacing()  { return active.spacing(); }
    public static Typography type()     { return active.type(); }
    public static Surface    surface()  { return active.surface(); }
    public static Accent     accent()   { return active.accent(); }
    public static Border     border()   { return active.border(); }
    public static Shadow     shadow()   { return active.shadow(); }
    public static Glow       glow()     { return active.glow(); }
    public static Elevation  elevation(){ return active.elevation(); }
    public static Motion     motion()   { return active.motion(); }
}
