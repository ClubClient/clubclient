package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

/**
 * FPS — the utility WHISPER (Stage 13.6, owner: a capsule made it read like content, not like
 * pinned-down auxiliary info). No capsule, no edge, no accent: just a small tabular value +
 * a faint caps-ish label, sitting quietly in the corner on a text shadow. Role over uniformity —
 * this is the one element that must NOT look like the others.
 * (Coordinates / CPS / BPS are a separate future element.)
 */
public final class InfoElement extends HudElement {
    private static final float VALUE_SIZE = 12f, LABEL_SIZE = 10f;
    private static final int GAP = 4;
    /** Width reserved for the value so 59↔240 doesn't shift the label every second. */
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

    // Whisper: no ground at all.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }
    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) { }

    private static String fps(MinecraftClient mc, boolean live) { return (live && mc != null ? mc.getCurrentFps() : 240) + ""; }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        float w = HudText.width(VALUE_RESERVE, Weight.SEMIBOLD, VALUE_SIZE) + GAP
                + Ui.text().width("FPS", Weight.MEDIUM, LABEL_SIZE);
        return new int[]{ Math.round(w), Math.round(Ui.text().lineHeight(Weight.SEMIBOLD, VALUE_SIZE)) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        int val = Color.scaleAlpha(Tokens.palette().textMuted(), alpha);   // quiet — this is aux info
        int lab = Color.scaleAlpha(Tokens.palette().textFaint(), alpha);
        float reserve = HudText.width(VALUE_RESERVE, Weight.SEMIBOLD, VALUE_SIZE);
        String v = fps(mc, live);
        float tw = HudText.width(v, Weight.SEMIBOLD, VALUE_SIZE);
        // value right-aligned inside its reserve → the label never shifts as digits change
        HudText.draw(ctx, v, ox + (reserve - tw) * s, oy,
                TextStyle.of(Weight.SEMIBOLD, VALUE_SIZE * s, val).effect(HudPaint.textShadow(alpha)), VALUE_SIZE, s);
        float labDy = Ui.text().ascent(Weight.SEMIBOLD, VALUE_SIZE) - Ui.text().ascent(Weight.MEDIUM, LABEL_SIZE);
        ctx.text().draw("FPS", ox + (reserve + GAP) * s, oy + labDy * s,
                TextStyle.of(Weight.MEDIUM, LABEL_SIZE * s, lab).effect(HudPaint.textShadow(alpha)));
    }
}
