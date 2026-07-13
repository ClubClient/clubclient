package com.club.modules.perf;

/**
 * THE TEST THAT REPLACES A PROMISE (Stage 67).
 *
 * <p>Batching the duotone icons means drawing them as a group instead of one at a time, and a group is
 * drawn at ONE moment — after the text and the gauges that used to be interleaved with them. That is a
 * change of painter's order, and the owner's rule is that the picture may not move by a pixel.
 *
 * <p>The reorder is invisible if and only if nothing that is drawn AFTER an icon, and would therefore have
 * covered it, overlaps it. Not "icons don't overlap anything" — a panel drawn BEFORE an icon may overlap it
 * all it likes, since the icon went on top of it then and goes on top of it now. The property is directional,
 * so the recorder is a SEQUENCE, not a set.
 *
 * <p>Recording is off in production: one {@code getstatic} and a branch per primitive. The harness turns it
 * on and asserts {@link #iconsCoveredLater()} is zero — with real armour and real effects, at every GUI
 * scale, because an empty HUD proves nothing.
 */
public final class DrawBoxes {
    private DrawBoxes() {}

    public static final int SHAPE = 0, TEXT = 1, ICON = 2;

    private static final int CAP = 8192;
    private static final float[] xy = new float[CAP * 4];
    private static final byte[] kind = new byte[CAP];
    private static int n;
    private static boolean overflowed;

    /** Armed by the harness only. */
    public static volatile boolean recording;

    public static void reset() { n = 0; overflowed = false; }

    /** One primitive, in the space it is actually submitted in (pose space — after the matrix). */
    public static void add(int k, float x0, float y0, float x1, float y1) {
        if (!recording) return;
        if (n >= CAP) { overflowed = true; return; }
        int o = n * 4;
        xy[o] = Math.min(x0, x1); xy[o + 1] = Math.min(y0, y1);
        xy[o + 2] = Math.max(x0, x1); xy[o + 3] = Math.max(y0, y1);
        kind[n] = (byte) k;
        n++;
    }

    public static int recorded() { return n; }
    public static boolean overflowed() { return overflowed; }
    public static int count(int k) {
        int c = 0;
        for (int i = 0; i < n; i++) if (kind[i] == k) c++;
        return c;
    }

    /**
     * How many (icon, later text/shape) pairs actually overlap. This is exactly the number of pixels'-worth
     * of z-order the icon batch would steal. It must be ZERO, or the batch is a visible change and must not
     * ship — no argument from "but they look the same" is admissible.
     */
    public static int iconsCoveredLater() {
        int bad = 0;
        for (int i = 0; i < n; i++) {
            if (kind[i] != ICON) continue;
            for (int j = i + 1; j < n; j++) {
                if (kind[j] == ICON) continue;              // icons keep their order among themselves
                if (intersects(i, j)) bad++;
            }
        }
        return bad;
    }

    /** A human-readable culprit for the report — the first offending pair, or null. */
    public static String firstOffender() {
        for (int i = 0; i < n; i++) {
            if (kind[i] != ICON) continue;
            for (int j = i + 1; j < n; j++) {
                if (kind[j] == ICON || !intersects(i, j)) continue;
                int a = i * 4, b = j * 4;
                return String.format("icon [%.1f,%.1f %.1f,%.1f] would be covered by %s [%.1f,%.1f %.1f,%.1f]",
                        xy[a], xy[a + 1], xy[a + 2], xy[a + 3],
                        kind[j] == TEXT ? "text" : "shape",
                        xy[b], xy[b + 1], xy[b + 2], xy[b + 3]);
            }
        }
        return null;
    }

    /** Strict overlap: shared edges are not a cover (a gauge line that starts where the icon ends is fine). */
    private static boolean intersects(int i, int j) {
        int a = i * 4, b = j * 4;
        return xy[a] < xy[b + 2] && xy[b] < xy[a + 2]
            && xy[a + 1] < xy[b + 3] && xy[b + 1] < xy[a + 3];
    }
}
