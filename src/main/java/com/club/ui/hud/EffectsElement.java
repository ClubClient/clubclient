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

/** Active effects: a compact column of "Name  Time" rows (no per-effect box). Pure-vector, no sprite. */
public final class EffectsElement extends HudElement {
    private static final int ROW = 18, GAP = 16, MIN_W = 56;   // GAP = min space between the name and time columns

    // Fade-in per effect (keyed by title): a newly-gained effect eases in instead of popping. Expiring
    // effects still drop instantly — exit-fade is deferred (the expiring-first list reorders as timers tick).
    private final java.util.HashMap<String, Reveal> enter = new java.util.HashMap<>();

    public EffectsElement() { super("effects"); }

    private ClubConfig.Hud h() { return ClubConfig.get().hud; }
    @Override public int   cfgX() { return h().potionX; }
    @Override public int   cfgY() { return h().potionY; }
    @Override public void  cfgX(int v) { h().potionX = v; }
    @Override public void  cfgY(int v) { h().potionY = v; }
    @Override public float cfgScale() { return h().potionScale; }
    @Override public boolean cfgEnabled() { return h().potions; }

    /** In-world: show only when there are real effects (no sample fallback outside the editor). */
    @Override public boolean hasContent(MinecraftClient mc) {
        return !live(mc) || !com.club.hud.PotionHud.effects(mc).isEmpty();
    }

    private static final String[][] SAMPLE = {{"Speed II", "1:24"}, {"Strength I", "0:42"}};

    /** Live status effects (expiring first) via PotionHud; representative sample when no player / no effects. */
    private String[][] rows(MinecraftClient mc, boolean live) {
        if (!live) return SAMPLE;
        var fx = com.club.hud.PotionHud.effects(mc);
        if (fx.isEmpty()) return SAMPLE;
        String[][] out = new String[fx.size()][2];
        for (int i = 0; i < fx.size(); i++) {
            out[i][0] = com.club.hud.PotionHud.title(fx.get(i));
            out[i][1] = com.club.hud.PotionHud.time(fx.get(i));
        }
        return out;
    }

    /** Widest row = longest name + gap + longest time (hug the content, no fixed width). Unscaled. */
    private float measureW(String[][] rows) {
        Typography.Role r = Tokens.type().label();
        float nameW = 0, timeW = 0;
        for (String[] row : rows) {
            nameW = Math.max(nameW, Ui.text().width(row[0], Weight.SEMIBOLD, r.size()));   // name is heavier
            timeW = Math.max(timeW, Ui.text().width(row[1], r.weight(), r.size()));
        }
        return Math.max(MIN_W, nameW + GAP + timeW);
    }

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        String[][] rows = rows(mc, live);
        return new int[]{ Math.round(measureW(rows)), Math.max(ROW, rows.length * ROW) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text(); Typography ty = Tokens.type();
        float now = ctx.time();
        int hi = Tokens.palette().textHi(), mut = Tokens.palette().textMuted();
        String[][] rows = rows(mc, live);
        float w = measureW(rows) * s;   // time column right-aligns at the measured content edge
        // Forget fade-ins for effects no longer present, so a re-gained effect fades in fresh.
        enter.keySet().removeIf(title -> !hasRow(rows, title));
        for (int i = 0; i < rows.length; i++) {
            Reveal rev = enter.computeIfAbsent(rows[i][0],
                    k -> new Reveal(Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate(), now));
            float a = rev.progress(now) * alpha;               // row entrance × element appear/disappear fade
            float ry = oy + i * ROW * s;                       // tight rows: name (white SemiBold) left, time (muted) right — no box
            t.draw(rows[i][0], ox, ry, TextStyle.of(Weight.SEMIBOLD, ty.label().size() * s, Color.scaleAlpha(hi, a)).effect(HudPaint.textShadow(a)));
            t.draw(rows[i][1], ox + w, ry, TextStyle.of(ty.label().weight(), ty.label().size() * s, Color.scaleAlpha(mut, a)).align(Align.RIGHT).effect(HudPaint.textShadow(a)));
        }
    }

    private static boolean hasRow(String[][] rows, String title) {
        for (String[] row : rows) if (row[0].equals(title)) return true;
        return false;
    }
}
