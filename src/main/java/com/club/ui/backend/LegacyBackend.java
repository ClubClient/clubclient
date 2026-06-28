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
    private final int[] clipStack = new int[64]; // supports up to 16 nested clips
    private int clipDepth = 0;

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

    @Override public void pushClip(float x, float y, float w, float h) {
        if (ctx == null) return;
        int x0 = (int) x, y0 = (int) y, x1 = (int) (x + w), y1 = (int) (y + h);
        int base = clipDepth * 4;
        clipStack[base]     = x0;
        clipStack[base + 1] = y0;
        clipStack[base + 2] = x1;
        clipStack[base + 3] = y1;
        clipDepth++;
        ctx.enableScissor(x0, y0, x1, y1);
    }
    @Override public void pushRoundedClip(float x, float y, float w, float h, float r) { pushClip(x, y, w, h); }
    @Override public void popClip() {
        if (ctx == null || clipDepth == 0) return;
        clipDepth--;
        if (clipDepth == 0) {
            ctx.disableScissor();
        } else {
            // restore the parent clip rect
            int base = (clipDepth - 1) * 4;
            ctx.enableScissor(clipStack[base], clipStack[base + 1], clipStack[base + 2], clipStack[base + 3]);
        }
    }
    @Override public void pushOpacity(float m) {}
    @Override public void popOpacity() {}
}
