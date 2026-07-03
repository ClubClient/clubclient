package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.IconGlyph;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.motion.Reveal;
import com.club.ui.text.TextStyle;
import com.club.ui.text.Weight;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;

import java.util.HashMap;

/**
 * Active effects on the V4 "Chips" language (Stage 14): ONE capsule for the whole stack (mirrors
 * Armor — per-effect chips read as choppy slivers). Each row is a compact cluster
 * [icon&nbsp;&nbsp;II&nbsp;·&nbsp;1:24]: the effect's SDF icon tinted with its association color
 * ({@link EffectStyles}), the roman level for II+ (muted — a quiet qualifier, level I is
 * unmarked), a faint dot separator, and the exact countdown (tabular, textHi, right-aligned down
 * the stack like Armor's values — no effect names). The row's LIVE LINE underneath drains with
 * the remaining time against the effect's TOTAL duration — it keeps the effect's own color and
 * warms toward amber over the last ~30% (smooth, never a snap). The game doesn't store the total,
 * so it's tracked as the max duration seen per effect+amplifier (re-application resets the scale —
 * exactly what the eye expects). Vertical stack (default) or a horizontal row of cells.
 * On LEGACY the icons are skipped (times/lines still show) — emergency mode only.
 */
public final class EffectsElement extends HudElement {
    private static final int ICON = 16, GAP = 5;              // icon content box; icon ↔ text gap
    private static final float PAD_X = 8f, PAD_Y = 5f;        // capsule padding (mirrors Armor)
    private static final float BAR_H = 2f, BAR_GAP = 2f;      // per-row live line + gap above it
    private static final float ROW_BLOCK = ICON + BAR_GAP + BAR_H;   // icon + gap + line = 20
    private static final float ROW_GAP = 5f, CELL_GAP = 12f;  // vertical row spacing / horizontal cell spacing
    // The separator between level and time ("какая-нибудь точка") — a faint middle dot, drawn as geometry.
    private static final float DOT = 2f, DOT_PAD = 2.5f;

    // Fade-in per effect row: a newly-gained effect eases in instead of popping. Expiring effects
    // still drop instantly — exit-fade is deferred (the expiring-first list reorders as timers tick).
    private final HashMap<String, Reveal> enter = new HashMap<>();
    // Max duration seen per effect+amplifier = the drain scale's denominator (reset on re-application).
    private final HashMap<String, Integer> maxSeen = new HashMap<>();
    // Snapshot of the last non-empty stack: when the final effect expires the canvas fades the element
    // out over ~200ms — during that fade we draw this frozen frame (not the editor sample, not nothing).
    private Fx[] lastLive;
    private boolean frozen;   // set by rows() while the frozen frame is showing (rows skip entrance fades)

    public EffectsElement() { super("effects"); }
    @Override public String displayName() { return "Effects"; }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().potionX; }
    @Override public int   cfgY() { return h().potionY; }
    @Override public void  cfgX(int v) { h().potionX = v; }
    @Override public void  cfgY(int v) { h().potionY = v; }
    @Override public float cfgScale() { return h().potionScale; }
    @Override public boolean cfgEnabled() { return h().potions; }

    // V4: the capsule is drawn in paint(); no shared outer panel.
    @Override protected float panelPadX() { return 0f; }
    @Override protected float panelPadY() { return 0f; }
    @Override protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) { }

    /** In-world: show only when there are real effects (no sample fallback outside the editor). */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || !com.club.hud.PotionHud.effects(mc).isEmpty();
    }

    /** One row's data: stable key, sprite texture, fallback glyph, association color, level, countdown, fraction. */
    private record Fx(String key, Identifier tex, IconGlyph icon, int color, String amp, String time, float frac) {}

    private static final Fx[] SAMPLE = {
        sample("speed", 1, "1:24", 0.47f), sample("strength", 0, "0:42", 0.23f),
    };
    private static Fx sample(String id, int amp, String time, float frac) {
        EffectStyles.Style st = EffectStyles.byId(id);
        return new Fx("sample:" + id, Identifier.of("minecraft", "textures/mob_effect/" + id + ".png"),
                st.icon(), st.color(), roman(amp), time, frac);
    }

    /** Live effects (expiring first) with drain fractions; representative sample otherwise. */
    private Fx[] rows(MinecraftClient mc, boolean live) {
        frozen = false;
        if (!live) return SAMPLE;
        var fx = com.club.hud.PotionHud.effects(mc);
        if (fx.isEmpty()) {
            // exit fade: draw the frozen last frame; scales + entrance fades die with the stack
            enter.clear(); maxSeen.clear();
            frozen = true;
            return lastLive != null ? lastLive : SAMPLE;
        }
        Fx[] out = new Fx[fx.size()];
        for (int i = 0; i < fx.size(); i++) {
            var e = fx.get(i);
            EffectStyles.Style st = EffectStyles.of(e);
            Identifier eid = e.getEffectType().getKey().map(k -> k.getValue()).orElse(null);
            Identifier tex = eid == null ? null
                    : Identifier.of(eid.getNamespace(), "textures/mob_effect/" + eid.getPath() + ".png");
            // key includes the amplifier: Speed I → Speed II is a NEW row (fresh drain scale + fade)
            String key = (eid == null ? "?" : eid.toString()) + "#" + e.getAmplifier();
            float frac;
            if (e.isInfinite()) {
                frac = 1f;
            } else {
                int dur = e.getDuration();
                int max = maxSeen.merge(key, dur, Math::max);   // re-application (dur > seen) resets the scale
                frac = max > 0 ? (float) dur / max : 0f;
            }
            out[i] = new Fx(key, tex, st.icon(), st.color(), roman(e.getAmplifier()), time(e), frac);
        }
        // Forget scales/fades for effects no longer present, so a re-gained effect starts fresh.
        enter.keySet().removeIf(k -> !hasRow(out, k));
        maxSeen.keySet().removeIf(k -> !hasRow(out, k));
        lastLive = out;
        return out;
    }

    /** Level I is unmarked; II+ shows its roman level — the effect's strength, quiet. */
    private static String roman(int amp) {
        if (amp < 1) return "";
        return switch (amp + 1) {
            case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(amp + 1);
        };
    }

    private static String time(StatusEffectInstance e) {
        if (e.isInfinite()) return "—";   // Onest has no ∞ glyph; em dash reads as "permanent"
        int t = e.getDuration() / 20, sec = t % 60;
        return (t / 60) + ":" + (sec < 10 ? "0" : "") + sec;
    }

    /** Inner cell width: icon + gap + widest [level · time] cluster (times right-align down the stack). */
    private int cellW(Fx[] rows) {
        Typography ty = Tokens.type();
        float w = 0;
        for (Fx r : rows) {
            float t = HudText.width(r.time(), Weight.SEMIBOLD, ty.body().size());
            if (!r.amp().isEmpty())
                t += Ui.text().width(r.amp(), Weight.SEMIBOLD, ty.label().size()) + 2 * DOT_PAD + DOT;
            w = Math.max(w, t);
        }
        return Math.round(ICON + GAP + w);
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        Fx[] rows = rows(mc, live);
        int n = rows.length;
        if (n == 0) return new int[]{0, 0};
        int cell = cellW(rows);
        if (h().potionHorizontal)
            return new int[]{ Math.round(2 * PAD_X + n * cell + (n - 1) * CELL_GAP),
                              Math.round(2 * PAD_Y + ROW_BLOCK) };
        return new int[]{ Math.round(2 * PAD_X + cell),
                          Math.round(2 * PAD_Y + n * ROW_BLOCK + (n - 1) * ROW_GAP) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        Typography ty = Tokens.type();
        float now = ctx.time();
        Fx[] rows = rows(mc, live);
        if (rows.length == 0) return;
        boolean horizontal = h().potionHorizontal;
        int cell = cellW(rows);
        float base = ty.body().size(), ampSize = ty.label().size();
        float lh = ty.body().lineHeight();
        // text block centered on the icon box, +1px: digits have no descender, so lineHeight
        // centering leaves them optically high against the icon (same nudge as Armor's values)
        float textTop = (ICON - lh) * 0.5f + 1f;
        float ampDy = Ui.text().ascent(Weight.SEMIBOLD, base) - Ui.text().ascent(Weight.SEMIBOLD, ampSize);
        // the separator dot sits at the digits' optical middle (~half x-height above the baseline)
        float dotY = textTop + Ui.text().ascent(Weight.SEMIBOLD, base) - base * 0.28f - DOT * 0.5f;

        // ONE capsule for the whole stack — the rows inside carry their own live lines.
        int[] cs = contentSize(mc, live);
        HudPaint.chip(ctx, ox, oy, cs[0] * s, cs[1] * s, HudPaint.CHIP_RAD * s, alpha);

        for (int i = 0; i < rows.length; i++) {
            Fx row = rows[i];
            float ra = 1f;                                     // frozen exit frame draws rows at full alpha
            if (!frozen) {
                Reveal rev = enter.computeIfAbsent(row.key(),
                        k -> new Reveal(Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate(), now));
                ra = rev.progress(now);
            }
            float a = ra * alpha;                              // row entrance × element appear/disappear fade
            float cx = ox + (PAD_X + (horizontal ? i * (cell + CELL_GAP) : 0)) * s;
            float cy = oy + (PAD_Y + (horizontal ? 0 : i * (ROW_BLOCK + ROW_GAP))) * s;

            // duotone vanilla effect sprite (18px art, centered on the 16px box); SDF glyph fallback
            if (row.tex() == null
                    || !com.club.hud.PixelIcons.draw(row.tex(), cx - s, cy - s, (ICON + 2) * s, 18, row.color(), a))
                row.icon().draw(ctx, cx, cy, ICON * s, Color.scaleAlpha(row.color(), a));
            // countdown right-aligned in the shared column (tabular — a ticking second never jitters)
            float timeW = HudText.width(row.time(), Weight.SEMIBOLD, base);
            float timeX = cell - timeW;
            HudText.draw(ctx, row.time(), cx + timeX * s, cy + textTop * s,
                    TextStyle.of(Weight.SEMIBOLD, base * s, Color.scaleAlpha(Tokens.palette().textHi(), a))
                            .effect(HudPaint.textShadow(a)), base, s);
            if (!row.amp().isEmpty()) {
                // [II · 1:24] — the level hugs its own time, separated by the faint dot. It speaks
                // in the effect's association color (it IS the effect's identity — "Speed II"),
                // muted gray here read as dark and tasteless (owner).
                float ampW = Ui.text().width(row.amp(), Weight.SEMIBOLD, ampSize);
                float dotX = timeX - DOT_PAD - DOT;
                ctx.renderer().roundedRect(cx + dotX * s, cy + dotY * s, DOT * s, DOT * s, DOT * 0.5f * s,
                        Color.scaleAlpha(Tokens.palette().textFaint(), a));
                ctx.text().draw(row.amp(), cx + (dotX - DOT_PAD - ampW) * s, cy + (textTop + ampDy) * s,
                        TextStyle.of(Weight.SEMIBOLD, ampSize * s, Color.scaleAlpha(row.color(), a))
                                .effect(HudPaint.textShadow(a)));
            }
            // the effect's live line — UNDER THE ICON only (its gauge, echoing the menu card stripe;
            // spanning the whole cell read as an element divider — owner)
            HudPaint.rowBar(ctx, cx, cy + (ICON + BAR_GAP) * s, ICON * s, BAR_H,
                    row.frac(), timeColor(row.frac(), row.color()), s, a);
        }
    }

    /** The line keeps the effect's own color while time remains; warms toward amber over the last ~30%. */
    private static int timeColor(float f, int assoc) {
        if (f >= 0.30f) return assoc;
        return Color.lerp(Tokens.palette().stateWarn(), assoc, Math.max(0f, f) / 0.30f);
    }

    private static boolean hasRow(Fx[] rows, String key) {
        for (Fx row : rows) if (row.key().equals(key)) return true;
        return false;
    }
}
