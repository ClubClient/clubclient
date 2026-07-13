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
     *  held keyboard key.
     *
     *  <p>Not while a screen is open (Stage 62): {@code setScreen} calls {@code KeyBinding.unpressAll()},
     *  and re-pressing the key behind that every tick means the mod is fighting the game over state the
     *  game just deliberately cleared. There is nothing to sprint for inside a screen anyway.</p> */
    public static void tick(MinecraftClient mc) {
        boolean force = active(mc) && mc.currentScreen == null;
        if (force) mc.options.sprintKey.setPressed(true);
        else if (wasForcing) release(mc);
        wasForcing = force;
    }

    /**
     * Give the sprint key back to the player.
     *
     * <p>{@code sprintKey} is a {@code StickyKeyBinding}, and its behaviour changes under the player's
     * feet: while vanilla's "Sprint: Toggle" option is ON, {@code setPressed(false)} is a NO-OP (a sticky
     * binding only flips on a {@code true}). So the one sequence that mattered — the player turns that
     * vanilla option on WHILE this module is force-holding the key — hit an off-edge whose release did
     * nothing: the key stayed latched down, and neither the game nor the mod would ever clear it. The
     * player sprints forever, and the only fix is a restart (Stage 62; the harness now asserts it).</p>
     *
     * <p>{@code updatePressedStates()} is deliberately NOT called in the sticky case: it re-presses from
     * the PHYSICAL key state, which a sticky binding reads as another toggle — the release would undo
     * itself.</p>
     */
    private static void release(MinecraftClient mc) {
        KeyBinding sprint = mc.options.sprintKey;
        sprint.setPressed(false);
        if (mc.options.getSprintToggled().getValue()) {
            if (sprint.isPressed()) sprint.setPressed(true);   // sticky: a `true` TOGGLES — this is the off
        } else {
            KeyBinding.updatePressedStates();                  // restore a physically held key (Stage 45)
        }
    }
}
