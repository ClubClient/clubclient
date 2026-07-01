package com.club.ui.menu;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Icon;
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
import com.club.ui.motion.Reveal;
import com.club.ui.motion.Transition;
import com.club.ui.motion.ValueTween;
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
    private static final float RAIL_ROW = 36f;       // category row height (tighter than the old 40 — less dead air)

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
    private Transition[] railText;   // per-category label colour ease (hover / active)
    private Transition entrance;     // screen open: scrim fades in + window rises a few px (no scale)
    private float entranceYOff;      // current window rise offset (added to winY in layoutAll)

    // settings popover (RMB), anchored to a card
    private Module popModule;
    private Column popCol;
    private ScrollArea popScroll;   // wraps popCol so long settings/dropdown lists scroll instead of overflowing
    private int tabIndex;
    private DropdownSetting openDrop;   // the dropdown whose pick-list is expanded in the popover
    private float popX, popY, popW, popH, popAX, popAY, popAW, popAH;
    private int pressOwner;
    // Popover open/close/resize motion: reveal grows it in / out; popHTween eases the target height
    // (dropdown expand, tab switch). Content is clipped to the eased height so any resize reveals smoothly.
    private Reveal popReveal;
    private boolean popClosing;
    private final ValueTween popHTween =
            new ValueTween(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate());

    private float winX, winY, winW, winH, bodyY, bodyH, contentX, contentW, railW, headH, footH;

    // Draggable window: a compact centred rectangle, moved only via the small top grip, position saved to
    // ClubConfig (menuX/menuY, -1 = centred), always clamped fully on-screen.
    private static final float WIN_W = 660f, WIN_H = 380f;   // compact landscape rectangle (smaller in both dims)
    private static final float GRIP_W = 44f, GRIP_H = 5f, GRIP_TOP = 6f;   // grip straddles the top edge (winY - 3)
    private boolean draggingWin;
    private int winGrabX, winGrabY;

    private TextStyle stBrand, stFootMut, stName, stNameOff, stCat, stCatOn;

    public ClubMenuScreen() { super(Text.literal("Club")); }

    private void openHudEditor() { MinecraftClient.getInstance().setScreen(new HudEditorScreen(this)); }

    @Override protected void init() {
        railW = 178; headH = 48; footH = 40;   // taller header so the search bar isn't glued to the top edge
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
        indicator = new Transition(railYRel(catIndex), Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
        railText = new Transition[cats.size()];
        for (int i = 0; i < cats.size(); i++)
            railText[i] = new Transition(i == catIndex ? 1f : 0f,
                    Tokens.motion().durations().fast(), Tokens.motion().easings().standard());
        entrance = new Transition(0f, Tokens.motion().durations().slow(), Tokens.motion().easings().decelerate());
    }

    // Indicator tracks the row offset RELATIVE to bodyY, so it never lags behind the window when it's dragged /
    // rises on open (it only eases when the category actually changes).
    private float railYRel(int i) { return 8 + i * RAIL_ROW; }
    private float railY(int i) { return bodyY + railYRel(i); }   // absolute row position (rows + hit-test)

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
        popClosing = false; popReveal = null;   // render() plays the grow-in on the first frame
        rebuildPopover();
    }

    private void rebuildPopover() {
        float prevOffset = (popScroll != null) ? popScroll.scrollOffset() : 0f;   // survive dropdown-expand / tab-switch rebuild
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
        popScroll.scrollOffset(prevOffset);                  // restore scroll after layout has set the clamp bounds
    }

    private void positionPopover() {
        popX = clamp(popAX, winX + 8, winX + winW - popW - 8);
        float below = popAY + popAH + 8;
        popY = (below + popH <= winY + winH - 8) ? below : (popAY - popH - 8);
        popY = clamp(popY, winY + 8, Math.max(winY + 8, winY + winH - 8 - popH));   // keep fully inside the window
        popScroll.layout(popX + POP_PAD, popY + POP_PAD, popW - 2 * POP_PAD, popH - 2 * POP_PAD);
    }

    /** Deferred close: begins the shrink-out; render() calls {@link #reallyClosePopover} once it has fully collapsed. */
    private void closePopover() {
        if (popModule != null) popClosing = true;
    }

    private void reallyClosePopover() {
        popModule = null; popCol = null; popScroll = null; openDrop = null;
        popReveal = null; popClosing = false;
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
            // When a dropdown is expanded, show ONLY it + its options — the other controls are hidden so the
            // pick-list never pushes/overlaps them (no "teleporting" siblings during the height animation).
            if (openDrop != null && s != openDrop) continue;
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

        if (m.hasReset() && openDrop == null) {   // hidden while a dropdown is expanded (see the guard above)
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
        winW = Math.min(WIN_W, width - 2 * m);
        winH = Math.min(WIN_H, height - 2 * m);
        // saved top-left (or centred default), always clamped fully on-screen (keep room above for the grip)
        ClubConfig cfg = ClubConfig.get();
        float cx = cfg.menuX >= 0 ? cfg.menuX : (width - winW) / 2f;
        float cy = cfg.menuY >= 0 ? cfg.menuY : (height - winH) / 2f;
        winX = clamp(cx, 0, Math.max(0, width - winW));
        winY = clamp(cy, GRIP_TOP, Math.max(GRIP_TOP, height - winH)) + entranceYOff;
        bodyY = winY + headH; bodyH = winH - headH - footH;
        contentX = winX + railW; contentW = winW - railW;

        root.layout(0, 0, width, height);

        float searchW = 200, searchH = 32;
        search.layout(contentX + contentW - 16 - searchW, winY + (headH - searchH) / 2f, searchW, searchH);   // header row, top-right

        // cards at full width (not stretched), just under the search bar so they never overlap it
        float gridW = contentW - 32;
        grid.cols(Math.max(2, (int) (gridW / 172)));
        if (gridScroll != null) gridScroll.layout(contentX + 16, bodyY + 6, gridW, bodyH - 6 - 12);

        if (popModule != null) positionPopover();
    }

    // ---- render --------------------------------------------------------------

    @Override public void render(DrawContext dc, int mouseX, int mouseY, float delta) {
        Ui.beginFrame(dc);
        UiRenderer r = Ui.renderer();
        if (stBrand == null) initStyles();
        uiCtx.setTime(CLOCK_BASE + (System.nanoTime() - startNanos) / 1_000_000_000f);
        float now = uiCtx.time();
        float ep = 1f;
        if (entrance != null) { entrance.target(1f, now); ep = entrance.value(now); }
        entranceYOff = (1f - ep) * 12f;   // window rises into place as it appears (graceful, no scale)

        layoutAll();
        float lg = Tokens.radius().lg();
        Typography ty = Tokens.type();
        float catLh = ty.label().lineHeight();

        // No background scrim: the world stays fully visible so settings apply live (e.g. adjust a hand slider
        // and watch the hand move behind/around the window). Move the window aside via the top grip to see more.

        // three-tone depth: header/frame lightest (surface) > rail medium (bg2) > content darkest (bg1)
        r.roundedRect(winX, winY, winW, winH, lg, Tokens.surface().surface());   // top layer — header/footer/frame (lightest)
        r.rect(winX, bodyY, railW, bodyH, Tokens.surface().bg2());               // categories rail (medium)
        r.rect(contentX, bodyY, contentW, bodyH, Tokens.surface().bg1());        // content/functions (darkest — deepest list panel)

        int dv = Tokens.border().defaultColor();
        r.rect(winX + railW, winY, 1, winH - footH, dv);   // CLUB/rail | content — full height
        r.rect(winX, bodyY, railW, 1, dv);                 // under CLUB — gives the category list a top edge
        r.rect(contentX, bodyY, contentW, 1, dv);          // under the search/header — separates the raised header from the content list
        r.rect(winX, winY + winH - footH, winW, 1, dv);    // above footer

        // header — CLUB wordmark centred in the rail cell (dot + text as one group)
        float clubTextW = uiCtx.text().width("CLUB", ty.display().weight(), ty.display().size());
        float clubX = winX + (railW - (16 + clubTextW)) / 2f;
        r.roundedRect(clubX, winY + headH / 2f - 4, 8, 8, 2, Tokens.accent().accent());
        uiCtx.text().draw("CLUB", clubX + 16, winY + (headH - ty.display().lineHeight()) / 2f, stBrand);

        float fy = winY + winH - footH + (footH - ty.label().lineHeight()) / 2f;
        uiCtx.text().draw("Profile · Default", winX + 18, fy, stFootMut);

        // rail: the active-row highlight pill slides with the accent indicator (drawn once, under the text)
        if (indicator != null) indicator.target(railYRel(catIndex), now);
        float indY = bodyY + (indicator != null ? indicator.value(now) : railYRel(catIndex));
        r.roundedRect(winX + 8, indY + 3, railW - 16, RAIL_ROW - 6, Tokens.radius().sm(), Tokens.surface().surfaceHi());

        // rail categories (text only, no icons) — label colour eases on hover / active
        for (int i = 0; i < cats.size(); i++) {
            float yy = railY(i);
            boolean active = i == catIndex;
            boolean hov = mouseX >= winX && mouseX <= winX + railW && mouseY >= yy && mouseY < yy + RAIL_ROW;
            railText[i].target((active || hov) ? 1f : 0f, now);
            int col = Color.lerp(Tokens.palette().textMuted(), Tokens.palette().textHi(), railText[i].value(now));
            // leading category icon (procedural, diagonal-free) — colour eases with the label
            float isz = 15f, iconX = winX + 16f;
            cats.get(i).icon().draw(r, iconX, yy + (RAIL_ROW - isz) / 2f, isz, col, 1.5f);
            float textX = iconX + isz + 8f, clipR = winX + railW - 14f;
            r.pushClip(textX, yy, clipR - textX, RAIL_ROW);
            uiCtx.text().draw(cats.get(i).name(), textX, yy + (RAIL_ROW - catLh) / 2f,
                    TextStyle.of(ty.label().weight(), ty.label().size(), col));
            r.popClip();
        }

        // cards + search
        root.mouseMoved(mouseX, mouseY);
        root.render(uiCtx);

        if (indicator != null)
            r.rect(winX, indY + 4, 4, RAIL_ROW - 8, Tokens.accent().accent());   // beefier active indicator (slid Y from above)

        r.border(winX, winY, winW, winH, lg, Tokens.border().thickness(), Tokens.border().strong());

        // drag grip — a clearly visible pill straddling the top edge; the ONLY handle for moving the window.
        // Dark halo underneath keeps it visible on any background; brightens to accent on hover/drag.
        float gx = winX + (winW - GRIP_W) / 2f, gy = winY - 3f;
        boolean gripHov = draggingWin
                || (mouseX >= gx - 6 && mouseX <= gx + GRIP_W + 6 && mouseY >= gy - 6 && mouseY <= gy + GRIP_H + 6);
        int gripCol = gripHov ? Tokens.accent().accent() : Color.withAlpha(Tokens.palette().textHi(), 0xC8);
        r.roundedRect(gx - 1, gy - 1, GRIP_W + 2, GRIP_H + 2, (GRIP_H + 2) / 2f, Color.scaleAlpha(Color.withAlpha(0xFF000000, 0x66), ep));
        r.roundedRect(gx, gy, GRIP_W, GRIP_H, GRIP_H / 2f, Color.scaleAlpha(gripCol, ep));

        // popover on top — grows in / shrinks out; content clipped to the eased height (also eases resize)
        if (popModule != null && popScroll != null) {
            if (popReveal == null) {
                popReveal = new Reveal(Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate(), now);
                popHTween.snap(popH, now);
            }
            if (popClosing) popReveal.close(now);
            popHTween.set(popH, now);   // eases the popover height on open + on resize (dropdown open/close)
            if (popClosing && popReveal.gone(now)) {
                reallyClosePopover();
            } else {
                float drawnH = Math.max(1f, popHTween.get(now) * popReveal.progress(now));
                float pr = Tokens.radius().md();
                r.roundedRect(popX, popY, popW, drawnH, pr, Tokens.surface().bg2());
                r.border(popX, popY, popW, drawnH, pr, Tokens.border().thickness(), Tokens.border().strong());
                r.pushClip(popX, popY, popW, drawnH);
                popScroll.mouseMoved(mouseX, mouseY);
                popScroll.render(uiCtx);
                r.popClip();
            }
        }
    }

    private void initStyles() {
        Typography t = Tokens.type();
        stBrand     = TextStyle.of(t.display().weight(), t.display().size(), Tokens.palette().textHi());
        stFootMut   = TextStyle.of(t.label().weight(), t.label().size(),
                Color.lerp(Tokens.palette().textMuted(), Tokens.palette().textHi(), 0.35f));   // a touch more contrast
        stName      = TextStyle.of(t.heading().weight(), t.heading().size(), Tokens.palette().textHi());
        stNameOff   = TextStyle.of(t.heading().weight(), t.heading().size(), Tokens.palette().textMuted());
        stCat       = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textMuted());
        stCatOn     = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textHi());
    }

    @Override public void renderBackground(DrawContext dc, int mx, int my, float d) {
        // Intentionally empty: no darkening and no blur — the world stays fully visible so settings apply live.
    }

    // ---- input ---------------------------------------------------------------

    private boolean insidePop(double mx, double my) {
        return popModule != null && !popClosing && mx >= popX && mx <= popX + popW && my >= popY && my <= popY + popH;
    }

    /** The small top grip is the only place the window can be grabbed (generous hit padding). */
    private boolean overGrip(double mx, double my) {
        float gx = winX + (winW - GRIP_W) / 2f, gy = winY - 3f;
        return mx >= gx - 6 && mx <= gx + GRIP_W + 6 && my >= gy - 6 && my <= gy + GRIP_H + 6;
    }

    @Override public boolean mouseClicked(double mx, double my, int b) {
        focus.clickFocus(mx, my);
        if (b == 0 && overGrip(mx, my)) {   // start moving the window (grip only)
            draggingWin = true; winGrabX = (int) mx - (int) winX; winGrabY = (int) my - (int) winY;
            closePopover(); return true;
        }
        if (insidePop(mx, my)) {
            popScroll.mouseClicked(mx, my, 0); pressOwner = 1; return true;   // RMB behaves as LMB inside; never closes
        }
        if (b == 0 && mx >= winX && mx <= winX + railW && my >= bodyY + 8 && my < bodyY + 8 + cats.size() * RAIL_ROW) {
            int i = (int) ((my - (bodyY + 8)) / RAIL_ROW);
            if (i >= 0 && i < cats.size()) { if (i != catIndex) setCategory(i); return true; }
        }
        if (root.mouseClicked(mx, my, b)) { pressOwner = 2; return true; }
        closePopover();                                        // click on empty space (any button) → close
        pressOwner = 0;
        return super.mouseClicked(mx, my, b);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        if (draggingWin) { draggingWin = false; ClubConfig.save(); return true; }   // persist the new position
        // inside the popover the gesture is routed as left-button (RMB acts as LMB there)
        boolean h = (pressOwner == 1) ? (popScroll != null && popScroll.mouseReleased(mx, my, 0)) : root.mouseReleased(mx, my, b);
        pressOwner = 0;
        return h || super.mouseReleased(mx, my, b);
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (draggingWin) {   // move the window (grip drag), clamped fully on-screen; layoutAll picks it up next frame
            ClubConfig cfg = ClubConfig.get();
            cfg.menuX = (int) clamp((float) mx - winGrabX, 0, Math.max(0, width - winW));
            cfg.menuY = (int) clamp((float) my - winGrabY, GRIP_TOP, Math.max(GRIP_TOP, height - winH));
            return true;
        }
        boolean h = (pressOwner == 1) ? (popScroll != null && popScroll.mouseDragged(mx, my, 0, dx, dy))
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
    private static final int POP_PAD = 8;   // tighter popover gutter — the scrollbar fills the right, so a wide left pad read as empty

    /** Compact module card (name only). LMB = enable/disable (or run the action); RMB = settings popover. */
    private final class ModuleTile extends Component {
        private final Module m;
        private final Transition onT;    // enabled → accent tint (fill/edge/name), eased
        private final Transition hoverT = // hover → tone lift, eased
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
        ModuleTile(Module m) {
            this.m = m;
            this.onT = new Transition(m.enabled() ? 1f : 0f,
                    Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
        }

        @Override public Size measure(float availW, float availH) { return new Size(150f, TILE_H); }

        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            float now = ctx.time();
            float rad = Tokens.radius().md();
            onT.target(m.enabled() ? 1f : 0f, now);
            hoverT.target(hovered ? 1f : 0f, now);
            float onv = onT.value(now), hv = hoverT.value(now);

            // Fill/edge/name all ride one 0->1 "enabled" factor (onv) + a hover factor (hv) — no instant swap.
            int base = Tokens.surface().surface();
            int offFill = Color.lerp(base, Tokens.surface().surfaceHi(), hv);                // off: neutral, hover lifts
            int onFill  = Color.lerp(base, Tokens.accent().accent(), 0.14f + 0.06f * hv);    // on: accent tint, deeper on hover
            int fill = Color.lerp(offFill, onFill, onv);
            int onEdge = Color.withAlpha(Color.lerp(Tokens.accent().accent(), VIOLET, 0.62f), 0xB0);
            int edge = Color.lerp(Tokens.border().defaultColor(), onEdge, onv);
            r.roundedRect(x, y, w, h, rad, fill);
            r.border(x, y, w, h, rad, Tokens.border().thickness(), edge);

            // action-only cards (HUD Editor) read as available (bright), never "off"
            float brightv = m.hasToggle() ? onv : 1f;
            int nameCol = Color.lerp(Tokens.palette().textMuted(), Tokens.palette().textHi(), brightv);
            float nameLh = Tokens.type().heading().lineHeight();
            r.pushClip(x + TILE_PAD, y, w - 2 * TILE_PAD, TILE_H);
            ctx.text().draw(m.name(), x + TILE_PAD, y + (TILE_H - nameLh) / 2f,
                    TextStyle.of(Tokens.type().heading().weight(), Tokens.type().heading().size(), nameCol));
            r.popClip();
        }

        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (!contains(mx, my)) return false;
            if (b == 0) { activate(m); return true; }
            if (b == 1) { if (hasConfigurable(m)) { if (popModule == m && !popClosing) closePopover(); else openPopover(m, x, y, w, h); } return true; }
            return false;
        }
    }

    /** Minimal single-line search input: no icon, placeholder when idle, blinking caret when focused. */
    private static final class SearchField extends Component {
        private final String placeholder;
        private String text = "";
        private Consumer<String> onChange;
        private final Transition focusT =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());
        private final Transition hoverT =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());

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
            float now = ctx.time();
            float rad = Tokens.radius().md();
            float pad = Tokens.spacing().md();
            boolean foc = isFocused();
            boolean empty = text.isEmpty();
            focusT.target(foc ? 1f : 0f, now);
            hoverT.target(isHovered() ? 1f : 0f, now);
            float fv = focusT.value(now), hv = hoverT.value(now);

            r.roundedRect(x, y, w, h, rad, Tokens.surface().bg1());
            // border eases default -> accent@0x99 (hover) -> accent (focus)
            int hoverBorder = Color.lerp(Tokens.border().defaultColor(), Color.withAlpha(Tokens.accent().accent(), 0x99), hv);
            r.border(x, y, w, h, rad, Tokens.border().thickness(), Color.lerp(hoverBorder, Tokens.accent().accent(), fv));

            // leading magnifier glyph (diagonal-free) — tints toward accent on focus, matching the border
            float isz = 13f;
            Icon.SEARCH.draw(r, x + pad, y + (h - isz) / 2f, isz,
                    Color.lerp(Tokens.palette().textMuted(), Tokens.accent().accent(), fv), 1.4f);
            float textX = x + pad + isz + 6f;

            float ty0 = y + (h - ty.body().lineHeight()) / 2f;
            r.pushClip(textX, y, x + w - pad - textX, h);
            if (!empty) ctx.text().draw(text, textX, ty0, TextStyle.of(ty.body().weight(), ty.body().size(), Tokens.palette().textHi()));
            else {   // placeholder dissolves as focus grows (instead of snapping off on first focus/keypress)
                float pa = 1f - fv;
                if (pa > 0.001f) ctx.text().draw(placeholder, textX, ty0, TextStyle.of(ty.body().weight(), ty.body().size(),
                        Color.scaleAlpha(Color.lerp(Tokens.palette().textFaint(), Tokens.palette().textMuted(), 0.4f), pa)));
            }
            r.popClip();

            if (fv > 0.001f) {   // caret: smooth ~1 Hz sine pulse (not a hard blink), scaled by focus
                float blink = 0.15f + 0.85f * (0.5f + 0.5f * (float) Math.sin(now * 2f * (float) Math.PI));
                float tw = empty ? 0f : ctx.text().width(text, ty.body().weight(), ty.body().size());
                r.rect(textX + tw + 1f, ty0, 1f, ty.body().lineHeight(), Color.scaleAlpha(Tokens.accent().accent(), fv * blink));
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
        private final Transition hoverT =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
        OptionRow(String text, boolean selected, Runnable onClick) { this.text = text; this.selected = selected; this.onClick = onClick; }

        @Override public Size measure(float aw, float ah) { return new Size(aw, OPT_H); }
        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            Typography ty = Tokens.type();
            float now = ctx.time();
            float rad = Tokens.radius().sm();
            hoverT.target(isHovered() ? 1f : 0f, now);
            float hv = hoverT.value(now);
            if (selected) r.roundedRect(x, y, w, h, rad, Color.withAlpha(Tokens.accent().accent(), 0x24));   // selected tint (baked)
            else if (hv > 0.001f) r.roundedRect(x, y, w, h, rad, Color.scaleAlpha(Tokens.surface().surfaceHi(), hv));  // hover wash eases in
            int col = selected ? Tokens.accent().accent()
                               : Color.lerp(Tokens.palette().textMuted(), Tokens.palette().textHi(), hv);
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
