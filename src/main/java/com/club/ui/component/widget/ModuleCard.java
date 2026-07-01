package com.club.ui.component.widget;

import com.club.ui.Icon;
import com.club.ui.UiContext;
import com.club.ui.component.Container;
import com.club.ui.layout.Size;
import com.club.ui.text.Align;
import com.club.ui.theme.Tokens;

/**
 * Module card for the D-menu grid. Composition: a {@link Toggle} + name/desc/hotkey/count {@link Label}s
 * on a flat card surface. State is shown through the accent (frozen flat spec — no shadow/glow):
 * <ul>
 *   <li>enabled (toggle on) → a 2px accent top-edge;</li>
 *   <li>selected → accent border + tone lift (connects to the settings detail pane);</li>
 *   <li>hover → tone lift + stronger hairline.</li>
 * </ul>
 * Clicking the toggle flips the module; clicking the card body selects it ({@link #onSelect}).
 */
public final class ModuleCard extends Container {

    // Shape geometry (proportions, NOT design tokens).
    private static final float H = 96f;        // card height
    private static final float PAD = 13f;      // inner padding
    private static final float EDGE = 2f;      // accent top-edge thickness
    private static final float ICON = 17f;     // leading icon box

    private final Label name, desc, hk, nset;
    private final Toggle toggle;
    private boolean selected;
    private Icon icon;
    private Runnable onSelect;

    public ModuleCard(String name, String desc, String hotkey, int settings, boolean enabled) {
        this.toggle = new Toggle(enabled);
        this.name = new Label(name, Tokens.type().heading()).color(Tokens.palette().textHi());
        this.desc = new Label(desc, Tokens.type().caption()).color(Tokens.palette().textDesc());
        this.hk   = new Label(hotkey, Tokens.type().label()).color(Tokens.palette().textMuted());
        this.nset = new Label(settings + (settings == 1 ? " setting" : " settings"),
                              Tokens.type().caption()).color(Tokens.palette().textFaint()).align(Align.RIGHT);
        addChild(this.name); addChild(this.desc); addChild(this.hk); addChild(this.nset); addChild(this.toggle);
    }

    public ModuleCard selected(boolean s) { this.selected = s; return this; }
    public ModuleCard icon(Icon i) { this.icon = i; return this; }
    public ModuleCard onSelect(Runnable r) { this.onSelect = r; return this; }
    public ModuleCard onToggle(BoolConsumer cb) { toggle.onChange(cb); return this; }
    public boolean value() { return toggle.value(); }

    @Override public Size measure(float availW, float availH) {
        return new Size(Tokens.spacing().xxl() * 6f, H);   // sensible min width; height fixed
    }

    @Override public void layout(float x, float y, float w, float h) {
        super.layout(x, y, w, h);
        float tw = 40f, th = 22f;                                   // toggle bounds (matches Toggle.measure)
        float topY = y + EDGE + Tokens.spacing().md();             // first row baseline area
        toggle.layout(x + w - PAD - tw, topY, tw, th);
        float nameLh = name.role().lineHeight();
        float nameX = x + PAD + (icon != null ? ICON + Tokens.spacing().sm() : 0f);
        name.layout(nameX, topY + (th - nameLh) / 2f, (x + w - PAD - tw - Tokens.spacing().sm()) - nameX, nameLh);
        float descLh = Tokens.type().caption().lineHeight();
        desc.layout(x + PAD, topY + th + Tokens.spacing().sm(), w - PAD * 2f, descLh);
        float footY = y + h - PAD - descLh;
        hk.layout(x + PAD, footY, w - PAD * 2f, descLh);
        nset.layout(x + PAD, footY, w - PAD * 2f, descLh);          // RIGHT-aligned → anchors to right edge
    }

    @Override public void render(UiContext ctx) {
        float r = Tokens.radius().md();
        boolean lift = selected || hovered;
        int fill   = lift ? Tokens.surface().surfaceHi() : Tokens.surface().surface();
        int border = selected ? Tokens.accent().accent()
                              : (hovered ? Tokens.border().strong() : Tokens.border().defaultColor());
        WidgetPaint.surface(ctx, x, y, w, h, r, fill, border);

        // enabled = accent top-edge (between the rounded corners)
        if (toggle.value()) ctx.renderer().rect(x + r, y + EDGE / 2f, w - r * 2f, EDGE, Tokens.accent().accent());

        // leading icon: accent when enabled, muted otherwise
        if (icon != null) {
            float iy = y + EDGE + Tokens.spacing().md() + (22f - ICON) / 2f;
            icon.draw(ctx.renderer(), x + PAD, iy, ICON,
                    toggle.value() ? Tokens.accent().accent() : Tokens.palette().textMuted(), 1.6f);
        }

        ctx.renderer().pushClip(x, y, w, h);   // keep long names/descriptions inside the card
        super.render(ctx);                      // name, desc, hk, nset, toggle
        ctx.renderer().popClip();
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;        // toggle consumed the press
        if (button == 0 && contains(mx, my)) {                      // card body → select
            if (onSelect != null) onSelect.run();
            return true;
        }
        return false;
    }
}
