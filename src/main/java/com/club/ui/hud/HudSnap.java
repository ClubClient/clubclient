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

    /** True if the {@code w×h} box at (x,y) overlaps the {x,y,w,h} rect {@code o} (touching edges is not
     *  overlap). Pure — used by the editor's no-overlap constraint and its test. */
    public static boolean overlaps(int x, int y, int w, int h, int[] o) {
        return x < o[0] + o[2] && o[0] < x + w && y < o[1] + o[3] && o[1] < y + h;
    }

    /** True if the box overlaps ANY of {@code others} (each {x,y,w,h}). */
    public static boolean overlapsAny(int x, int y, int w, int h, int[][] others) {
        for (int[] o : others) if (overlaps(x, y, w, h, o)) return true;
        return false;
    }

    /**
     * Nudge a proposed top-left OUT of every {@code others} rect it overlaps, so HUD elements can't be
     * dragged onto each other (owner, v0.1.3: "чтобы худы не могли заехать друг на друга"). Push-out is on
     * the axis of LEAST penetration — the element slides along a neighbour's edge instead of jumping over
     * it — and a few passes settle it against several neighbours at once. Pure geometry: no element/GL deps,
     * so it is unit-tested headlessly like the rest of this class. Returns the adjusted {nx, ny}; the caller
     * still clamps to the screen and, if the box is cornered (edge + neighbour) and cannot separate, keeps
     * its previous position rather than forcing an overlap.
     */
    public static int[] avoidOverlap(int nx, int ny, int w, int h, int[][] others) {
        for (int pass = 0; pass < 4; pass++) {
            boolean moved = false;
            for (int[] o : others) {
                int ox = o[0], oy = o[1], ow = o[2], oh = o[3];
                int ix = Math.min(nx + w, ox + ow) - Math.max(nx, ox);   // horizontal penetration
                int iy = Math.min(ny + h, oy + oh) - Math.max(ny, oy);   // vertical penetration
                if (ix <= 0 || iy <= 0) continue;                        // not overlapping this one
                moved = true;
                if (ix < iy) nx = (nx + w / 2 <= ox + ow / 2) ? ox - w : ox + ow;   // push left/right
                else         ny = (ny + h / 2 <= oy + oh / 2) ? oy - h : oy + oh;   // push up/down
            }
            if (!moved) break;
        }
        return new int[]{nx, ny};
    }
}
