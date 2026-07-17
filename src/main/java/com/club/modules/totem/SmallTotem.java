package com.club.modules.totem;

/**
 * Small Totem (owner: "как и щит… вшита внутрь и всегда включена") — the totem-of-undying pop animation,
 * shrunk and lifted toward the top so it stops filling the middle of the screen. Baked in, always on, no
 * toggle and no setting, the way the perf culls are.
 *
 * <p>Club draws none of it. Vanilla renders the pop by centring the item at (width/2, height/2), scaling it
 * up and spinning it; this only rescales the two calls that do the positioning — a {@code translate} and a
 * {@code scale} on the {@link net.minecraft.client.util.math.MatrixStack} — via {@code @ModifyArg}. No new
 * draw, no shader, so the 1.21.5+ render inversion cannot be tripped: the same seam works on the immediate
 * path (1.21.1) and the recorded path (1.21.8/1.21.11).</p>
 *
 * <p>The render method moved classes, measured across the cached jars: it lives in {@code GameRenderer}
 * through 1.21.5 and in {@code InGameOverlayRenderer} from 1.21.6. So two mixins carry the same two handlers,
 * each stonecutter-gated to the versions where its class owns the method; both call the constants here so the
 * numbers live in ONE place. Retuning is a one-line edit, by owner's design.</p>
 */
public final class SmallTotem {
    private SmallTotem() {}

    /** Vanilla centres the pop at height/2; this multiplies that Y down toward the top. 0.4 → ~20% from the
     *  top edge (up, still centred horizontally — X is left untouched). */
    private static final float Y_FACTOR = 0.40f;

    /** Uniform shrink of the pop. 0.5 → half vanilla size. */
    private static final float SCALE = 0.50f;

    /** Lift the vanilla-centred Y toward the top of the screen. Proportional, so it needs no window height. */
    public static float liftY(float centeredY) { return centeredY * Y_FACTOR; }

    /** Shrink one component of the vanilla scale call. */
    public static float shrink(float component) { return component * SCALE; }
}
