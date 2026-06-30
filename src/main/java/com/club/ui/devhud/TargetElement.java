package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Entity under the crosshair: name + "<hp> HP" + a 2px HP-fraction line (the only accent). No avatar/distance (per the approved minimalist design). */
public final class TargetElement extends HudElement {
    private static final int CONTENT_W = 150, CONTENT_H = 34;
    public TargetElement() { super("target"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().targetX; }
    @Override public int   cfgY() { return h().targetY; }
    @Override public void  cfgX(int v) { h().targetX = v; }
    @Override public void  cfgY(int v) { h().targetY = v; }
    @Override public float cfgScale() { return h().targetScale; }
    @Override public boolean cfgEnabled() { return h().target; }

    @Override public int autoX(MinecraftClient mc) { return mc != null ? mc.getWindow().getScaledWidth() / 2 + 16 : -1; }
    @Override public int autoY(MinecraftClient mc) { return mc != null ? mc.getWindow().getScaledHeight() / 2 - CONTENT_H / 2 : -1; }

    // {name, hp-subline} — sample now; Phase 3 raycasts via TargetHud.raycastTarget.
    private String name = "Steve_42", sub = "18.6 HP"; private float frac = 0.62f;

    @Override public int[] contentSize(MinecraftClient mc, boolean live) { return new int[]{ CONTENT_W, CONTENT_H }; }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var r = ctx.renderer(); var t = ctx.text(); Typography ty = Tokens.type();
        int hi = Tokens.palette().textHi(), desc = Tokens.palette().textDesc(),
            track = Tokens.surface().surfaceHi(), accent = Tokens.accent().accent(), low = Tokens.palette().stateLow();
        t.draw(name, ox, oy, TextStyle.of(ty.body().weight(), ty.body().size() * s, hi));
        t.draw(sub, ox, oy + 18 * s, TextStyle.of(ty.caption().weight(), ty.caption().size() * s, desc));
        float barY = oy + 30 * s, barW = CONTENT_W * s, barH = 2 * s, rr = 1 * s;
        r.roundedRect(ox, barY, barW, barH, rr, track);
        if (frac > 0) r.roundedRect(ox, barY, barW * frac, barH, rr, frac < 0.30f ? low : accent);
    }
}
