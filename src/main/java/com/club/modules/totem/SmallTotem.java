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

    /** Uniform shrink of the pop. 0.5 → half the vanilla size. */
    private static final float SCALE = 0.50f;

    /** &lt;1.21.6: the translate Y is the screen centre (height/2·sway, +Y down). Multiply it down to lift
     *  toward the top. 0.45 → from mid-screen up to ~the upper quarter. */
    private static final float LEGACY_Y_FACTOR = 0.45f;

    /** &gt;=1.21.6: the translate Y is a small wobble around a projection-centred origin, in that render
     *  space's units (the item sits at Z≈-10, scaled 0.8). A constant added here moves the whole pop.
     *  <b>This is the ONE number to tune against a live pop</b> — the frame is not visible from the build,
     *  so both its size and its SIGN are a first guess. Positive Y is guessed as "up"; if the totem drops
     *  instead, negate this. */
    private static final float MODERN_LIFT = 3.0f;

    /** Shrink one component of the vanilla scale call. */
    public static float shrink(float component) { return component * SCALE; }

    /** Lift the legacy (&lt;1.21.6) centred Y toward the top — proportional, so it needs no window height. */
    public static float liftLegacyY(float centeredY) { return centeredY * LEGACY_Y_FACTOR; }

    /** Lift the modern (&gt;=1.21.6) wobble Y by a constant in the pop's own render space. */
    public static float liftModernY(float wobbleY) { return wobbleY + MODERN_LIFT; }
}
