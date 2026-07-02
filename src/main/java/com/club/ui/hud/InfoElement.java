package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/**
 * FPS on the V4 "Chips" language (Stage 13): one small capsule ["FPS  <value>"] with a subtle
 * half-strength brand edge — the one calm brand touch on the HUD (identity, not state; FPS has
 * no meaningful fraction, so the edge stays full-width). Value is SemiBold, digits never tinted.
 * (Coordinates / CPS / BPS are a separate future element.)
 */
public final class InfoElement extends HudElement {
    // Shared row metrics (Stage 13.4 tightening): PAD_X 8 / gaps 5 across every row capsule.
    private static final float CHIP_H = 24f, PAD_X = 8f, PAD_TOP = 3f, BAR_H = 2f;
    private static final int GAP = 5;
    /** Width reserved for the value so 59↔240 doesn't resize the capsule every second. */
    private static final String VALUE_RESERVE = "888";

    public InfoElement() { super("info"); }
    @Override public String displayName() { return "FPS"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().infoX; }
    @Override public int   cfgY() { return h().infoY; }
    @Override public void  cfgX(int v) { h().infoX = v; }
    @Override public void  cfgY(int v) { h().infoY = v; }
    @Override public float cfgScale() { return h().infoScale; }
    @Override public boolean cfgEnabled() { return h().info; }

    // V4: the capsule is drawn in paint(); no shared outer panel.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }
    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) { }

    private static String fps(MinecraftClient mc, boolean live) { return (live && mc != null ? mc.getCurrentFps() : 240) + ""; }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        Typography.Role r = Tokens.type().label();
        float w = 2 * PAD_X + Ui.text().width("FPS", r.weight(), r.size()) + GAP
                + HudText.width(VALUE_RESERVE, Weight.SEMIBOLD, r.size());
        return new int[]{ Math.round(w), Math.round(CHIP_H) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text(); Typography.Role r = Tokens.type().label();
        float cw = contentSize(mc, live)[0];
        HudPaint.chip(ctx, ox, oy, cw * s, CHIP_H * s, HudPaint.CHIP_RAD * s, alpha);
        HudPaint.edgeBar(ctx, ox, oy, cw * s, CHIP_H * s, BAR_H, 1f,
                Color.scaleAlpha(Tokens.accent().accent(), 0.5f), s, alpha);

        int mut = Color.scaleAlpha(Tokens.palette().textMuted(), alpha);
        int hi  = Color.scaleAlpha(Tokens.palette().textHi(), alpha);
        float labelW = Ui.text().width("FPS", r.weight(), r.size());
        float ty = oy + (PAD_TOP + (CHIP_H - BAR_H - HudPaint.EDGE_BOT - PAD_TOP - r.lineHeight()) * 0.5f) * s;
        t.draw("FPS", ox + PAD_X * s, ty, TextStyle.of(r.weight(), r.size() * s, mut).effect(HudPaint.textShadow(alpha)));
        HudText.draw(ctx, fps(mc, live), ox + (PAD_X + labelW + GAP) * s, ty,
                TextStyle.of(Weight.SEMIBOLD, r.size() * s, hi).effect(HudPaint.textShadow(alpha)), r.size(), s);
    }
}
