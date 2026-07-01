package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.motion.Reveal;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Active effects: a compact column of "Name  Time" rows (no per-effect box). Pure-vector, no sprite. */
public final class EffectsElement extends HudElement {
    private static final int ROW = 18, CONTENT_W = 132;

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

    @Override public int[] contentSize(MinecraftClient mc, boolean live) {
        int n = rows(mc, live).length;
        return new int[]{ CONTENT_W, Math.max(ROW, n * ROW) };
    }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var t = ctx.text(); Typography ty = Tokens.type();
        float now = ctx.time();
        int hi = Tokens.palette().textHi(), mut = Tokens.palette().textMuted();
        float w = CONTENT_W * s;
        String[][] rows = rows(mc, live);
        // Forget fade-ins for effects no longer present, so a re-gained effect fades in fresh.
        enter.keySet().removeIf(title -> !hasRow(rows, title));
        for (int i = 0; i < rows.length; i++) {
            Reveal rev = enter.computeIfAbsent(rows[i][0],
                    k -> new Reveal(Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate(), now));
            float a = rev.progress(now);                       // eased 0->1 alpha for a soft entrance
            float ry = oy + i * ROW * s;                       // tight rows: name (white) left, time (muted) right — no box
            t.draw(rows[i][0], ox, ry, TextStyle.of(ty.label().weight(), ty.label().size() * s, Color.scaleAlpha(hi, a)));
            t.draw(rows[i][1], ox + w, ry, TextStyle.of(ty.label().weight(), ty.label().size() * s, Color.scaleAlpha(mut, a)).align(Align.RIGHT));
        }
    }

    private static boolean hasRow(String[][] rows, String title) {
        for (String[] row : rows) if (row[0].equals(title)) return true;
        return false;
    }
}
