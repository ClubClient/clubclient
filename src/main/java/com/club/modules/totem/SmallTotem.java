package com.club.modules.totem;

/**
 * Small Totem (owner: "как и щит… вшита внутрь и всегда включена") — the totem-of-undying pop, shrunk and
 * lifted toward the top so it stops filling the middle of the screen. Baked in, always on, no toggle.
 *
 * <p>Club draws none of it: two {@code @ModifyArg} on vanilla's own translate/scale calls (measured: exactly
 * one of each in the method), no new draw and no shader, so the 1.21.5+ render inversion is never tripped.</p>
 *
 * <h2>Why the lift is TWO different functions</h2>
 *
 * <p>The pop's render moved classes at 1.21.6, and the two implementations position the item DIFFERENTLY —
 * measured out of the bytecode, not assumed:</p>
 * <ul>
 *   <li><b>&lt;1.21.6 (GameRenderer):</b> the translate Y IS the screen centre — {@code getScaledWindowHeight/2}
 *       times a sway. Multiply it down and the whole thing lifts toward the top. {@link #liftLegacyY}.</li>
 *   <li><b>&gt;=1.21.6 (InGameOverlayRenderer):</b> the matrix arrives as identity from {@code renderOverlays}
 *       and the projection centres the origin; the translate Y is only a small WOBBLE around that centre
 *       ({@code floatingItemOffsetY·0.3·sin}). Multiplying it just shrinks the sway — the bug the owner saw,
 *       where the totem stayed dead centre. To move it you ADD a constant in that render space.
 *       {@link #liftModernY}.</li>
 * </ul>
 *
 * <p>The scale is uniform on both and works the same, so {@link #shrink} is shared.</p>
 */
public final class SmallTotem {
    private SmallTotem() {}

    /** Uniform shrink of the pop. 0.425 → 15% smaller than the 0.5 it first shipped at (owner), ~0.34 of
     *  vanilla on screen. */
    private static final float SCALE = 0.425f;

    /** &lt;1.21.6: the translate Y is the screen centre (height/2·sway, +Y down). Multiply it down to lift
     *  toward the top. 0.45 → from mid-screen up to ~the upper quarter. */
    private static final float LEGACY_Y_FACTOR = 0.45f;

    /** &gt;=1.21.6: the translate Y is a small wobble around a projection-centred origin. The X wobble is
     *  multiplied by the framebuffer aspect (w/h) — the tell of a clip/NDC-like space where Y spans roughly
     *  [-1, 1] over the whole screen height. So a lift is a SMALL constant: the first attempt used 3.0 and the
     *  pop flew clean off the screen (owner saw nothing at all), which is what pinned the scale. 0.4 keeps it
     *  on screen and nudged up. <b>Still the one number to tune</b>, and its SIGN is a guess — +Y taken as
     *  "up" (NDC convention); if the totem sits low instead, negate. */
    private static final float MODERN_LIFT = 0.4f;

    /** Shrink one component of the vanilla scale call. */
    public static float shrink(float component) { return component * SCALE; }

    /** Lift the legacy (&lt;1.21.6) centred Y toward the top — proportional, so it needs no window height. */
    public static float liftLegacyY(float centeredY) { return centeredY * LEGACY_Y_FACTOR; }

    /** Lift the modern (&gt;=1.21.6) wobble Y by a constant in the pop's own render space. */
    public static float liftModernY(float wobbleY) { return wobbleY + MODERN_LIFT; }
}
