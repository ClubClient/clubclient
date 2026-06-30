package com.club.ui.hud;

import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

/** Static, non-draggable HUD decals (watermark + crosshair) — ported from the approved HudView mock. */
public final class Decals {
    private Decals() {}
    private static TextStyle st(Typography.Role r, int c) { return TextStyle.of(r.weight(), r.size(), c); }
    private static float tw(String s, Typography.Role r) { return Ui.text().width(s, r.weight(), r.size()); }

    public static void watermark(UiContext ctx) {
        var r = ctx.renderer(); var t = ctx.text(); Typography ty = Tokens.type();
        int hi = Tokens.palette().textHi(), faint = Tokens.palette().textFaint(), accent = Tokens.accent().accent();
        r.roundedRect(18, 20, 8, 8, 2, accent);
        t.draw("CLUB", 32, 16, st(ty.title(), hi));
        t.draw("v2.5", 32 + tw("CLUB", ty.title()) + 8, 19, st(ty.label(), faint));
    }

    public static void crosshair(UiContext ctx, int w, int h) {
        var r = ctx.renderer();
        float cx = w / 2f, cy = h * 0.47f;
        int xh = Color.withAlpha(Tokens.palette().textHi(), 0x99);
        r.rect(cx - 9, cy - 1, 18, 2, xh);
        r.rect(cx - 1, cy - 9, 2, 18, xh);
    }
}
