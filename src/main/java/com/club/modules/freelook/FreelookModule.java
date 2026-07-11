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

    /** END_CLIENT_TICK: edge-driven engage/release on the hold key. */
    public static void tick(MinecraftClient mc) {
        boolean want = ClubConfig.get().freelook.enabled
                && com.club.ClubClient.freelookKey != null && com.club.ClubClient.freelookKey.isPressed()
                && mc.player != null && mc.currentScreen == null
                && mc.getCameraEntity() == mc.player;
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
        }
    }
}
