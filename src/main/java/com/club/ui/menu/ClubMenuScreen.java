package com.club.ui.menu;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.IconGlyph;
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
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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
    private static final float RAIL_ROW = 36f;       // category row height (tighter than the old 40 — less dead air)

    private final UiContextImpl uiCtx = new UiContextImpl();
    private final FocusManager focus = new FocusManager();
    private final long startNanos = System.nanoTime();

    private final List<Category> cats = MenuContent.build(this::openHudEditor);
    // The selected category survives close/reopen within the session (owner: no reset to Combat).
    private static int lastCatIndex = 0;
    private int catIndex = lastCatIndex;
    private String query = "";

    private final Pane root = new Pane();
    private final Grid grid = new Grid(GRID_COLS, Tokens.spacing().sm());
    private float cardNameSize = NAME_BASE;   // uniform per-category name size (auto-fit in layoutAll)
    // Whole-window fade (11.8): shapes ride the renderer opacity stack; text/icon glyphs can't
    // (known backend limit) — every text/glyph draw multiplies this via Color.scaleAlpha instead.
    private float screenAlpha = 1f;
    private SearchField search;
    private ScrollArea gridScroll;
    private Transition indicator;
    private Transition[] railText;   // per-category label colour ease (hover / active)
    private Transition entrance;     // screen open: scrim fades in + window rises a few px (no scale)
    private float entranceYOff;      // current window rise offset (added to winY in layoutAll)
    // Rail accent bar: eases between CATEGORY colours on switch (identity colour — Stage 11 palette A)
    private int railBarFrom;
    private Transition railBarBlend;
    // Stage-22 wells (passe-partout composition): shallow category tray + deep content well.
    // Zones separate by panel EDGES and depth — every hairline divider is gone.
    private float railWX, railWY, railWW, railWH;   // the shallow tray (hugs the category list)
    private float wellX, wellY, wellW, wellH;       // the deep content well
    // Ambient halo behind the window: thin 2px rings with QUADRATIC falloff — dense right at the
    // window edge, whispering out fast (equal alphas fell off linearly and read as "a big dark
    // buffer" around the window — owner). Cumulative ≈21% at the edge, ≈5% by 12px out.
    private static final float HALO_STEP = 2f;
    private static final int[] HALO_ALPHAS = {9, 8, 7, 6, 5, 4, 3, 3, 2, 2, 2, 1, 1, 1};
    /** Footer version whisper — balances the profile chip on the content well's right axis. */
    private static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("club")
            .map(c -> "Club " + c.getMetadata().getVersion().getFriendlyString())
            .orElse("Club");

    // ---- search reflow (Stage 21, owner's choreography) -----------------------------------------
    // Motion is keyed by Module so it SURVIVES grid rebuilds: fast typing retargets the SAME
    // transitions mid-flight — the interface flows, it never restarts. Phases: exits fade+shrink
    // from 0ms; survivors re-aim at +40ms; enters fade+grow at +70ms. The window/grid container
    // itself never moves (no jelly). Category open staggers enters 17ms/card; search NEVER staggers.
    // ~20% slower than the first cut (owner: «буквально чуток медленнее»).
    private static final float EXIT_DUR = 0.22f, ENTER_DUR = 0.20f, MOVE_DUR = 0.24f;
    private static final float MOVE_DELAY = 0.05f, ENTER_DELAY = 0.08f, CAT_STAGGER = 0.02f;
    /** Exit cascade (owner, round 3): 75ms per card, receding FROM THE TAIL — the last card in
     *  the grid dissolves first and the wave walks back toward the start. */
    private static final float EXIT_STAGGER = 0.075f;
    private static final float TILE_SCALE_FROM = 0.97f;   // enter 0.97→1; exit mirrors it
    private final java.util.HashMap<Module, TileMotion> tileMotion = new java.util.HashMap<>();
    // Cards that stopped matching keep painting HERE while they dissolve (they left the grid already).
    private final java.util.LinkedHashMap<Module, ModuleTile> leaving = new java.util.LinkedHashMap<>();
    private Transition noteFade;   // "No matching modules" — single smooth fade in/out

    /** One card's motion across rebuilds: eased position + fade (the 0.97→1 scale rides the fade). */
    private static final class TileMotion {
        Transition px, py;      // eased position — created snapped on first sighting (no fly-in)
        Transition fade;        // 0→1 enter / →0 exit; recreated per direction (durations differ),
                                //   always seeded from the current value → turn-arounds stay smooth
        float tx, ty;           // last applied position target
        boolean hasPos;
        float showDelay;        // enter delay (stagger / +70ms phase), resolved on first render —
        float showAt = -1f;     //   rebuilds can run before the ui clock ticks (init)
        boolean shown;
        float hideAt = -1f;     // exit gate: the dissolve starts once time passes this (exit cascade)
        boolean scaleIn = true; // search language: fade+scale; category cascades are FADE-ONLY
                                //   (the 0.97→1 pop per card read as popcorn — owner)
        float moveAt;           // survivor gate: position re-aims only after this (+40ms phase)
        boolean movePending;
        boolean leaving;
        float bx, by, bw, bh;   // last visual box — the frozen stage for a dissolving card
    }

    // settings popover (RMB), anchored to a card
    private Module popModule;
    private Column popCol;
    private ScrollArea popScroll;   // wraps popCol so long settings/dropdown lists scroll instead of overflowing
    private int tabIndex;
    private Transition segSlide;    // segmented-tab pill position — outer, so it survives popover rebuilds
    private DropdownSetting openDrop;   // the dropdown whose pick-list is expanded in the popover
    private float popX, popY, popW, popH, popAX, popAY, popAH;
    private int pressOwner;
    // Popover open/close/resize motion: reveal grows it in / out; popHTween eases the target height
    // (dropdown expand, tab switch). Content is clipped to the eased height so any resize reveals smoothly.
    private Reveal popReveal;
    private boolean popClosing;
    private final ValueTween popHTween =
            new ValueTween(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate());

    private float winX, winY, winW, winH, bodyY, bodyH, headH, footH;

    // Fixed centred window (owner decision 2026-07-02): dragging + grip removed — the menu always
    // sits dead centre. Compact 660 width kept (owner): 4 columns fit via SMALLER cards, not a
    // wider window — hence the vertical card composition (chip on top, name under).
    private static final float WIN_W = 660f, WIN_H = 380f;
    private static final int GRID_COLS = 4;
    private boolean closing;   // Right-Shift close: plays the entrance in reverse, then really closes

    private int stFootMutCol;      // footer tone (colour only — styles are built inline now: fade needs live alpha)
    private boolean stylesInit;

    public ClubMenuScreen() { super(Text.literal("Club")); }

    private void openHudEditor() { MinecraftClient.getInstance().setScreen(new HudEditorScreen(this)); }

    @Override protected void init() {
        headH = 48; footH = 36;   // footer slimmed with its divider gone (Stage 22)
        query = ""; popModule = null; popCol = null; openDrop = null; pressOwner = 0;
        search = new SearchField("Search modules").onChange(q -> { query = q; rebuildGrid(GridRebuild.SEARCH); layoutAll(); });
        gridScroll = new ScrollArea(grid);
        root.clear();
        root.add(search);
        root.add(gridScroll);
        focus.clear();
        focus.register(search);
        rebuildGrid(GridRebuild.OPEN);
        layoutAll();
        indicator = new Transition(railYRel(catIndex), Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
        railText = new Transition[cats.size()];
        for (int i = 0; i < cats.size(); i++)
            railText[i] = new Transition(i == catIndex ? 1f : 0f,
                    Tokens.motion().durations().fast(), Tokens.motion().easings().standard());
        entrance = new Transition(0f, Tokens.motion().durations().slow(), Tokens.motion().easings().decelerate());
        railBarFrom = catAccent(catIndex);
        railBarBlend = new Transition(1f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
    }

    /** Identity colour of a category (Stage 11 palette A) — keyed off its semantic icon. */
    private int catAccent(int i) {
        return switch (cats.get(i).icon()) {
            case COMBAT  -> Tokens.categories().combat();
            case VISUALS -> Tokens.categories().visuals();
            case PLAYER  -> Tokens.categories().player();
            default      -> Tokens.categories().misc();
        };
    }

    /** Rail bar colour, easing from the previous category's hue to the current one. */
    private int railBarColor(float now) {
        return Color.lerp(railBarFrom, catAccent(catIndex), railBarBlend.value(now));
    }

    // Indicator tracks the row offset RELATIVE to bodyY, so it never lags behind the window when it's dragged /
    // rises on open (it only eases when the category actually changes).
    private float railYRel(int i) { return 10 + i * RAIL_ROW; }   // tray top (+2) + tray padding (+8)
    private float railY(int i) { return bodyY + railYRel(i); }   // absolute row position (rows + hit-test)

    // ---- state ---------------------------------------------------------------

    private void setCategory(int i) {
        float now = uiCtx.time();
        railBarFrom = railBarColor(now);   // ease the bar from wherever its colour currently is
        catIndex = i; lastCatIndex = i;
        railBarBlend = new Transition(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
        railBarBlend.target(1f, now);
        closePopover();
        query = "";
        if (search != null) search.clear();
        rebuildGrid(GridRebuild.CATEGORY);
        layoutAll();
    }

    /** Name matching (name-only since 21.2): short queries (&lt;3 chars) anchor to WORD STARTS —
     *  "n" means the No-* family, not "scree[n]" (owner); 3+ chars fall back to substring so
     *  fragments like "stret" still hit "Screen Stretch". */
    private static boolean nameMatches(String name, String q) {
        if (q.length() >= 3) return name.contains(q);
        if (name.startsWith(q)) return true;
        for (int sp = name.indexOf(' '); sp >= 0; sp = name.indexOf(' ', sp + 1))
            if (name.startsWith(q, sp + 1)) return true;
        return false;
    }

    /** Grid rebuild flavours: menu OPEN (cards ride the window entrance, no per-card animation),
     *  CATEGORY switch (instant swap + 20ms/card cascade), live SEARCH (the Stage-21 reflow). */
    private enum GridRebuild { OPEN, CATEGORY, SEARCH }

    private void rebuildGrid(GridRebuild mode) {
        float now = uiCtx.time();
        String q = query.toLowerCase(Locale.ROOT);
        int accent = catAccent(catIndex);
        java.util.List<Module> match = new java.util.ArrayList<>();
        for (Module m : cats.get(catIndex).modules()) {
            if (!q.isEmpty() && !nameMatches(m.name().toLowerCase(Locale.ROOT), q)) continue;
            match.add(m);
        }

        if (mode != GridRebuild.SEARCH) {
            // fresh set — no cross-animation. CATEGORY cascades the enters (owner: only here);
            // OPEN shows the cards at once, riding the whole-window entrance fade as before
            // (a cascade during the window rise read as a glitch).
            tileMotion.clear(); leaving.clear();
            grid.clear();
            int i = 0;
            for (Module m : match) {
                TileMotion tm = new TileMotion();
                if (mode == GridRebuild.CATEGORY) {
                    tm.fade = new Transition(0f, ENTER_DUR, Tokens.motion().easings().decelerate());
                    tm.showDelay = i++ * CAT_STAGGER;
                    tm.scaleIn = false;   // quiet cascade: fade only, no per-card pop
                } else {
                    tm.fade = new Transition(1f, ENTER_DUR, Tokens.motion().easings().decelerate());
                    tm.shown = true;
                }
                tileMotion.put(m, tm);
                grid.add(new ModuleTile(m, accent));
            }
            return;
        }

        // live search — phase 1: cards that stopped matching dissolve ONE AFTER ANOTHER, receding
        // FROM THE TAIL (owner): the last exiting card in grid order goes first, the wave walks
        // back toward the start. Each exit holds (seeded fade) until its gate fires in render.
        java.util.List<TileMotion> exits = new java.util.ArrayList<>();
        for (var c : grid.children()) {
            ModuleTile t = (ModuleTile) c;
            if (match.contains(t.m)) continue;
            TileMotion tm = tileMotion.get(t.m);
            if (tm == null || tm.leaving) continue;
            if (!tm.hasPos) { tileMotion.remove(t.m); continue; }   // never rendered — nothing to dissolve
            tm.leaving = true; tm.shown = true; tm.movePending = false; tm.scaleIn = true;   // exits always mirror the search scale
            tm.fade = new Transition(tm.fade.value(now), EXIT_DUR, Tokens.motion().easings().standard());
            exits.add(tm);
            leaving.put(t.m, t);
        }
        for (int j = 0; j < exits.size(); j++)
            exits.get(j).hideAt = now + (exits.size() - 1 - j) * EXIT_STAGGER;
        grid.clear();
        for (Module m : match) {
            TileMotion tm = tileMotion.get(m);
            if (tm == null) {                     // brand new match — fade+grow in at +70ms
                tm = new TileMotion();
                tm.fade = new Transition(0f, ENTER_DUR, Tokens.motion().easings().decelerate());
                tm.showDelay = ENTER_DELAY;
                tileMotion.put(m, tm);
            } else if (tm.leaving) {              // matched again mid-exit — turn around, no restart
                tm.leaving = false; tm.shown = true; tm.hideAt = -1f;
                Transition f = new Transition(tm.fade.value(now), ENTER_DUR, Tokens.motion().easings().decelerate());
                f.target(1f, now);
                tm.fade = f;
                leaving.remove(m);
            } else if (tm.hasPos) {               // survivor — glides to its new slot after +40ms
                tm.movePending = true; tm.moveAt = now + MOVE_DELAY;
            }
            grid.add(new ModuleTile(m, accent));
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
        popModule = m; popAX = ax; popAY = ay; popAH = ah; tabIndex = 0; openDrop = null;
        popClosing = false; popReveal = null;   // render() plays the grow-in on the first frame
        segSlide = new Transition(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
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
        int accent = catAccent(catIndex);   // 11.9: the popover speaks its category's colour

        List<Setting> settings;
        if (m.hasTabs()) {
            List<Tab> tabs = m.tabs();
            String[] labels = new String[tabs.size()];
            for (int i = 0; i < tabs.size(); i++) labels[i] = tabs.get(i).label();
            col.add(new SegmentRow(labels, accent));
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
                Button field = new Button(d.options()[cur]).variant(Button.Variant.GHOST).accent(accent)
                        .onClick(() -> { openDrop = (openDrop == d) ? null : d; rebuildPopover(); });
                row.add(field);
                col.add(row); focus.register(field);
                if (openDrop == d) {
                    for (int i = 0; i < d.options().length; i++) {
                        final int oi = i;
                        col.add(new OptionRow(d.options()[i], i == cur, accent,
                                () -> { d.set().accept(oi); openDrop = null; rebuildPopover(); }));
                    }
                }
            } else if (s instanceof ActionSetting) {
                Component ctrl = buildControl(s, accent);
                Row rr = new Row(); rr.add(ctrl); col.add(rr); focus.register(ctrl);
            } else {
                Component ctrl = buildControl(s, accent);
                Row rr = new Row().crossAlign(CrossAlign.CENTER);
                rr.add(new Label(s.label(), Tokens.type().label()).color(Tokens.palette().textMuted()), Sizing.fill());
                rr.add(ctrl);
                col.add(rr); focus.register(ctrl);
            }
        }

        if (m.hasReset() && openDrop == null) {   // hidden while a dropdown is expanded (see the guard above)
            Button reset = new Button("Reset to Default").variant(Button.Variant.GHOST).accent(accent)
                    .onClick(() -> { m.reset().run(); openDrop = null; rebuildPopover(); });
            Row rr = new Row(); rr.add(Spacer.fill()); rr.add(reset);
            col.add(rr); focus.register(reset);
        }
        return col;
    }

    private static int clampIdx(DropdownSetting d) {
        return Math.max(0, Math.min(d.get().getAsInt(), d.options().length - 1));
    }

    private Component buildControl(Setting s, int accent) {
        if (s instanceof SliderSetting sl) return new Slider(sl.get().get(), sl.min(), sl.max(), sl.step()).onChange(sl.set()).accent(accent);
        if (s instanceof ToggleSetting t)  return new Toggle(t.get().getAsBoolean()).onChange(t.set()).accent(accent);
        if (s instanceof CheckSetting ck)  return new Checkbox(ck.get().getAsBoolean()).onChange(ck.set()).accent(accent);
        if (s instanceof ActionSetting a)  return new Button(a.label()).variant(Button.Variant.GHOST).onClick(a.action()).accent(accent);
        throw new IllegalStateException("unsupported inline setting: " + s);
    }

    // ---- layout --------------------------------------------------------------

    private void layoutAll() {
        float m = 24;
        winW = Math.min(WIN_W, width - 2 * m);
        winH = Math.min(WIN_H, height - 2 * m);
        winX = (width - winW) / 2f;                     // always dead centre (no drag, no saved position)
        winY = (height - winH) / 2f + entranceYOff;
        bodyY = winY + headH; bodyH = winH - headH - footH;

        root.layout(0, 0, width, height);

        // Stage-22 wells: the shallow tray hugs the category list; the deep well owns the rest.
        // 16px of breathing between them (owner: two figures, not one), 12px to the frame edges.
        railWX = winX + 12; railWY = bodyY + 2; railWW = 172;
        railWH = 16 + cats.size() * RAIL_ROW;
        wellX = railWX + railWW + 16; wellY = bodyY + 2;
        wellW = winX + winW - 12 - wellX;
        wellH = bodyH - 2 - 6;

        float searchW = 200, searchH = 32;
        // the search's RIGHT edge lands exactly on the content well's right line (header on the grid)
        search.layout(winX + winW - 12 - searchW, winY + (headH - searchH) / 2f, searchW, searchH);

        // fixed 4-column grid (like the reference board) — sparse rows are fine for now
        float gridW = wellW - 24;
        grid.cols(GRID_COLS);

        // One name size per category (11.7): the largest size <= NAME_BASE at which the LONGEST
        // module name of the category still fits the card's text slot. All visible names share it —
        // uniform look, nothing ever truncates OR overflows (the floor is sanity-only; with sane
        // names sizes stay >= ~9px). Computed over the whole category (not the search subset) so
        // the size doesn't jump while typing.
        float cellW = (gridW - (GRID_COLS - 1) * Tokens.spacing().sm()) / GRID_COLS;
        float slot = cellW - (TILE_PAD + CHIP + NAME_GAP + TILE_PAD);
        float fit = NAME_BASE;
        var nameWeight = Tokens.type().heading().weight();
        for (Module mod : cats.get(catIndex).modules()) {
            float atBase = Ui.text().width(mod.name(), nameWeight, NAME_BASE);
            if (atBase > slot) fit = Math.min(fit, Math.max(NAME_MIN, slot * NAME_BASE / atBase));
        }
        cardNameSize = fit;

        if (gridScroll != null) gridScroll.layout(wellX + 12, wellY + 12, gridW, wellH - 24);

        if (popModule != null) positionPopover();
    }

    // ---- render --------------------------------------------------------------

    @Override public void render(DrawContext dc, int mouseX, int mouseY, float delta) {
        Ui.beginFrame(dc);
        UiRenderer r = Ui.renderer();
        if (!stylesInit) initStyles();
        uiCtx.setTime(CLOCK_BASE + (System.nanoTime() - startNanos) / 1_000_000_000f);
        float now = uiCtx.time();
        float ep = 1f;
        if (entrance != null) { entrance.target(closing ? 0f : 1f, now); ep = entrance.value(now); }
        entranceYOff = (1f - ep) * 12f;   // window rises into place on open; sinks back out on close
        if (closing && ep <= 0.001f) {    // reverse animation finished — really close now
            MinecraftClient.getInstance().setScreen(null);
            return;
        }

        layoutAll();
        screenAlpha = ep;
        r.pushOpacity(ep);   // whole-window fade: shapes here; text/glyphs multiply screenAlpha
        float lg = Tokens.radius().lg();
        Typography ty = Tokens.type();
        float catLh = ty.label().lineHeight();

        // No background scrim: the world stays fully visible so settings apply live (e.g. adjust a hand slider
        // and watch the hand move behind/around the window).

        // Ambient halo BEHIND the window (Stage 22): thin rings, quadratic falloff — a shadow that
        // HUGS the window and dissipates fast, not a dark buffer. World separation, not UI depth.
        for (int i = HALO_ALPHAS.length; i >= 1; i--) {
            float s = i * HALO_STEP;
            r.roundedRect(winX - s, winY - s + s * 0.3f, winW + 2 * s, winH + 2 * s, lg + s,
                    Color.withAlpha(0xFF000000, HALO_ALPHAS[i - 1]));
        }

        // Passe-partout (Stage 22): ONE frame tone + two wells whose edges do the separating —
        // shallow category tray (Δ≈1 tone step), deep content well (Δ≈3). Zero hairline dividers.
        float md = Tokens.radius().md();
        r.roundedRect(winX, winY, winW, winH, lg, Tokens.surface().surface());
        r.roundedRect(railWX, railWY, railWW, railWH, md, Tokens.surface().wellShallow());
        r.roundedRect(wellX, wellY, wellW, wellH, md, Tokens.surface().well());

        // header — CLUB wordmark centred on the category tray's axis (the header sits on the grid)
        float logoSz = 15f;
        float clubTextW = uiCtx.text().width("CLUB", ty.display().weight(), ty.display().size());
        float clubX = railWX + (railWW - (logoSz + 7 + clubTextW)) / 2f;
        IconGlyph.LOGO.draw(uiCtx, clubX, winY + (headH - logoSz) / 2f, logoSz,
                Color.scaleAlpha(Tokens.accent().accent(), ep));
        uiCtx.text().draw("CLUB", clubX + logoSz + 7, winY + (headH - ty.display().lineHeight()) / 2f,
                TextStyle.of(ty.display().weight(), ty.display().size(), Color.scaleAlpha(Tokens.palette().textHi(), ep)));

        // footer — the profile chip (the future switcher: mark + name + chevron) on the tray axis,
        // a version whisper on the content well's right line. No caption floating in a void.
        float fooY = winY + winH - footH;
        float chipCy = fooY + footH / 2f;
        r.roundedRect(railWX, chipCy - 8, 16, 16, 8, Color.withAlpha(Tokens.accent().accent(), 0x24));
        IconGlyph.LOGO.draw(uiCtx, railWX + 3.5f, chipCy - 8 + 3.5f, 9,
                Color.scaleAlpha(Tokens.accent().accent(), ep));
        float profX = railWX + 16 + 8;
        uiCtx.text().draw("Default", profX, chipCy - ty.label().lineHeight() / 2f,
                TextStyle.of(ty.label().weight(), ty.label().size(), Color.scaleAlpha(stFootMutCol, ep)));
        float profW = uiCtx.text().width("Default", ty.label().weight(), ty.label().size());
        int chevCol = Color.scaleAlpha(Tokens.palette().textFaint(), ep);
        float chvX = profX + profW + 7, chvY = chipCy - 2;
        for (int i = 0; i < 4; i++) r.rect(chvX + i, chvY + i, 7 - 2 * i, 1, chevCol);   // tiny ▾
        float verW = uiCtx.text().width(VERSION, ty.label().weight(), 11.5f);
        uiCtx.text().draw(VERSION, winX + winW - 12 - verW, chipCy - uiCtx.text().lineHeight(ty.label().weight(), 11.5f) / 2f,
                TextStyle.of(ty.label().weight(), 11.5f, Color.scaleAlpha(Tokens.palette().textDesc(), ep)));

        // rail: the active-row highlight pill slides with the accent indicator (drawn once, under the text)
        if (indicator != null) indicator.target(railYRel(catIndex), now);
        float indY = bodyY + (indicator != null ? indicator.value(now) : railYRel(catIndex));
        r.roundedRect(railWX + 8, indY + 3, railWW - 16, RAIL_ROW - 6, Tokens.radius().sm(), Tokens.surface().surfaceHi());

        // rail categories inside the tray — label colour eases on hover / active; icon rides the same ease
        for (int i = 0; i < cats.size(); i++) {
            float yy = railY(i);
            boolean active = i == catIndex;
            boolean hov = mouseX >= railWX && mouseX <= railWX + railWW && mouseY >= yy && mouseY < yy + RAIL_ROW;
            railText[i].target((active || hov) ? 1f : 0f, now);
            int col = Color.scaleAlpha(
                    Color.lerp(Tokens.palette().textMuted(), Tokens.palette().textHi(), railText[i].value(now)), ep);
            float isz = 15f, iconX = railWX + 20f;
            cats.get(i).icon().draw(uiCtx, iconX, yy + (RAIL_ROW - isz) / 2f, isz, col);
            float textX = iconX + isz + 8f, clipR = railWX + railWW - 10f;
            r.pushClip(textX, yy, clipR - textX, RAIL_ROW);
            uiCtx.text().draw(cats.get(i).name(), textX, yy + (RAIL_ROW - catLh) / 2f,
                    TextStyle.of(ty.label().weight(), ty.label().size(), col));
            r.popClip();
        }

        // cards + search
        root.mouseMoved(mouseX, mouseY);
        root.render(uiCtx);

        // Empty search: a quiet centred note. One SMOOTH fade — it waits for the dissolving cards
        // to finish, then eases in (stepped alpha read as a glitch); retargets to 0 the moment
        // anything matches again.
        boolean noteOn = !query.isEmpty() && grid.children().isEmpty() && leaving.isEmpty();
        if (noteFade == null) noteFade = new Transition(0f, ENTER_DUR, Tokens.motion().easings().decelerate());
        noteFade.target(noteOn ? 1f : 0f, now);
        float noteA = noteFade.value(now);
        if (noteA > 0.001f) {
            uiCtx.text().draw("No matching modules", wellX + wellW / 2f, wellY + wellH / 2f - ty.body().lineHeight() / 2f,
                    TextStyle.of(ty.body().weight(), ty.body().size(),
                            Color.scaleAlpha(Tokens.palette().textMuted(), screenAlpha * noteA)).align(Align.CENTER));
        }

        // Stage-21 reflow: cards that stopped matching dissolve over their old spots — they left
        // the grid already, so they paint here, inside the same scroll viewport clip. Pruned once
        // fully dissolved. Never receive input (not in the component tree).
        if (!leaving.isEmpty()) {
            r.pushClip(wellX + 12, wellY + 12, wellW - 24, wellH - 24);
            for (var it = leaving.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                en.getValue().render(uiCtx);
                TileMotion tm = tileMotion.get(en.getKey());
                if (tm == null || tm.fade.value(now) <= 0.001f) {
                    it.remove();
                    tileMotion.remove(en.getKey());
                }
            }
            r.popClip();
        }

        if (indicator != null)   // active indicator bar: CATEGORY colour, hugging the tray's left edge
            r.roundedRect(railWX, indY + 4, 4, RAIL_ROW - 8, 2f, railBarColor(now));

        r.border(winX, winY, winW, winH, lg, Tokens.border().thickness(), Tokens.border().strong());

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
        r.popOpacity();
    }

    private void initStyles() {
        stFootMutCol = Color.lerp(Tokens.palette().textMuted(), Tokens.palette().textHi(), 0.35f);   // a touch more contrast
        stylesInit = true;
    }

    @Override public void renderBackground(DrawContext dc, int mx, int my, float d) {
        // Intentionally empty: no darkening and no blur — the world stays fully visible so settings apply live.
    }

    // ---- input ---------------------------------------------------------------

    private boolean insidePop(double mx, double my) {
        return popModule != null && !popClosing && mx >= popX && mx <= popX + popW && my >= popY && my <= popY + popH;
    }

    /** Starts the reverse-of-open animation; render() really closes once it has fully played out.
     *  The cursor is hidden AND the camera is live IMMEDIATELY (owner: both must return at the
     *  keypress): a raw GLFW grab + the {@code cursorLocked} flag via accessor — NOT
     *  {@code Mouse.lockCursor()}, which in 1.21.1 calls {@code setScreen(null)} internally and
     *  would kill the screen before the reverse animation renders a single frame (the Stage 12.2
     *  regression). {@code Mouse.tick()} gates mouse-look purely on the flag, so look works while
     *  the window fades; render()'s real {@code setScreen(null)} then no-ops through vanilla's
     *  already-locked guard. Clicks are swallowed by the closing guard meanwhile. */
    private void beginClose() {
        if (closing) return;
        closing = true;
        closePopover();
        MinecraftClient mc = MinecraftClient.getInstance();
        double cx = mc.getWindow().getWidth() / 2.0, cy = mc.getWindow().getHeight() / 2.0;
        InputUtil.setCursorParameters(mc.getWindow().getHandle(), GLFW_CURSOR_DISABLED, cx, cy);
        var mouse = (com.club.mixin.MouseAccessor) mc.mouse;
        mouse.club$setCursorLocked(true);
        // mirror vanilla lockCursor: re-centre the tracked pos + drop pending deltas, or the first
        // look after closing dumps (centre − last menu pos) into the camera — a teleport jerk
        mouse.club$setX(cx); mouse.club$setY(cy);
        mouse.club$setCursorDeltaX(0); mouse.club$setCursorDeltaY(0);
        KeyBinding.updatePressedStates();   // vanilla lockCursor does this too — keys stay coherent
    }

    @Override public boolean mouseClicked(double mx, double my, int b) {
        if (closing) return true;   // window is fading out — swallow clicks
        focus.clickFocus(mx, my);
        if (insidePop(mx, my)) {
            popScroll.mouseClicked(mx, my, 0); pressOwner = 1; return true;   // RMB behaves as LMB inside; never closes
        }
        if (b == 0 && mx >= railWX && mx <= railWX + railWW && my >= bodyY + 10 && my < bodyY + 10 + cats.size() * RAIL_ROW) {
            int i = (int) ((my - (bodyY + 10)) / RAIL_ROW);
            if (i >= 0 && i < cats.size()) { if (i != catIndex) setCategory(i); return true; }
        }
        if (root.mouseClicked(mx, my, b)) { pressOwner = 2; return true; }
        closePopover();                                        // click on empty space (any button) → close
        pressOwner = 0;
        return super.mouseClicked(mx, my, b);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        // inside the popover the gesture is routed as left-button (RMB acts as LMB there)
        boolean h = (pressOwner == 1) ? (popScroll != null && popScroll.mouseReleased(mx, my, 0)) : root.mouseReleased(mx, my, b);
        pressOwner = 0;
        return h || super.mouseReleased(mx, my, b);
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
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
        // The menu closes on the SAME key that opens it (owner decision 2026-07-02) — with the
        // reverse-of-open animation, but NOT while typing in the search (the bound letter must
        // type, not close). ESC only closes the settings popover, never the menu.
        if (com.club.ClubClient.openMenuKey.matchesKey(k, scan)
                && !(search != null && search.isFocused())) { beginClose(); return true; }
        if (k == GLFW_KEY_ESCAPE && popModule != null) { closePopover(); return true; }
        if (k == GLFW_KEY_TAB) { if ((mods & GLFW_MOD_SHIFT) != 0) focus.previous(); else focus.next(); return true; }
        return focus.keyPressed(k, scan, mods) || super.keyPressed(k, scan, mods);
    }
    @Override public boolean charTyped(char c, int mods) {
        // "/" jumps into the search (the keycap hint in the field advertises it); consumed so the
        // slash itself never lands in the query
        if (c == '/' && search != null && !search.isFocused() && !closing) { focus.focus(search); return true; }
        return focus.charTyped(c, mods) || super.charTyped(c, mods);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean shouldPause() { return false; }

    // ---- gui-move (Stage 12): movement stays live while the menu is open --------

    private boolean[] moveWasDown;   // per-binding raw state — setPressed only on EDGES (sticky-safe)

    /** Movement keys keep working while the menu is open (WASD/jump/sneak/sprint): each tick the
     *  RAW keyboard state of whatever keys those actions are bound to is fed into the vanilla
     *  bindings — the camera stays GUI-locked, other screens stay blocked. Typing in the search
     *  field suspends it (otherwise a "wasd" query would walk the player around). setPressed is
     *  called only when the raw state CHANGES, mirroring vanilla key events — sneak/sprint may be
     *  StickyKeyBindings (toggle mode) and a per-tick setPressed(true) would flip them endlessly. */
    @Override public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        boolean typing = search != null && search.isFocused();
        long handle = mc.getWindow().getHandle();
        KeyBinding[] moves = {
                mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey,
                mc.options.jumpKey, mc.options.sneakKey, mc.options.sprintKey };
        if (moveWasDown == null) moveWasDown = new boolean[moves.length];
        for (int i = 0; i < moves.length; i++) {
            boolean down = !typing && rawKeyDown(handle, moves[i]);
            if (down != moveWasDown[i]) {
                moves[i].setPressed(down);
                moveWasDown[i] = down;
            }
        }
    }

    /** True if the physical key a binding is bound to is currently held (keyboard-bound only). */
    private static boolean rawKeyDown(long handle, KeyBinding binding) {
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        if (key.getCategory() != InputUtil.Type.KEYSYM) return false;   // mouse-bound → leave to vanilla
        int code = key.getCode();
        return code != GLFW_KEY_UNKNOWN && InputUtil.isKeyPressed(handle, code);
    }

    // ---- module card ---------------------------------------------------------

    // Card anatomy (Stage 11, approved): icon chip + centered state stripe under it + name + ghost glyph
    // — HORIZONTAL, per the reference board. Compact 4-column metrics (11.7): chip 22, tight pads; the
    // name never truncates — its SIZE fits the space (one uniform size per category, see cardNameSize).
    // State lives in COLOUR only (grey <-> category hue via one eased factor) — geometry never jumps.
    private static final float TILE_H = 58f, TILE_PAD = 7f, NAME_GAP = 6f;
    private static final float CHIP = 22f, CHIP_RAD = 7f, CHIP_ICON = 13f;
    private static final float STRIPE_W = 14f, STRIPE_H = 3f, STRIPE_GAP = 4f;
    private static final float NAME_BASE = 12f;
    private static final float NAME_MIN = 6f;   // hard sanity floor ONLY — the fit math guarantees no
                                                // overflow (11.8 fix: a 9px floor let long names spill)
    private static final int POP_PAD = 8;   // tighter popover gutter — the scrollbar fills the right, so a wide left pad read as empty

    /** Module card: icon chip + centered state stripe + name + ghost underlay.
     *  LMB = enable/disable (or run the action); RMB = settings popover. */
    private final class ModuleTile extends Component {
        private final Module m;
        private final int accent;        // this category's identity colour (palette A)
        private final Transition onT;    // enabled → chip/stripe/name/ghost ride one eased factor
        private final Transition hoverT = // hover → tone lift, eased
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
        // Ghost geometry: deterministic per module NAME, so the underlays vary in size/position/crop
        // and read organic instead of stamped (owner feedback 2026-07-02). Large + heavily cropped.
        private final float ghostSz, ghostYOff, ghostBleed, ghostA;
        ModuleTile(Module m, int accent) {
            this.m = m;
            this.accent = accent;
            // action-only cards seed at 1 (always "available") — else every grid rebuild replays a grey->colour fade
            this.onT = new Transition(m.hasToggle() ? (m.enabled() ? 1f : 0f) : 1f,
                    Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
            int hsh = m.name().hashCode();
            // 11.8 "cleaner": smaller, quieter, tighter to the corner — near-invisible when OFF so a
            // full grid doesn't read as noise; the ghost brightening is itself a state cue.
            ghostSz    = 52f + (hsh & 11);                     // 52..63px
            ghostYOff  = ((hsh >>> 4) % 9) - 4f;               // -4..+4px vertical drift
            ghostBleed = 8f + ((hsh >>> 8) & 7);               // 8..15px past the right edge
            ghostA     = 0.02f + ((hsh >>> 12) & 3) * 0.005f;  // 2..3.5% when OFF
        }

        @Override public Size measure(float availW, float availH) { return new Size(150f, TILE_H); }

        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            float now = ctx.time();
            float rad = Tokens.radius().md();

            // Stage-21 reflow: draw at the EASED position, box-scaled 0.97→1 by the fade. Bounds are
            // mutated for this frame only (layoutAll re-assigns them next frame), so input follows
            // the visual card. A dissolving card is frozen at its last visual box.
            TileMotion tm = tileMotion.get(m);
            float ta = 1f;
            if (tm != null) {
                if (tm.showAt < 0f) tm.showAt = now + tm.showDelay;   // resolve vs the live ui clock
                if (!tm.shown && now >= tm.showAt) { tm.shown = true; tm.fade.target(1f, now); }
                if (tm.hideAt >= 0f && now >= tm.hideAt) { tm.hideAt = -1f; tm.fade.target(0f, now); }   // its turn in the exit cascade
                ta = tm.fade.value(now);
                // Positions ease in WINDOW space: the entrance rise (and any window recentre) moves
                // cards rigidly with the frame — easing screen coords made them lag/chase the window
                // ("подпрыгивают" after open). Only slot-to-slot moves animate.
                float relX = x - winX, relY = y - winY;
                float ex, ey;
                if (tm.leaving) {
                    ex = winX + tm.bx; ey = winY + tm.by; w = tm.bw; h = tm.bh;
                    hovered = false;   // a dissolving card must not keep its hover lift
                } else {
                    if (!tm.hasPos) {   // first sighting — snap, never fly in from nowhere
                        tm.px = new Transition(relX, MOVE_DUR, Tokens.motion().easings().standard());
                        tm.py = new Transition(relY, MOVE_DUR, Tokens.motion().easings().standard());
                        tm.tx = relX; tm.ty = relY; tm.hasPos = true;
                    } else if (tm.movePending) {
                        if (now >= tm.moveAt) {   // the +50ms phase — survivors re-aim now
                            tm.movePending = false;
                            tm.tx = relX; tm.ty = relY;
                            tm.px.target(relX, now); tm.py.target(relY, now);
                        }
                    } else if (tm.tx != relX || tm.ty != relY) {   // grid change outside search — re-aim
                        tm.tx = relX; tm.ty = relY;
                        tm.px.target(relX, now); tm.py.target(relY, now);
                    }
                    tm.bx = tm.px.value(now); tm.by = tm.py.value(now);   // window-relative visual spot
                    tm.bw = w; tm.bh = h;
                    ex = winX + tm.bx; ey = winY + tm.by;
                }
                float sc = tm.scaleIn ? TILE_SCALE_FROM + (1f - TILE_SCALE_FROM) * Math.min(1f, ta) : 1f;
                float sw = w * sc, sh = h * sc;
                x = ex + (w - sw) / 2f; y = ey + (h - sh) / 2f; w = sw; h = sh;
                if (ta <= 0.001f) return;   // pre-delay or fully dissolved — nothing to draw
            }

            // action-only cards (HUD Editor) read as available (full colour), never "off"
            onT.target(m.hasToggle() ? (m.enabled() ? 1f : 0f) : 1f, now);
            hoverT.target(hovered ? 1f : 0f, now);
            float onv = onT.value(now), hv = hoverT.value(now);

            // 11.8: states pulled further apart — OFF sits low and quiet (faint everything), ON is
            // unmistakable: category-tinted ground + tinted edge + full-colour chip/stripe/name/ghost.
            // Stage 22: base lifted one tone step (surfaceHi ground, strong edge) — the old surface
            // base sank into the deep content well.
            int fillHov = Color.lerp(Tokens.surface().surfaceHi(), 0xFFFFFFFF, 0.05f);
            int fill = Color.lerp(Color.lerp(Tokens.surface().surfaceHi(), fillHov, hv),
                                  accent, 0.055f * onv);
            int edgeHov = Color.lerp(Tokens.border().strong(), 0xFFFFFFFF, 0.16f);
            int edge = Color.lerp(Color.lerp(Tokens.border().strong(), edgeHov, hv),
                                  accent, 0.35f * onv);
            r.roundedRect(x, y, w, h, rad, Color.scaleAlpha(fill, ta));

            // Ghost underlay: the SAME glyph, cropped by the card — size, drift, bleed and alpha vary
            // per module so the pattern never reads as stamped. Near-invisible OFF, present ON.
            // Rect clip vs the rounded corner is invisible at this alpha (spec §7 risk — checked).
            int ghostCol = Color.lerp(Tokens.palette().textDesc(), accent, onv);
            r.pushClip(x, y, w, h);
            m.icon().draw(ctx, x + w - ghostSz + ghostBleed, y + (h - ghostSz) / 2f + ghostYOff, ghostSz,
                    Color.scaleAlpha(ghostCol, (ghostA + 0.05f * onv) * screenAlpha * ta));
            r.popClip();

            r.border(x, y, w, h, rad, Tokens.border().thickness(), Color.scaleAlpha(edge, ta));

            // Icon chip + the state stripe centered under it (one column, geometry constant).
            float chipX = x + TILE_PAD;
            float chipY = y + (h - (CHIP + STRIPE_GAP + STRIPE_H)) / 2f;
            int chipBg = Color.lerp(Color.withAlpha(Tokens.palette().textFaint(), 0x12),
                                    Color.withAlpha(accent, 0x30), onv);
            int iconCol = Color.lerp(Tokens.palette().textFaint(), accent, onv);
            r.roundedRect(chipX, chipY, CHIP, CHIP, CHIP_RAD, Color.scaleAlpha(chipBg, ta));
            m.icon().draw(ctx, chipX + (CHIP - CHIP_ICON) / 2f, chipY + (CHIP - CHIP_ICON) / 2f, CHIP_ICON,
                    Color.scaleAlpha(iconCol, screenAlpha * ta));
            int stripeCol = Color.lerp(Tokens.border().strong(), accent, onv);
            r.roundedRect(chipX + (CHIP - STRIPE_W) / 2f, chipY + CHIP + STRIPE_GAP,
                    STRIPE_W, STRIPE_H, STRIPE_H / 2f, Color.scaleAlpha(stripeCol, ta));

            // Name: uniform per-category size (cardNameSize, auto-fit in layoutAll) — never truncated.
            // OFF drops to textDesc (not textMuted) so the on/off gap is obvious at a glance.
            int nameCol = Color.lerp(Tokens.palette().textDesc(), Tokens.palette().textHi(), onv);
            float ns = cardNameSize;
            float nameLh = ctx.text().lineHeight(Tokens.type().heading().weight(), ns);
            float nameX = chipX + CHIP + NAME_GAP;
            ctx.text().draw(m.name(), nameX, y + (h - nameLh) / 2f,
                    TextStyle.of(Tokens.type().heading().weight(), ns, Color.scaleAlpha(nameCol, screenAlpha * ta)));
        }

        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (!contains(mx, my)) return false;
            if (b == 0) { activate(m); return true; }
            if (b == 1) { if (hasConfigurable(m)) { if (popModule == m && !popClosing) closePopover(); else openPopover(m, x, y, w, h); } return true; }
            return false;
        }
    }

    /** Minimal single-line search input: leading glyph, placeholder when idle, blinking caret when
     *  focused. Inner (non-static) so its text/glyph colours can ride the whole-window fade. */
    private final class SearchField extends Component {
        private final String placeholder;
        private String text = "";
        private Consumer<String> onChange;
        private final Transition focusT =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());
        private final Transition hoverT =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());

        SearchField(String placeholder) { this.placeholder = placeholder; }
        SearchField onChange(Consumer<String> cb) { this.onChange = cb; return this; }
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

            int acc = catAccent(catIndex);   // 11.9: search highlight speaks the current category's colour
            // Stage 22: the field joins the WELL family — well tone, borderless at rest (the
            // accent border eases in on hover/focus only), a whisper of inner light on the top
            // edge (recessed polish, not glow).
            r.roundedRect(x, y, w, h, rad, Tokens.surface().well());
            r.rect(x + rad, y + 1, w - 2 * rad, 1, Color.withAlpha(Tokens.accent().accent(), 0x0D));
            int bAlpha = Math.min(255, Math.round(0x66 * hv * (1f - fv) + 0xFF * fv));
            if (bAlpha > 2) r.border(x, y, w, h, rad, Tokens.border().thickness(), Color.withAlpha(acc, bAlpha));

            // leading magnifier glyph — tints toward the category accent on focus, matching the border
            float isz = 13f;
            IconGlyph.SEARCH.draw(ctx, x + pad, y + (h - isz) / 2f, isz,
                    Color.scaleAlpha(Color.lerp(Tokens.palette().textMuted(), acc, fv), screenAlpha));
            float textX = x + pad + isz + 6f;

            // "/" key hint (focuses the field in-game) — dissolves on focus, hidden while a query exists
            float ka = (1f - fv) * (empty ? 1f : 0f) * screenAlpha;
            if (ka > 0.001f) {
                float kb = 18f, kx = x + w - 7f - kb, ky = y + (h - kb) / 2f;
                r.roundedRect(kx, ky, kb, kb, 5f, Color.withAlpha(acc, Math.round(0x12 * ka)));
                r.border(kx, ky, kb, kb, 5f, 1f, Color.scaleAlpha(Tokens.border().strong(), ka));
                float slw = ctx.text().width("/", ty.label().weight(), 11f);
                ctx.text().draw("/", kx + (kb - slw) / 2f, ky + (kb - ctx.text().lineHeight(ty.label().weight(), 11f)) / 2f,
                        TextStyle.of(ty.label().weight(), 11f, Color.scaleAlpha(Tokens.palette().textFaint(), ka)));
            }

            float ty0 = y + (h - ty.body().lineHeight()) / 2f;
            r.pushClip(textX, y, x + w - pad - textX, h);
            if (!empty) ctx.text().draw(text, textX, ty0, TextStyle.of(ty.body().weight(), ty.body().size(),
                    Color.scaleAlpha(Tokens.palette().textHi(), screenAlpha)));
            else {   // placeholder dissolves as focus grows (instead of snapping off on first focus/keypress)
                float pa = (1f - fv) * screenAlpha;
                if (pa > 0.001f) ctx.text().draw(placeholder, textX, ty0, TextStyle.of(ty.body().weight(), ty.body().size(),
                        Color.scaleAlpha(Color.lerp(Tokens.palette().textFaint(), Tokens.palette().textMuted(), 0.4f), pa)));
            }
            if (fv > 0.001f) {   // caret: smooth ~1 Hz sine pulse — inside the clip so a long query can't spill it past the field
                float blink = 0.15f + 0.85f * (0.5f + 0.5f * (float) Math.sin(now * 2f * (float) Math.PI));
                float tw = empty ? 0f : ctx.text().width(text, ty.body().weight(), ty.body().size());
                r.rect(textX + tw + 1f, ty0, 1f, ty.body().lineHeight(), Color.scaleAlpha(acc, fv * blink));
            }
            r.popClip();
        }
    }

    private static final float OPT_H = 24f;

    /** Compact dropdown option row: subtle category-accent tint + accent text for the selected value,
     *  hover wash for the rest — no heavy button chrome, so the list stays neat inside the popover. */
    private static final class OptionRow extends Component {
        private final String text;
        private final boolean selected;
        private final int accent;
        private final Runnable onClick;
        private final Transition hoverT =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
        OptionRow(String text, boolean selected, int accent, Runnable onClick) {
            this.text = text; this.selected = selected; this.accent = accent; this.onClick = onClick;
        }

        @Override public Size measure(float aw, float ah) { return new Size(aw, OPT_H); }
        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            Typography ty = Tokens.type();
            float now = ctx.time();
            float rad = Tokens.radius().sm();
            hoverT.target(isHovered() ? 1f : 0f, now);
            float hv = hoverT.value(now);
            if (selected) r.roundedRect(x, y, w, h, rad, Color.withAlpha(accent, 0x24));   // selected tint (baked)
            else if (hv > 0.001f) r.roundedRect(x, y, w, h, rad, Color.scaleAlpha(Tokens.surface().surfaceHi(), hv));  // hover wash eases in
            int col = selected ? accent
                               : Color.lerp(Tokens.palette().textMuted(), Tokens.palette().textHi(), hv);
            float lh = ty.body().lineHeight();
            ctx.text().draw(text, x + Tokens.spacing().sm(), y + (OPT_H - lh) / 2f, TextStyle.of(ty.body().weight(), ty.body().size(), col));
        }
        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (b == 0 && contains(mx, my)) { onClick.run(); return true; }
            return false;
        }
    }

    private static final float SEG_H = 28f;

    /** Quiet segmented selector for popover tabs (11.9 — replaces the loud PRIMARY/GHOST button pair):
     *  one recessed track, equal segments, an eased category-tinted pill sliding between them; active
     *  label = category accent, inactive = muted. The slide lives in the outer {@code segSlide} so it
     *  survives the popover rebuild a tab switch triggers. */
    private final class SegmentRow extends Component {
        private final String[] labels;
        private final int accent;
        SegmentRow(String[] labels, int accent) { this.labels = labels; this.accent = accent; }

        @Override public Size measure(float aw, float ah) { return new Size(aw, SEG_H); }

        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            Typography ty = Tokens.type();
            float now = ctx.time();
            float rad = 8f;   // matches the card chip radius
            r.roundedRect(x, y, w, h, rad, Tokens.surface().bg1());
            r.border(x, y, w, h, rad, Tokens.border().thickness(), Tokens.border().defaultColor());
            float segW = w / labels.length;
            float px = x + (segSlide != null ? segSlide.value(now) : tabIndex) * segW;
            r.roundedRect(px + 2, y + 2, segW - 4, h - 4, rad - 2, Color.withAlpha(accent, 0x2E));
            float lh = ty.label().lineHeight();
            for (int i = 0; i < labels.length; i++) {
                int col = (i == tabIndex) ? accent : Tokens.palette().textMuted();
                ctx.text().draw(labels[i], x + segW * i + segW / 2f, y + (h - lh) / 2f,
                        TextStyle.of(ty.label().weight(), ty.label().size(), col).align(Align.CENTER));
            }
        }

        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (b != 0 || !contains(mx, my)) return false;
            int seg = Math.max(0, Math.min(labels.length - 1, (int) ((mx - x) / (w / labels.length))));
            if (seg != tabIndex) {
                tabIndex = seg;
                if (segSlide != null) segSlide.target(seg, uiCtx.time());
                openDrop = null;
                rebuildPopover();
            }
            return true;
        }
    }

    /** Free-form container: children are positioned by the screen, not auto-laid. */
    private static final class Pane extends Container {
        void add(Component c) { addChild(c); }
        void clear() { children.clear(); }
        @Override public Size measure(float aw, float ah) { return new Size(aw, ah); }
    }
}
