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

    /** END_CLIENT_TICK: force-hold, and cleanly release when the module turns off. The off-edge
     *  EXPLICITLY unpresses the key first (Stage 45): updatePressedStates() only resets KEYSYM keys
     *  with a known code, so a mouse-bound or UNBOUND sprint key would keep the forced press stuck
     *  and the player would auto-sprint forever. updatePressedStates() then restores a physically
     *  held keyboard key. */
    public static void tick(MinecraftClient mc) {
        boolean force = active(mc);
        if (force) mc.options.sprintKey.setPressed(true);
        else if (wasForcing) { mc.options.sprintKey.setPressed(false); KeyBinding.updatePressedStates(); }
        wasForcing = force;
    }
}
