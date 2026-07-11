package com.club.modules.togglesprint;

import com.club.config.ClubConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Toggle Sprint (v0.1 kit, Stage 41): holds the sprint key down for the player every client tick —
 * vanilla still decides when sprinting is actually possible (forward motion, hunger, collisions),
 * we only spare the pinky. Skipped entirely while the vanilla "Sprint: Toggle" option is on: the
 * sticky binding flips per setPressed call (forcing it every tick would strobe), and vanilla's own
 * toggle already achieves the effect. The quiet HUD chip lives in {@code ui.hud.SprintElement}.
 */
public final class ToggleSprintModule {
    private ToggleSprintModule() {}

    private static boolean wasForcing;

    /** Whether the module is holding sprint this tick (also feeds the HUD chip's live state). */
    public static boolean active(MinecraftClient mc) {
        return ClubConfig.get().toggleSprint.enabled
                && mc.player != null
                && !mc.options.getSprintToggled().getValue();
    }

    /** END_CLIENT_TICK: force-hold, and cleanly release the key when the module turns off —
     *  a forced pressed state would otherwise stick until the next physical key event. */
    public static void tick(MinecraftClient mc) {
        boolean force = active(mc);
        if (force) mc.options.sprintKey.setPressed(true);
        else if (wasForcing) KeyBinding.updatePressedStates();   // re-read the real key state once
        wasForcing = force;
    }
}
