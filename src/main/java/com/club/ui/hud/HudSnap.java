package com.club.ui.hud;

/**
 * Pure HUD-editor geometry: edge/center magnetism, grid snap, on-screen clamp.
 * No GL/Minecraft deps — unit-tested headlessly (cf. Slider.quantize / ScrollArea.clampOffset).
 * Ports the legacy gui/HudEditorScreen snapX/snapY semantics (targets {MARGIN, centered, far}, THRESHOLD).
 */
public final class HudSnap {
    private HudSnap() {}

    public static final int NO_GUIDE = Integer.MIN_VALUE;
    public static final int MARGIN = 4, THRESHOLD = 6;

    /** Snapped top-left + the guide-line coord to draw (NO_GUIDE if no snap). */
    public record Snap(int pos, int guide) {}

    /** Edge/center magnetism on one axis. First match wins (left, centered, far). */
    public static Snap snapAxis(int pos, int size, int screen) {
        int[] targets = { MARGIN, (screen - size) / 2, screen - size - MARGIN };
        int[] guides  = { MARGIN, screen / 2, screen - MARGIN };
        for (int i = 0; i < targets.length; i++)
            if (Math.abs(pos - targets[i]) <= THRESHOLD) return new Snap(targets[i], guides[i]);
        return new Snap(pos, NO_GUIDE);
    }

    /** Snap to the nearest multiple of {@code step} (step <= 0 → unchanged). */
    public static int snapToGrid(int pos, int step) {
        return step > 0 ? Math.round((float) pos / step) * step : pos;
    }

    /** Clamp a top-left coord so a {@code size}-wide element stays fully within {@code screen}. */
    public static int clampAxis(int pos, int size, int screen) {
        return Math.max(0, Math.min(pos, screen - size));
    }
}
