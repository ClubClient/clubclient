package com.club.ui.component.widget;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.theme.Tokens;

/**
 * Shared render snippets for widgets — the single source of truth for state-visuals and shared surfaces.
 * Widgets MUST route these through here instead of duplicating the call sequences (composition mandate).
 * Render-only (no logic).
 */
final class WidgetPaint {
    private WidgetPaint() {}

    // =========================================================================
    // CLUB UI DESIGN LANGUAGE — one source of truth for the whole widget library.
    // Depth is flat: tone-step + 1px hairline (never shadow/glow). Handles are
    // pucks: Toggle = white, Slider = accentHi + onAccent ring (white rejected
    // for Slider — UI-V2-MENU §3). States are uniform: hover lighten/wash,
    // press darken+grow, focus = offset accent ring.
    // =========================================================================

    /** Handle (knob/puck) radius growth, in px — uniform across Toggle/Slider. */
    static final float HANDLE_HOVER_GROW = 1f;
    static final float HANDLE_PRESS_GROW = 2f;

    /** Resolves a per-widget accent override (Stage 11.9: category-tinted popovers): 0 → theme accent. */
    static int acc(int override) { return override != 0 ? override : Tokens.accent().accent(); }
    /** Hover-lightened companion of {@link #acc}: theme accentHi, or the same ~18% white lift derived
     *  from the override (matches #7CABFF → #93BBFF). */
    static int accHi(int override) {
        return override != 0 ? Color.lerp(override, 0xFFFFFFFF, 0.18f) : Tokens.accent().accentHi();
    }

    /** Universal flat surface: rounded fill + 1px hairline. Elevation = the chosen fill/border tone
     *  ({@code border==0} → no hairline). */
    static void surface(UiContext ctx, float x, float y, float w, float h, float radius, int fill, int border) {
        ctx.renderer().roundedRect(x, y, w, h, radius, fill);
        if (border != 0) ctx.renderer().border(x, y, w, h, radius, Tokens.border().thickness(), border);
    }

    /** Inset control surface (Toggle-off track, Checkbox-off box): the control tone + a visible hairline. */
    static void controlTrack(UiContext ctx, float x, float y, float w, float h, float radius) {
        surface(ctx, x, y, w, h, radius, Tokens.surface().surfaceHi(), Tokens.border().defaultColor());
    }

    /** Filled circular handle: fill + optional ring (flat — no shadow/glow). */
    static void puck(UiContext ctx, float cx, float cy, float r, int fill, int ring, float ringThk) {
        ctx.renderer().circle(cx, cy, r, fill);
        if (ringThk > 0f) ctx.renderer().border(cx - r, cy - r, r * 2f, r * 2f, r, ringThk, ring);
    }

    /** White puck handle (Toggle): the white-fill special case of {@link #puck}. {@code rimThk<=0} → no rim. */
    static void whitePuck(UiContext ctx, float cx, float cy, float r, int rimColor, float rimThk) {
        puck(ctx, cx, cy, r, Tokens.palette().white(), rimColor, rimThk);
    }

    /** Focus ring around a circular handle at (cx,cy,r) — the knob-local twin of {@link #focusRing}. */
    static void focusRingCircle(UiContext ctx, float cx, float cy, float r) {
        focusRingCircle(ctx, cx, cy, r, Tokens.interaction().focusRing());
    }

    /** {@link #focusRingCircle} with an explicit ring colour (category-accent overrides, Stage 11.9). */
    static void focusRingCircle(UiContext ctx, float cx, float cy, float r, int color) {
        float fr = r + FOCUS_GAP;
        ctx.renderer().border(cx - fr, cy - fr, fr * 2f, fr * 2f, fr,
                Tokens.interaction().focusRingWidth(), color);
    }

    /** Gap (px) between a component's bounds and its focus ring, so the ring reads as an offset
     *  halo rather than overlapping the fill/border. Shape geometry, not a design token. */
    private static final float FOCUS_GAP = 2f;

    /** Focus ring: an offset accent halo just outside the component's bounds — drawn only while focused.
     *  Flat (a hairline stroke, not a glow); the 2px gap keeps it off the element's own edge. */
    static void focusRing(UiContext ctx, Component c, float radius) {
        focusRing(ctx, c, radius, Tokens.interaction().focusRing());
    }

    /** {@link #focusRing} with an explicit ring colour (category-accent overrides, Stage 11.9). */
    static void focusRing(UiContext ctx, Component c, float radius, int color) {
        if (!c.isFocusVisible()) return;   // keyboard focus only — clicked controls don't wear the halo
        ctx.renderer().border(c.xLeft() - FOCUS_GAP, c.yTop() - FOCUS_GAP,
                c.width() + FOCUS_GAP * 2f, c.height() + FOCUS_GAP * 2f, radius + FOCUS_GAP,
                Tokens.interaction().focusRingWidth(), color);
    }

    /** Hover-wash overlay scaled by progress t∈[0,1] (alpha-scaled token, no literals). */
    static void hoverWash(UiContext ctx, float x, float y, float w, float h, float radius, float t) {
        if (t <= 0f) return;
        ctx.renderer().roundedRect(x, y, w, h, radius, Color.scaleAlpha(Tokens.interaction().hoverWash(), t));
    }

    /** Press overlay (full strength), drawn while the control is pressed. */
    static void pressOverlay(UiContext ctx, float x, float y, float w, float h, float radius) {
        ctx.renderer().roundedRect(x, y, w, h, radius, Tokens.interaction().pressOverlay());
    }
}
