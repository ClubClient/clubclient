package com.club.hud;

import com.club.config.ClubConfig;
import com.club.ui.ClubCanvas;
import net.minecraft.client.MinecraftClient;

/**
 * Moves a player's saved HUD positions into the Club canvas, once (Stage 63).
 *
 * <p>Until now the HUD was laid out in Minecraft's GUI-scaled units, and its x/y were saved as absolute
 * pixels of that space — a space whose size IS the player's GUI Scale setting. So the numbers in the config
 * only ever meant anything together with the scale that was active when they were written. Now the HUD lives
 * on the {@link ClubCanvas} (a fixed 540 units tall, whatever the video settings say), and the same numbers
 * mean something different: a chip saved at y=120 under GUI Scale 4 (a 270-unit-tall screen — nearly halfway
 * down) would reappear near the top of a 540-unit canvas.</p>
 *
 * <p>So they are converted, by the ratio between the two spaces, using the scale the player last had — which
 * is the one they are looking at right now. This is deliberately deferred to the first HUD frame rather than
 * done in {@code ClubConfig.migrate()}: at config-load time the window does not exist yet, and its scale
 * factor is exactly what the conversion depends on.</p>
 *
 * <p>Auto positions (-1) are left alone: they are not coordinates, they are "decide for me".</p>
 */
public final class HudSpace {
    private HudSpace() {}

    /** Club canvas units. */
    private static final int CLUB_UNITS = 1;

    private static boolean done;

    /** First HUD frame: convert the saved coordinates if they were written in the old space. */
    public static void migrate(MinecraftClient mc) {
        if (done || mc == null) return;
        done = true;

        ClubConfig.Hud h = ClubConfig.get().hud;
        if (h.space != null && h.space == CLUB_UNITS) return;   // already ours

        if (h.space == null) {          // a fresh install: the defaults were authored in Club units
            h.space = CLUB_UNITS;
            ClubConfig.save();
            return;
        }

        int scaledH = mc.getWindow().getScaledHeight();
        if (scaledH <= 0) { done = false; return; }             // no window yet — try again next frame
        float f = ClubCanvas.HEIGHT / scaledH;

        h.armorX  = conv(h.armorX,  f); h.armorY  = conv(h.armorY,  f);
        h.potionX = conv(h.potionX, f); h.potionY = conv(h.potionY, f);
        h.targetX = conv(h.targetX, f); h.targetY = conv(h.targetY, f);
        h.infoX   = conv(h.infoX,   f); h.infoY   = conv(h.infoY,   f);
        h.sprintX = conv(h.sprintX, f); h.sprintY = conv(h.sprintY, f);

        h.space = CLUB_UNITS;
        ClubConfig.save();
    }

    /** Scale one saved coordinate; -1 (auto) is a decision, not a position, and survives untouched. */
    private static int conv(int v, float f) { return v < 0 ? v : Math.round(v * f); }

    /** Harness seam: re-arm the one-shot so a test can drive the migration deliberately. */
    public static void resetForTest() { done = false; }
}
