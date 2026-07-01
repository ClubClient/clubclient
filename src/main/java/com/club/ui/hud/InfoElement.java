package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** A neat, minimal FPS counter. (Coordinates / CPS / BPS are a separate future element.) */
public final class InfoElement extends HudElement {
    private static final int GAP = 6;

    public InfoElement() { super("info"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().infoX; }
    @Override public int   cfgY() { return h().infoY; }
    @Override public void  cfgX(int v) { h().infoX = v; }
    @Override public void  cfgY(int v) { h().infoY = v; }
    @Override public float cfgScale() { return h().infoScale; }
    @Override public boolean cfgEnabled() { return h().info; }

    private static String fps(MinecraftClient mc, boolean live) { return (live && mc != null ? mc.getCurrentFps() : 240) + ""; }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        Typography.Role r = Tokens.type().label();
        float w = Ui.text().width("FPS", r.weight(), r.size()) + GAP + Ui.text().width(fps(mc, live), r.weight(), r.size());
        return new int[]{ Math.round(w), Math.round(r.lineHeight()) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text(); Typography.Role r = Tokens.type().label();
        int desc = Color.scaleAlpha(Tokens.palette().textDesc(), alpha);
        int acc  = Color.scaleAlpha(Tokens.accent().accent(), alpha);
        float labelW = Ui.text().width("FPS", r.weight(), r.size());
        t.draw("FPS", ox, oy, TextStyle.of(r.weight(), r.size() * s, desc));
        t.draw(fps(mc, live), ox + (labelW + GAP) * s, oy, TextStyle.of(r.weight(), r.size() * s, acc));
    }
}
