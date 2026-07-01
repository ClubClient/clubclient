package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Axis;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.motion.ValueTween;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

/**
 * Target — the "Hero" element (docs/HUD-LANGUAGE.md §8): the entity under the crosshair, composed to read
 * instantly in PvP while feeling like it belongs to the client (menu language), not a card laid over the game.
 *
 * <p>Locked etalon: a <b>recessive</b> panel (34% {@code bg1}, no border — the border was the "card tell" — plus a
 * whisper-subtle vertical tone gradient so it reads as a surface, not a slab), radius 10, generous padding.
 * Name (18 SemiBold, white) left; HP two-tone right — number bright ({@link #HP_NUM}), unit "HP" dim
 * ({@link #HP_UNIT}); a 4px HP bar below. Hierarchy: name → HP → bar → panel. The bar is the object of motion:
 * a soft low-contrast track, a brighter fill, and a colour that <b>ramps</b> as HP drains — cold steel-blue →
 * purple → orange → red (not a hard switch), so damage feels alive. Digits are never coloured (state = the bar).
 */
public final class TargetElement extends HudElement {
    private static final float NAME_SIZE = 18f, HP_SIZE = 13f;
    private static final int   GAP = 14;           // name ↔ HP-group gap (unscaled)
    private static final float BAR_H = 4f;         // #1 bar heavier — health is the heaviest object
    private static final int   BAR_TOP = 27;       // ~10px air below the name band
    private static final int   CONTENT_H = BAR_TOP + (int) BAR_H;
    private static final float MIN_W = 92f, MAX_W = 172f;   // content-width clamp (long names truncate into MAX_W)
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

    public TargetElement() { super("target"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().targetX; }
    @Override public int   cfgY() { return h().targetY; }
    @Override public void  cfgX(int v) { h().targetX = v; }
    @Override public void  cfgY(int v) { h().targetY = v; }
    @Override public float cfgScale() { return h().targetScale; }
    @Override public boolean cfgEnabled() { return h().target; }

    // Hero panel: bigger radius + generous, deliberate padding (menu language), recessive fill (see drawPanel).
    @Override protected float panelPadX() { return 15f; }
    @Override protected float panelPadY() { return 13f; }
    @Override protected float panelRadius() { return 10f; }

    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) {
        if (a <= 0f) return;
        int base = Tokens.surface().bg1();
        // ~34% fill, top a hair lighter / bottom a hair darker — so faint the player never "sees" a gradient,
        // it just stops reading as a flat slab. No border (the #1D2536 hairline is the "menu card" tell).
        int top = Color.withAlpha(Color.lerp(base, 0xFFFFFFFF, 0.03f), 0x57);
        int bot = Color.withAlpha(Color.lerp(base, 0xFF000000, 0.03f), 0x57);
        ctx.renderer().gradient(x, y, w, h, radius, Color.scaleAlpha(top, a), Color.scaleAlpha(bot, a), Axis.VERTICAL);
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
        var r = ctx.renderer(); var t = ctx.text();
        float now = ctx.time();
        // cur* refreshed in contentSize() this frame. Snap the bar when the target changes; else ease it.
        if (!curName.equals(tweenName)) { hpFrac.snap(curFrac, now); tweenName = curName; } else hpFrac.set(curFrac, now);
        float frac = hpFrac.get(now);

        int nameC = Color.scaleAlpha(Tokens.palette().textHi(), alpha);
        int numC  = Color.scaleAlpha(HP_NUM, alpha);
        int unitC = Color.scaleAlpha(HP_UNIT, alpha);

        float cw = contentW();
        float numW  = Ui.text().width(curNum, Weight.MEDIUM, HP_SIZE);
        float unitW = Ui.text().width(UNIT,   Weight.MEDIUM, HP_SIZE);
        float hpW   = numW + unitW;
        // baseline-align the smaller HP text to the name's baseline (premium alignment, not top-aligned)
        float hpDy = Ui.text().ascent(Weight.SEMIBOLD, NAME_SIZE) - Ui.text().ascent(Weight.MEDIUM, HP_SIZE);

        // name (primary) — left, truncated into the width left after the HP group
        String name = fitName(curName, cw - GAP - hpW);
        t.draw(name, ox, oy, TextStyle.of(Weight.SEMIBOLD, NAME_SIZE * s, nameC).effect(HudPaint.textShadow(alpha)));
        // HP two-tone — right-aligned group ending at the content edge
        float numX  = ox + (cw - hpW) * s;
        float unitX = ox + (cw - unitW) * s;
        t.draw(curNum, numX, oy + hpDy * s, TextStyle.of(Weight.MEDIUM, HP_SIZE * s, numC).effect(HudPaint.textShadow(alpha)));
        t.draw(UNIT,   unitX, oy + hpDy * s, TextStyle.of(Weight.MEDIUM, HP_SIZE * s, unitC).effect(HudPaint.textShadow(alpha)));

        // HP bar — the object of motion. Soft low-contrast track + brighter, hue-ramped fill.
        float barW = cw * s, barY = oy + BAR_TOP * s, barH = BAR_H * s, rr = barH * 0.5f;
        int track = Color.scaleAlpha(Color.scaleAlpha(Tokens.surface().surfaceHi(), 0.55f), alpha);   // #1 less contrast
        r.roundedRect(ox, barY, barW, barH, rr, track);
        if (frac > 0f) r.roundedRect(ox, barY, barW * frac, barH, rr, Color.scaleAlpha(hpColor(frac), alpha));
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

    /** Unscaled content width — hug (name + gap + HP), clamped so short names stay tight and long names truncate. */
    private float contentW() {
        float hpW = Ui.text().width(curNum, Weight.MEDIUM, HP_SIZE) + Ui.text().width(UNIT, Weight.MEDIUM, HP_SIZE);
        float nameW = Ui.text().width(curName, Weight.SEMIBOLD, NAME_SIZE);
        float nameMax = MAX_W - GAP - hpW;
        return Math.max(MIN_W, Math.min(nameW, nameMax) + GAP + hpW);
    }

    /** Truncate {@code name} with an ellipsis to fit {@code availW} (px, unscaled) at the name style. */
    private String fitName(String name, float availW) {
        if (Ui.text().width(name, Weight.SEMIBOLD, NAME_SIZE) <= availW) return name;
        String cut = name;
        while (cut.length() > 1 && Ui.text().width(cut + "…", Weight.SEMIBOLD, NAME_SIZE) > availW)
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
