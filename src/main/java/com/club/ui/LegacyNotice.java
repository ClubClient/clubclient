package com.club.ui;

import com.club.ClubMod;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;

/**
 * LEGACY-backend warning (Stage 26). MODERN starts everywhere Minecraft runs; LEGACY is a
 * parachute — deliberately unpainted, but it must be HONEST: one loud WARN in the log and a
 * visible plaque in the menu and over the HUD, so a broken resource pack / shader failure gets
 * reported instead of being mistaken for a subtle restyle.
 */
public final class LegacyNotice {
    private LegacyNotice() {}

    private static final String TEXT = "UI fallback mode — check resource packs / report";
    private static boolean logged;

    /** True while the fallback is active. Logs the WARN once, on first observation. */
    public static boolean active() {
        if (Ui.backend() != Ui.Backend.LEGACY) return false;
        if (!logged) {
            logged = true;
            ClubMod.LOGGER.warn("[Club] UI running in LEGACY fallback mode (MSDF shaders/atlas unavailable) — check resource packs / report this");
        }
        return true;
    }

    /** Centered warning plaque pinned to the top of the screen. The menu and the HUD dispatcher
     *  call this after their content; it draws nothing while MODERN is up. */
    public static void draw(UiContext ctx, float screenW) {
        if (!active()) return;
        var role = Tokens.type().label();
        float tw = ctx.text().width(TEXT, role.weight(), role.size());
        float w = tw + 24, h = role.lineHeight() + 10;
        float x = (screenW - w) / 2f, y = 6;
        int warn = Tokens.palette().stateWarn();
        ctx.renderer().roundedRect(x, y, w, h, h / 2f, Color.withAlpha(0xFF000000, 0xA0));
        ctx.renderer().border(x, y, w, h, h / 2f, 1f, Color.withAlpha(warn, 0x66));
        ctx.text().draw(TEXT, x + 12, y + 5, TextStyle.of(role.weight(), role.size(), warn));
    }
}
