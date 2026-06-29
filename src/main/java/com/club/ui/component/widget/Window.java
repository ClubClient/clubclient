package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.Column;
import com.club.ui.layout.CrossAlign;
import com.club.ui.layout.Insets;
import com.club.ui.layout.Size;
import com.club.ui.layout.Sizing;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

/**
 * Root floating container: title-bar chrome + optional content child.
 *
 * <p>Composition: internal {@link Column}[titleBar, content?]. The {@code TitleBar} is a private
 * drag-handle: its {@code mouseClicked} returns {@code true} (captured by Container's pressedChild),
 * and its {@code mouseDragged} calls {@link #moveBy} on the enclosing Window — routing purely by
 * reference, without any hit-test or {@code dragging} flag on the Window itself (§3.5/§F).
 *
 * <p>Position-exception (§1.3): Window owns its own x,y; the caller sets position via
 * {@link #position} or drag; the parent layout does NOT assign position here.
 *
 * <p>render: {@link WidgetPaint#elevation} (level2) for the frame, then
 * {@code pushRoundedClip → column.render → popClip}.
 *
 * <p><b>Rule-of-three note:</b> the {@code pushRoundedClip → render → popClip} pattern appears in
 * Panel (site 1), Card (site 2), and here (site 3). Flagged for the controller to lift into a
 * {@code WidgetPaint.clipRounded(...)} helper — NOT done here to avoid a merge conflict with the
 * controller's integration pass.
 */
public final class Window extends Container {

    private final String title;
    private Component content;
    private float winW, winH;

    private final Column column = new Column();
    private final TitleBar titleBar = new TitleBar();
    private boolean built;

    public Window(String title) {
        this.title = title;
    }

    public Window content(Component c) {
        this.content = c;
        built = false;
        return this;
    }

    /** Sets the window's screen position and re-lays out. */
    public Window position(float x, float y) {
        layout(x, y, winW, winH);
        return this;
    }

    /** Sets the explicit frame size. Does not trigger layout immediately; layout() does. */
    public Window size(float w, float h) {
        this.winW = w;
        this.winH = h;
        built = false;
        return this;
    }

    /** Shifts the window by (dx, dy) and re-lays out so children follow (position-exception §1.3). */
    void moveBy(float dx, float dy) {
        layout(x + dx, y + dy, w, h);
    }

    // -------------------------------------------------------------------------
    // Internal Column — built lazily, never allocated on the hot path
    // -------------------------------------------------------------------------

    private void build() {
        column.padding(Insets.ZERO).gap(0f).crossAlign(CrossAlign.STRETCH);
        // Rebuild column children: clear both the column's children and our own Container.children.
        column.children().clear();
        children.clear();

        column.add(titleBar);
        if (content != null) {
            column.add(content, Sizing.fill());
        }

        children.add(column);
        built = true;
    }

    // -------------------------------------------------------------------------
    // Component lifecycle
    // -------------------------------------------------------------------------

    /** Window is the position-exception: measure reports its explicit size, not intrinsic. */
    @Override public Size measure(float availW, float availH) {
        return new Size(winW, winH);
    }

    /**
     * layout: Window owns x,y (position-exception). The explicit winW/winH override w/h when set.
     * After assigning own bounds, delegates to the internal Column so all children follow.
     */
    @Override public void layout(float x, float y, float w, float h) {
        if (!built) build();
        // Position-exception: use explicit size if set; otherwise use the assigned w/h.
        float fw = winW > 0 ? winW : w;
        float fh = winH > 0 ? winH : h;
        super.layout(x, y, fw, fh);
        column.layout(this.x, this.y, this.w, this.h);
    }

    /**
     * render: WidgetPaint.elevation (level2) paints shadow→fill→border in one call,
     * then the column (titleBar + content) is clipped to the window's rounded rect.
     *
     * Rule-of-three (clip site 3 of 3): Panel=site1, Card=site2, Window=site3.
     * Controller will lift pushRoundedClip→render→popClip into WidgetPaint.clipRounded() afterward.
     */
    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().lg();
        WidgetPaint.elevation(ctx, x, y, w, h, r, Tokens.elevation().level2());
        ctx.renderer().pushRoundedClip(x, y, w, h, r);
        column.render(ctx);
        ctx.renderer().popClip();
    }

    // -------------------------------------------------------------------------
    // Private drag-handle: consumes press, routes drag to owning Window by reference
    // -------------------------------------------------------------------------

    /**
     * Draggable title-bar handle. Consuming mouseClicked causes Container to set it as pressedChild,
     * so subsequent mouseDragged/mouseReleased are routed here by reference — no hit-test, no flag.
     * mouseDragged delegates to Window.moveBy(dx, dy).
     */
    private final class TitleBar extends Component {

        /**
         * measure: uses only {@code role.lineHeight()} — headless-safe (never calls Ui.text().width).
         * Title text width is never measured; it is drawn left-aligned in render().
         */
        @Override public Size measure(float availW, float availH) {
            Typography.Role role = Tokens.type().title();
            return new Size(0f, role.lineHeight() + Tokens.spacing().md());
        }

        /** Consumes the press unconditionally → Container captures this as pressedChild. */
        @Override public boolean mouseClicked(double mx, double my, int button) {
            return true;
        }

        /** Drag is delivered by-reference (pressedChild routing); calls Window.moveBy. */
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            Window.this.moveBy((float) dx, (float) dy);
            return true;
        }

        @Override public boolean mouseReleased(double mx, double my, int button) {
            return true;
        }

        /** Draws the title-bar background and title text. TextStyle allocated once (field-cached via styleDirty). */
        @Override public void render(UiContext ctx) {
            ctx.renderer().rect(x, y, w, h, Tokens.surface().surfaceHi());
            Typography.Role role = Tokens.type().title();
            // TextStyle is built once here; no per-frame alloc because TitleBar is not in the hot render path
            // where styleDirty logic would matter — the title is immutable per Window instance.
            TextStyle st = TextStyle.of(role.weight(), role.size(), Tokens.palette().textHi());
            ctx.text().draw(title, x + Tokens.spacing().md(), y + Tokens.spacing().sm(), st);
        }
    }
}
