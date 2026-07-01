package com.club.ui.component.widget;

import com.club.ui.Color;
import com.club.ui.Icon;
import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;

/**
 * Category rail row: icon + label + enabled-count, with a strong active state (accent-tinted fill +
 * accent icon/label). The active left-bar is NOT drawn here — the screen draws it once as a single
 * sliding accent indicator (motion). Click selects the category ({@link #onSelect}). Flat.
 */
public final class CategoryItem extends Component {
    private static final float H = 40f;

    private final String name;
    private final Icon icon;
    private int count;
    private boolean active;
    private Runnable onSelect;

    public CategoryItem(String name, Icon icon) { this.name = name; this.icon = icon; }
    public CategoryItem count(int c) { this.count = c; return this; }
    public CategoryItem active(boolean a) { this.active = a; return this; }
    public CategoryItem onSelect(Runnable r) { this.onSelect = r; return this; }

    @Override public Size measure(float availW, float availH) { return new Size(availW, H); }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && contains(mx, my)) { if (onSelect != null) onSelect.run(); return true; }
        return false;
    }

    @Override public void render(UiContext ctx) {
        float rad = Tokens.radius().sm();
        if (active) {
            // active fill only; the accent left-bar is drawn by the screen as one sliding indicator (motion).
            ctx.renderer().roundedRect(x + 6, y + 4, w - 12, h - 8, rad, Color.withAlpha(Tokens.accent().accent(), 0x1F));
        } else if (hovered) {
            ctx.renderer().roundedRect(x + 6, y + 4, w - 12, h - 8, rad, Tokens.interaction().hoverWash());
        }
        int icol = active ? Tokens.accent().accent() : Tokens.palette().textMuted();
        float is = 16f;
        icon.draw(ctx.renderer(), x + 20, y + (h - is) / 2f, is, icol, 1.7f);

        Typography.Role r = Tokens.type().label();
        int tcol = active ? Tokens.palette().textHi() : Tokens.palette().textMuted();
        float ty = y + (h - r.lineHeight()) / 2f;
        ctx.text().draw(name, x + 20 + is + 12, ty, TextStyle.of(r.weight(), r.size(), tcol));
        if (count > 0) {
            int ccol = active ? Tokens.accent().accent() : Tokens.palette().textFaint();
            ctx.text().draw(String.valueOf(count), x + w - 18, ty,
                    TextStyle.of(r.weight(), r.size(), ccol).align(Align.RIGHT));
        }
    }
}
