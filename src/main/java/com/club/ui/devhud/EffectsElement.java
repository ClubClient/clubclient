package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Active effects: a column of [chip] Name  Time rows. Pure-vector (no sprite — matches the V2 mock). */
public final class EffectsElement extends HudElement {
    private static final int ROW = 26, CHIP_H = 22, CHIP_W = 120;
    public EffectsElement() { super("effects"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().potionX; }
    @Override public int   cfgY() { return h().potionY; }
    @Override public void  cfgX(int v) { h().potionX = v; }
    @Override public void  cfgY(int v) { h().potionY = v; }
    @Override public float cfgScale() { return h().potionScale; }
    @Override public boolean cfgEnabled() { return h().potions; }

    /** {name,time} rows — sample now; Phase 3 swaps in PotionHud.effects(mc). */
    private String[][] rows(MinecraftClient mc, boolean live) {
        return new String[][]{{"Speed II", "1:24"}, {"Strength I", "0:42"}};
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        int n = rows(mc, live).length;
        return new int[]{ CHIP_W, Math.max(CHIP_H, (n - 1) * ROW + CHIP_H) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var r = ctx.renderer(); var t = ctx.text(); Typography ty = Tokens.type();
        int chip = Color.withAlpha(Tokens.palette().ink0(), 0x8C), sub = Tokens.border().subtle();
        int hi = Tokens.palette().textHi(), mut = Tokens.palette().textMuted();
        float sm = Tokens.radius().sm() * s, cw = CHIP_W * s, ch = CHIP_H * s, lh = ty.label().lineHeight() * s;
        String[][] rows = rows(mc, live);
        for (int i = 0; i < rows.length; i++) {
            float py = oy + i * ROW * s;
            r.roundedRect(ox, py, cw, ch, sm, chip);
            r.border(ox, py, cw, ch, sm, 1, sub);
            t.draw(rows[i][0], ox + 9 * s, py + (ch - lh) / 2f, TextStyle.of(ty.label().weight(), ty.label().size() * s, hi));
            t.draw(rows[i][1], ox + cw - 9 * s, py + (ch - lh) / 2f, TextStyle.of(ty.label().weight(), ty.label().size() * s, mut).align(Align.RIGHT));
        }
    }
}
