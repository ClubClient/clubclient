package com.club.modules.freelook;

import com.club.config.ClubConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * Freelook (v0.1 kit, Stage 42): hold the freelook key to swing the CAMERA around the player without
 * turning them — classic over-the-shoulder look. While held: third-person-back is forced (the
 * previous perspective is restored on release), mouse deltas rotate the camera instead of the player
 * ({@code MixinMouse} redirects changeLookDirection here), and {@code MixinCamera} feeds the free
 * yaw/pitch into Camera.update's setRotation. The player's real rotation — and therefore aim,
 * movement direction and every packet — never changes: this is a camera feature, not an aim tool.
 */
public final class FreelookModule {
    private FreelookModule() {}

    private static boolean active;
    private static float yaw, pitch;
    private static Perspective prev;

    public static boolean active() { return active; }
    public static float camYaw() { return yaw; }
    public static float camPitch() { return pitch; }

    /** Mouse delta while freelooking — same 0.15°/count scale as vanilla changeLookDirection. */
    public static void onLook(double dx, double dy) {
        yaw += (float) (dx * 0.15);
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) (dy * 0.15)));
    }

    /**
     * END_CLIENT_TICK: edge-driven engage/release. Uses the RAW key (Keys.held) so freelook works even if its
     * key is also bound to something else.
     *
     * <p><b>No {@code enabled} flag here either</b> — same reasoning as {@link com.club.modules.zoom.ZoomModule}
     * (owner, v0.1.3). A hold module has no off state to store: freelook you are not holding IS off, and
     * unbinding the key is how you switch it off. {@code ClubConfig.Freelook.enabled} survives only so an old
     * file loads; no code reads it.
     */
    public static void tick(MinecraftClient mc) {
        boolean want = com.club.util.Keys.held(com.club.ClubClient.freelookKey)
                && mc.player != null && mc.currentScreen == null
                && mc.getCameraEntity() == mc.player;
        apply(want, mc);
    }

    /** Engage / release / re-assert the freelook state for a desired hold. Extracted from tick() so
     *  the engage cycle is testable without a real key press. */
    public static void apply(boolean want, MinecraftClient mc) {
        if (want && !active) {
            active = true;
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            prev = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!want && active) {
            active = false;
            if (prev != null) mc.options.setPerspective(prev);
            prev = null;
        } else if (want && active && mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            // F5 must not escape freelook mid-hold: re-assert it (else the camera would apply the
            // free rotation in first person while the crosshair tracks the player — a view/aim desync).
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }
}
