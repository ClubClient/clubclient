package com.club.compat;

import net.minecraft.client.gui.ScreenRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clip must land on the cards, and who scales it changed mid-1.21.
 *
 * <p>This is the test for the bug that made the whole Club menu look empty on 1.21.8: the panel drew, the
 * frame drew, and every card inside was cut away by a scissor rect at twice the intended scale. Nothing
 * crashed and nothing logged. The owner saw a blank menu and had no way to tell that from "the cards were
 * never built".
 *
 * <h2>Why the arithmetic is worth a test at all</h2>
 *
 * <p>Because the answer is a fact about Minecraft, not about us. Through 1.21.5 {@code enableScissor} ignores
 * the matrix and the caller must convert by hand; from 1.21.6 it calls {@code ScreenRect.transform(matrices)}
 * itself. Club draws the menu through a matrix scaled by {@code canvasK} AND hands the same {@code canvasK}
 * to the backend as {@code unitK} — so on 1.21.6+ both conversions fired and the clip went to
 * {@code canvasK²}. Read out of the bytecode, per version:
 *
 * <pre>{@code 1.21.1   ScreenRect.<init> -> ScissorStack.push -> setScissor          (no matrix, anywhere)
 * 1.21.8   ScreenRect.<init> -> getfield matrices -> transform(Matrix3x2f)  -> push
 * 1.21.11  ScreenRect.<init> -> getfield matrices -> transform(Matrix3x2fc) -> push}</pre>
 *
 * <h2>What makes this test trustworthy</h2>
 *
 * <p>It asks VANILLA what vanilla does — {@link ScreenRect#transform} is the very method the bytecode above
 * shows {@code enableScissor} calling, so the expected value is produced by Minecraft's own code rather than
 * by a second implementation of it written from the same reading. A test that models the thing it is checking
 * agrees with itself for free.
 *
 * <p><b>What it does NOT prove,</b> stated plainly: it does not call {@code enableScissor}, because that needs
 * a {@code DrawContext} and a live GL context. It proves the rect we hand vanilla lands on the ink once
 * vanilla has done its part. The step in between — that {@code pushClip} passes these numbers through
 * unchanged — is one line, and it is the kind of line this suite cannot reach without opening a window.
 */
class MtxScissorTest {

    /**
     * 1080p at GUI Scale 4 — Minecraft's OWN auto-default at that resolution — gives canvasK = 0.5. The bug
     * needs only {@code canvasK != 1}, which is almost every real machine: a whole 1 comes out at 1080p @
     * GUI Scale 2 and little else, which is why this survived local testing.
     */
    private static final float K = 0.5f;

    /**
     * Where vanilla ACTUALLY puts the rect on this version — its code, not our restatement of it.
     *
     * <p>The 1.21.5 shape is deliberately absent: it transforms with a {@code Matrix4f}, not a
     * {@code Matrix3x2f}, so a node added there would fail to COMPILE here rather than quietly assert the
     * wrong thing. Loud is the correct failure for a version nobody has measured this against.
     */
    private static ScreenRect asVanillaWillPlaceIt(int[] r) {
        return asVanillaWillPlaceIt(r, K);
    }

    /** Same, at an arbitrary canvas scale — the parameter the K=0.5-only tests never varied. */
    private static ScreenRect asVanillaWillPlaceIt(int[] r, float k) {
        ScreenRect given = new ScreenRect(r[0], r[1], r[2] - r[0], r[3] - r[1]);
        //? if <1.21.5 {
        return given;
        //?} else {
        /*return given.transform(new org.joml.Matrix3x2f().scale(k, k));*/
        //?}
    }

    /**
     * The whole bug in one assertion. A card whose ink is drawn at Club x=300 through a canvasK=0.5 matrix
     * lands at GUI x=150. Before the fix the clip landed at 75 — half the menu away — and took every card
     * with it.
     */
    @Test
    void theClipLandsWhereTheCardsInkLands() {
        float x = 300, y = 100, w = 200, h = 50;

        ScreenRect clip = asVanillaWillPlaceIt(Mtx.scissorRect(x, y, w, h, K));

        assertEquals((int) (x * K), clip.getLeft(),
                "clip left must match the card's ink: the ink is drawn through the canvasK matrix, so the "
                        + "clip has to be scaled exactly once — no more (1.21.6+ scales it for us) and no "
                        + "less (through 1.21.5 it does not)");
        assertEquals((int) (y * K), clip.getTop(), "clip top must match the card's ink");
        assertEquals((int) ((x + w) * K), clip.getLeft() + clip.width(), "clip right must match the card's ink");
        assertEquals((int) ((y + h) * K), clip.getTop() + clip.height(), "clip bottom must match the card's ink");
    }

    /**
     * k=1 is the case that hid this: at GUI Scale 2 on 1080p, canvasK is exactly 1, {@code canvasK²} is also
     * 1, and the bug is perfectly invisible. Pinned so nobody "simplifies" the seam by testing only here.
     */
    @Test
    void theUnscaledCaseIsUnchangedOnEveryVersion() {
        int[] r = Mtx.scissorRect(300f, 100f, 200f, 50f, 1f);
        assertEquals(300, r[0]);
        assertEquals(100, r[1]);
        assertEquals(500, r[2]);
        assertEquals(150, r[3]);
    }

    /**
     * The clip must never be SMALLER than asked, at any scale — and this suite missed that for a day.
     *
     * <p>Both tests above use K=0.5 and whole-number rects, where truncation happens to be exact. The owner
     * found what they hid: on a large window the settings popover cut the right edge off its own values,
     * drawing "4.0" as "4.C", on 1.21.8/1.21.11 while 1.21.1 stayed clean. A test that only ever asks the one
     * question with a known-tidy answer is a test that agrees with itself.
     *
     * <p>Fractional on purpose: Club's layout produces fractional edges constantly (centred columns, halved
     * gutters), and it is the fraction that gets thrown away.
     */
    @Test
    void theClipNeverShrinksBelowWhatItWasAskedFor() {
        for (float k : new float[]{0.5f, 1f, 1.667f, 2f, 3.25f}) {
            float x = 300.4f, y = 100.7f, w = 200.3f, h = 50.9f;
            int[] r = Mtx.scissorRect(x, y, w, h, k);

            // The rect we hand over, and the rect we were asked for, in THE SAME units — which is the only
            // place the guarantee can live. Below 1.21.5 we do the scaling, so the rect is in GUI units; from
            // 1.21.5 vanilla does it, so the rect is in Club units. Both must contain what was requested; the
            // transform is monotonic, so containment survives it either way.
            //? if <1.21.5 {
            float wantL = x * k, wantT = y * k, wantR = (x + w) * k, wantB = (y + h) * k;
            //?} else {
            /*float wantL = x, wantT = y, wantR = x + w, wantB = y + h;*/
            //?}

            assertTrue(r[0] <= wantL, "k=" + k + ": clip starts at " + r[0] + ", inside the rect at " + wantL);
            assertTrue(r[1] <= wantT, "k=" + k + ": clip top cuts the rect");
            assertTrue(r[2] >= wantR,
                    "k=" + k + ": clip ends at " + r[2] + ", short of the rect at " + wantR
                            + " — this is the popover cutting '4.0' into '4.C'");
            assertTrue(r[3] >= wantB, "k=" + k + ": clip bottom cuts the rect");
        }
    }
}
