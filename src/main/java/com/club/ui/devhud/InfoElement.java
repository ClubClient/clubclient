package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Bottom-left readout: FPS + XYZ (CPS/BPS deferred — need a click tracker). */
public final class InfoElement extends HudElement {
    private static final int CONTENT_W = 160, ROW = 18;
    public InfoElement() { super("info"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().infoX; }
    @Override public int   cfgY() { return h().infoY; }
    @Override public void  cfgX(int v) { h().infoX = v; }
    @Override public void  cfgY(int v) { h().infoY = v; }
    @Override public float cfgScale() { return h().infoScale; }
    @Override public boolean cfgEnabled() { return h().info; }

    /** {label,value,isXYZ} rows — sample now; Phase 3 fills FPS + player XYZ. */
    private String[][] rows(MinecraftClient mc, boolean live) {
        return new String[][]{{"FPS", "240", "0"}, {"XYZ", "128 / 72 / -340", "1"}};
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        return new int[]{ CONTENT_W, rows(mc, live).length * ROW };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text(); Typography ty = Tokens.type();
        int desc = Tokens.palette().textDesc(), hi = Tokens.palette().textHi(), accent = Tokens.accent().accent();
        String[][] rows = rows(mc, live);
        for (int i = 0; i < rows.length; i++) {
            float ry = oy + i * ROW * s; boolean xyz = rows[i][2].equals("1");
            t.draw(rows[i][0], ox, ry, TextStyle.of(ty.label().weight(), ty.label().size() * s, desc));
            t.draw(rows[i][1], ox + 38 * s, ry, TextStyle.of(ty.label().weight(), ty.label().size() * s, xyz ? hi : accent));
        }
    }
}
