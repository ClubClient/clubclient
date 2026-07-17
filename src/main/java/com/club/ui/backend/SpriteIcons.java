package com.club.ui.backend;

import com.club.ClubMod;
import com.club.ui.text.MsdfMetrics;
import net.minecraft.client.gui.DrawContext;

/**
 * Icons for the versions where MODERN does not compile — the same MSDF atlas, one draw per icon.
 *
 * <p><b>What this is not.</b> It is not a text backend. On MODERN an icon IS a glyph and rides the text
 * pipeline, which is why {@code IconGlyph} composes it as a string; here there is no glyph pipeline to ride,
 * so the icon is drawn as what it physically is — one cell of one texture — and never touches
 * {@link LegacyText}. That matters: a PUA code point handed to the vanilla {@code TextRenderer} draws the
 * missing-glyph box, so the icon path must branch BEFORE the text path, not inside it.</p>
 *
 * <p><b>Placement is the MODERN contract, restated.</b> {@code IconGlyph.draw} promises the icon's content
 * box lands exactly at {@code (x, y)..(x+size, y+size)}. MODERN keeps that promise through the text
 * pipeline: it places the glyph from a baseline of {@code y + size} and lets the em-relative plane bounds
 * do the rest. This class does the same arithmetic directly, so the two paths put the icon in the same
 * place by construction rather than by two constants that agree today.</p>
 *
 * <p>Everything version-specific lives in {@code com.club.compat.IconPipe}; this file is ordinary Java and
 * compiles on every node.</p>
 */
public final class SpriteIcons {
    private SpriteIcons() {}

    private static MsdfAtlas atlas;
    /** Set on the first failure of metrics OR texture: icons then stay off for the session instead of
     *  re-throwing every frame. A missing icon must cost an icon, never the frame. */
    private static boolean broken;

    /** Once-flag for the shader verdict, so a machine whose driver rejects the shader says so one time
     *  rather than once per icon per frame. */
    private static boolean shaderFailureLogged;

    /**
     * Whether an icon can actually be drawn: the version supports the pipeline, the atlas loaded, AND the
     * shader compiled on THIS machine.
     *
     * <p>The last clause is the one that is not obvious. Club cannot compile GLSL at build time, so a driver
     * that rejects the shader is only discoverable at runtime — and an uncompiled pipeline does not draw
     * nothing, it throws inside vanilla's GUI flush where nothing of ours can catch it
     * ({@code IconPipe.ready}). Asking first is what keeps that a missing icon instead of a crash.
     */
    public static boolean available() {
        if (!com.club.compat.IconPipe.supported()) return false;   // short-circuits: <1.21.5 never loads the atlas
        MsdfAtlas a = atlas();
        if (a == null) return false;
        if (com.club.compat.IconPipe.ready(a.pxRange)) return true;
        if (!shaderFailureLogged) {
            shaderFailureLogged = true;
            ClubMod.LOGGER.warn("[Club] the icon shader did not compile on this machine — icons fall back to "
                    + "letter initials for this session; everything else is unaffected. The GL error itself is "
                    + "logged above by Minecraft's own shader loader.");
        }
        return false;
    }

    private static MsdfAtlas atlas() {
        if (atlas == null && !broken) {
            try {
                atlas = MsdfAtlas.loadIcons();
            } catch (Exception e) {
                broken = true;
                // WARN, not ERROR, and once: icons vanish, the UI survives. Same bargain FontRegistry
                // strikes on the MODERN path, for the same reason.
                ClubMod.LOGGER.warn("[Club] icon atlas failed to load — icons are off for this session; "
                        + "the rest of the UI keeps rendering", e);
            }
        }
        return atlas;
    }

    /**
     * Draws the icon for {@code codePoint} with its content box at {@code (x, y)..(x+size, y+size)},
     * tinted {@code argb}. Silently does nothing if icons are unavailable — callers that must keep an
     * identity check {@link #available()} first and fall back to a letter.
     */
    public static void draw(DrawContext ctx, int codePoint, float x, float y, float size, int argb) {
        if (ctx == null || !available()) return;
        MsdfAtlas a = atlas;
        MsdfMetrics.Glyph g = a.metrics.get(codePoint);
        if (g == null || !g.hasBounds) return;   // not packed into the atlas — nothing to draw
        try {
            a.ensureTexture();
        } catch (Exception e) {
            broken = true;
            ClubMod.LOGGER.warn("[Club] icon atlas texture failed to bind — icons are off for this session; "
                    + "the rest of the UI keeps rendering", e);
            return;
        }

        Quad q = quad(g, x, y, size, a.metrics.atlasW, a.metrics.atlasH);
        if (q == null) return;

        com.club.compat.IconPipe.draw(ctx, a.textureId, q.x, q.y, q.k, q.u, q.v, q.cellW, q.cellH,
                a.metrics.atlasW, a.metrics.atlasH, a.pxRange, argb);

        // The order proof (Stage 67) sees this path too. It is reached only when the backend is LEGACY —
        // MODERN sends the same icon through the text pipeline (IconGlyph.draw) — so it records the CELL
        // actually painted rather than the caller's content box: the two differ by the glyph's plane bounds,
        // and a box that is not the pixels is not evidence.
        if (com.club.modules.perf.DrawBoxes.recording) {
            var mt = com.club.compat.Mtx.model(ctx);
            float x1 = q.x + q.cellW * q.k, y1 = q.y + q.cellH * q.k;
            com.club.modules.perf.DrawBoxes.add(com.club.modules.perf.DrawBoxes.ICON,
                    mt.m00() * q.x + mt.m10() * q.y + mt.m30(), mt.m01() * q.x + mt.m11() * q.y + mt.m31(),
                    mt.m00() * x1 + mt.m10() * y1 + mt.m30(), mt.m01() * x1 + mt.m11() * y1 + mt.m31());
        }
    }

    /**
     * Where one glyph's quad goes and which texels it reads. Club units and TEXELS — the two things
     * {@code IconPipe.draw} needs and the only things that can be wrong without a window open.
     */
    record Quad(float x, float y, float k, float u, float v, int cellW, int cellH) {}

    /**
     * The placement arithmetic, pure so it can be tested — see {@code SpriteIconsPlacementTest}.
     *
     * <p>Null when the cell is degenerate. Everything here is a fact about the ATLAS, not about Minecraft,
     * which is exactly why it is worth pinning: a sign error in the y flip or the baseline draws a real
     * icon in the wrong place, or upside down, and nothing throws and nothing logs.
     */
    static Quad quad(MsdfMetrics.Glyph g, float x, float y, float size, int atlasW, int atlasH) {
        // MsdfMetrics stores UVs normalised and ALREADY flipped to a top-left origin (the atlas file itself
        // is yOrigin=bottom — MsdfMetrics.parse does `v0 = 1 - top/atlasH`). drawTexture wants texels, so
        // multiply back out rather than re-deriving the flip and risking doing it twice.
        float u = g.u0 * atlasW, v = g.v0 * atlasH;
        int cellW = Math.round((g.u1 - g.u0) * atlasW), cellH = Math.round((g.v1 - g.v0) * atlasH);
        if (cellW <= 0 || cellH <= 0) return null;

        // Plane bounds are em-relative to the BASELINE with y UP; the screen has y DOWN, hence the
        // subtraction. baseline = y + size is the MODERN contract restated (see the class note).
        float baseline = y + size;
        float qx = x + g.pl * size;
        float qy = baseline - g.pt * size;
        float k = ((g.pr - g.pl) * size) / cellW;
        return new Quad(qx, qy, k, u, v, cellW, cellH);
    }
}
