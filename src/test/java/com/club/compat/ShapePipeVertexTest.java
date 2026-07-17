package com.club.compat;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a recorded shape puts on its vertices — the half of {@link ShapePipe} a machine can check.
 *
 * <p><b>What this proves and what it cannot.</b> It proves the LAYOUT: winding, local coordinates, the
 * carriers, the per-vertex colours, that the pose reaches the position and does NOT reach localPos, and the
 * screen box. It cannot prove the shape is visible — a pipeline that compiles draws black quads just as
 * happily as it draws a menu, and only a launch can tell those apart. See the risks in the handover.
 *
 * <p>It drives {@code ShapeState.emit} rather than {@code setupVertices}, because that is the one method
 * whose signature differs between 1.21.8 (it takes a z) and 1.21.11 (it does not); the two-line adapter
 * between them is the only thing here left to the compiler. This file is excluded below 1.21.5 with the
 * class it tests (build.gradle) — 1.21.1 has no GuiRenderState to record into.
 */
class ShapePipeVertexTest {

    /** A quad: (10,20)-(50,60), so centre (30,40) and half-size (20,20). */
    private static ShapePipe.ShapeState state(Matrix3x2f pose, int cTL, int cBL, int cBR, int cTR) {
        return new ShapePipe.ShapeState(pose, 10f, 20f, 50f, 60f, 30f, 40f,
                /*hw*/ 640, /*hh*/ 640, /*rr*/ 384, /*th*/ 0, cTL, cBL, cBR, cTR, null);
    }

    // ---------------------------------------------------------------------
    // Winding and local coordinates
    // ---------------------------------------------------------------------

    @Test
    void emitsFourVerticesInVanillaWinding() {
        Rec r = new Rec();
        state(new Matrix3x2f(), 0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233).emit(r, 0f);

        // TL, BL, BR, TR — read out of TexturedQuadGuiElementRenderState.setupVertices (1.21.8 bytecode).
        // A different order is not a different look, it is a torn quad: DrawMode.QUADS indexes 0-1-2, 0-2-3.
        assertEquals(4, r.pos.size(), "a quad is four vertices");
        assertArrayEquals(new float[] {10f, 20f}, r.pos.get(0), "vertex 0 = top-left");
        assertArrayEquals(new float[] {10f, 60f}, r.pos.get(1), "vertex 1 = bottom-left");
        assertArrayEquals(new float[] {50f, 60f}, r.pos.get(2), "vertex 2 = bottom-right");
        assertArrayEquals(new float[] {50f, 20f}, r.pos.get(3), "vertex 3 = top-right");
    }

    @Test
    void localPosIsOffsetFromTheShapeCentre() {
        Rec r = new Rec();
        state(new Matrix3x2f(), 0, 0, 0, 0).emit(r, 0f);
        // This IS the SDF's input space: the shader measures |localPos| against halfSize, so an origin that
        // is not the centre silently moves every rounded corner.
        assertArrayEquals(new float[] {-20f, -20f}, r.uv0.get(0));
        assertArrayEquals(new float[] {-20f,  20f}, r.uv0.get(1));
        assertArrayEquals(new float[] { 20f,  20f}, r.uv0.get(2));
        assertArrayEquals(new float[] { 20f, -20f}, r.uv0.get(3));
    }

    // ---------------------------------------------------------------------
    // The pose reaches the position and nothing else
    // ---------------------------------------------------------------------

    @Test
    void poseTransformsPositionButNeverLocalPos() {
        Matrix3x2f pose = new Matrix3x2f().translate(100f, 200f).scale(2f);
        Rec r = new Rec();
        state(pose, 0, 0, 0, 0).emit(r, 0f);

        assertArrayEquals(new float[] {120f, 240f}, r.pos.get(0), 1e-4f, "position rides the matrix");
        assertArrayEquals(new float[] {200f, 320f}, r.pos.get(2), 1e-4f);

        // ...and localPos does NOT. If the matrix leaked in here the SDF would be evaluated in screen space
        // and every radius would scale twice — and line()'s rotation would shear the rounded box outright.
        assertArrayEquals(new float[] {-20f, -20f}, r.uv0.get(0), 1e-4f, "localPos stays in shape space");
        assertArrayEquals(new float[] { 20f,  20f}, r.uv0.get(2), 1e-4f);
    }

    @Test
    void zGoesOnEveryVertex() {
        Rec r = new Rec();
        state(new Matrix3x2f(), 0, 0, 0, 0).emit(r, 7.5f);
        for (float[] p : r.posZ) assertEquals(7.5f, p[0], "1.21.8 hands setupVertices a z; it must be used");
    }

    // ---------------------------------------------------------------------
    // Carriers
    // ---------------------------------------------------------------------

    @Test
    void everyVertexCarriesTheSameShapeParameters() {
        Rec r = new Rec();
        state(new Matrix3x2f(), 0, 0, 0, 0).emit(r, 0f);
        // Identical on all four, which is what makes interpolation exact rather than merely close.
        for (int i = 0; i < 4; i++) {
            assertArrayEquals(new int[] {640, 640}, r.overlay.get(i), "UV1 = half size, 1/32 px");
            assertArrayEquals(new int[] {384, 0},   r.light.get(i),   "UV2 = radius, thickness, 1/64 px");
        }
    }

    @Test
    void colourIsPerVertexSoAGradientNeedsNoSecondColourUniform() {
        Rec r = new Rec();
        // A left-to-right ramp: A on the left pair (TL, BL), B on the right pair (BR, TR).
        state(new Matrix3x2f(), 0xFF102030, 0xFF102030, 0x80A0B0C0, 0x80A0B0C0).emit(r, 0f);
        assertArrayEquals(new int[] {0x10, 0x20, 0x30, 0xFF}, r.color.get(0), "TL: r,g,b,a unpacked from ARGB");
        assertArrayEquals(new int[] {0x10, 0x20, 0x30, 0xFF}, r.color.get(1), "BL matches TL — same edge");
        assertArrayEquals(new int[] {0xA0, 0xB0, 0xC0, 0x80}, r.color.get(2), "BR: the far edge, alpha kept");
        assertArrayEquals(new int[] {0xA0, 0xB0, 0xC0, 0x80}, r.color.get(3), "TR matches BR — same edge");
    }

    // ---------------------------------------------------------------------
    // bounds()
    // ---------------------------------------------------------------------

    @Test
    void boundsCoverTheQuadAndRoundOutward() {
        ScreenRect b = state(new Matrix3x2f(), 0, 0, 0, 0).bounds();
        assertEquals(10, b.position().x());
        assertEquals(20, b.position().y());
        assertEquals(40, b.width());
        assertEquals(40, b.height());
    }

    @Test
    void boundsOfARotatedQuadUseAllFourCorners() {
        // 45 degrees about the origin: the diagonal line() draws. Taking only two opposite corners would
        // report a box that misses half the shape, and vanilla culls by this rect.
        Matrix3x2f pose = new Matrix3x2f().rotate((float) (Math.PI / 4));
        ScreenRect b = state(pose, 0, 0, 0, 0).bounds();

        float s = (float) Math.sqrt(0.5);
        float[][] corners = {{10, 20}, {10, 60}, {50, 60}, {50, 20}};
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] c : corners) {
            float x = s * c[0] - s * c[1], y = s * c[0] + s * c[1];
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        assertTrue(b.position().x() <= Math.floor(minX), "left edge must not clip the rotated quad");
        assertTrue(b.position().y() <= Math.floor(minY), "top edge must not clip the rotated quad");
        assertTrue(b.position().x() + b.width()  >= Math.ceil(maxX), "right edge must not clip it");
        assertTrue(b.position().y() + b.height() >= Math.ceil(maxY), "bottom edge must not clip it");
    }

    // ---------------------------------------------------------------------
    // The encoding, and its contract with the shader
    // ---------------------------------------------------------------------

    @Test
    void encodeRoundTripsThroughTheCarrierScale() {
        assertEquals(640, ShapePipe.enc(20f, ShapePipe.SIZE_SCALE), "20 px of half-size at 1/32");
        assertEquals(384, ShapePipe.enc(6f, ShapePipe.EDGE_SCALE), "6 px of radius at 1/64");
        assertEquals(-96, ShapePipe.enc(-1.5f, ShapePipe.EDGE_SCALE), "a glow's feather is carried NEGATIVE");
    }

    @Test
    void encodeSaturatesInsteadOfWrapping() {
        // A shape too big to encode should be drawn slightly wrong, not inside out: a wrapped short would
        // flip half-size negative and invert the SDF.
        assertEquals(Short.MAX_VALUE, ShapePipe.enc(9e9f, ShapePipe.SIZE_SCALE));
        assertEquals(Short.MIN_VALUE, ShapePipe.enc(-9e9f, ShapePipe.EDGE_SCALE));
    }

    @Test
    void shaderDividesByTheSameConstantsJavaMultipliesBy() throws Exception {
        // The carrier scale lives in TWO files and nothing but this test ties them together: change
        // SIZE_SCALE here and every shape on 1.21.8+ silently changes size, with a green build.
        String glsl = Files.readString(Path.of("src/main/resources/assets/club/shaders/include/club_shape_vert.glsl"));
        assertTrue(glsl.contains("vec2(UV1) / " + fmt(ShapePipe.SIZE_SCALE)),
                "club_shape_vert.glsl must divide UV1 by SIZE_SCALE (" + fmt(ShapePipe.SIZE_SCALE) + ")");
        assertTrue(glsl.contains("float(UV2.x) / " + fmt(ShapePipe.EDGE_SCALE)),
                "club_shape_vert.glsl must divide UV2.x by EDGE_SCALE (" + fmt(ShapePipe.EDGE_SCALE) + ")");
        assertTrue(glsl.contains("float(UV2.y) / " + fmt(ShapePipe.EDGE_SCALE)),
                "club_shape_vert.glsl must divide UV2.y by EDGE_SCALE (" + fmt(ShapePipe.EDGE_SCALE) + ")");
    }

    @Test
    void bothShaderDialectsExistAndDifferOnlyInTheVersionLine() throws Exception {
        // The 150/330 split is the whole reason there are two files; if they ever drift apart, one version
        // of Minecraft gets a fix and the other does not.
        for (String stage : new String[] {"vsh", "fsh"}) {
            String a = Files.readString(Path.of("src/main/resources/assets/club/shaders/core/club_ui_shape." + stage));
            String b = Files.readString(Path.of("src/main/resources/assets/club/shaders/core/club_ui_shape330." + stage));
            assertTrue(a.startsWith("#version 150"), stage + ": the 150 twin must say 150");
            assertTrue(b.startsWith("#version 330"), stage + ": the 330 twin must say 330");
            assertEquals(imports(a), imports(b), stage + ": the twins must import the same maths");
        }
    }

    private static List<String> imports(String src) {
        List<String> out = new ArrayList<>();
        for (String line : src.split("\n")) if (line.startsWith("#moj_import")) out.add(line.trim());
        return out;
    }

    /** 32f -> "32.0", the way the GLSL spells it. */
    private static String fmt(float f) { return String.format(java.util.Locale.ROOT, "%.1f", f); }

    // ---------------------------------------------------------------------
    // A VertexConsumer that only remembers
    // ---------------------------------------------------------------------

    /**
     * Records what was written instead of drawing it.
     *
     * <p>{@code color(int)} and {@code lineWidth(float)} carry NO {@code @Override}, and that is deliberate:
     * 1.21.11 made both abstract on VertexConsumer and 1.21.8 did not have them (javap, both jars). Without
     * the annotation they are implementations on one version and harmless extra methods on the other, so one
     * test file compiles on both without a {@code //?} of its own.
     */
    private static final class Rec implements VertexConsumer {
        final List<float[]> pos = new ArrayList<>(), posZ = new ArrayList<>(), uv0 = new ArrayList<>();
        final List<int[]> color = new ArrayList<>(), overlay = new ArrayList<>(), light = new ArrayList<>();

        @Override public VertexConsumer vertex(float x, float y, float z) {
            pos.add(new float[] {x, y}); posZ.add(new float[] {z}); return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            color.add(new int[] {r, g, b, a}); return this;
        }
        @Override public VertexConsumer texture(float u, float v) { uv0.add(new float[] {u, v}); return this; }
        @Override public VertexConsumer overlay(int u, int v) { overlay.add(new int[] {u, v}); return this; }
        @Override public VertexConsumer light(int u, int v) { light.add(new int[] {u, v}); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }

        public VertexConsumer color(int argb) { return this; }        // 1.21.11 only — see the class note
        public VertexConsumer lineWidth(float width) { return this; } // 1.21.11 only
    }
}
