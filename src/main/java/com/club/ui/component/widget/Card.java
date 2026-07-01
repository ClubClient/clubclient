package com.club.ui.component.widget;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.layout.Column;
import com.club.ui.layout.CrossAlign;
import com.club.ui.layout.Insets;
import com.club.ui.layout.Size;
import com.club.ui.layout.Sizing;
import com.club.ui.theme.Tokens;

/**
 * Raised card surface (flat) with optional header and footer sections.
 *
 * <p>Composition: internal {@link Column}[header?, Divider, content, Divider, footer?].
 * Surface drawn via {@link WidgetPaint#surface} (surfaceHi fill + hairline — one tone above Panel);
 * depth is tone+hairline, never shadow (frozen flat language). Content is clipped to the rounded rect.
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
                    .gap(Tokens.spacing().sm())
                    .crossAlign(CrossAlign.STRETCH);   // sections span card width (parity with Window)

            if (header != null) {
                column.add(header);
                column.add(new Divider().color(Tokens.border().defaultColor()));
            }
            if (content != null) {
                column.add(content, Sizing.fill());
            }
            if (footer != null) {
                column.add(new Divider().color(Tokens.border().defaultColor()));
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

    /** render: flat raised surface (surfaceHi + default hairline — one tone above Panel), then the
     *  inner Column clipped to the rounded rect. Elevation is tone+hairline, never shadow (flat language). */
    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().lg();
        WidgetPaint.surface(ctx, x, y, w, h, r, Tokens.surface().surfaceHi(), Tokens.border().defaultColor());
        WidgetPaint.clipRounded(ctx, x, y, w, h, r, column());
    }
}
