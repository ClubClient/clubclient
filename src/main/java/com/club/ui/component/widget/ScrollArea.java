package com.club.ui.component.widget;

import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.motion.ValueTween;
import com.club.ui.theme.Tokens;

/**
 * Scrollable viewport: vertical wheel + thumb-drag (pointer-capture).
 * The content child is measured at full height and clipped to the viewport.
 * Scrollbar (track + thumb) is shown only when overflowing.
 *
 * <p>Motion (Stage 9.2): the wheel eases the visual position ({@code displayOffset}) toward the logical
 * {@code offset} so scrolling glides instead of jumping; a thumb drag stays 1:1 with the cursor (no ease).
 * The thumb brightens on hover and while dragging. All neutral — accent is reserved for actions/selection.
 *
 * <p>R13: the parent Container already gates {@code mouseScrolled} on {@code enabled} —
 * this class does NOT add a redundant {@code !enabled} guard here (frozen §3.6 / §F).
 *
 * <p>Future virtualization seam (frozen §11): all children are laid out even when off-screen;
 * the offset/measure split means later optimisations can skip hidden children without API change.
 */
public final class ScrollArea extends Container {

    private final Component content;

    /** Logical (target) vertical scroll position in pixels (clamped). Input updates this instantly. */
    private float offset;
    /** Eased visual scroll position — what the content is actually drawn at (smooth wheel). */
    private float displayOffset;
    /** After a 1:1 thumb drag, re-base the ease from the exact offset (one alloc, on drag-end). */
    private boolean needResync;

    /** Set during layout; used in input handling and render. */
    private float contentH, viewportH;

    // Thumb-drag state (pointer-capture model) + hover, for the thumb affordance.
    private boolean draggingThumb, thumbHovered;

    // Smooth-scroll + thumb-highlight animators (Stage 9.2).
    private final ValueTween scroll =
            new ValueTween(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
    private final Transition thumbHi =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());

    public ScrollArea(Component content) {
        this.content = content;
        addChild(content);
    }

    /** Package-private accessor for tests. */
    float offset() { return offset; }

    /** Public scroll-position access so a screen can preserve it across a content rebuild (clamped on set). */
    public float scrollOffset() { return offset; }
    public void scrollOffset(float v) { offset = clampOffset(v, contentH, viewportH); }

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
        // Drawn position uses the eased displayOffset; render() re-lays each frame as it advances.
        content.layout(x, y - displayOffset, w, contentH);
    }

    // -------------------------------------------------------------------------
    // Scroll input
    // -------------------------------------------------------------------------

    /**
     * Handles wheel scroll. Updates the logical {@code offset}; the visual position eases toward it in
     * render(). No {@code !enabled} guard here — the parent Container already gates on {@code enabled}
     * before dispatching (R13, committed {@code 23ff2c3}).
     */
    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        float before = offset;
        float step = Tokens.spacing().xl();
        offset = clampOffset(offset - (float) amount * step, contentH, viewportH);
        return offset != before;   // content re-lays in render() as displayOffset eases; caller consumes if moved
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
            return true;   // stays 1:1: render() pins displayOffset to offset while dragging
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

    @Override public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx, my);
        thumbHovered = overflowing() && inThumb(mx, my);
    }

    // -------------------------------------------------------------------------
    // Scrollbar geometry helpers (alloc-free; values are scalars)
    // -------------------------------------------------------------------------

    private boolean overflowing() { return contentH > viewportH; }

    /** Proportional thumb height; minimum is one token spacing step for usability. */
    private float thumbHeight() {
        return Math.max(Tokens.spacing().lg(), viewportH * viewportH / Math.max(1f, contentH));
    }

    /** Y coordinate of the thumb's top edge — tracks the eased visual position. */
    private float thumbY() {
        float travel = viewportH - thumbHeight();
        float scrollRange = Math.max(1f, contentH - viewportH);
        return y + travel * (displayOffset / scrollRange);
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
        float now = ctx.time();

        // Advance the smooth-scroll: 1:1 while dragging the thumb, eased for the wheel.
        if (draggingThumb) {
            displayOffset = offset;
            needResync = true;                       // ease must re-base from the exact offset when the drag ends
        } else {
            if (needResync) { scroll.snap(offset, now); needResync = false; }
            scroll.set(offset, now);
            displayOffset = clampOffset(scroll.get(now), contentH, viewportH);
        }
        content.layout(x, y - displayOffset, w, contentH);

        // Clip content to the viewport rectangle (flat clip — not rounded).
        ctx.renderer().pushClip(x, y, w, viewportH);
        content.render(ctx);
        ctx.renderer().popClip();

        // Draw scrollbar only when overflowing.
        if (overflowing()) {
            float bx = x + w - barW();
            float r  = barW() / 2f;
            // Track
            ctx.renderer().roundedRect(bx, y, barW(), viewportH, r, Tokens.border().subtle());
            // Thumb — neutral (accent is reserved for actions/selection, never chrome); brightens on hover/drag.
            thumbHi.target(draggingThumb ? 1f : (thumbHovered ? 0.55f : 0f), now);
            int thumbColor = Color.lerp(Tokens.palette().textFaint(), Tokens.palette().textMuted(), thumbHi.value(now));
            ctx.renderer().roundedRect(bx, thumbY(), barW(), thumbHeight(), r, thumbColor);
        }
    }
}
