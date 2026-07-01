package com.club.ui.menu;

import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.UiRenderer;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.component.FocusManager;
import com.club.ui.component.UiContextImpl;
import com.club.ui.component.widget.Button;
import com.club.ui.component.widget.Checkbox;
import com.club.ui.component.widget.Label;
import com.club.ui.component.widget.ScrollArea;
import com.club.ui.component.widget.Slider;
import com.club.ui.component.widget.Toggle;
import com.club.ui.hud.HudEditorScreen;
import com.club.ui.layout.Column;
import com.club.ui.layout.CrossAlign;
import com.club.ui.layout.Grid;
import com.club.ui.layout.Row;
import com.club.ui.layout.Size;
import com.club.ui.layout.Sizing;
import com.club.ui.layout.Spacer;
import com.club.ui.menu.MenuContent.ActionSetting;
import com.club.ui.menu.MenuContent.Category;
import com.club.ui.menu.MenuContent.CheckSetting;
import com.club.ui.menu.MenuContent.DropdownSetting;
import com.club.ui.menu.MenuContent.Module;
import com.club.ui.menu.MenuContent.Setting;
import com.club.ui.menu.MenuContent.SliderSetting;
import com.club.ui.menu.MenuContent.Tab;
import com.club.ui.menu.MenuContent.ToggleSetting;
import com.club.ui.motion.Transition;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import static org.lwjgl.glfw.GLFW.*;

/**
 * V2 Club menu (Stage 6) — the main client interface, opened by Right Shift. Category rail (text only, with a
 * sliding accent indicator) │ a grid of compact module cards. LEFT-click a card = enable/disable (state reads
 * as an accent-lit surface with a soft accent↔violet edge); RIGHT-click a card = open/close a floating settings
 * popover beside it (dropdowns expand an inline pick-list). No always-on settings pane, no icons, no toggles on
 * cards. A thin view over {@link MenuContent} (pure data): the screen builds the widgets and wires them to config.
 */
public final class ClubMenuScreen extends Screen {

    private static final float CLOCK_BASE = 1000f;   // keep past any transition so fresh widgets read settled
    private static final int VIOLET = 0xFF9B7CFF;    // for the enabled edge: accent mixed toward violet

    private final UiContextImpl uiCtx = new UiContextImpl();
    private final FocusManager focus = new FocusManager();
    private final long startNanos = System.nanoTime();

    private final List<Category> cats = MenuContent.build(this::openHudEditor);
    private int catIndex = 0;
    private String query = "";

    private final Pane root = new Pane();
    private final Grid grid = new Grid(3, Tokens.spacing().md());
    private SearchField search;
    private ScrollArea gridScroll;
    private Transition indicator;

    // settings popover (RMB), anchored to a card
    private Module popModule;
    private Column popCol;
    private ScrollArea popScroll;   // wraps popCol so long settings/dropdown lists scroll instead of overflowing
    private int tabIndex;
    private DropdownSetting openDrop;   // the dropdown whose pick-list is expanded in the popover
    private float popX, popY, popW, popH, popAX, popAY, popAW, popAH;
    private int pressOwner;

    private float winX, winY, winW, winH, bodyY, bodyH, contentX, contentW, railW, headH, footH;

    private TextStyle stBrand, stTitle, stFootMut, stName, stNameOff, stCat, stCatOn, stCatNum;

    public ClubMenuScreen() { super(Text.literal("Club")); }

    private void openHudEditor() { MinecraftClient.getInstance().setScreen(new HudEditorScreen(this)); }

    @Override protected void init() {
        railW = 178; headH = 44; footH = 40;
        query = ""; popModule = null; popCol = null; openDrop = null; pressOwner = 0;
        search = new SearchField("Search modules").onChange(q -> { query = q; rebuildGrid(); layoutAll(); });
        gridScroll = new ScrollArea(grid);
        root.clear();
        root.add(search);
        root.add(gridScroll);
        focus.clear();
        focus.register(search);
        rebuildGrid();
        layoutAll();
        indicator = new Transition(railY(catIndex), Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
    }

    private float railY(int i) { return bodyY + 8 + i * 40; }

    // ---- state ---------------------------------------------------------------

    private void setCategory(int i) {
        catIndex = i;
        closePopover();
        query = "";
        if (search != null) search.clear();
        rebuildGrid();
        layoutAll();
    }

    private void rebuildGrid() {
        grid.clear();
        String q = query.toLowerCase(Locale.ROOT);
        for (Module m : cats.get(catIndex).modules()) {
            if (!q.isEmpty()
                    && !m.name().toLowerCase(Locale.ROOT).contains(q)
                    && !m.desc().toLowerCase(Locale.ROOT).contains(q)) continue;
            grid.add(new ModuleTile(m));
        }
    }

    private void activate(Module m) {
        if (m.hasToggle()) m.setEnabled(!m.enabled());   // rail counts are read live in render()
        else for (Setting s : m.settings()) if (s instanceof ActionSetting a) { a.action().run(); return; }
    }

    private boolean hasConfigurable(Module m) {
        if (m.hasTabs()) return true;
        for (Setting s : m.settings()) if (!(s instanceof ActionSetting)) return true;
        return false;
    }

    // ---- popover -------------------------------------------------------------

    private void openPopover(Module m, float ax, float ay, float aw, float ah) {
        popModule = m; popAX = ax; popAY = ay; popAW = aw; popAH = ah; tabIndex = 0; openDrop = null;
        rebuildPopover();
    }

    private void rebuildPopover() {
        focus.clear();
        focus.register(search);
        popCol = buildSettings(popModule);
        popW = Math.min(236f, Math.max(160f, winW - 16f));   // never wider than the window
        float innerW = popW - 2 * POP_PAD;
        float contentH = popCol.measure(innerW, 99999f).h();
        float maxPopH = Math.min(winH - 16f, 300f);          // cap height so a long list scrolls instead of covering the grid
        popH = Math.min(contentH + 2 * POP_PAD, maxPopH);
        popScroll = new ScrollArea(popCol);
        positionPopover();
    }

    private void positionPopover() {
        popX = clamp(popAX, winX + 8, winX + winW - popW - 8);
        float below = popAY + popAH + 8;
        popY = (below + popH <= winY + winH - 8) ? below : (popAY - popH - 8);
        popY = clamp(popY, winY + 8, Math.max(winY + 8, winY + winH - 8 - popH));   // keep fully inside the window
        popScroll.layout(popX + POP_PAD, popY + POP_PAD, popW - 2 * POP_PAD, popH - 2 * POP_PAD);
    }

    private void closePopover() {
        popModule = null; popCol = null; popScroll = null; openDrop = null;
        focus.clear();
        if (search != null) focus.register(search);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private Column buildSettings(Module m) {
        Column col = new Column().gap(Tokens.spacing().sm()).crossAlign(CrossAlign.STRETCH);

        List<Setting> settings;
        if (m.hasTabs()) {
            List<Tab> tabs = m.tabs();
            Row seg = new Row().gap(Tokens.spacing().sm());
            for (int i = 0; i < tabs.size(); i++) {
                final int ti = i;
                Button b = new Button(tabs.get(i).label())
                        .variant(ti == tabIndex ? Button.Variant.PRIMARY : Button.Variant.GHOST)
                        .onClick(() -> { tabIndex = ti; openDrop = null; rebuildPopover(); });
                seg.add(b); focus.register(b);
            }
            col.add(seg);
            settings = tabs.get(Math.min(tabIndex, tabs.size() - 1)).settings();
        } else settings = m.settings();

        for (Setting s : settings) {
            if (s instanceof DropdownSetting d) {
                int cur = clampIdx(d);
                Row row = new Row().crossAlign(CrossAlign.CENTER);
                row.add(new Label(d.label(), Tokens.type().label()).color(Tokens.palette().textMuted()), Sizing.fill());
                Button field = new Button(d.options()[cur]).variant(Button.Variant.GHOST)
                        .onClick(() -> { openDrop = (openDrop == d) ? null : d; rebuildPopover(); });
                row.add(field);
                col.add(row); focus.register(field);
                if (openDrop == d) {
                    for (int i = 0; i < d.options().length; i++) {
                        final int oi = i;
                        col.add(new OptionRow(d.options()[i], i == cur,
                                () -> { d.set().accept(oi); openDrop = null; rebuildPopover(); }));
                    }
                }
            } else if (s instanceof ActionSetting) {
                Component ctrl = buildControl(s);
                Row rr = new Row(); rr.add(ctrl); col.add(rr); focus.register(ctrl);
            } else {
                Component ctrl = buildControl(s);
                Row rr = new Row().crossAlign(CrossAlign.CENTER);
                rr.add(new Label(s.label(), Tokens.type().label()).color(Tokens.palette().textMuted()), Sizing.fill());
                rr.add(ctrl);
                col.add(rr); focus.register(ctrl);
            }
        }

        if (m.hasReset()) {
            Button reset = new Button("Reset to Default").variant(Button.Variant.GHOST)
                    .onClick(() -> { m.reset().run(); openDrop = null; rebuildPopover(); });
            Row rr = new Row(); rr.add(Spacer.fill()); rr.add(reset);
            col.add(rr); focus.register(reset);
        }
        return col;
    }

    private static int clampIdx(DropdownSetting d) {
        return Math.max(0, Math.min(d.get().getAsInt(), d.options().length - 1));
    }

    private Component buildControl(Setting s) {
        if (s instanceof SliderSetting sl) return new Slider(sl.get().get(), sl.min(), sl.max(), sl.step()).onChange(sl.set());
        if (s instanceof ToggleSetting t)  return new Toggle(t.get().getAsBoolean()).onChange(t.set());
        if (s instanceof CheckSetting ck)  return new Checkbox(ck.get().getAsBoolean()).onChange(ck.set());
        if (s instanceof ActionSetting a)  return new Button(a.label()).variant(Button.Variant.GHOST).onClick(a.action());
        throw new IllegalStateException("unsupported inline setting: " + s);
    }

    // ---- layout --------------------------------------------------------------

    private void layoutAll() {
        float m = 24;
        winW = Math.min(1040, width - 2 * m);
        winH = Math.min(600, height - 2 * m);
        winX = (width - winW) / 2f; winY = (height - winH) / 2f;
        bodyY = winY + headH; bodyH = winH - headH - footH;
        contentX = winX + railW; contentW = winW - railW;

        root.layout(0, 0, width, height);

        float searchW = 200, searchH = 32;
        search.layout(contentX + contentW - 16 - searchW, winY + (headH - searchH) / 2f, searchW, searchH);   // in the header row, next to CLUB

        float gridW = contentW - 32;
        grid.cols(Math.max(2, (int) (gridW / 172)));
        if (gridScroll != null) gridScroll.layout(contentX + 16, bodyY + 34, gridW, bodyH - 34 - 12);

        if (popModule != null) positionPopover();
    }

    // ---- render --------------------------------------------------------------

    @Override public void render(DrawContext dc, int mouseX, int mouseY, float delta) {
        Ui.beginFrame(dc);
        UiRenderer r = Ui.renderer();
        if (stBrand == null) initStyles();
        uiCtx.setTime(CLOCK_BASE + (System.nanoTime() - startNanos) / 1_000_000_000f);

        layoutAll();
        float lg = Tokens.radius().lg();
        Typography ty = Tokens.type();
        float catLh = ty.label().lineHeight();

        r.rect(0, 0, width, height, Color.withAlpha(Tokens.palette().ink0(), 0xD9));   // scrim

        r.roundedRect(winX, winY, winW, winH, lg, Tokens.surface().bg2());
        r.rect(winX, bodyY, railW, bodyH, Tokens.surface().bg1());

        int dv = Tokens.border().defaultColor();
        r.rect(winX + railW, bodyY, 1, bodyH, dv);         // rail | content
        r.rect(winX, winY + winH - footH, winW, 1, dv);    // above footer

        // header — CLUB wordmark + brand accent mark (identity, not a glyph icon); no full-width divider
        r.roundedRect(winX + 18, winY + headH / 2f - 4, 8, 8, 2, Tokens.accent().accent());
        uiCtx.text().draw("CLUB", winX + 34, winY + (headH - ty.display().lineHeight()) / 2f, stBrand);
        uiCtx.text().draw(cats.get(catIndex).name(), contentX + 16, bodyY + 8, stTitle);

        float fy = winY + winH - footH + (footH - ty.label().lineHeight()) / 2f;
        uiCtx.text().draw("Profile · Default", winX + 18, fy, stFootMut);

        // rail categories (text only, no icons)
        for (int i = 0; i < cats.size(); i++) {
            float yy = railY(i);
            boolean active = i == catIndex;
            boolean hov = mouseX >= winX && mouseX <= winX + railW && mouseY >= yy && mouseY < yy + 40;
            if (active) r.roundedRect(winX + 8, yy + 4, railW - 16, 32, Tokens.radius().sm(), Tokens.surface().surfaceHi());
            r.pushClip(winX + 20, yy, railW - 50, 40);   // keep a long name off the count
            uiCtx.text().draw(cats.get(i).name(), winX + 20, yy + (40 - catLh) / 2f, (active || hov) ? stCatOn : stCat);
            r.popClip();
            int cnt = cats.get(i).enabledCount();
            if (cnt > 0) uiCtx.text().draw(String.valueOf(cnt), winX + railW - 16, yy + (40 - catLh) / 2f, stCatNum);
        }

        // cards + search
        root.mouseMoved(mouseX, mouseY);
        root.render(uiCtx);

        if (indicator != null) {
            indicator.target(railY(catIndex), uiCtx.time());
            r.rect(winX, indicator.value(uiCtx.time()) + 8, 3, 24, Tokens.accent().accent());
        }

        r.border(winX, winY, winW, winH, lg, Tokens.border().thickness(), Tokens.border().strong());

        // popover on top
        if (popModule != null && popScroll != null) {
            float pr = Tokens.radius().md();
            r.roundedRect(popX, popY, popW, popH, pr, Tokens.surface().bg2());
            r.border(popX, popY, popW, popH, pr, Tokens.border().thickness(), Tokens.border().strong());
            r.pushClip(popX, popY, popW, popH);
            popScroll.mouseMoved(mouseX, mouseY);
            popScroll.render(uiCtx);
            r.popClip();
        }
    }

    private void initStyles() {
        Typography t = Tokens.type();
        stBrand     = TextStyle.of(t.display().weight(), t.display().size(), Tokens.palette().textHi());
        stTitle     = TextStyle.of(t.title().weight(), t.title().size(), Tokens.palette().textHi());
        stFootMut   = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textMuted());
        stName      = TextStyle.of(t.heading().weight(), t.heading().size(), Tokens.palette().textHi());
        stNameOff   = TextStyle.of(t.heading().weight(), t.heading().size(), Tokens.palette().textMuted());
        stCat       = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textMuted());
        stCatOn     = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textHi());
        stCatNum    = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textFaint()).align(Align.RIGHT);
    }

    @Override public void renderBackground(DrawContext dc, int mx, int my, float d) { /* scrim drawn in render() */ }

    // ---- input ---------------------------------------------------------------

    private boolean insidePop(double mx, double my) {
        return popModule != null && mx >= popX && mx <= popX + popW && my >= popY && my <= popY + popH;
    }

    @Override public boolean mouseClicked(double mx, double my, int b) {
        focus.clickFocus(mx, my);
        if (insidePop(mx, my)) {
            if (b == 1) { closePopover(); return true; }       // RMB inside → close
            popScroll.mouseClicked(mx, my, b); pressOwner = 1; return true;
        }
        if (b == 0 && mx >= winX && mx <= winX + railW && my >= bodyY + 8 && my < bodyY + 8 + cats.size() * 40) {
            int i = (int) ((my - (bodyY + 8)) / 40);
            if (i >= 0 && i < cats.size()) { if (i != catIndex) setCategory(i); return true; }
        }
        if (root.mouseClicked(mx, my, b)) { pressOwner = 2; return true; }
        if (b == 1) closePopover();                            // RMB on empty → close
        pressOwner = 0;
        return super.mouseClicked(mx, my, b);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        boolean h = (pressOwner == 1) ? (popScroll != null && popScroll.mouseReleased(mx, my, b)) : root.mouseReleased(mx, my, b);
        pressOwner = 0;
        return h || super.mouseReleased(mx, my, b);
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        boolean h = (pressOwner == 1) ? (popScroll != null && popScroll.mouseDragged(mx, my, b, dx, dy))
                                      : root.mouseDragged(mx, my, b, dx, dy);
        return h || super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public void mouseMoved(double mx, double my) {
        root.mouseMoved(mx, my);
        if (popScroll != null) popScroll.mouseMoved(mx, my);
    }
    @Override public boolean mouseScrolled(double mx, double my, double hx, double v) {
        if (insidePop(mx, my) && popScroll != null && popScroll.mouseScrolled(mx, my, v)) return true;
        return root.mouseScrolled(mx, my, v) || super.mouseScrolled(mx, my, hx, v);
    }
    @Override public boolean keyPressed(int k, int scan, int mods) {
        if (k == GLFW_KEY_ESCAPE && popModule != null) { closePopover(); return true; }
        if (k == GLFW_KEY_TAB) { if ((mods & GLFW_MOD_SHIFT) != 0) focus.previous(); else focus.next(); return true; }
        return focus.keyPressed(k, scan, mods) || super.keyPressed(k, scan, mods);
    }
    @Override public boolean charTyped(char c, int mods) { return focus.charTyped(c, mods) || super.charTyped(c, mods); }

    @Override public boolean shouldPause() { return false; }

    // ---- module card ---------------------------------------------------------

    private static final float TILE_H = 46f, TILE_PAD = 12f;
    private static final int POP_PAD = 12;

    /** Compact module card (name only). LMB = enable/disable (or run the action); RMB = settings popover. */
    private final class ModuleTile extends Component {
        private final Module m;
        ModuleTile(Module m) { this.m = m; }

        @Override public Size measure(float availW, float availH) { return new Size(150f, TILE_H); }

        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            float rad = Tokens.radius().md();
            boolean on = m.enabled();
            boolean bright = on || !m.hasToggle();   // action-only cards (HUD Editor) read as available, not "off"
            int base = Tokens.surface().surface();
            int fill = on ? Color.lerp(base, Tokens.accent().accent(), hovered ? 0.20f : 0.14f)
                          : (hovered ? Tokens.surface().surfaceHi() : base);
            int edge = on ? Color.withAlpha(Color.lerp(Tokens.accent().accent(), VIOLET, 0.62f), 0xB0)
                          : Tokens.border().defaultColor();
            r.roundedRect(x, y, w, h, rad, fill);
            r.border(x, y, w, h, rad, Tokens.border().thickness(), edge);

            float nameLh = Tokens.type().heading().lineHeight();
            r.pushClip(x + TILE_PAD, y, w - 2 * TILE_PAD, TILE_H);
            ctx.text().draw(m.name(), x + TILE_PAD, y + (TILE_H - nameLh) / 2f, bright ? stName : stNameOff);
            r.popClip();
        }

        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (!contains(mx, my)) return false;
            if (b == 0) { activate(m); return true; }
            if (b == 1) { if (hasConfigurable(m)) { if (popModule == m) closePopover(); else openPopover(m, x, y, w, h); } return true; }
            return false;
        }
    }

    /** Minimal single-line search input: no icon, placeholder when idle, blinking caret when focused. */
    private static final class SearchField extends Component {
        private final String placeholder;
        private String text = "";
        private Consumer<String> onChange;

        SearchField(String placeholder) { this.placeholder = placeholder; }
        SearchField onChange(Consumer<String> cb) { this.onChange = cb; return this; }
        String text() { return text; }
        void clear() { text = ""; }

        @Override public Size measure(float aw, float ah) {
            return new Size(160f, Tokens.type().body().lineHeight() + Tokens.spacing().md());
        }
        @Override public boolean mouseClicked(double mx, double my, int b) { return enabled && contains(mx, my); }
        @Override public boolean charTyped(char c, int mods) {
            if (c >= 32) { text += c; if (onChange != null) onChange.accept(text); return true; }
            return false;
        }
        @Override public boolean keyPressed(int k, int scan, int mods) {
            if (k == GLFW_KEY_BACKSPACE && !text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
                if (onChange != null) onChange.accept(text);
                return true;
            }
            return false;
        }
        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            Typography ty = Tokens.type();
            float rad = Tokens.radius().md();
            float pad = Tokens.spacing().md();
            boolean foc = isFocused();
            boolean empty = text.isEmpty();
            r.roundedRect(x, y, w, h, rad, Tokens.surface().bg1());
            int bcol = foc ? Tokens.accent().accent()
                    : isHovered() ? Color.withAlpha(Tokens.accent().accent(), 0x99)
                                  : Tokens.border().defaultColor();
            r.border(x, y, w, h, rad, Tokens.border().thickness(), bcol);

            float ty0 = y + (h - ty.body().lineHeight()) / 2f;
            r.pushClip(x + pad, y, w - 2 * pad, h);
            if (!empty) ctx.text().draw(text, x + pad, ty0, TextStyle.of(ty.body().weight(), ty.body().size(), Tokens.palette().textHi()));
            else if (!foc) ctx.text().draw(placeholder, x + pad, ty0, TextStyle.of(ty.body().weight(), ty.body().size(), Tokens.palette().textFaint()));
            r.popClip();

            if (foc && ((long) (ctx.time() * 2)) % 2 == 0) {   // ~2 Hz blink
                float tw = empty ? 0f : ctx.text().width(text, ty.body().weight(), ty.body().size());
                r.rect(x + pad + tw + 1f, ty0, 1f, ty.body().lineHeight(), Tokens.accent().accent());
            }
        }
    }

    private static final float OPT_H = 24f;

    /** Compact dropdown option row: subtle accent tint + accent text for the selected value, hover wash for
     *  the rest — no heavy button chrome, so the list stays neat inside the settings popover. */
    private static final class OptionRow extends Component {
        private final String text;
        private final boolean selected;
        private final Runnable onClick;
        OptionRow(String text, boolean selected, Runnable onClick) { this.text = text; this.selected = selected; this.onClick = onClick; }

        @Override public Size measure(float aw, float ah) { return new Size(aw, OPT_H); }
        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            Typography ty = Tokens.type();
            float rad = Tokens.radius().sm();
            if (selected) r.roundedRect(x, y, w, h, rad, Color.withAlpha(Tokens.accent().accent(), 0x24));
            else if (isHovered()) r.roundedRect(x, y, w, h, rad, Tokens.surface().surfaceHi());
            int col = selected ? Tokens.accent().accent() : (isHovered() ? Tokens.palette().textHi() : Tokens.palette().textMuted());
            float lh = ty.body().lineHeight();
            ctx.text().draw(text, x + Tokens.spacing().sm(), y + (OPT_H - lh) / 2f, TextStyle.of(ty.body().weight(), ty.body().size(), col));
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (b == 0 && contains(mx, my)) { onClick.run(); return true; }
            return false;
        }
    }

    /** Free-form container: children are positioned by the screen, not auto-laid. */
    private static final class Pane extends Container {
        void add(Component c) { addChild(c); }
        void clear() { children.clear(); }
        @Override public Size measure(float aw, float ah) { return new Size(aw, ah); }
    }
}
