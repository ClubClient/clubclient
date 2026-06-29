package com.club.ui.component.widget;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.theme.Elevation;
import com.club.ui.theme.Shadow;
import com.club.ui.theme.Tokens;

/**
 * Shared render snippets for widgets — the single source of truth for state-visuals and elevation surfaces.
 * Widgets MUST route these through here instead of duplicating the call sequences (composition mandate).
 * Render-only (no logic); correctness verified visually in the dev-gallery.
 */
final class WidgetPaint {
    private WidgetPaint() {}

    /** Elevation surface: shadow → rounded fill → border (+ optional glow). Used by Card (level1) / Window (level2). */
    static void elevation(UiContext ctx, float x, float y, float w, float h, float radius, Elevation.Level lv) {
        Shadow.Preset sh = lv.shadow();
        if (sh != null && sh.color() != 0) ctx.renderer().shadow(x, y, w, h, radius, sh.dx(), sh.dy(), sh.blur(), sh.color());
        ctx.renderer().roundedRect(x, y, w, h, radius, lv.surface());
        if (lv.border() != 0) ctx.renderer().border(x, y, w, h, radius, Tokens.border().thickness(), lv.border());
        if (lv.glow() != null) ctx.renderer().glow(x, y, w, h, radius, lv.glow().size(), lv.glow().color());
    }

    /** Flat grouping surface (no shadow): rounded fill + subtle border. Used by Panel. */
    static void flatSurface(UiContext ctx, float x, float y, float w, float h, float radius, int fill) {
        ctx.renderer().roundedRect(x, y, w, h, radius, fill);
        ctx.renderer().border(x, y, w, h, radius, Tokens.border().thickness(), Tokens.border().subtle());
    }

    /** Focus ring around the component's own bounds — drawn only while focused. */
    static void focusRing(UiContext ctx, Component c, float radius) {
        if (!c.isFocused()) return;
        ctx.renderer().border(c.xLeft(), c.yTop(), c.width(), c.height(), radius,
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
}
