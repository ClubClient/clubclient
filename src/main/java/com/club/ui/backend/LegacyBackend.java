package com.club.ui.backend;

import com.club.ui.Axis;
import com.club.ui.Color;
import com.club.ui.Radii;
import com.club.ui.UiRenderer;
import net.minecraft.client.gui.DrawContext;

/** Fallback renderer over DrawContext.fill. Not resolution-independent. Used only when MODERN is unavailable. */
public final class LegacyBackend implements UiRenderer {
    private DrawContext ctx;

    /** Clip stack stored as interleaved int quads: x0, y0, x1, y1 per entry. */
    private int clipDepth = 0;   // depth only — vanilla's ScissorStack owns the rects (see popClip)

    public void begin(DrawContext ctx) { this.ctx = ctx; clipDepth = 0; }
    @Override public boolean isResolutionIndependent() { return false; }

    @Override public void rect(float x, float y, float w, float h, int c) {
        if (ctx != null) ctx.fill((int) x, (int) y, (int) (x + w), (int) (y + h), c);
    }
    @Override public void roundedRect(float x, float y, float w, float h, float r, int c) { rect(x, y, w, h, c); }
    @Override public void roundedRect(float x, float y, float w, float h, Radii r, int c) { rect(x, y, w, h, c); }
    @Override public void border(float x, float y, float w, float h, float r, float th, int c) {
        rect(x, y, w, th, c); rect(x, y + h - th, w, th, c); rect(x, y, th, h, c); rect(x + w - th, y, th, h, c);
    }
    @Override public void gradient(float x, float y, float w, float h, float r, int a, int b, Axis axis) {
        int n = (int) (axis == Axis.VERTICAL ? h : w);
        for (int i = 0; i < n; i++) {
            int col = Color.lerp(a, b, n <= 1 ? 0f : (float) i / (n - 1));
            if (axis == Axis.VERTICAL) rect(x, y + i, w, 1, col); else rect(x + i, y, 1, h, col);
        }
    }
    @Override public void shadow(float x, float y, float w, float h, float r, float dx, float dy, float blur, int c) {
        rect(x + dx, y + dy, w, h, Color.scaleAlpha(c, 0.5f));
    }
    @Override public void glow(float x, float y, float w, float h, float r, float size, int c) {
        rect(x - 1, y - 1, w + 2, h + 2, Color.scaleAlpha(c, 0.4f));
    }
    @Override public void line(float x1, float y1, float x2, float y2, float th, int c) {
        rect(Math.min(x1, x2), Math.min(y1, y2), Math.max(th, Math.abs(x2 - x1)), Math.max(th, Math.abs(y2 - y1)), c);
    }
    @Override public void circle(float cx, float cy, float r, int c) { rect(cx - r, cy - r, r * 2, r * 2, c); }

    // DrawContext.enableScissor PUSHES onto vanilla's ScissorStack (intersecting with whatever is on
    // top) and disableScissor POPS it — the stack already remembers and re-applies the parent rect. The
    // old popClip re-called enableScissor for a nested clip, i.e. it pushed a THIRD entry instead of
    // popping: the stack never unwound, every clip after the first intersected down to nothing, and the
    // LEGACY menu drew one card and then blank — including the plaque meant to explain the fallback
    // (Stage 59 audit). Push/pop is now 1:1, and the depth is bounded so a runaway can't walk the array.
    private static final int MAX_CLIP = 16;

    @Override public void pushClip(float x, float y, float w, float h) {
        if (ctx == null || clipDepth >= MAX_CLIP) return;
        clipDepth++;
        ctx.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
    }
    @Override public void pushRoundedClip(float x, float y, float w, float h, float r) { pushClip(x, y, w, h); }
    @Override public void popClip() {
        if (ctx == null || clipDepth == 0) return;
        clipDepth--;
        ctx.disableScissor();
    }
    @Override public void pushOpacity(float m) {}
    @Override public void popOpacity() {}
}
