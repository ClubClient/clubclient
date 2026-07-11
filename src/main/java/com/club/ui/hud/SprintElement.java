package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.modules.togglesprint.ToggleSprintModule;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

/**
 * Toggle Sprint indicator (Stage 41) — a QUIET chip in the V4 language: the capsule ground +
 * one Medium 12 label. State speaks through text brightness only (muted when armed, textHi while
 * actually sprinting) — no colour, no icon; this is auxiliary info, one step above the FPS whisper
 * because it is a STATE, not a reading. Shown in-world only while the Toggle Sprint module is on.
 */
public final class SprintElement extends HudElement {
    private static final float TEXT_SIZE = 12f;
    private static final float PAD_X = 8f;
    private static final int CONTENT_H = 20;
    private static final String LABEL = "Sprint";

    public SprintElement() { super("sprint"); }
    @Override public String displayName() { return "Sprint"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().sprintX; }
    @Override public int   cfgY() { return h().sprintY; }
    @Override public void  cfgX(int v) { h().sprintX = v; }
    @Override public void  cfgY(int v) { h().sprintY = v; }
    @Override public float cfgScale() { return h().sprintScale; }
    @Override public boolean cfgEnabled() { return h().sprint; }

    // V4: the chip is the element — capsule painted in paint(), no shared panel.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }

    /** Default: the mod's top-left stack, one line below the FPS whisper — clear of the vanilla chat
     *  and hotbar (a bottom-left default sat on top of the chat history). Movable in the editor. */
    @Override public int autoX(MinecraftClient mc) { return mc != null ? 8 : -1; }
    @Override public int autoY(MinecraftClient mc) { return mc != null ? 148 : -1; }

    /** In-world the chip exists only while the module is on (the editor always shows the sample). */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || ToggleSprintModule.active(mc);
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        float w = 2 * PAD_X + Ui.text().width(LABEL, Weight.MEDIUM, TEXT_SIZE);
        return new int[]{ Math.round(w), CONTENT_H };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        float cw = 2 * PAD_X + Ui.text().width(LABEL, Weight.MEDIUM, TEXT_SIZE);
        HudPaint.chip(ctx, ox, oy, cw * s, CONTENT_H * s, HudPaint.CHIP_RAD * s, alpha);
        boolean sprinting = live && mc != null && mc.player != null && mc.player.isSprinting();
        int col = Color.scaleAlpha(sprinting ? Tokens.palette().textHi() : Tokens.palette().textMuted(), alpha);
        float lh = Ui.text().lineHeight(Weight.MEDIUM, TEXT_SIZE);
        ctx.text().draw(LABEL, ox + PAD_X * s, oy + (CONTENT_H - lh) * 0.5f * s,
                TextStyle.of(Weight.MEDIUM, TEXT_SIZE * s, col).effect(HudPaint.textShadow(alpha)));
    }
}
