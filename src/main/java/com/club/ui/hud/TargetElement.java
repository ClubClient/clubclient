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
 * the HP bar. <b>HP-first hierarchy</b> — the health number (18 SemiBold since Stage 19, bright) leads, the name
 * follows quieter (13 Medium, muted, truncated with "…" at a FIXED width so the chip never
 * stretches for long names). In PvP the eye catches the number and the live edge; the name is
 * secondary. The bottom edge is a 6px GAUGE (item 11) whose fill ramps GREEN → YELLOW → RED as HP drains
 * (smooth lerp, never a hard switch) — the universal health language, spoken in the palette's OWN state
 * tokens, so the bar informs instead of merely backing the type. Digits are never coloured (state = the
 * edge); that law is also what keeps the chip safe for red-green colour blindness — the NUMBER carries the
 * value, the hue is only an additive cue.
 */
public final class TargetElement extends HudElement {
    // v0.1.5 (owner: "таргет слишком длинный… выглядит огромным"): the whole chip slimmed ~17% — every
    // dimension scaled together so the anatomy and the HP-first hierarchy are unchanged, it just reads
    // smaller. HP 18→15 stays the loudest text (name 11, unit 10); height and the name field came down with
    // it so the capsule is shorter AND less long. Stage-19 note kept for history: HP had been 16→18 for
    // "more presence"; the owner has now asked for the opposite.
    private static final float HP_SIZE = 15f, UNIT_SIZE = 10f, NAME_SIZE = 11f;
    private static final int   GAP = 8;             // HP-group ↔ name gap (unscaled)
    private static final float PAD_X = 8f, PAD_TOP = 5f;
    // Item 11 (owner: the bar must read as a MEANS OF INFORMATION, not as a backing plate). 4 → 6px: the
    // next step on the HUD's own 2px bar grid (Armor/Effects row lines are 2px; this edge was 4). 8px — the
    // doubling, spacing.sm — was rejected: at 8 the bar stops being an edge and becomes a second surface
    // competing with the capsule, and the owner asked for "чуть" (slightly) wider. At 6 the Target still owns
    // the loudest bar in the HUD (3× a row line), which is the hero rank HUD-LANGUAGE §1/§2 grants it.
    private static final float BAR_H = 5f;          // live edge height — the HP gauge (slimmed with the chip)
    // ...and the chip absorbs exactly what the bar took, DOWNWARD, so no type moves:
    //     bar top = CONTENT_H - BAR_H - EDGE_BOT   →   old: 34-4-2 = 28    new: 36-6-2 = 28   (identical)
    // So the text band is untouched and the HP number keeps its exact position, size and air — the gauge grew
    // into the chip's own bottom margin, never into the type. 36 also lands the chip on the 4px spacing grid
    // for the first time (34 is not divisible by 4) and makes the height an exact 4 × CHIP_RAD.
    private static final int   CONTENT_H = 30;      // full chip height (text band + edge zone) — slimmed from 36
    private static final float NAME_MAX_W = 62f;    // FIXED name field — longer names ellipsize here (was 80)
    private static final float MIN_W = 76f;         // was 92
    private static final String UNIT = " HP";

    // HP tone C: number a touch brighter than the name-support tone, the "HP" unit dimmer — a micro-hierarchy
    // inside the value so it reads dearer without a third text colour on the name. These are NEVER ramped:
    // the digits stay neutral at every HP (state lives on the edge), and that law is precisely what makes a
    // green/red gauge safe for the ~8% of men with red-green colour blindness — the number carries the value.
    private static final int HP_NUM  = 0xFFEAEDF2;
    private static final int HP_UNIT = 0xFF7E8696;
    // The steel-blue → purple → orange ramp (Stage 13, HUD-LANGUAGE §8) is RETIRED by item 11 — the owner
    // wants the universal health language. Its colours were local one-offs; the replacement introduces no new
    // hex at all: it uses the palette's own state family, the same three ArmorElement.stateColor() already
    // ramps durability through, so the HUD keeps ONE green, ONE yellow, ONE red. See hpColor().

    // HP-bar fraction easing: a short tween so the bar glides on damage/heal but never trails real HP by more
    // than the fast duration; snaps when the target changes so it re-bases.
    private final ValueTween hpFrac =
            new ValueTween(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
    // Current shown data — cached so a lost target (raycast null during the fade-out / on death) keeps the LAST
    // real name/HP instead of leaking the editor sample. Sample ("Steve_42") only ever shows in the editor.
    private String curName = "Steve_42", curNum = "18";
    private float curFrac = 0.62f;
    // Target identity = entity ID, not the display name (Stage 23): two zombies both read "Zombie", but
    // switching between them must still snap the HP tween and replay the acquire-pop.
    private static final int NO_ID = Integer.MIN_VALUE, SAMPLE_ID = -1;
    private int curId = SAMPLE_ID;
    private int tweenId = NO_ID;   // the target id the hp tween is based on (snap on change)
    // Scale-pop on target acquire/change: a quick 0.955 → 1.0 ease so a new target "lands" instead of popping in.
    private static final float POP_FROM = 0.955f;
    private int popId = NO_ID;     // last target id we popped for
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
        if (curId != popId) { popId = curId; popStart = now; }   // acquired / changed → play
        if (popStart < 0f) return 1f;
        float t = (now - popStart) / Tokens.motion().durations().fast();
        if (t >= 1f) return 1f;
        return POP_FROM + (1f - POP_FROM) * Tokens.motion().easings().decelerate().apply(Math.max(0f, t));
    }

    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) {
        // no-op: the V4 capsule + live edge are drawn in paint() (needs the eased HP fraction)
    }

    // Auto-position: beside the crosshair — which is the centre of the CLUB canvas now, not of Minecraft's
    // GUI-scaled screen (Stage 63). Both rectangles cover the same monitor, so the centre agrees; the units
    // do not, and mixing them would offset the chip by half its own width at any scale but 2.
    @Override public int autoX(MinecraftClient mc) { return mc != null ? com.club.ui.ClubCanvas.widthI(mc) / 2 + 16 : -1; }
    @Override public int autoY(MinecraftClient mc) { return mc != null ? com.club.ui.ClubCanvas.heightI() / 2 - CONTENT_H / 2 : -1; }

    /** In-world: show only when actually aiming at a living entity (no sample fallback outside the editor).
     *  Reads the frame's cached target (resolved once per frame by HudManager) — no extra raycast here. */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || mc.world == null || com.club.hud.TargetHud.current() != null;
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        resolve(mc, live);   // refresh cached data once per frame (called before paint via layoutFromConfig)
        return new int[]{ Math.round(contentW()), CONTENT_H };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text();
        float now = ctx.time();
        // cur* refreshed in contentSize() this frame. Snap the edge when the target changes; else ease it.
        if (curId != tweenId) { hpFrac.snap(curFrac, now); tweenId = curId; } else hpFrac.set(curFrac, now);
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

    /** Item 11 — the gauge's colour: the universal health language, GREEN (healthy) → YELLOW → RED (dying).
     *
     *  <p><b>Hues.</b> The palette's state family, verbatim: stateGood #2ECC71, stateWarn #E3C66A, stateLow
     *  #E06B6B. No new colour is introduced. ArmorElement.stateColor() already ramps durability through these
     *  same three, so a green Target bar is not a foreign import — it is the Target finally speaking the
     *  green/yellow/red its sibling element already spoke, and the HUD still holds exactly one green.
     *
     *  <p><b>Anchors.</b> Green while f ≥ 0.60; pure yellow at 0.30; pure red at 0 (halving: 30 = 60/2).
     *
     *  <p>The 30% figure is NOT invented here and it is NOT preserved from the code either — the code and its
     *  own source of truth disagreed, and this aligns them. docs/HUD-LANGUAGE.md has said "&lt;30% → the bar
     *  lerps to stateLow" since Stage 19; the shipped {@code hpColor} lerped from <b>20%</b>. Nobody noticed
     *  because the old ramp bottomed out in a red nobody read as red anyway (it arrived via ORANGE, from
     *  PURPLE, from STEEL). The doc wins: it is the etalon, and a HUD whose code quietly contradicts its own
     *  specification is how a design language rots.
     *
     *  <p>The green plateau earns its keep: with a lerp straight from 1.0 the bar would tint on the first
     *  scratch, and colour that moves when nothing important happened is noise. The first hue movement should
     *  MEAN something.
     *
     *  <p><b>Interpolated, not stepped</b> — and deliberately unlike Armor, which plateaus. The bar's LENGTH
     *  already glides (the hpFrac tween); a hue that snapped while the length glided would read as a glitch,
     *  not as a threshold. Armor is a glance-and-zone indicator, so steps suit it; Target is the combat focus
     *  and the player tracks the drain in real time, so it stays continuous — role over uniformity
     *  (HUD-LANGUAGE §0). The discrete, glanceable channel here is the NUMBER ("20 HP"), not the hue; that is
     *  also the channel a red-green colour-blind player reads, which is why the ramp may be smooth at all. */
    private static int hpColor(float f) {
        int green  = Tokens.palette().stateGood();
        int yellow = Tokens.palette().stateWarn();
        int red    = Tokens.palette().stateLow();
        if (f >= 0.60f) return green;
        if (f >= 0.30f) return Color.lerp(yellow, green,  (f - 0.30f) / 0.30f);
        return Color.lerp(red, yellow, Math.max(0f, f) / 0.30f);
    }

    /** Unscaled chip width — hug (pad + HP group + gap + name), the name capped at its FIXED field. */
    private float contentW() {
        float hpW = Ui.text().width(curNum, Weight.SEMIBOLD, HP_SIZE) + Ui.text().width(UNIT, Weight.MEDIUM, UNIT_SIZE);
        float nameW = Math.min(Ui.text().width(curName, Weight.MEDIUM, NAME_SIZE), NAME_MAX_W);
        return Math.max(MIN_W, 2 * PAD_X + hpW + GAP + nameW);
    }

    // fitName cache (Stage 32): the truncation loop measures strings — per-frame it churned on long
    // names. The target name changes rarely; re-fit only when the input differs from the last one.
    private String fitIn, fitOut;

    /** Truncate {@code name} with an ellipsis to fit {@code availW} (px, unscaled) at the name style. */
    private String fitName(String name, float availW) {
        if (name.equals(fitIn)) return fitOut;
        String out;
        if (Ui.text().width(name, Weight.MEDIUM, NAME_SIZE) <= availW) out = name;
        else {
            String cut = name;
            while (cut.length() > 1 && Ui.text().width(cut + "…", Weight.MEDIUM, NAME_SIZE) > availW)
                cut = cut.substring(0, cut.length() - 1);
            out = cut + "…";
        }
        fitIn = name; fitOut = out;
        return out;
    }

    /** Refresh the cached target data. A lost target keeps the last real values (so the fade-out / death frame
     *  shows the real name, not the sample); the sample is only ever used in the editor (live=false). */
    private void resolve(MinecraftClient mc, boolean live) {
        if (!live) { curName = "Steve_42"; curNum = "18"; curFrac = 0.62f; curId = SAMPLE_ID; return; }
        if (mc == null || mc.world == null) return;                    // keep last known
        var le = com.club.hud.TargetHud.current();                     // frame cache — resolved once in HudManager
        if (le == null) return;                                        // no target now — keep last, never the sample
        curId = le.getId();
        curName = le.getName().getString();
        float hp = le.getHealth(), max = le.getMaxHealth();
        curFrac = max > 0 ? Math.max(0f, Math.min(1f, hp / max)) : 0f;
        curNum = Math.abs(hp - Math.round(hp)) < 0.05f ? String.valueOf(Math.round(hp))
                : String.format(java.util.Locale.ROOT, "%.1f", hp);
    }
}
