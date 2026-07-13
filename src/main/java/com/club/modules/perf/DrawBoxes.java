package com.club.modules.perf;

/**
 * THE TEST THAT REPLACES A PROMISE (Stage 67).
 *
 * <p>Batching the duotone icons means drawing them as a group instead of one at a time, so they land after
 * the text and the gauges that used to be interleaved with them. That is a change of painter's order, and
 * the owner's rule is that the picture may not move by a pixel.
 *
 * <p>The reorder is invisible if and only if nothing drawn AFTER an icon, and inside the same HUD element,
 * overlaps it. Every clause of that sentence was paid for:
 *
 * <ul>
 *   <li><b>AFTER.</b> A panel drawn BEFORE an icon may overlap it all it likes — the icon went on top of it
 *       then and goes on top of it now. The property is directional, so this records a SEQUENCE, not a set.</li>
 *   <li><b>SAME ELEMENT.</b> The batch is flushed at every element boundary, because two HUD elements CAN
 *       overlap: the player can drag one onto another in the editor. This test found that the hard way — it
 *       went green three runs, and on the fourth a mob wandered into the crosshair, the Target chip appeared,
 *       and its panel landed on an Effects icon. Across that boundary the icon would have popped through.</li>
 *   <li><b>EVERY FRAME.</b> That violation was intermittent. A single sampled frame is not a test: elements
 *       fade and slide, and the frame that breaks is the one you did not look at. Every finished frame is
 *       judged as it is retired and the worst one is the verdict.</li>
 * </ul>
 *
 * <p>Recording is off in production: one {@code getstatic} and a branch per primitive. The harness turns it
 * on and asserts zero — with real armour and three real effects, at every GUI scale, because an empty HUD
 * proves nothing.
 */
public final class DrawBoxes {
    private DrawBoxes() {}

    public static final int SHAPE = 0, TEXT = 1, ICON = 2;

    private static final int CAP = 8192;
    private static final float[] xy = new float[CAP * 4];
    private static final byte[] kind = new byte[CAP];
    private static final String[] who = new String[CAP];   // which HUD element issued it — names the culprit
    private static int n;
    private static boolean overflowed;

    /** Armed by the harness only. */
    public static volatile boolean recording;
    /** The element currently rendering (set by HudCanvas) — so an offender has a name, not just a box. */
    public static volatile String current = "?";

    // The violation that started this was INTERMITTENT — three runs green, the fourth caught a shape landing
    // on an icon at GUI scale 4. A single sampled frame is therefore not a test: elements fade and slide, and
    // the frame that breaks is the one you did not look at. So every finished frame is judged as it is
    // retired, and the WORST one is what the harness asserts on.
    private static int worst;
    private static String worstWho;

    public static void reset() {
        int bad = iconsCoveredLater();
        if (bad > worst) { worst = bad; worstWho = firstOffender(); }
        n = 0; overflowed = false;
    }

    /** Clears the running worst — call before a measured stretch. */
    public static void clearWorst() { worst = 0; worstWho = null; }
    /** The most (icon, later-primitive) overlaps any single frame had since {@link #clearWorst()}. */
    public static int worstCovered() { return worst; }
    public static String worstOffender() { return worstWho; }

    /** One primitive, in the space it is actually submitted in (pose space — after the matrix). */
    public static void add(int k, float x0, float y0, float x1, float y1) {
        if (!recording) return;
        if (n >= CAP) { overflowed = true; return; }
        int o = n * 4;
        xy[o] = Math.min(x0, x1); xy[o + 1] = Math.min(y0, y1);
        xy[o + 2] = Math.max(x0, x1); xy[o + 3] = Math.max(y0, y1);
        kind[n] = (byte) k;
        who[n] = current;
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
                if (!sameElement(i, j)) continue;           // the batch is flushed at every element boundary
                if (intersects(i, j)) bad++;
            }
        }
        return bad;
    }

    /** The batch never crosses an element, so only same-element pairs can have their order changed. Two HUD
     *  elements CAN overlap (the editor lets the player stack them) and their order is untouched. */
    private static boolean sameElement(int i, int j) {
        return who[i] != null && who[i].equals(who[j]);
    }

    /** A human-readable culprit for the report — the first offending pair, or null. */
    public static String firstOffender() {
        for (int i = 0; i < n; i++) {
            if (kind[i] != ICON) continue;
            for (int j = i + 1; j < n; j++) {
                if (kind[j] == ICON || !sameElement(i, j) || !intersects(i, j)) continue;
                int a = i * 4, b = j * 4;
                return String.format("%s icon [%.1f,%.1f %.1f,%.1f] would be covered by %s %s [%.1f,%.1f %.1f,%.1f]",
                        who[i], xy[a], xy[a + 1], xy[a + 2], xy[a + 3],
                        who[j], kind[j] == TEXT ? "text" : "shape",
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
