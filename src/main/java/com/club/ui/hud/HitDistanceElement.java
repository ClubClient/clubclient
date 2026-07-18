package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.hud.HitDistanceTracker;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * Hit Distance readout — a QUIET chip in the V4 language (the Sprint capsule ground + one label): the
 * distance at which your LAST attack connected, {@code "2.34 blocks"}, in plain white.
 *
 * <p><b>It answers one question, once per hit.</b> Land an attack on an entity and the chip appears with the
 * reach of that hit; miss, and there is nothing to show. It holds for {@link HitDistanceTracker#WINDOW_MS 10 s}
 * after the hit and then dissolves — a fresh hit refreshes the value and the clock. The appear/dissolve is the
 * canvas's own element fade (driven by {@link #hasContent}), the same short motion every chip uses; a combat
 * readout should arrive without ceremony.</p>
 *
 * <p><b>No colour states.</b> Owner reversed the green/red idea: the number is the whole message, drawn
 * {@link Tokens.Palette#textHi white} like any other value. The unit word ("blocks") is set 30% smaller than
 * the number, which trims the pill so it no longer reads as an over-long strip.</p>
 *
 * <p>Visibility is owned by the Combat card ({@code MenuContent.hitDistance} → {@code hud.hitDistance}); the
 * HUD editor shows this element Size only.</p>
 */
public final class HitDistanceElement extends HudElement {
    private static final float TEXT_SIZE = 12f;
    private static final float UNIT_SIZE = TEXT_SIZE * 0.7f;   // "blocks" 30% smaller than the number
    private static final float PAD_X = 8f;
    private static final float GAP   = 3f;                     // number ↔ unit
    private static final int   CONTENT_H = 20;
    private static final String UNIT = "blocks";
    /** Box width comes from this fixed number template so a changing reading never resizes the chip
     *  (reach is single-digit, so every value is "d.dd"). */
    private static final String NUM_TEMPLATE = "0.00";

    public HitDistanceElement() { super("hitDistance"); }
    @Override public String displayName() { return "Hit Distance"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().hitDistanceX; }
    @Override public int   cfgY() { return h().hitDistanceY; }
    @Override public void  cfgX(int v) { h().hitDistanceX = v; }
    @Override public void  cfgY(int v) { h().hitDistanceY = v; }
    @Override public float cfgScale() { return h().hitDistanceScale; }
    /** The Combat card is the SINGLE owner of this flag; the editor suppresses its Enabled row and shows Size only. */
    @Override public boolean cfgEnabled() { return h().hitDistance; }

    // V4: the chip is the element — capsule painted in paint(), no shared panel.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }

    /** Default: centred, one step below the crosshair — a combat readout sits where the eye already is,
     *  clear of Sprint (top-left) and the Target chip (right of the crosshair). Movable. */
    @Override public int autoX(MinecraftClient mc) {
        if (mc == null) return -1;
        int sw = com.club.ui.ClubCanvas.widthI(mc);
        return (sw - contentSize(mc, false)[0]) / 2;
    }
    @Override public int autoY(MinecraftClient mc) {
        return mc != null ? com.club.ui.ClubCanvas.heightI() / 2 + 14 : -1;
    }

    /** In-world the chip exists only for the 10 s window after a landed hit — the canvas fades it in on the
     *  hit and out when the window lapses. The editor always shows the sample so it stays positionable. */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || HitDistanceTracker.hasHit(System.currentTimeMillis());
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        float w = 2 * PAD_X + Ui.text().width(NUM_TEMPLATE, Weight.MEDIUM, TEXT_SIZE)
                + GAP + Ui.text().width(UNIT, Weight.MEDIUM, UNIT_SIZE);
        return new int[]{ Math.round(w), CONTENT_H };
    }

    /** The number part of the readout (unit-tested): two decimals, ROOT locale (a dot, never a comma). */
    public static String numberText(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        // Editor sample shows a representative reading; in-world we draw the last hit's distance (valid whenever
        // the element is on screen — hasContent gates that, and the value persists through the fade-out).
        String num = live ? numberText(HitDistanceTracker.distance()) : numberText(2.34);
        int col = Color.scaleAlpha(Tokens.palette().textHi(), alpha);   // plain white — no colour states

        float cw = 2 * PAD_X + Ui.text().width(NUM_TEMPLATE, Weight.MEDIUM, TEXT_SIZE)
                + GAP + Ui.text().width(UNIT, Weight.MEDIUM, UNIT_SIZE);
        // Same dense ground as the Sprint chip (bg1 at 0.72), legible over a bright PvP world without a loud panel.
        ctx.renderer().roundedRect(ox, oy, cw * s, CONTENT_H * s, HudPaint.CHIP_RAD * s,
                Color.scaleAlpha(Tokens.surface().bg1(), 0.72f * alpha));

        // Number and unit share a baseline (the smaller unit sits ON the number's baseline, not floating).
        float lhNum = Ui.text().lineHeight(Weight.MEDIUM, TEXT_SIZE);
        float numYTop = oy + (CONTENT_H - lhNum) * 0.5f * s;
        float baseShift = (ctx.text().ascent(Weight.MEDIUM, TEXT_SIZE) - ctx.text().ascent(Weight.MEDIUM, UNIT_SIZE)) * s;
        float numX = ox + PAD_X * s;
        float unitX = numX + (Ui.text().width(num, Weight.MEDIUM, TEXT_SIZE) + GAP) * s;

        ctx.text().draw(num, numX, numYTop,
                TextStyle.of(Weight.MEDIUM, TEXT_SIZE * s, col).effect(HudPaint.textShadow(alpha)));
        ctx.text().draw(UNIT, unitX, numYTop + baseShift,
                TextStyle.of(Weight.MEDIUM, UNIT_SIZE * s, col).effect(HudPaint.textShadow(alpha)));
    }
}
