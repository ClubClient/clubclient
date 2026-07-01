package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.motion.ValueTween;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Entity under the crosshair: name + "<hp> HP" + a 2px HP-fraction line (the only accent). No avatar/distance (per the approved minimalist design). */
public final class TargetElement extends HudElement {
    private static final int CONTENT_H = 40;
    private static final float MIN_W = 82f;

    // HP-bar fraction easing: a short tween so the bar glides on damage/heal but never trails real HP by
    // more than the fast duration; snaps when the target changes so it re-bases.
    private final ValueTween hpFrac =
            new ValueTween(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
    // Current shown data — cached so a lost target (raycast null during the fade-out / on death) keeps the
    // LAST real name/HP instead of leaking the editor sample ("Steve_42"). Sample only ever shows in the editor.
    private String curName = "Steve_42", curSub = "18.6 HP";
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
        var r = ctx.renderer(); var t = ctx.text(); Typography ty = Tokens.type();
        float now = ctx.time();
        int track = Tokens.surface().surfaceHi(), accent = Tokens.accent().accent(), low = Tokens.palette().stateLow();
        // cur* was refreshed in contentSize() this frame. Snap the bar when the target changes; else ease it.
        if (!curName.equals(tweenName)) { hpFrac.snap(curFrac, now); tweenName = curName; } else hpFrac.set(curFrac, now);
        float shownFrac = hpFrac.get(now);
        int hiA  = Color.scaleAlpha(Tokens.palette().textHi(), alpha);      // name — white (primary)
        int subA = Color.scaleAlpha(Tokens.palette().textMuted(), alpha);   // "<hp> HP" — muted (secondary)

        t.draw(curName, ox, oy, TextStyle.of(ty.title().weight(), ty.title().size() * s, hiA).effect(HudPaint.textShadow(alpha)));
        t.draw(curSub, ox, oy + 19 * s, TextStyle.of(ty.heading().weight(), ty.heading().size() * s, subA).effect(HudPaint.textShadow(alpha)));
        // HP-fraction line — the single accent, spanning the measured content width
        float barW = contentW() * s, barY = oy + 35 * s, barH = 3 * s, rr = 1.5f * s;
        r.roundedRect(ox, barY, barW, barH, rr, Color.scaleAlpha(track, alpha));
        if (shownFrac > 0) {
            float ct = Math.max(0f, Math.min(1f, (shownFrac - 0.24f) / 0.12f));   // smooth low->accent crossing
            r.roundedRect(ox, barY, barW * shownFrac, barH, rr, Color.scaleAlpha(Color.lerp(low, accent, ct), alpha));
        }
    }

    /** Unscaled content width — hug the wider of name / value, with a minimum so the bar always reads. */
    private float contentW() {
        Typography ty = Tokens.type();
        float nameW = Ui.text().width(curName, ty.title().weight(), ty.title().size());
        float subW  = Ui.text().width(curSub, ty.heading().weight(), ty.heading().size());
        return Math.max(MIN_W, Math.max(nameW, subW));
    }

    /** Refresh the cached target data. A lost target keeps the last real values (so the fade-out / death frame
     *  shows the real name, not the sample); the "Steve_42" sample is only ever used in the editor (live=false). */
    private void resolve(MinecraftClient mc, boolean live) {
        if (!live) { curName = "Steve_42"; curSub = "18.6 HP"; curFrac = 0.62f; return; }
        if (mc == null || mc.world == null) return;                    // keep last known
        var le = com.club.hud.TargetHud.raycastTarget(mc, 1f);
        if (le == null) return;                                        // no target now — keep last, never the sample
        String n = le.getName().getString();
        if (n.length() > 18) n = n.substring(0, 17) + "…";
        float hp = le.getHealth(), max = le.getMaxHealth();
        curFrac = max > 0 ? Math.max(0f, Math.min(1f, hp / max)) : 0f;
        curSub = (Math.abs(hp - Math.round(hp)) < 0.05f ? String.valueOf(Math.round(hp))
                : String.format(java.util.Locale.ROOT, "%.1f", hp)) + " HP";
        curName = n;
    }
}
