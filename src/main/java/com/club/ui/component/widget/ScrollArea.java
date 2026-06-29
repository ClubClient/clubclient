package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;

/**
 * Scrollable viewport: vertical wheel + thumb-drag (pointer-capture).
 * The content child is measured at full height and clipped to the viewport.
 * Scrollbar (track + thumb) is shown only when overflowing.
 *
 * <p>R13: the parent Container already gates {@code mouseScrolled} on {@code enabled} —
 * this class does NOT add a redundant {@code !enabled} guard here (frozen §3.6 / §F).
 *
 * <p>Future virtualization seam (frozen §11): all children are laid out even when off-screen;
 * the offset/measure split means later optimisations can skip hidden children without API change.
 */
public final class ScrollArea extends Container {

    private final Component content;

    /** Current vertical scroll position in pixels (clamped). */
    private float offset;

    /** Set during layout; used in input handling and render. */
    private float contentH, viewportH;

    // Thumb-drag state (pointer-capture model)
    private boolean draggingThumb;

    public ScrollArea(Component content) {
        this.content = content;
        addChild(content);
    }

    /** Package-private accessor for tests. */
    float offset() { return offset; }

    /**
     * Clamps {@code off} into {@code [0, max(0, contentH - viewportH)]}.
     * Package-private to allow unit testing without any GL/Minecraft context.
     */
    static float clampOffset(float off, float contentH, float viewportH) {
        float max = Math.max(0f, contentH - viewportH);
        return Math.max(0f, Math.min(off, max));
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    @Override public Size measure(float availW, float availH) {
        // ScrollArea takes the natural size of its content (caller constrains the viewport via layout).
        return content.measure(availW, availH);
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        viewportH = h;
        contentH = content.measure(w, h).h();
        offset = clampOffset(offset, contentH, viewportH);
        // Virtualization seam (frozen §11): lay out all children at their natural height.
        content.layout(x, y - offset, w, contentH);
    }

    // -------------------------------------------------------------------------
    // Scroll input
    // -------------------------------------------------------------------------

    /**
     * Handles wheel scroll. No {@code !enabled} guard here — the parent Container already
     * gates on {@code enabled} before dispatching (R13, committed {@code 23ff2c3}).
     */
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        float before = offset;
        float step = Tokens.spacing().xl();
        offset = clampOffset(offset - (float) amount * step, contentH, viewportH);
        if (offset != before) {
            content.layout(x, y - offset, w, contentH);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Thumb-drag (pointer-capture)
    // -------------------------------------------------------------------------

    @Override public boolean mouseClicked(double mx, double my, int button) {
        // If the click lands on the scrollbar thumb, capture it for dragging.
        if (overflowing() && inThumb(mx, my)) {
            draggingThumb = true;
            return true;
        }
        // Otherwise route the press to the content child (normal capture chain).
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingThumb) {
            float trackRange = Math.max(1f, viewportH - thumbHeight());
            float ratio = (contentH - viewportH) / trackRange;
            offset = clampOffset(offset + (float) dy * ratio, contentH, viewportH);
            content.layout(x, y - offset, w, contentH);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (draggingThumb) {
            draggingThumb = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    // -------------------------------------------------------------------------
    // Scrollbar geometry helpers (alloc-free; values are scalars)
    // -------------------------------------------------------------------------

    private boolean overflowing() { return contentH > viewportH; }

    /** Proportional thumb height; minimum is one token spacing step for usability. */
    private float thumbHeight() {
        return Math.max(Tokens.spacing().lg(), viewportH * viewportH / Math.max(1f, contentH));
    }

    /** Y coordinate of the thumb's top edge. */
    private float thumbY() {
        float travel = viewportH - thumbHeight();
        float scrollRange = Math.max(1f, contentH - viewportH);
        return y + travel * (offset / scrollRange);
    }

    /** Width of the scrollbar track/thumb. */
    private float barW() { return Tokens.spacing().xs(); }

    /** Returns true if the cursor is within the scrollbar thumb bounds. */
    private boolean inThumb(double mx, double my) {
        float bx = x + w - barW();
        float ty = thumbY();
        return mx >= bx && mx <= x + w && my >= ty && my <= ty + thumbHeight();
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override public void render(UiContext ctx) {
        // Clip content to the viewport rectangle (flat clip — not rounded, different from Panel/Card).
        ctx.renderer().pushClip(x, y, w, viewportH);
        content.render(ctx);
        ctx.renderer().popClip();

        // Draw scrollbar only when overflowing.
        if (overflowing()) {
            float bx = x + w - barW();
            float r  = barW() / 2f;
            // Track
            ctx.renderer().roundedRect(bx, y, barW(), viewportH, r, Tokens.border().subtle());
            // Thumb
            ctx.renderer().roundedRect(bx, thumbY(), barW(), thumbHeight(), r, Tokens.accent().accent());
        }
    }
}
