package com.club.ui;

import com.club.ui.backend.Backends;
import net.minecraft.client.gui.DrawContext;

public final class Ui {
    private Ui() {}
    public enum Backend { MODERN, LEGACY }
    private static Backend forced = null; // null = auto

    /** Call once at client init (registers shaders). No-op where the shader path is not built — see
     *  {@link Backends}. */
    public static void init() {
        //? if <1.21.5 {
        com.club.ui.backend.UiShaders.register();
        //?}
    }

    /** Call at the start of every screen/HUD render pass (drawing in Minecraft's GUI units). */
    public static void beginFrame(DrawContext ctx) { beginFrame(ctx, 1f); }

    /**
     * Same, but for a caller drawing in its OWN unit space: {@code k} is how many Minecraft GUI units
     * one caller unit is worth, and the caller has already pushed the matching {@code scale(k)} onto the
     * matrix stack. Shapes and text ride that matrix for free — but the GL SCISSOR does not (it is set in
     * framebuffer pixels, outside the matrix), so the backends need to know the factor to convert clip
     * rects with. The Club menu uses this to keep a FIXED design canvas whatever the player's GUI scale
     * is (Stage 60).
     */
    public static void beginFrame(DrawContext ctx, float k) {
        Backends.begin(ctx);
        //? if <1.21.5 {
        Backends.MODERN_R.unitScale(k);
        //?}
        Backends.LEGACY_R.unitScale(k);
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        // k is MC GUI units per caller unit; the window's scale factor is DEVICE px per MC GUI unit. Their
        // product is the only number a hairline needs: how many physical pixels one caller unit is worth.
        devicePx = (mc == null) ? 0f : (float) (k * mc.getWindow().getScaleFactor());
    }

    // -------------------------------------------------------------------------
    // Hairline pixel snapping
    // -------------------------------------------------------------------------

    /**
     * DEVICE pixels per caller unit for the frame in flight, or 0 when unknown.
     *
     * <p>Set by {@link #beginFrame(DrawContext, float)}. Zero means "nobody told us the scale", and every
     * helper below then returns its input unchanged — an un-snapped hairline is the old behaviour, which is
     * merely soft; snapping against a guessed scale would move it somewhere wrong.
     */
    private static float devicePx = 0f;

    /** DEVICE pixels per caller unit, or 0 if this frame never opened with {@link #beginFrame}. */
    public static float devicePxPerUnit() { return devicePx; }

    /**
     * A coordinate moved to the nearest whole DEVICE pixel, in caller units.
     *
     * <p><b>Only hairlines may use this.</b> Club's layout is fractional by design — {@code railWW} is 171.6
     * units, {@code cellW} is 100.1 — so four cards in one row start at four different sub-pixel phases. For
     * a card that is invisible: its fill is 200 px wide and half a pixel of phase is nothing. For the 1-unit
     * BORDER around it, the phase IS the appearance: the same border renders at a different weight on each
     * of the four cards, which is the mechanical source of "не точно". Snapping general geometry instead
     * would change the layout the design is frozen at, so the rule is narrow on purpose — thin lines only,
     * where sub-pixel phase is what makes them mushy.
     *
     * <p>Safe under the menu's matrix because the only transform between caller units and the framebuffer is
     * {@code Mtx.scale(canvasK)} — a pure scale about the origin, so {@code round(v*K)/K} really does land on
     * a physical pixel boundary. A caller that pushed a fractional TRANSLATE would break that assumption;
     * none does today (the sole translate in the shape path is inside {@code line()}'s rotated branch, which
     * draws a rounded rect and never reaches here).
     */
    public static float snapPx(float v) {
        return devicePx <= 0f ? v : Math.round(v * devicePx) / devicePx;
    }

    /** One device pixel expressed in caller units — the floor a snapped hairline may never fall below. */
    public static float onePx() { return devicePx <= 0f ? 0f : 1f / devicePx; }

    /**
     * A stroke width rounded to a whole number of device pixels, never below one.
     *
     * <p>The floor is what stops the fix from erasing anything: on a small window a 1-unit border is 1.33
     * device px, which rounds to 1 — thinner, but still drawn. Rounding to 0 would delete every border on
     * the screen.
     */
    public static float snapThickness(float t) {
        if (devicePx <= 0f) return t;
        return Math.max(1f, Math.round(t * devicePx)) / devicePx;
    }

    /** True when a primitive is thin enough that sub-pixel phase, not size, decides how it reads. Card
     *  borders are 1 unit, the focus ring 1.5, the "no settings" outline 1.6, the panel rule and the footer
     *  chevron's stacked rects 1 — everything the recon named sits at or under 3. */
    public static boolean isHairline(float thickness) { return thickness > 0f && thickness <= 3f; }

    /**
     * Call at the END of every render pass that called {@link #beginFrame}. Shapes are BATCHED (Stage
     * 61): they sit in a buffer until something forces them out, and the end of the pass is the last
     * such point — miss it and the frame's final shapes are simply never drawn. Cheap and idempotent.
     */
    public static void endFrame() {
        //? if <1.21.5 {
        Backends.MODERN_R.flush();
        Backends.MODERN_T.flush();
        com.club.ui.backend.IconBatch.flush();
        //?}
    }

    /**
     * Submit the pending icons NOW (Stage 67). The icon batch is what turns four GL draws into one, and it
     * does that by outliving the text and shapes interleaved with it — but only INSIDE one HUD element.
     *
     * <p>It may not outlive the element, and the harness is why we know: the order proof went green three
     * runs and then caught a real violation on the fourth. A mob had wandered into the crosshair, the Target
     * chip appeared — and the Target chip is drawn AFTER the Effects chip, so its panel landed on top of an
     * Effects icon. Batching across that boundary would have popped the icon out through the panel. Two HUD
     * elements CAN overlap: the player can drag them onto each other in the editor.
     *
     * <p>So {@code HudCanvas} calls this between elements. Inside an element the reorder is proved harmless;
     * across elements the order is not reordered at all.
     */
    public static void flushIcons() {
        //? if <1.21.5 {
        com.club.ui.backend.IconBatch.flush();
        //?}
    }

    /**
     * Whether the MODERN path is BUILT on this Minecraft at all — a fact about the jar, not about this run.
     *
     * <p>Not the same question as {@link #modernAvailable()}, and the difference is the whole point: there
     * they both answer "false" on 1.21.8, but for opposite reasons. On 1.21.1 a false means something BROKE —
     * a resource pack, a shader that would not load — and the player should be told. On 1.21.5+ it means the
     * shader path was never compiled in, so LEGACY is the road, not a parachute, and there is nothing to
     * report. Telling that player to "check resource packs" is advice about a problem they do not have.
     *
     * <p>It is {@code true} everywhere again. 1.21.5+ reaches the same pixels by the other road —
     * {@code ModernShapes} records a {@code GuiElementRenderState} where {@code ModernBackend} drew and
     * flushed — so a fallback there means something broke, exactly as it does on 1.21.1, and
     * {@link LegacyNotice} says so again on every version. The owner's rule, restated: the plaque tracks
     * "broken", never "old".
     */
    public static boolean modernSupported() {
        return true;
    }

    /**
     * Whether MODERN can actually draw THIS session — asked fresh, because a shader that fails to load is a
     * runtime fact, not a build one. False sends {@link #backend()} down the LEGACY road and lights
     * {@link LegacyNotice}.
     *
     * <p>Both sides ask the same question of their own machinery. Below 1.21.5 that is the registered shader
     * programs; from 1.21.5 it is whether our pipeline linked — {@code ShapePipe.ready()} calls
     * {@code precompilePipeline(p).isValid()}, which matters more than it looks: an invalid pipeline does not
     * merely fail to draw, it throws from inside {@code GuiRenderer}'s flush, long after our call returned,
     * and kills the client on vanilla's stack. Asking first is what makes a broken shader cost the player
     * their rounded corners instead of their session — the promise this class has always made.
     */
    public static boolean modernAvailable() {
        //? if <1.21.5 {
        return com.club.ui.backend.UiShaders.ready() && Backends.MODERN_T.healthy() && Backends.MODERN_R.healthy();
        //?} else {
        /*return com.club.compat.ShapePipe.ready() && Backends.MODERN_S.healthy();*/
        //?}
    }
    public static Backend backend() {
        if (forced != null) return forced;
        return modernAvailable() ? Backend.MODERN : Backend.LEGACY;
    }
    public static void setBackend(Backend b) { forced = b; }
    public static void setAuto() { forced = null; }

    //? if <1.21.5 {
    public static UiRenderer renderer() { return backend() == Backend.MODERN ? Backends.MODERN_R : Backends.LEGACY_R; }
    public static UiText text() { return backend() == Backend.MODERN ? Backends.MODERN_T : Backends.LEGACY_T; }
    //?} else {
    /*public static UiRenderer renderer() { return backend() == Backend.MODERN ? Backends.MODERN_S : Backends.LEGACY_R; }
    // Text is not routed here on this side: LegacyText holds the fork itself, delegating to SpriteText when
    // its pipeline is up and to vanilla's TextRenderer when it is not. Two backends deciding the same thing
    // in two places is how they come to disagree, so the one that owns the glyphs owns the choice.
    public static UiText text() { return Backends.LEGACY_T; }*/
    //?}
}
