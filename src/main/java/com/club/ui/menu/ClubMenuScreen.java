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
import com.club.ui.component.widget.TextEditState;
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
    // Stage 37: returning from the HUD editor replays a CALM entrance — fast fade, no 12px rise
    // (the full rise on every Done read as a jerk, owner 2026-07-09). Right-Shift open keeps the
    // full motion; beginClose restores it so the close always plays the polished reverse.
    private boolean reentry;         // next init() = a return from a child screen (HUD editor)
    private float entranceRise = 12f;
    // Background scrim (Stage 37, owner 2026-07-09: the bright world drowned the menu — "мало
    // контраста и довольно ярко"). A flat dark veil, NOT blur (blur stays rejected, 49bc69b);
    // the world remains visible through it so live settings still read. Rides the entrance fade.
    private static final int SCRIM = 0x9006090C;   // ~56% near-black ink
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
    // Popover mini-halo (Stage 25): the same quadratic law, sheet-sized — 6 rings, ≈9% at the edge.
    private static final int[] POP_HALO_ALPHAS = {8, 6, 4, 3, 2, 1};
    /** Footer version whisper — balances the profile chip on the content well's right axis. */
    private static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("club")
            .map(c -> "Club " + c.getMetadata().getVersion().getFriendlyString())
            .orElse("Club");

    // ---- search reflow (Stage 21, owner's choreography) -----------------------------------------
    // Per-card motion is a Module-keyed TileMotion (extracted Stage 28 — see that class for the
    // choreography + owner-frozen pacing). It SURVIVES grid rebuilds so fast typing retargets the
    // SAME transitions mid-flight (the interface flows, it never restarts).
    private final java.util.HashMap<Module, TileMotion> tileMotion = new java.util.HashMap<>();
    // Cards that stopped matching keep painting HERE while they dissolve (they left the grid already).
    private final java.util.LinkedHashMap<Module, ModuleTile> leaving = new java.util.LinkedHashMap<>();
    private Transition noteFade;   // "No matching modules" — single smooth fade in/out

    // Reset confirmation (Stage 35): first click arms, second executes; the arm decays on timeout.
    // Arm/decay swap the button IN PLACE (Stage 38) — see the buildSettings reset block.
    private static final float RESET_ARM_HOLD = 3f;
    private boolean resetArmed;
    private float resetArmAt;
    private Button popResetBtn;   // the live reset button of the open popover (null when none)

    // Module keybind capture (Stage 43): the popover's Bind button arms listening; the next
    // keyPressed assigns (Esc cancels, Backspace/Delete clears). Screen-level so it wins over
    // every other key route, including the menu-close key.
    private boolean bindListening;
    private Module bindModule;
    private Button popBindBtn;   // the live Bind button of the open popover (focus handback after capture)

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

    // Keyboard grid navigation (Stage 27): the card cursor. Two keyboard "zones" — the search field
    // (FocusManager) and the card grid (this cursor). gridFocused marks the grid as the active zone
    // (always keyboard-driven, so the card focus ring is inherently focus-visible). gridFocus is the
    // Module under the cursor; it's resolved against the live grid each use, so a category switch /
    // search reflow that drops it just re-seeds to the first card.
    private boolean gridFocused;
    private Module gridFocus;
    // Space-opened popover hands the zone OFF to the popover controls and hands it BACK on a
    // keyboard close (Esc) — without this the grid ring and the popover focus were live at once
    // (Stage 31 fix of a Stage 27 rough edge). Any mouse interaction cancels the hand-back.
    private boolean popFromGrid;

    private int stFootMutCol;      // footer tone (colour only — styles are built inline now: fade needs live alpha)
    private boolean stylesInit;

    public ClubMenuScreen() { super(Text.literal("Club")); }

    private void openHudEditor() {
        reentry = true;   // coming back from the editor replays a calm, fade-only entrance (Stage 37)
        MinecraftClient.getInstance().setScreen(new HudEditorScreen(this));
    }

    @Override protected void init() {
        headH = 48; footH = 36;   // footer slimmed with its divider gone (Stage 22)
        query = ""; popModule = null; popCol = null; openDrop = null; pressOwner = 0;
        gridFocused = false; gridFocus = null;
        search = new SearchField("Search modules")
                .onChange(q -> { query = q; rebuildGrid(GridRebuild.SEARCH); layoutAll(); })
                .onSubmit(this::submitSearch);   // Enter activates the first result
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
        if (reentry) {   // back from the HUD editor: quick fade into place, no rise (Stage 37)
            reentry = false;
            entrance = new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
            entranceRise = 0f;
        } else {
            entrance = new Transition(0f, Tokens.motion().durations().slow(), Tokens.motion().easings().decelerate());
            entranceRise = 12f;
        }
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
                // CATEGORY = fade-only cascade that rises into place; OPEN = shown at once (rides the window fade)
                TileMotion tm = (mode == GridRebuild.CATEGORY) ? TileMotion.categoryEnter(i++) : TileMotion.opening();
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
            tm.beginExit(now);
            exits.add(tm);
            leaving.put(t.m, t);
        }
        for (int j = 0; j < exits.size(); j++)
            exits.get(j).scheduleHideFromTail(now, j, exits.size());
        grid.clear();
        for (Module m : match) {
            TileMotion tm = tileMotion.get(m);
            if (tm == null) {                     // brand new match — fade+grow in at +110ms
                tm = TileMotion.searchEnter();
                tileMotion.put(m, tm);
            } else if (tm.leaving) {              // matched again mid-exit — turn around, no restart
                tm.reverseToEnter(now);
                leaving.remove(m);
            } else if (tm.hasPos) {               // survivor — glides to its new slot after +70ms
                tm.scheduleMove(now);
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
        // Every toggleable module carries a Bind row since Stage 43 — flag modules (Fullbright,
        // Freelook, No-*) must open a popover too, or their keybind would be unreachable.
        if (m.hasToggle()) return true;
        for (Setting s : m.settings()) if (!(s instanceof ActionSetting)) return true;
        return false;
    }

    // ---- keyboard navigation (Stage 27) --------------------------------------

    /** The modules currently shown in the grid, in grid order (matching, non-leaving). */
    private java.util.List<Module> gridModules() {
        java.util.List<Module> out = new java.util.ArrayList<>();
        for (var c : grid.children()) out.add(((ModuleTile) c).m);
        return out;
    }

    /** The live tile for a module (for popover anchoring), or null if it isn't shown. */
    private ModuleTile tileOf(Module m) {
        for (var c : grid.children()) { ModuleTile t = (ModuleTile) c; if (t.m == m) return t; }
        return null;
    }

    /** Enter activates the first result — toggles it or runs its action. */
    private void submitSearch() {
        java.util.List<Module> mods = gridModules();
        if (!mods.isEmpty()) activate(mods.get(0));
    }

    private void enterSearchZone() { gridFocused = false; if (search != null) focus.focusKeyboard(search); }
    private void enterGridZone() {
        java.util.List<Module> mods = gridModules();
        if (mods.isEmpty()) { enterSearchZone(); return; }   // nothing to focus → stay on search
        if (gridFocus == null || !mods.contains(gridFocus)) gridFocus = mods.get(0);
        gridFocused = true; focus.blur();
    }
    /** Tab with no popover: cycle SEARCH → GRID → SEARCH (first Tab from nothing lands on search). */
    private void toggleZone() {
        if (gridFocused) enterSearchZone();
        else if (search != null && search.isFocused()) enterGridZone();
        else enterSearchZone();
    }

    /** Ctrl+Tab switches category (the rail), keeping the current keyboard zone. */
    private void cycleCategory(int dir) {
        int n = cats.size();
        setCategory(((catIndex + dir) % n + n) % n);
        gridFocus = null;                 // re-seed to the first card of the new category on next use
        if (gridFocused) enterGridZone();
    }

    /** Arrow/Enter/Space handling while the grid zone is active. Returns true if consumed. */
    private boolean gridNav(int k) {
        java.util.List<Module> mods = gridModules();
        if (mods.isEmpty()) return false;
        int idx = mods.indexOf(gridFocus);
        if (idx < 0) idx = 0;
        switch (k) {
            case GLFW_KEY_LEFT:  idx = Math.max(0, idx - 1); break;
            case GLFW_KEY_RIGHT: idx = Math.min(mods.size() - 1, idx + 1); break;
            case GLFW_KEY_UP:
                if (idx < GRID_COLS) { enterSearchZone(); return true; }   // top row → hop up to search
                idx -= GRID_COLS; break;
            case GLFW_KEY_DOWN:  idx = Math.min(mods.size() - 1, idx + GRID_COLS); break;
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER: activate(mods.get(idx)); gridFocus = mods.get(idx); return true;
            case GLFW_KEY_SPACE: openPopoverForFocused(mods.get(idx)); gridFocus = mods.get(idx); return true;
            default: return false;
        }
        gridFocus = mods.get(idx);
        return true;
    }

    /** Space on a focused card opens (or closes) its settings popover, anchored to the card. The
     *  grid zone hands off to the popover controls while it's open (one keyboard owner at a time)
     *  and is restored when the popover closes via keyboard. */
    private void openPopoverForFocused(Module m) {
        if (!hasConfigurable(m)) return;
        ModuleTile t = tileOf(m);
        if (t == null) return;
        if (popModule == m && !popClosing) closePopover();
        else {
            openPopover(m, t.xLeft(), t.yTop(), t.width(), t.height());
            gridFocused = false; popFromGrid = true;
        }
    }

    // ---- popover -------------------------------------------------------------

    private void openPopover(Module m, float ax, float ay, float aw, float ah) {
        popModule = m; popAX = ax; popAY = ay; popAH = ah; tabIndex = 0; openDrop = null;
        resetArmed = false;                     // a fresh popover never opens pre-armed
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
        popReveal = null; popClosing = false; resetArmed = false; popResetBtn = null;
        bindListening = false; bindModule = null; popBindBtn = null;
        focus.clear();
        if (search != null) focus.register(search);
        if (popFromGrid) { popFromGrid = false; enterGridZone(); }   // hand the zone back (Space → Esc round-trip)
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
                Button field = new Button(d.options()[cur]).variant(Button.Variant.GHOST).accent(accent).compact()
                        .onClick(() -> { openDrop = (openDrop == d) ? null : d; rebuildPopover(); });
                row.add(field);
                col.add(new LaneRow(row)); focus.register(field);
                if (openDrop == d) {
                    for (int i = 0; i < d.options().length; i++) {
                        final int oi = i;
                        col.add(new OptionRow(d.options()[i], i == cur, accent,
                                () -> { d.set().accept(oi); openDrop = null; rebuildPopover(); }));
                    }
                }
            } else if (s instanceof ActionSetting) {
                Component ctrl = buildControl(s, accent);
                Row rr = new Row().crossAlign(CrossAlign.CENTER); rr.add(ctrl); col.add(rr); focus.register(ctrl);
            } else {
                Component ctrl = buildControl(s, accent);
                Row rr = new Row().crossAlign(CrossAlign.CENTER);
                rr.add(new Label(s.label(), Tokens.type().label()).color(Tokens.palette().textMuted()), Sizing.fill());
                rr.add(ctrl);
                col.add(new LaneRow(rr)); focus.register(ctrl);
            }
        }

        if (m.hasToggle() && openDrop == null) {
            // Stage 43: per-module toggle keybind. The value field shows the bound key ("None" when
            // unbound); click → listening ("Press a key…", the accent voice): the next key assigns,
            // Esc cancels, Backspace/Delete clears. Width pinned to the widest state.
            String cur = com.club.modules.binds.ModuleBinds.label(m.name());
            boolean listening = bindListening && bindModule == m;
            Button bind = new Button(listening ? "Press a key…" : (cur != null ? cur : "None"))
                    .variant(listening ? Button.Variant.PRIMARY : Button.Variant.GHOST).accent(accent).compact();
            bind.minWidth(new Button("Press a key…").compact().measure(10_000f, 22f).w());
            bind.onClick(() -> {
                boolean was = bindListening && bindModule == m;
                bindListening = !was; bindModule = bindListening ? m : null;
                rebuildPopover();
            });
            popBindBtn = bind;
            Row rr = new Row().crossAlign(CrossAlign.CENTER);
            rr.add(new Label("Bind", Tokens.type().label()).color(Tokens.palette().textMuted()), Sizing.fill());
            rr.add(bind);
            col.add(new LaneRow(rr)); focus.register(bind);
        } else popBindBtn = null;

        if (m.hasReset() && openDrop == null) {   // hidden while a dropdown is expanded (see the guard above)
            // Stage 35 (hardened in 38): a destructive action asks first. Click 1 ARMS the button — it
            // turns into the category-accent PRIMARY "Sure? Reset" (the loudest voice this popover has,
            // reserved for exactly this moment); click 2 within the hold executes. Arm and decay swap
            // IN PLACE (label/variant only, width pinned to the idle box) — a rebuild here would orphan
            // an in-flight slider drag (losing its save-on-release) and wipe keyboard focus. Only the
            // CONFIRM rebuilds (the controls must re-seed to the reset values); keyboard focus is handed
            // to the fresh button so Enter-Enter works end to end.
            Button reset = new Button(resetArmed ? "Sure? Reset" : "Reset to Default")
                    .variant(resetArmed ? Button.Variant.PRIMARY : Button.Variant.GHOST).accent(accent);
            reset.minWidth(new Button("Reset to Default").measure(10_000f, 22f).w());
            reset.onClick(() -> {
                if (resetArmed) {
                    boolean kb = popResetBtn != null && popResetBtn.isFocusVisible();
                    resetArmed = false;
                    m.reset().run(); openDrop = null;
                    rebuildPopover();
                    if (kb && popResetBtn != null) focus.focusKeyboard(popResetBtn);
                } else {
                    resetArmed = true; resetArmAt = uiCtx.time();
                    if (popResetBtn != null) popResetBtn.label("Sure? Reset").variant(Button.Variant.PRIMARY);
                }
            });
            popResetBtn = reset;
            Row rr = new Row().crossAlign(CrossAlign.CENTER); rr.add(Spacer.fill()); rr.add(reset);
            col.add(rr); focus.register(reset);
        } else popResetBtn = null;
        return col;
    }

    private static int clampIdx(DropdownSetting d) {
        return Math.max(0, Math.min(d.get().getAsInt(), d.options().length - 1));
    }

    private Component buildControl(Setting s, int accent) {
        // Stage 29: the setter applies the value LIVE (onChange); the disk write happens once per
        // gesture on release/step (onRelease), not ~150× across a drag.
        if (s instanceof SliderSetting sl) return new Slider(sl.get().get(), sl.min(), sl.max(), sl.step())
                .onChange(sl.set()).onRelease(ClubConfig::save).accent(accent);
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
        entranceYOff = (1f - ep) * entranceRise;   // window rises into place on open; sinks back out on close
        if (closing && ep <= 0.001f) {    // reverse animation finished — really close now
            MinecraftClient.getInstance().setScreen(null);
            return;
        }

        layoutAll();
        screenAlpha = ep;

        // Background scrim (Stage 37): a flat ~56% ink veil — the bright world was drowning the menu
        // (owner: «мало контраста и довольно ярко»). Translucent, so settings still apply live and
        // visibly (hand sliders etc.); NOT blur (rejected, 49bc69b). Faded EXPLICITLY (scaleAlpha,
        // outside pushOpacity) so it truly rides the entrance/close on LEGACY too, where pushOpacity
        // is a no-op. During a calm post-editor re-entry the veil HOLDS full (Stage 38): the editor
        // was already dark — fading in from 0 flashed the bright world between two dark states.
        float scrimA = (entranceRise == 0f && !closing) ? 1f : ep;
        r.rect(0, 0, width, height, Color.scaleAlpha(SCRIM, scrimA));

        r.pushOpacity(ep);   // whole-window fade: shapes here; text/glyphs multiply screenAlpha
        float lg = Tokens.radius().lg();
        Typography ty = Tokens.type();
        float catLh = ty.label().lineHeight();

        // Ambient halo BEHIND the window (Stage 22): thin rings, quadratic falloff — a shadow that
        // HUGS the window and dissipates fast, not a dark buffer. World separation, not UI depth.
        // Gated off on LEGACY (Stage 26): pushOpacity is a no-op there, so the rings would render
        // as a stack of solid black frames instead of a whisper.
        if (Ui.backend() == Ui.Backend.MODERN) {
            for (int i = HALO_ALPHAS.length; i >= 1; i--) {
                float s = i * HALO_STEP;
                r.roundedRect(winX - s, winY - s + s * 0.3f, winW + 2 * s, winH + 2 * s, lg + s,
                        Color.withAlpha(0xFF000000, HALO_ALPHAS[i - 1]));
            }
        }

        // Passe-partout (Stage 22): ONE frame tone + two wells whose edges do the separating —
        // shallow category tray (Δ≈1 tone step), deep content well (Δ≈3). Zero hairline dividers.
        float md = Tokens.radius().md();
        r.roundedRect(winX, winY, winW, winH, lg, Tokens.surface().surface());
        r.roundedRect(railWX, railWY, railWW, railWH, md, Tokens.surface().wellShallow());
        r.roundedRect(wellX, wellY, wellW, wellH, md, Tokens.surface().well());

        // header — CLUB wordmark centred on the category tray's axis (the header sits on the grid).
        // On LEGACY the logo glyph can't draw — don't reserve its width (Stage 26), the wordmark
        // re-centres alone instead of hanging beside an invisible hole.
        float logoSz = 15f;
        float logoAdv = IconGlyph.available() ? logoSz + 7 : 0;
        float clubTextW = uiCtx.text().width("CLUB", ty.display().weight(), ty.display().size());
        float clubX = railWX + (railWW - (logoAdv + clubTextW)) / 2f;
        if (logoAdv > 0) IconGlyph.LOGO.draw(uiCtx, clubX, winY + (headH - logoSz) / 2f, logoSz,
                Color.scaleAlpha(Tokens.accent().accent(), ep));
        uiCtx.text().draw("CLUB", clubX + logoAdv, winY + (headH - ty.display().lineHeight()) / 2f,
                TextStyle.of(ty.display().weight(), ty.display().size(), Color.scaleAlpha(Tokens.palette().textHi(), ep)));

        // footer — the profile chip (the future switcher: mark + name + chevron) on the tray axis,
        // a version whisper on the content well's right line. No caption floating in a void.
        float fooY = winY + winH - footH;
        float chipCy = fooY + footH / 2f;
        r.roundedRect(railWX, chipCy - 8, 16, 16, 8, Color.withAlpha(Tokens.accent().accent(), 0x24));
        if (IconGlyph.available()) {
            IconGlyph.LOGO.draw(uiCtx, railWX + 3.5f, chipCy - 8 + 3.5f, 9,
                    Color.scaleAlpha(Tokens.accent().accent(), ep));
        } else {   // LEGACY letter fallback (Stage 26): the pill keeps an identity, not an empty circle
            float cw = uiCtx.text().width("C", ty.label().weight(), 9f);
            uiCtx.text().draw("C", railWX + (16 - cw) / 2f, chipCy - uiCtx.text().lineHeight(ty.label().weight(), 9f) / 2f,
                    TextStyle.of(ty.label().weight(), 9f, Color.scaleAlpha(Tokens.accent().accent(), ep)));
        }
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
        if (noteFade == null) noteFade = new Transition(0f, TileMotion.ENTER_DUR, Tokens.motion().easings().decelerate());
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
                if (tm == null || tm.fadeValue(now) <= 0.001f) {   // dissolve finished — prune the departed card
                    it.remove();
                    tileMotion.remove(en.getKey());
                }
            }
            r.popClip();
        }

        if (indicator != null)   // active indicator bar: CATEGORY colour, hugging the tray's left edge
            r.roundedRect(railWX, indY + 4, 4, RAIL_ROW - 8, 2f, railBarColor(now));

        r.border(winX, winY, winW, winH, lg, Tokens.border().thickness(), Tokens.border().strong());

        // armed reset decays back to the quiet ghost when the hold expires (Stage 35). In place —
        // NO rebuild (Stage 38): a timer-driven rebuild would orphan an in-flight slider drag
        // (freezing it and skipping its save-on-release) and silently clear keyboard focus.
        if (resetArmed && now - resetArmAt > RESET_ARM_HOLD) {
            resetArmed = false;
            if (popResetBtn != null) popResetBtn.label("Reset to Default").variant(Button.Variant.GHOST);
        }

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
                // Stage 25 (owner board, variant A): the popover is a RAISED SHEET, not a hole —
                // under the Stage-22 depth grammar (darker = recessed) the old bg2 ground + strong
                // border read as a punched-out box. Card tone one step above the window ground,
                // the window's quadratic mini-halo instead of a loud border, and a quiet hairline
                // to hold the edge where the sheet crosses the light wells. Halo gated on LEGACY
                // (Stage 26) like the window halo — no pushOpacity there means solid black rings.
                if (Ui.backend() == Ui.Backend.MODERN) {
                    for (int i = POP_HALO_ALPHAS.length; i >= 1; i--) {
                        float hs = i * HALO_STEP;
                        r.roundedRect(popX - hs, popY - hs, popW + 2 * hs, drawnH + 2 * hs, pr + hs,
                                Color.withAlpha(0xFF000000, POP_HALO_ALPHAS[i - 1]));
                    }
                }
                r.roundedRect(popX, popY, popW, drawnH, pr, Tokens.surface().surface());
                r.border(popX, popY, popW, drawnH, pr, Tokens.border().thickness(), Tokens.border().defaultColor());
                r.pushClip(popX, popY, popW, drawnH);
                popScroll.mouseMoved(mouseX, mouseY);
                popScroll.render(uiCtx);
                r.popClip();
            }
        }
        r.popOpacity();
        com.club.ui.LegacyNotice.draw(uiCtx, width);   // loud fallback plaque (draws nothing on MODERN)
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
        // A calm (post-editor) entrance must not shortchange the CLOSE — restore the polished
        // reverse: re-seed the standard slow transition at the current value. The 12px sink comes
        // back ONLY from a settled window (Stage 38): mid-fade, flipping the rise would teleport
        // the window down by (1-ep)*12 in a single frame — the exact jerk Stage 37 removed.
        if (entranceRise == 0f && entrance != null) {
            float cur = entrance.value(uiCtx.time());
            entrance = new Transition(cur,
                    Tokens.motion().durations().slow(), Tokens.motion().easings().decelerate());
            if (cur >= 0.999f) entranceRise = 12f;
        }
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
        gridFocused = false;        // any mouse interaction leaves the keyboard grid zone (ring hides)
        popFromGrid = false;        //   …and cancels the popover's pending zone hand-back
        if (bindListening) {        // a click cancels key capture (clicking the Bind button re-arms it)
            bindListening = false; bindModule = null;
            rebuildPopover();
        }
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
        boolean shift = (mods & GLFW_MOD_SHIFT) != 0, ctrl = (mods & GLFW_MOD_CONTROL) != 0;

        // Keybind capture wins over EVERYTHING (incl. the menu-close key): the next key assigns,
        // Esc cancels, Backspace/Delete clears (Stage 43, hardened 45).
        if (bindListening && bindModule != null) {
            if (k == GLFW_KEY_ESCAPE) { /* cancel — keep the current bind */ }
            else if (k == GLFW_KEY_BACKSPACE || k == GLFW_KEY_DELETE)
                com.club.modules.binds.ModuleBinds.set(bindModule.name(), null);
            else if (k == GLFW_KEY_UNKNOWN)
                return true;   // no GLFW keycode → would be a dead SCANCODE bind; ignore, keep listening
            else if (com.club.ClubClient.openMenuKey.matchesKey(k, scan)) { /* reserved — don't bind the menu key */ }
            else com.club.modules.binds.ModuleBinds.set(bindModule.name(),
                    InputUtil.fromKeyCode(k, scan).getTranslationKey());
            bindListening = false; bindModule = null;
            rebuildPopover();
            if (popBindBtn != null) focus.focusKeyboard(popBindBtn);   // keyboard flow continues on the Bind row
            return true;
        }

        // The menu closes on the SAME key that opens it (owner decision 2026-07-02) — with the
        // reverse-of-open animation, but NOT while typing in the search (the bound letter must
        // type, not close).
        if (com.club.ClubClient.openMenuKey.matchesKey(k, scan)
                && !(search != null && search.isFocused())) { beginClose(); return true; }

        // ESC precedence (Stage 27, chain widened in Stage 31): popover → clear a live query (from
        // ANY zone — arrowing the filtered grid then Esc no longer strands the filter) → blur the
        // focused search → leave the grid zone. Never closes the menu (shouldCloseOnEsc = false).
        if (k == GLFW_KEY_ESCAPE) {
            if (popModule != null) { closePopover(); return true; }
            if (!query.isEmpty() && search != null) { search.clear(); query = ""; rebuildGrid(GridRebuild.SEARCH); layoutAll(); return true; }
            if (search != null && search.isFocused()) { focus.blur(); return true; }
            if (gridFocused) { gridFocused = false; return true; }
            return false;
        }

        // Ctrl+Tab switches category (the rail) — Up/Down are reserved for the grid.
        if (k == GLFW_KEY_TAB && ctrl) { cycleCategory(shift ? -1 : +1); return true; }

        // While a popover is open its controls own Tab + all keys (unchanged behaviour).
        if (popModule != null && !popClosing) {
            if (k == GLFW_KEY_TAB) { if (shift) focus.previous(); else focus.next(); return true; }
            return focus.keyPressed(k, scan, mods) || super.keyPressed(k, scan, mods);
        }

        // Tab toggles the keyboard zone: search field ↔ card grid.
        if (k == GLFW_KEY_TAB) { toggleZone(); return true; }

        // The focused search field owns its editing keys (caret, Enter, Ctrl+A, …).
        if (search != null && search.isFocused()) {
            if (search.keyPressed(k, scan, mods)) return true;
            if (k == GLFW_KEY_DOWN) { enterGridZone(); return true; }   // Down out of search → into the grid
            return super.keyPressed(k, scan, mods);
        }

        // Card grid navigation (arrows / Enter / Space) when the grid zone is active.
        if (gridFocused && gridNav(k)) return true;

        return focus.keyPressed(k, scan, mods) || super.keyPressed(k, scan, mods);
    }
    @Override public boolean charTyped(char c, int mods) {
        if (bindListening) return true;   // capturing a key — its char must not type/route anywhere
        // "/" jumps into the search (the keycap hint in the field advertises it); consumed so the
        // slash itself never lands in the query
        if (c == '/' && search != null && !search.isFocused() && !closing) { enterSearchZone(); return true; }
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
        // Suspend gui-move while ANY keyboard zone is active (Stage 27): typing in the search, or
        // driving the card grid — otherwise Space (open settings) doubles as jump and WASD would
        // walk the player while arrowing the grid. Mouse users (no keyboard zone) keep moving.
        boolean typing = (search != null && search.isFocused()) || gridFocused;
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

    // Key-parse cache (Stage 32): fromTranslationKey does a registry/string parse — 7 bindings × 20
    // ticks/s churned it for a value that only changes on a rebind. Keyed by the translation-key
    // STRING, so a mid-session rebind naturally misses the cache and re-parses.
    private static final java.util.Map<String, InputUtil.Key> KEY_CACHE = new java.util.HashMap<>();

    /** True if the physical key a binding is bound to is currently held (keyboard-bound only). */
    private static boolean rawKeyDown(long handle, KeyBinding binding) {
        InputUtil.Key key = KEY_CACHE.computeIfAbsent(
                binding.getBoundKeyTranslationKey(), InputUtil::fromTranslationKey);
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
                ta = tm.tick(now);   // resolve lazy show / cascade-hide gates, get the fade
                // Positions ease in WINDOW space: the entrance rise (and any window recentre) moves
                // cards rigidly with the frame — easing screen coords made them lag/chase the window
                // ("подпрыгивают" after open). Only slot-to-slot moves animate.
                float relX = x - winX, relY = y - winY;
                float ex, ey;
                if (tm.leaving) {
                    ex = winX + tm.bx; ey = winY + tm.by; w = tm.bw; h = tm.bh;
                    hovered = false;   // a dissolving card must not keep its hover lift
                } else {
                    tm.place(now, relX, relY, w, h);
                    ex = winX + tm.bx; ey = winY + tm.by + tm.driftY(ta);   // driftY rises category enters into place
                }
                float sc = tm.scale(ta);
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
            if (IconGlyph.available()) {
                m.icon().draw(ctx, chipX + (CHIP - CHIP_ICON) / 2f, chipY + (CHIP - CHIP_ICON) / 2f, CHIP_ICON,
                        Color.scaleAlpha(iconCol, screenAlpha * ta));
            } else {   // LEGACY letter fallback (Stage 26): the module's initial, not an empty square
                String ini = m.name().isEmpty() ? "?" : m.name().substring(0, 1).toUpperCase(Locale.ROOT);
                float iw = ctx.text().width(ini, Tokens.type().heading().weight(), 13f);
                float ilh = ctx.text().lineHeight(Tokens.type().heading().weight(), 13f);
                ctx.text().draw(ini, chipX + (CHIP - iw) / 2f, chipY + (CHIP - ilh) / 2f,
                        TextStyle.of(Tokens.type().heading().weight(), 13f, Color.scaleAlpha(iconCol, screenAlpha * ta)));
            }
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

            // Keyboard focus ring (Stage 27) — only in the grid zone (always keyboard-driven, so
            // this is inherently focus-visible), hugging the card just outside its edge.
            if (gridFocused && gridFocus == m) {
                float fw = Tokens.interaction().focusRingWidth();
                r.border(x - 2, y - 2, w + 4, h + 4, rad + 2, fw,
                        Color.scaleAlpha(Tokens.interaction().focusRing(), screenAlpha * ta));
            }
        }

        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (!contains(mx, my)) return false;
            if (b == 0) { activate(m); return true; }
            if (b == 1) { if (hasConfigurable(m)) { if (popModule == m && !popClosing) closePopover(); else openPopover(m, x, y, w, h); } return true; }
            return false;
        }
    }

    /** Minimal single-line search input: leading glyph, placeholder when idle, blinking caret when
     *  focused. The editing model lives in {@link TextEditState} (unit-tested, Stage 31); the field
     *  keeps only pixel concerns — caret hit-testing, drag-select, double-click word select, the
     *  system clipboard (Ctrl+C/X/V) and drawing. Enter = submit (activate the first result). Inner
     *  (non-static) so its text/glyph colours can ride the whole-window fade. */
    private final class SearchField extends Component {
        private final String placeholder;
        private final TextEditState st = new TextEditState();
        private Consumer<String> onChange;
        private Runnable onSubmit;
        private long lastClickMs;     // double-click (word select) detection
        private int lastClickCaret = -1;
        private final Transition focusT =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());
        private final Transition hoverT =
                new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());

        SearchField(String placeholder) { this.placeholder = placeholder; }
        SearchField onChange(Consumer<String> cb) { this.onChange = cb; return this; }
        SearchField onSubmit(Runnable cb) { this.onSubmit = cb; return this; }
        void clear() { st.clear(); }

        private void notifyChange() { if (onChange != null) onChange.accept(st.text()); }

        @Override public Size measure(float aw, float ah) {
            return new Size(160f, Tokens.type().body().lineHeight() + Tokens.spacing().md());
        }

        private float textStartX() { return x + Tokens.spacing().md() + 13f + 6f; }

        /** Caret index nearest to pixel {@code mx} (character-boundary hit test). */
        private int caretAt(double mx) {
            float base = textStartX();
            var weight = Tokens.type().body().weight();
            float size = Tokens.type().body().size();
            String text = st.text();
            int best = text.length(); float bestD = Float.MAX_VALUE;
            for (int i = 0; i <= text.length(); i++) {
                float cx = base + Ui.text().width(text.substring(0, i), weight, size);
                float d = Math.abs((float) mx - cx);
                if (d < bestD) { bestD = d; best = i; }
            }
            return best;
        }

        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (!(enabled && contains(mx, my))) return false;
            int at = caretAt(mx);
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastClickMs < 300 && at == lastClickCaret) {   // double-click → word select
                st.selectWordAt(at);
            } else {
                st.moveCaret(at, hasShiftDown());   // Shift+click extends the selection
            }
            lastClickMs = nowMs; lastClickCaret = at;
            return true;
        }

        /** Drag extends the selection from the press point (pointer capture routes the drag here). */
        @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
            st.moveCaret(caretAt(mx), true);
            return true;
        }

        @Override public boolean charTyped(char c, int mods) {
            if (c >= 32) { if (st.insert(String.valueOf(c))) notifyChange(); return true; }
            return false;
        }

        @Override public boolean keyPressed(int k, int scan, int mods) {
            boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0, shift = (mods & GLFW_MOD_SHIFT) != 0;
            switch (k) {
                case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER:
                    if (onSubmit != null) onSubmit.run();
                    return true;
                case GLFW_KEY_LEFT:  st.left(shift); return true;
                case GLFW_KEY_RIGHT: st.right(shift); return true;
                case GLFW_KEY_HOME:  st.home(shift); return true;
                case GLFW_KEY_END:   st.end(shift); return true;
                case GLFW_KEY_A:
                    if (ctrl) { st.selectAll(); return true; }
                    return false;
                case GLFW_KEY_C:
                    if (ctrl) { copySelection(); return true; }
                    return false;
                case GLFW_KEY_X:
                    if (ctrl) { copySelection(); if (st.deleteSelection()) notifyChange(); return true; }
                    return false;
                case GLFW_KEY_V:
                    if (ctrl) { if (st.insert(sanitizeClipboard())) notifyChange(); return true; }
                    return false;
                case GLFW_KEY_BACKSPACE:
                    if (st.backspace(ctrl)) notifyChange();
                    return true;
                case GLFW_KEY_DELETE:
                    if (st.delete(ctrl)) notifyChange();
                    return true;
                default:
                    return false;
            }
        }

        private void copySelection() {
            if (st.hasSelection()) MinecraftClient.getInstance().keyboard.setClipboard(st.selectedText());
        }

        /** Clipboard text flattened for a single-line query: control chars (incl. newlines) stripped. */
        private String sanitizeClipboard() {
            String s = MinecraftClient.getInstance().keyboard.getClipboard();
            if (s == null || s.isEmpty()) return "";
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c >= 32) sb.append(c); }
            return sb.toString();
        }

        @Override public void render(UiContext ctx) {
            UiRenderer r = ctx.renderer();
            Typography ty = Tokens.type();
            float now = ctx.time();
            float rad = Tokens.radius().md();
            float pad = Tokens.spacing().md();
            boolean foc = isFocused();
            String text = st.text();
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
            float textX = textStartX();

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
            var weight = ty.body().weight();
            float size = ty.body().size();
            r.pushClip(textX, y, x + w - pad - textX, h);
            if (st.hasSelection()) {   // selection highlight behind the text (faint accent well)
                float sx = textX + ctx.text().width(text.substring(0, st.selectionStart()), weight, size);
                float ex = textX + ctx.text().width(text.substring(0, st.selectionEnd()), weight, size);
                r.roundedRect(sx, ty0, Math.max(1f, ex - sx), ty.body().lineHeight(), 2f,
                        Color.scaleAlpha(Color.withAlpha(acc, 0x3A), screenAlpha));
            }
            if (!empty) ctx.text().draw(text, textX, ty0, TextStyle.of(weight, size,
                    Color.scaleAlpha(Tokens.palette().textHi(), screenAlpha)));
            else {   // placeholder dissolves as focus grows (instead of snapping off on first focus/keypress)
                float pa = (1f - fv) * screenAlpha;
                if (pa > 0.001f) ctx.text().draw(placeholder, textX, ty0, TextStyle.of(weight, size,
                        Color.scaleAlpha(Color.lerp(Tokens.palette().textFaint(), Tokens.palette().textMuted(), 0.4f), pa)));
            }
            if (fv > 0.001f) {   // caret at the edit position: smooth ~1 Hz sine pulse, inside the clip
                float blink = 0.15f + 0.85f * (0.5f + 0.5f * (float) Math.sin(now * 2f * (float) Math.PI));
                float cx = textX + ctx.text().width(text.substring(0, st.caret()), weight, size);
                r.rect(cx + 1f, ty0, 1f, ty.body().lineHeight(), Color.scaleAlpha(acc, fv * blink));
            }
            r.popClip();
        }
    }

    /** One settings row pinned to the 24px control LANE (Stage 34): the Slider's lane (spacing.xl)
     *  is the popover's rhythm unit — a dropdown field (34px button) or a toggle (22px) must not
     *  stretch/shrink its row. Content-driven row heights made the Animations / Screen Stretch
     *  popovers breathe unevenly next to the all-slider Hands popover (owner's ideal). */
    private static final class LaneRow extends Container {
        private final Row row;
        LaneRow(Row row) { this.row = row; addChild(row); }
        @Override public Size measure(float aw, float ah) {
            Size s = row.measure(aw, ah);
            return new Size(s.w(), Math.max(Tokens.spacing().xl(), s.h()));
        }
        @Override public void layout(float x, float y, float w, float h) {
            super.layout(x, y, w, h);
            row.layout(x, y, w, h);   // CrossAlign.CENTER inside the row centres shorter controls in the lane
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
            // Stage 25: quiet track from the WELL family (SearchField precedent after Stage 22) —
            // well tone, borderless at rest; the sliding pill alone carries the state.
            r.roundedRect(x, y, w, h, rad, Tokens.surface().well());
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
