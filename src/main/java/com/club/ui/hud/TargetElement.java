package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.motion.ValueTween;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

/**
 * Target — V4 "Chips" + variant C (Stage 13, owner-picked): a single capsule whose BOTTOM EDGE is
 * the HP bar. <b>HP-first hierarchy</b> — the health number (16 SemiBold, bright) leads, the name
 * follows quieter (13 Medium, muted, truncated with "…" at a FIXED width so the chip never
 * stretches for long names). In PvP the eye catches the number and the live edge; the name is
 * secondary. The edge fill hue-ramps as HP drains — cold steel-blue → purple → orange → red
 * (smooth lerp, never a hard switch). Digits are never coloured (state = the edge).
 */
public final class TargetElement extends HudElement {
    private static final float HP_SIZE = 16f, UNIT_SIZE = 12f, NAME_SIZE = 13f;
    private static final int   GAP = 12;            // HP-group ↔ name gap (unscaled)
    private static final float PAD_X = 12f, PAD_TOP = 7f;   // chip padding (unscaled)
    private static final float BAR_H = 3f;          // live edge height
    private static final int   CONTENT_H = 36;      // full chip height (text band + edge zone)
    private static final float NAME_MAX_W = 84f;    // FIXED name field — longer names ellipsize here
    private static final float MIN_W = 96f;
    private static final String UNIT = " HP";

    // HP tone C: number a touch brighter than the name-support tone, the "HP" unit dimmer — a micro-hierarchy
    // inside the value so it reads dearer without a third text colour on the name.
    private static final int HP_NUM  = 0xFFEAEDF2;
    private static final int HP_UNIT = 0xFF7E8696;
    // #4 accent for the bar: a cold, de-saturated steel-blue (the old #7CABFF read "cheat-client toy" on the bar).
    private static final int STEEL   = 0xFF86A6CC;
    private static final int PURPLE  = 0xFF9B7FCB;
    private static final int ORANGE  = 0xFFE0A24E;

    // HP-bar fraction easing: a short tween so the bar glides on damage/heal but never trails real HP by more
    // than the fast duration; snaps when the target changes so it re-bases.
    private final ValueTween hpFrac =
            new ValueTween(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
    // Current shown data — cached so a lost target (raycast null during the fade-out / on death) keeps the LAST
    // real name/HP instead of leaking the editor sample. Sample ("Steve_42") only ever shows in the editor.
    private String curName = "Steve_42", curNum = "18";
    private float curFrac = 0.62f;
    private String tweenName;   // the name the hp tween is based on (snap on change)
    // Scale-pop on target acquire/change: a quick 0.955 → 1.0 ease so a new target "lands" instead of popping in.
    private static final float POP_FROM = 0.955f;
    private String popName;     // last name we popped for
    private float popStart = -1f;

    public TargetElement() { super("target"); }
    @Override public String displayName() { return "Target"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().targetX; }
    @Override public int   cfgY() { return h().targetY; }
    @Override public void  cfgX(int v) { h().targetX = v; }
    @Override public void  cfgY(int v) { h().targetY = v; }
    @Override public float cfgScale() { return h().targetScale; }
    @Override public boolean cfgEnabled() { return h().target; }

    // V4: the chip IS the element — no outer panel, no extra padding (the capsule + edge are drawn
    // in paint(), so the editor drag box frames the capsule exactly).
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }

    /** Quick scale-pop when the target is acquired or changes (per the "feel" pass). Same easing/token family
     *  as the other HUD motion, so all animations share one speed language. */
    @Override protected float visualScale(UiContext ctx) {
        float now = ctx.time();
        if (!curName.equals(popName)) { popName = curName; popStart = now; }   // acquired / changed → play
        if (popStart < 0f) return 1f;
        float t = (now - popStart) / Tokens.motion().durations().fast();
        if (t >= 1f) return 1f;
        return POP_FROM + (1f - POP_FROM) * Tokens.motion().easings().decelerate().apply(Math.max(0f, t));
    }

    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) {
        // no-op: the V4 capsule + live edge are drawn in paint() (needs the eased HP fraction)
    }

    @Override public int autoX(MinecraftClient mc) { return mc != null ? mc.getWindow().getScaledWidth() / 2 + 16 : -1; }
    @Override public int autoY(MinecraftClient mc) { return mc != null ? mc.getWindow().getScaledHeight() / 2 - CONTENT_H / 2 : -1; }

    /** In-world: show only when actually aiming at a living entity (no sample fallback outside the editor). */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || mc.world == null || com.club.hud.TargetHud.raycastTarget(mc, 1f) != null;
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        resolve(mc, live);   // refresh cached data once per frame (called before paint via layoutFromConfig)
        return new int[]{ Math.round(contentW()), CONTENT_H };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text();
        float now = ctx.time();
        // cur* refreshed in contentSize() this frame. Snap the edge when the target changes; else ease it.
        if (!curName.equals(tweenName)) { hpFrac.snap(curFrac, now); tweenName = curName; } else hpFrac.set(curFrac, now);
        float frac = hpFrac.get(now);

        float cw = contentW(), ch = CONTENT_H;
        HudPaint.chip(ctx, ox, oy, cw * s, ch * s, HudPaint.CHIP_RAD * s, alpha);
        HudPaint.edgeBar(ctx, ox, oy, cw * s, ch * s, BAR_H, frac, hpColor(frac), s, alpha);

        int numC  = Color.scaleAlpha(HP_NUM, alpha);
        int unitC = Color.scaleAlpha(HP_UNIT, alpha);
        int nameC = Color.scaleAlpha(Tokens.palette().textMuted(), alpha);

        float numW  = Ui.text().width(curNum, Weight.SEMIBOLD, HP_SIZE);
        float unitW = Ui.text().width(UNIT,   Weight.MEDIUM, UNIT_SIZE);
        // one shared baseline: the HP number leads it; unit and name hang off it (premium alignment)
        float unitDy = Ui.text().ascent(Weight.SEMIBOLD, HP_SIZE) - Ui.text().ascent(Weight.MEDIUM, UNIT_SIZE);
        float nameDy = Ui.text().ascent(Weight.SEMIBOLD, HP_SIZE) - Ui.text().ascent(Weight.MEDIUM, NAME_SIZE);

        float tx = ox + PAD_X * s, ty = oy + PAD_TOP * s;
        t.draw(curNum, tx, ty, TextStyle.of(Weight.SEMIBOLD, HP_SIZE * s, numC).effect(HudPaint.textShadow(alpha)));
        t.draw(UNIT, tx + numW * s, ty + unitDy * s,
                TextStyle.of(Weight.MEDIUM, UNIT_SIZE * s, unitC).effect(HudPaint.textShadow(alpha)));
        String name = fitName(curName, NAME_MAX_W);
        t.draw(name, tx + (numW + unitW + GAP) * s, ty + nameDy * s,
                TextStyle.of(Weight.MEDIUM, NAME_SIZE * s, nameC).effect(HudPaint.textShadow(alpha)));
    }

    /** #5 HP-bar colour ramp as health drains: steel-blue (high) → purple → orange → red (low), smoothly lerped
     *  — a hard switch at one threshold looks cheap; the ramp makes the drain feel expensive. */
    private static int hpColor(float f) {
        int red = Tokens.palette().stateLow();
        if (f >= 0.55f) return STEEL;
        if (f >= 0.38f) return Color.lerp(PURPLE, STEEL,  (f - 0.38f) / 0.17f);
        if (f >= 0.20f) return Color.lerp(ORANGE, PURPLE, (f - 0.20f) / 0.18f);
        return Color.lerp(red, ORANGE, Math.max(0f, f) / 0.20f);
    }

    /** Unscaled chip width — hug (pad + HP group + gap + name), the name capped at its FIXED field. */
    private float contentW() {
        float hpW = Ui.text().width(curNum, Weight.SEMIBOLD, HP_SIZE) + Ui.text().width(UNIT, Weight.MEDIUM, UNIT_SIZE);
        float nameW = Math.min(Ui.text().width(curName, Weight.MEDIUM, NAME_SIZE), NAME_MAX_W);
        return Math.max(MIN_W, 2 * PAD_X + hpW + GAP + nameW);
    }

    /** Truncate {@code name} with an ellipsis to fit {@code availW} (px, unscaled) at the name style. */
    private String fitName(String name, float availW) {
        if (Ui.text().width(name, Weight.MEDIUM, NAME_SIZE) <= availW) return name;
        String cut = name;
        while (cut.length() > 1 && Ui.text().width(cut + "…", Weight.MEDIUM, NAME_SIZE) > availW)
            cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
    }

    /** Refresh the cached target data. A lost target keeps the last real values (so the fade-out / death frame
     *  shows the real name, not the sample); the sample is only ever used in the editor (live=false). */
    private void resolve(MinecraftClient mc, boolean live) {
        if (!live) { curName = "Steve_42"; curNum = "18"; curFrac = 0.62f; return; }
        if (mc == null || mc.world == null) return;                    // keep last known
        var le = com.club.hud.TargetHud.raycastTarget(mc, 1f);
        if (le == null) return;                                        // no target now — keep last, never the sample
        curName = le.getName().getString();
        float hp = le.getHealth(), max = le.getMaxHealth();
        curFrac = max > 0 ? Math.max(0f, Math.min(1f, hp / max)) : 0f;
        curNum = Math.abs(hp - Math.round(hp)) < 0.05f ? String.valueOf(Math.round(hp))
                : String.format(java.util.Locale.ROOT, "%.1f", hp);
    }
}
