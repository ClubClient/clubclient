package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.IconGlyph;
import com.club.ui.UiContext;
import com.club.ui.motion.Reveal;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;

/**
 * Active effects on the V4 "Chips" language, icon-only (Stage 14): ONE capsule for the whole
 * stack (mirrors Armor — per-effect chips read as choppy slivers), NO text at all. Each row is
 * the effect's SDF icon tinted with its association color ({@link EffectStyles}), an amplifier
 * pip column for II+ (geometry, not numerals), and a LIVE LINE underneath that drains with the
 * remaining time against the effect's TOTAL duration — the line keeps the effect's own color and
 * warms toward amber over the last ~30% (smooth, never a snap). The game doesn't store the total,
 * so it's tracked as the max duration seen per effect+amplifier (re-application resets the scale —
 * exactly what the eye expects). Vertical stack (default) or a horizontal row of cells.
 * On LEGACY the icons are skipped (lines still show) — emergency mode only.
 */
public final class EffectsElement extends HudElement {
    private static final int ICON = 16;                       // icon content box
    private static final float PAD_X = 8f, PAD_Y = 5f;        // capsule padding (mirrors Armor)
    private static final float BAR_H = 2f, BAR_GAP = 2f;      // per-row live line + gap above it
    private static final float ROW_BLOCK = ICON + BAR_GAP + BAR_H;   // icon + gap + line = 20
    private static final float ROW_GAP = 5f, CELL_GAP = 12f;  // vertical row spacing / horizontal cell spacing
    // Amplifier pips (level II and up): a tiny dot column right of the icon — geometry, not text.
    private static final float DOT = 2f, DOT_GAP = 1.5f, DOT_INSET = 2f;

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

    /** One row's data: stable key, icon, association color, amplifier pips, remaining/total fraction. */
    private record Fx(String key, IconGlyph icon, int color, int dots, float frac) {}

    private static final Fx[] SAMPLE = {
        sample("speed", 1, 0.47f), sample("strength", 0, 0.23f),
    };
    private static Fx sample(String id, int amp, float frac) {
        EffectStyles.Style st = EffectStyles.byId(id);
        return new Fx("sample:" + id, st.icon(), st.color(), pips(amp), frac);
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
            // key includes the amplifier: Speed I → Speed II is a NEW row (fresh drain scale + fade)
            String key = e.getEffectType().getKey().map(k -> k.getValue().toString()).orElse("?")
                    + "#" + e.getAmplifier();
            float frac;
            if (e.isInfinite()) {
                frac = 1f;
            } else {
                int dur = e.getDuration();
                int max = maxSeen.merge(key, dur, Math::max);   // re-application (dur > seen) resets the scale
                frac = max > 0 ? (float) dur / max : 0f;
            }
            out[i] = new Fx(key, st.icon(), st.color(), pips(e.getAmplifier()), frac);
        }
        // Forget scales/fades for effects no longer present, so a re-gained effect starts fresh.
        enter.keySet().removeIf(k -> !hasRow(out, k));
        maxSeen.keySet().removeIf(k -> !hasRow(out, k));
        lastLive = out;
        return out;
    }

    /** Level I is unmarked; II+ shows its level as pips (capped at 4 — command-level stays sane). */
    private static int pips(int amp) { return amp >= 1 ? Math.min(amp + 1, 4) : 0; }

    /** Inner cell width: icon + pip column when any visible effect is II+ (uniform across rows). */
    private int cellW(Fx[] rows) {
        for (Fx r : rows) if (r.dots() > 0) return Math.round(ICON + DOT_INSET + DOT);
        return ICON;
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
        float now = ctx.time();
        Fx[] rows = rows(mc, live);
        if (rows.length == 0) return;
        boolean horizontal = h().potionHorizontal;
        int cell = cellW(rows);

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

            row.icon().draw(ctx, cx, cy, ICON * s, Color.scaleAlpha(row.color(), a));
            if (row.dots() > 0) {
                float dh = row.dots() * DOT + (row.dots() - 1) * DOT_GAP;
                float dx = cx + (ICON + DOT_INSET) * s;
                float dy = cy + (ICON - dh) * 0.5f * s;        // pip column vertically centered on the icon
                int dot = Color.scaleAlpha(row.color(), a);
                for (int k = 0; k < row.dots(); k++)
                    ctx.renderer().roundedRect(dx, dy + k * (DOT + DOT_GAP) * s, DOT * s, DOT * s, DOT * 0.5f * s, dot);
            }
            HudPaint.rowBar(ctx, cx, cy + (ICON + BAR_GAP) * s, cell * s, BAR_H,
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
