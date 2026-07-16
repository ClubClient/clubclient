package com.club.compat;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

/**
 * The GUI matrix stack, across Minecraft versions.
 *
 * <p><b>Why this class exists.</b> In 1.21.6 {@code DrawContext.getMatrices()} stopped returning a 4x4
 * {@code MatrixStack} and started returning a 2D {@code org.joml.Matrix3x2fStack}: {@code push()}/{@code pop()}
 * became {@code pushMatrix()}/{@code popMatrix()}, {@code scale(x, y, 1f)} lost its third axis, and
 * {@code peek().getPositionMatrix()} has nothing to return — there is no 4x4 to peek at any more. That one
 * change touches nine files across the HUD, the menu, both backends and the icon batch.</p>
 *
 * <p>Those nine files call the methods below and stay ordinary, version-blind Java. The {@code //?} comments
 * live HERE and nowhere else, which is the whole design: over the seam, one source for every version; under
 * it, one implementation per version (docs/superpowers/specs/2026-07-16-multiversion-design.md §3).</p>
 *
 * <p><b>The boundary is 1.21.6, and it is measured, not guessed.</b> {@code DrawContext.getMatrices()}
 * returns {@code net.minecraft.client.util.math.MatrixStack} through 1.21.5 and {@code org.joml.Matrix3x2fStack}
 * from 1.21.6 on — read out of the Yarn mappings for every version from 1.21.1 to 1.21.11 on 2026-07-16.
 * A guessed {@code >=1.21.2} would have compiled just as well today and silently taken the wrong branch the
 * day someone added 1.21.4.</p>
 */
public final class Mtx {
    private Mtx() {}

    /** Save the current transform. Always pair with {@link #pop} in a finally. */
    public static void push(DrawContext ctx) {
        //? if <1.21.6 {
        ctx.getMatrices().push();
        //?} else {
        /*ctx.getMatrices().pushMatrix();*/
        //?}
    }

    /** Restore the transform saved by the matching {@link #push}. */
    public static void pop(DrawContext ctx) {
        //? if <1.21.6 {
        ctx.getMatrices().pop();
        //?} else {
        /*ctx.getMatrices().popMatrix();*/
        //?}
    }

    /** Move the origin. The GUI is flat, so there is no z to move along. */
    public static void translate(DrawContext ctx, float x, float y) {
        //? if <1.21.6 {
        ctx.getMatrices().translate(x, y, 0f);
        //?} else {
        /*ctx.getMatrices().translate(x, y);*/
        //?}
    }

    /** Scale both axes by {@code k}. */
    public static void scale(DrawContext ctx, float k) {
        //? if <1.21.6 {
        ctx.getMatrices().scale(k, k, 1f);
        //?} else {
        /*ctx.getMatrices().scale(k, k);*/
        //?}
    }

    /**
     * Rotate about the screen normal by {@code radians}.
     *
     * <p>The 4x4 path spells this out as a quaternion about +Z; the 2D stack rotates in its own plane, which
     * is the same thing said in fewer words. This is the rotation both backends draw diagonals with.</p>
     */
    public static void rotateZ(DrawContext ctx, float radians) {
        //? if <1.21.6 {
        ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(radians));
        //?} else {
        /*ctx.getMatrices().rotate(radians);*/
        //?}
    }

    /**
     * The current transform as a 4x4, for the vertex paths that bake a matrix into their quads
     * ({@code IconBatch}, {@code ModernBackend}, {@code ModernText}) and for {@code DrawBoxes}.
     *
     * <p>Nothing is lost in the widening: a GUI transform is a 2D affine, and the callers only ever read
     * {@code m00/m10/m30} and {@code m01/m11/m31} — the six numbers a {@code Matrix3x2f} is made of. The 2D
     * branch writes them into the 4x4 by hand rather than leaning on a JOML conversion, so the column order
     * is stated in the source and not taken on trust.</p>
     */
    public static Matrix4f model(DrawContext ctx) {
        //? if <1.21.6 {
        return ctx.getMatrices().peek().getPositionMatrix();
        //?} else {
        /*org.joml.Matrix3x2f m = ctx.getMatrices();
        return new Matrix4f(
                m.m00(), m.m01(), 0f, 0f,
                m.m10(), m.m11(), 0f, 0f,
                0f,      0f,      1f, 0f,
                m.m20(), m.m21(), 0f, 1f);*/
        //?}
    }
}
