package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.motion.Reveal;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;

/**
 * Active effects on the V4 "Chips" language (Stage 13): each effect is its own capsule
 * ["Name  Time"], and the capsule's LIVE EDGE drains with the remaining time — a glance says
 * "what's about to run out" without reading a single digit; the countdown text stays exact.
 * Edge colour: neutral steel-grey, warming toward amber as the effect nears its end (smooth lerp,
 * never a snap). Vertical stack (default) or a row of capsules ({@code potionHorizontal}).
 * The effect's TOTAL duration isn't stored by the game, so it's tracked as the max duration seen
 * per title (a re-application resets the scale — exactly what the eye expects).
 */
public final class EffectsElement extends HudElement {
    private static final float CHIP_H = 24f, CHIP_GAP = 4f;   // capsule height / stack gap (unscaled)
    private static final float PAD_X = 8f, PAD_TOP = 3f;      // shared row metrics (13.4): PAD_X 8 everywhere
    private static final float BAR_H = 2f;                    // live edge height
    private static final int   GAP = 12, MIN_TEXT_W = 56;     // name ↔ time min gap; min text width
    private static final int   NEUTRAL = 0xFF4A5A75;          // calm steel-grey edge (plenty of time left)

    // Fade-in per effect (keyed by title): a newly-gained effect eases in instead of popping. Expiring
    // effects still drop instantly — exit-fade is deferred (the expiring-first list reorders as timers tick).
    private final HashMap<String, Reveal> enter = new HashMap<>();
    // Max duration seen per title = the drain scale's denominator (reset on re-application).
    private final HashMap<String, Integer> maxSeen = new HashMap<>();

    public EffectsElement() { super("effects"); }
    @Override public String displayName() { return "Effects"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().potionX; }
    @Override public int   cfgY() { return h().potionY; }
    @Override public void  cfgX(int v) { h().potionX = v; }
    @Override public void  cfgY(int v) { h().potionY = v; }
    @Override public float cfgScale() { return h().potionScale; }
    @Override public boolean cfgEnabled() { return h().potions; }

    // V4: capsules are drawn per effect in paint(); no shared outer panel.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }
    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) { }

    /** In-world: show only when there are real effects (no sample fallback outside the editor). */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || !com.club.hud.PotionHud.effects(mc).isEmpty();
    }

    /** One capsule's data: title, countdown text, remaining/total fraction. */
    private record Fx(String title, String time, float frac) {}

    private static final Fx[] SAMPLE = {
        new Fx("Speed II", "1:24", 0.47f), new Fx("Strength I", "0:42", 0.23f),
    };

    /** Live effects (expiring first) with drain fractions; representative sample otherwise. */
    private Fx[] rows(MinecraftClient mc, boolean live) {
        if (!live) return SAMPLE;
        var fx = com.club.hud.PotionHud.effects(mc);
        if (fx.isEmpty()) return SAMPLE;
        Fx[] out = new Fx[fx.size()];
        for (int i = 0; i < fx.size(); i++) {
            var e = fx.get(i);
            String title = com.club.hud.PotionHud.title(e);
            float frac;
            if (e.isInfinite()) {
                frac = 1f;
            } else {
                int dur = e.getDuration();
                int max = maxSeen.merge(title, dur, Math::max);   // re-application (dur > seen) resets the scale
                frac = max > 0 ? (float) dur / max : 0f;
            }
            out[i] = new Fx(title, com.club.hud.PotionHud.time(e), frac);
        }
        return out;
    }

    /** Uniform capsule width: pad + (longest name + gap + longest time) + pad. Unscaled. */
    private float chipW(Fx[] rows) {
        Typography.Role r = Tokens.type().label();
        float nameW = 0, timeW = 0;
        for (Fx row : rows) {
            nameW = Math.max(nameW, Ui.text().width(row.title(), Weight.SEMIBOLD, r.size()));   // name is heavier
            timeW = Math.max(timeW, Ui.text().width(row.time(), r.weight(), r.size()));
        }
        return 2 * PAD_X + Math.max(MIN_TEXT_W, nameW + GAP + timeW);
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        Fx[] rows = rows(mc, live);
        int cw = Math.round(chipW(rows));
        if (h().potionHorizontal)
            return new int[]{ Math.round(rows.length * cw + (rows.length - 1) * CHIP_GAP), Math.round(CHIP_H) };
        return new int[]{ cw, Math.round(rows.length * CHIP_H + (rows.length - 1) * CHIP_GAP) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text(); Typography ty = Tokens.type();
        float now = ctx.time();
        int hi = Tokens.palette().textHi(), mut = Tokens.palette().textMuted();
        Fx[] rows = rows(mc, live);
        boolean horizontal = h().potionHorizontal;
        float cw = chipW(rows);
        float textInset = (CHIP_H - BAR_H - HudPaint.EDGE_BOT - PAD_TOP - ty.label().lineHeight()) * 0.5f + PAD_TOP;
        // Forget scales/fades for effects no longer present, so a re-gained effect starts fresh.
        enter.keySet().removeIf(title -> !hasRow(rows, title));
        maxSeen.keySet().removeIf(title -> !hasRow(rows, title));
        for (int i = 0; i < rows.length; i++) {
            Fx row = rows[i];
            Reveal rev = enter.computeIfAbsent(row.title(),
                    k -> new Reveal(Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate(), now));
            float a = rev.progress(now) * alpha;               // row entrance × element appear/disappear fade
            float cx = horizontal ? ox + i * (cw + CHIP_GAP) * s : ox;
            float cy = horizontal ? oy : oy + i * (CHIP_H + CHIP_GAP) * s;

            HudPaint.chip(ctx, cx, cy, cw * s, CHIP_H * s, HudPaint.CHIP_RAD * s, a);
            HudPaint.edgeBar(ctx, cx, cy, cw * s, CHIP_H * s, BAR_H, row.frac(), edgeColor(row.frac()), s, a);

            float ry = cy + textInset * s;
            t.draw(row.title(), cx + PAD_X * s, ry,
                    TextStyle.of(Weight.SEMIBOLD, ty.label().size() * s, Color.scaleAlpha(hi, a)).effect(HudPaint.textShadow(a)));
            t.draw(row.time(), cx + (cw - PAD_X) * s, ry,
                    TextStyle.of(ty.label().weight(), ty.label().size() * s, Color.scaleAlpha(mut, a))
                            .align(Align.RIGHT).effect(HudPaint.textShadow(a)));
        }
    }

    /** Calm steel-grey while time remains; warms toward amber over the last ~30% (smooth, no snap). */
    private static int edgeColor(float f) {
        if (f >= 0.30f) return NEUTRAL;
        return Color.lerp(Tokens.palette().stateWarn(), NEUTRAL, Math.max(0f, f) / 0.30f);
    }

    private static boolean hasRow(Fx[] rows, String title) {
        for (Fx row : rows) if (row.title().equals(title)) return true;
        return false;
    }
}
