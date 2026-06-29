package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.Column;
import com.club.ui.layout.Insets;
import com.club.ui.layout.Size;
import com.club.ui.layout.Sizing;
import com.club.ui.theme.Tokens;

/**
 * Elevated card surface with optional header and footer sections.
 *
 * <p>Composition: internal {@link Column}[header?, Divider, content, Divider, footer?].
 * Surface drawn via {@link WidgetPaint#elevation} (level1) — shadow→fill→border in one call.
 * Content is clipped to the card's rounded rect.
 *
 * <p>The internal Column is rebuilt lazily behind a dirty flag — never allocated on the hot path.
 */
public final class Card extends Container {

    private Component header;
    private Component content;
    private Component footer;
    private Insets padding = Insets.symmetric(Tokens.spacing().md(), Tokens.spacing().md());

    /** Dirty flag: true when the Column must be rebuilt before next measure/layout/render. */
    private boolean dirty = true;
    private Column column;

    public Card() {}

    public Card(Component content) { this.content = content; }

    public Card content(Component c) { this.content = c;  dirty = true; return this; }
    public Card header(Component c)  { this.header  = c;  dirty = true; return this; }
    public Card footer(Component c)  { this.footer  = c;  dirty = true; return this; }
    public Card padding(Insets p)    { this.padding  = p; dirty = true; return this; }

    // -------------------------------------------------------------------------
    // Internal Column — rebuilt lazily (no allocation on hot path)
    // -------------------------------------------------------------------------

    private Column column() {
        if (dirty) {
            column = new Column()
                    .padding(padding)
                    .gap(Tokens.spacing().sm());

            if (header != null) {
                column.add(header);
                column.add(new Divider());
            }
            if (content != null) {
                column.add(content, Sizing.fill());
            }
            if (footer != null) {
                column.add(new Divider());
                column.add(footer);
            }

            // Keep Container.children in sync so Container's input/render dispatch works.
            children.clear();
            children.add(column);
            dirty = false;
        }
        return column;
    }

    // -------------------------------------------------------------------------
    // Component lifecycle
    // -------------------------------------------------------------------------

    @Override public Size measure(float availW, float availH) {
        return column().measure(availW, availH);
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        column().layout(x, y, w, h);
    }

    /**
     * render: WidgetPaint.elevation (level1) paints shadow→fill→border in one call,
     * then content is clipped to the rounded rect.
     *
     * NOTE — rule-of-three observation: Panel also does pushRoundedClip→child.render→popClip.
     * Window will be a third site. Flagged in card-report.md so the controller can lift this
     * into WidgetPaint if desired (a lambda-based helper would allocate per frame, so not done here).
     */
    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().lg();
        WidgetPaint.elevation(ctx, x, y, w, h, r, Tokens.elevation().level1());
        ctx.renderer().pushRoundedClip(x, y, w, h, r);
        column().render(ctx);
        ctx.renderer().popClip();
    }
}
