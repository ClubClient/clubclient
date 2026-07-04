package com.club.modules.screenstretch;

import com.club.config.ClubConfig;
import net.minecraft.client.MinecraftClient;

/**
 * Computes the horizontal scale applied to the world projection matrix to
 * fake a different aspect ratio, plus the letterbox bar size when black bars
 * are enabled.
 *
 * <p><b>Trade-off (documented, Stage 29):</b> the letterbox bars are painted by
 * {@code HudManager.drawBlackBars} as opaque fills over the full-height/width edge
 * strips. They intentionally cover WHATEVER sits in those strips — including the
 * vanilla chat box (bottom-left) and the ends of the hotbar/health/hunger when the
 * bars are horizontal. This is accepted: the bars exist to hide the over-rendered
 * world edges of the faked aspect, and a player who wants stretch accepts the mask.
 * See {@code docs/ANIMATIONS.md} §7.
 */
public final class ScreenStretchModule {
    private ScreenStretchModule() {}

    public static StretchPreset preset() {
        return StretchPreset.fromName(ClubConfig.get().screenStretch.preset);
    }

    public static boolean blackBars() {
        return ClubConfig.get().screenStretch.blackBars;
    }

    public static boolean enabled() {
        return ClubConfig.get().screenStretch.enabled;
    }

    public static boolean isActive() {
        return enabled() && !preset().isAuto();
    }

    /** Real window aspect ratio (width/height). */
    public static float windowAspect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getFramebufferWidth();
        int h = Math.max(1, mc.getWindow().getFramebufferHeight());
        return (float) w / (float) h;
    }

    /**
     * Horizontal scale applied to the projection matrix' X axis. We render the
     * world as if the window had the target aspect ratio, i.e. m00 = f / target
     * instead of f / real, which is a factor of (real / target).
     *
     * Example: 4:3 on a 16:9 monitor -> real(1.78)/target(1.33) = 1.33, the view
     * is stretched horizontally like an old 4:3 image filling a wide screen.
     */
    public static float projectionScaleX() {
        StretchPreset p = preset();
        if (p.isAuto()) return 1f;
        float target = p.aspect();
        float real = windowAspect();
        return real / target;
    }

    /**
     * Letterbox bar thickness in GUI-scaled pixels (per side) when black bars
     * are on. Bars hide the over-rendered edges: vertical bars when the view is
     * squeezed wider than the window, horizontal bars when taller.
     */
    public static int barThickness(int guiHeight, int guiWidth, boolean[] vertical) {
        if (!isActive() || !blackBars()) return 0;
        float scaleX = projectionScaleX();
        if (Math.abs(scaleX - 1f) < 0.001f) return 0;
        if (scaleX > 1f) {
            // horizontally stretched -> mask the sides with vertical bars
            vertical[0] = true;
            float keep = 1f / scaleX;
            return Math.round(guiWidth * (1f - keep) / 2f);
        } else {
            vertical[0] = false;
            float keep = scaleX;
            return Math.round(guiHeight * (1f - keep) / 2f);
        }
    }
}