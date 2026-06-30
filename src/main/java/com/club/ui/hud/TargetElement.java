package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.UiContext;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;

/** Entity under the crosshair: name + "<hp> HP" + a 2px HP-fraction line (the only accent). No avatar/distance (per the approved minimalist design). */
public final class TargetElement extends HudElement {
    private static final int CONTENT_W = 160, CONTENT_H = 56;
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

    @Override public int[] contentSize(MinecraftClient mc, boolean live) { return new int[]{ CONTENT_W, CONTENT_H }; }

    @Override public void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float s, boolean live) {
        var r = ctx.renderer(); var t = ctx.text(); Typography ty = Tokens.type();
        int hi = Tokens.palette().textHi(), track = Tokens.surface().surfaceHi(),
            accent = Tokens.accent().accent(), low = Tokens.palette().stateLow();
        // live raycast target (name + HP fraction) via the legacy raycaster; representative sample when none / no world
        String name = "Steve_42", sub = "18.6 HP"; float frac = 0.62f;
        if (live && mc != null && mc.world != null) {
            var le = com.club.hud.TargetHud.raycastTarget(mc, 1f);
            if (le != null) {
                name = le.getName().getString();
                if (name.length() > 18) name = name.substring(0, 17) + "…";
                float hp = le.getHealth(), max = le.getMaxHealth();
                frac = max > 0 ? Math.max(0f, Math.min(1f, hp / max)) : 0f;
                sub = (Math.abs(hp - Math.round(hp)) < 0.05f ? String.valueOf(Math.round(hp))
                        : String.format(java.util.Locale.ROOT, "%.1f", hp)) + " HP";
            }
        }
        // name — the lead (white, title)
        t.draw(name, ox, oy, TextStyle.of(ty.title().weight(), ty.title().size() * s, hi));
        // HP value — prominent: heading-size white (not a muted caption), clearly separated below the name
        t.draw(sub, ox, oy + 26 * s, TextStyle.of(ty.heading().weight(), ty.heading().size() * s, hi));
        // HP-fraction line — the single accent, thicker for emphasis, set apart from the text
        float barY = oy + 50 * s, barW = CONTENT_W * s, barH = 3 * s, rr = 1.5f * s;
        r.roundedRect(ox, barY, barW, barH, rr, track);
        if (frac > 0) r.roundedRect(ox, barY, barW * frac, barH, rr, frac < 0.30f ? low : accent);
    }
}
