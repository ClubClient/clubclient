package com.club.ui.theme.themes;

import com.club.ui.Color;
import com.club.ui.motion.Curves;
import com.club.ui.text.Weight;
import com.club.ui.theme.*;

public final class ClubDark {
    private ClubDark() {}

    public static Theme create() {
        Palette p = new Palette(
            0xFF06090F, 0xFF090E16, 0xFF0B111A, 0xFF0C1320, 0xFF0F1624, 0xFF131B2A, 0xFF18212F, 0xFF1D2536, 0xFF222A38,
            0xFF7CABFF, 0xFF78D7FF,
            0xFFF4F6FA, 0xFFA6ADBB, 0xFF767E8E, 0xFF5A6273,
            0xFF2ECC71, 0xFFE3C66A, 0xFFE06B6B,
            0xFFFFFFFF);

        // THE LADDER (owner, v0.1.3 item 4: "подложка вроде есть, а вроде нету — выглядит как однотонная
        // заливка"). He was not being fussy; the ladder was BROKEN, and the arithmetic says so:
        //
        //   window   surface     #131B2A
        //   rail     wellShallow #131A28   <- Δ 0 / 1 / 2 from the window. Not "low contrast". IDENTICAL.
        //   well     well        #111927   <- Δ 2 / 2 / 3 from the window
        //   card     surfaceHi   #18212F
        //
        // Two of the four surfaces were the same colour, and a third was three units away. The separation
        // existed in the code and did not exist for the eye. Rebuilt as a monotone ladder, every step ≥ 4
        // units on every channel, using tones that ALREADY live in the ink ramp — nothing invented:
        //
        //   window   #131B2A  ink5   the frame
        //   rail     #0F1624  ink4   a tray recessed into it          (Δ 4 / 7 / 6)
        //   well     #0B111A  ink2   the deepest recess               (Δ 4 / 5 / 10)
        //   card     #18212F  ink6   floats above the well            (Δ 13 / 16 / 21)
        //
        // The well stops at ink2, not ink1/ink0: the owner rejected a deeper one as "жестокая линия чёрного
        // квадрата", and he was right — a near-black rectangle inside a dark panel reads as a hole, not as
        // depth. Depth is a LADDER, not a pit. Cards also take a 1px hairline (see ClubMenuScreen), because
        // tone alone drowns on a cheap panel and the hairline is what survives there.
        Surface surface = new Surface(p.ink0(), p.ink2(), p.ink4(), p.ink5(), p.ink6(), 0xFF0B111A, 0xFF0F1624);
        Accent accent   = new Accent(p.accent(), 0xFF93BBFF, p.accent(), p.accent2(), p.ink0(), 0xFF5F83C2);
        Border border   = new Border(Color.withAlpha(p.white(), 0x0F), 0xFF1D2536, 0xFF2A3550, 1f);
        Radius radius   = new Radius(4f, 6f, 10f, 14f, 20f);
        Spacing spacing = new Spacing(4f, 8f, 12f, 16f, 24f, 32f);

        Typography type = new Typography(
            new Typography.Role(Weight.SEMIBOLD, 20f, 26f),  // display
            new Typography.Role(Weight.SEMIBOLD, 16f, 22f),  // title
            new Typography.Role(Weight.MEDIUM,   15f, 20f),  // heading
            new Typography.Role(Weight.MEDIUM,   13f, 18f),  // body
            new Typography.Role(Weight.MEDIUM,   12f, 16f),  // label
            new Typography.Role(Weight.REGULAR,  12f, 16f)); // caption

        Shadow shadow = new Shadow(
            new Shadow.Preset(0f, 1f, 4f,  Color.withAlpha(0xFF000000, 0x40)),
            new Shadow.Preset(0f, 4f, 12f, Color.withAlpha(0xFF000000, 0x4D)),
            new Shadow.Preset(0f, 8f, 24f, Color.withAlpha(0xFF000000, 0x59)));

        Glow glow = new Glow(
            new Glow.Preset(6f,  Color.withAlpha(p.accent(), 0x1A)),  // subtle ~10%
            new Glow.Preset(10f, Color.withAlpha(p.accent(), 0x2E))); // active ~18%

        Elevation elevation = new Elevation(
            new Elevation.Level(surface.bg1(),     0, new Shadow.Preset(0f, 0f, 0f, 0), null),
            new Elevation.Level(surface.surface(), border.defaultColor(), shadow.sm(), null),
            new Elevation.Level(surface.bg2(),     border.defaultColor(), shadow.md(), null),
            new Elevation.Level(surface.bg2(),     border.strong(),       shadow.lg(), glow.subtle()));

        Motion motion = new Motion(
            new Motion.Durations(0f, 0.17f, 0.28f, 0.45f),   // ~1.4x softer than the original 0.12/0.20/0.32 (owner request)
            new Motion.Easings(Curves.STANDARD, Curves.DECELERATE, Curves.ACCELERATE, Curves.LINEAR));

        Interaction interaction = new Interaction(
            Color.withAlpha(p.white(), 0x17),   // hoverWash    — white ~9%
            Color.withAlpha(p.ink0(),  0x26),   // pressOverlay — ink0 ~15%
            0.38f,                              // disabledAlpha
            p.accent(),                         // focusRing (reference to accent)
            1.5f);                              // focusRingWidth

        CategoryAccents categories = new CategoryAccents(   // palette "A" (Stage 11) — identity only, see record doc
            0xFFC9808A,   // Combat  — muted crimson (attack/damage)
            0xFF9E8BD9,   // Visuals — muted violet (render; continuity with the enabled-edge violet)
            0xFF7FBFA6,   // Player  — cold teal-green (body/self)
            0xFF8C9BB5,   // Misc    — slate (neutral toolbox, the quietest)
            // Performance — muted sand (v0.1.3). It had to be a fifth hue that no existing one owns, and the
            // obvious candidates were all taken by something that MEANS: any blue is the brand accent (which
            // marks INTERACTION, never identity — v2.5 rule), any strong amber is stateWarn, red is stateLow,
            // and green is stateGood, which the Target HP bar now speaks. Sand sits between slate and warm,
            // owns nothing else, and is lightness-matched to the other four so no category shouts over its
            // neighbours. It also, deliberately, is not a colour anyone reads as "speed".
            0xFFC0A47E); // Performance — muted sand (rendering / cost)

        return new Theme(p, radius, spacing, type, surface, accent, border, shadow, glow, elevation, motion, interaction, categories);
    }
}
