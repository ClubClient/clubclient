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
        float fr = r + FOCUS_GAP;
        ctx.renderer().border(cx - fr, cy - fr, fr * 2f, fr * 2f, fr,
                Tokens.interaction().focusRingWidth(), Tokens.interaction().focusRing());
    }

    /** Gap (px) between a component's bounds and its focus ring, so the ring reads as an offset
     *  halo rather than overlapping the fill/border. Shape geometry, not a design token. */
    private static final float FOCUS_GAP = 2f;

    /** Focus ring: an offset accent halo just outside the component's bounds — drawn only while focused.
     *  Flat (a hairline stroke, not a glow); the 2px gap keeps it off the element's own edge. */
    static void focusRing(UiContext ctx, Component c, float radius) {
        if (!c.isFocused()) return;
        ctx.renderer().border(c.xLeft() - FOCUS_GAP, c.yTop() - FOCUS_GAP,
                c.width() + FOCUS_GAP * 2f, c.height() + FOCUS_GAP * 2f, radius + FOCUS_GAP,
                Tokens.interaction().focusRingWidth(), Tokens.interaction().focusRing());
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

    /** Press overlay scaled by progress t∈[0,1] — for controls that ease their press (e.g. Dropdown). */
    static void pressOverlay(UiContext ctx, float x, float y, float w, float h, float radius, float t) {
        if (t <= 0f) return;
        ctx.renderer().roundedRect(x, y, w, h, radius, Color.scaleAlpha(Tokens.interaction().pressOverlay(), t));
    }
}
