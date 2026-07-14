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
    // Live layout metrics — the ideal above, shrunk to whatever the screen actually gives us (layoutAll).
    private float railRow = RAIL_ROW;
    private int gridCols = GRID_COLS;
    private float cardNameSlot = 100f;               // width the card name may use (it is clipped to it)

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
    // The menu key is reserved (it opens/closes this screen). Pressing it while listening used to
    // silently CANCEL the capture — and the key's GLFW repeat then landed on the close route, so the
    // menu shut itself (owner, Stage 58). It now KEEPS listening and says why, so no repeat can leak.
    private boolean bindReserved;
    /** Physical keys held right now (this screen's view). A press whose key is already in here is a
     *  GLFW auto-repeat — see keyPressed. Cleared on init: a key released while a child screen owned
     *  the keyboard would otherwise stay "down" forever. */
    private final java.util.Set<Integer> keysDown = new java.util.HashSet<>();

    // settings popover (RMB), anchored to a card
    private Module popModule;
    private Column popCol;
    private ScrollArea popScroll;   // wraps popCol so long settings/dropdown lists scroll instead of overflowing
    private int tabIndex;
    private Transition segSlide;    // segmented-tab pill position — outer, so it survives popover rebuilds
    private DropdownSetting openDrop;   // the dropdown whose pick-list is expanded in the popover
    private float popX, popY, popW, popH, popAX, popAY, popAH, popContentH, popInnerW;
    private float popRoom;   // vertical room the sheet was allowed — the denominator of "maximal" (harness seam)
    private int pressOwner;
    // Popover open/close/resize motion: reveal grows it in / out; popHTween eases the target height
    // (dropdown expand, tab switch). Content is clipped to the eased height so any resize reveals smoothly.
    private Reveal popReveal;
    private boolean popClosing;
    private final ValueTween popHTween =
            new ValueTween(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate());

    /**
     * THE SHEET SLIDES; IT DOES NOT DIE AND RESPAWN (owner, v0.1.3 item 7).
     *
     * <p>His report was three symptoms of one omission: "анимация скрытия не проигрывается при нажатии на
     * другой поповер + в таком раскладе половина анимации входа второго поповера съедается". And the code
     * agreed with him — right-clicking a DIFFERENT card went straight to {@code openPopover}, which sets
     * {@code popReveal = null}. The old sheet was not closed. It was ERASED, in the same frame, and a new one
     * began growing from zero. The eye waits for an exit it never gets, so it reads the entrance as an
     * offcut.
     *
     * <p>The obvious fix — play the close, then the open — costs a full 280ms of nothing on every switch, and
     * he flagged that himself. The right one is that a switch is not a close followed by an open: the player
     * did not shut the sheet, he MOVED it. So the sheet stays alive and GLIDES to the new card's column,
     * easing its position and its height, with the new content inside. Nothing vanishes, so nothing can be
     * truncated.
     *
     * <p>Not a content cross-fade, and the reason is a rule bought with a bug: text does not dim through
     * {@code pushOpacity} in this stack (Stage 9 — only {@code Color.scaleAlpha} works), and the widgets
     * inside the sheet paint themselves straight from the tokens. A fade would have silently faded the
     * grounds and left the labels at full strength. The glide needs no alpha at all.
     */
    private final ValueTween popXTween =
            new ValueTween(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
    private final ValueTween popYTween =
            new ValueTween(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
    private boolean popSwapping;   // a live sheet is gliding to another card — do NOT restart the reveal

    private float winX, winY, winW, winH, bodyY, bodyH, headH, footH;

    // Fixed centred window (owner decision 2026-07-02): dragging + grip removed — the menu always
    // sits dead centre. Compact 660 width kept (owner): 4 columns fit via SMALLER cards, not a
    // wider window — hence the vertical card composition (chip on top, name under).
    private static final float WIN_W = 660f, WIN_H = 380f;

    // ---- the Club canvas (Stage 60) ---------------------------------------------------------------
    // The menu USED to live in Minecraft's GUI units, where 1 unit = the player's GUI Scale in pixels.
    // So the same 660x380 window was 1320px wide at scale 2 and 2640px at scale 4 — on a 1920px screen
    // the latter doesn't fit, and the menu clamped, dropped columns and squeezed its rail. In other
    // words a Minecraft VIDEO setting silently redesigned our UI (owner: "он всегда должен быть как
    // делался изначально, независимо от масштаба интерфейса").
    //
    // So the menu now owns its own canvas: a FIXED 540 units tall, always — which is exactly the space
    // it was designed in (gui scale 2 at 1080p). Everything is drawn in those units through one matrix
    // scale, so the window is always the full 660x380 with 4 columns and the full rail, and it keeps the
    // same PROPORTION of the screen on 720p, 1080p, 1440p or 4K. The player's GUI Scale no longer
    // reaches it at all.
    // The canvas itself now lives in com.club.ui.ClubCanvas — the HUD moved onto the same space in Stage 63
    // ("какого хера у нас худы меняют свой размер в зависимости от настроек в игре"), and two copies of this
    // arithmetic in two files is how they drift apart.
    private static final float CANVAS_H = com.club.ui.ClubCanvas.HEIGHT;
    private float canvasW = 960f, canvasH = CANVAS_H;
    private float canvasK = 1f;   // Minecraft GUI units per Club unit (the matrix scale)

    /** Resolve the Club canvas for the current window. */
    private void updateCanvas() {
        MinecraftClient mc = MinecraftClient.getInstance();
        canvasK = com.club.ui.ClubCanvas.scale(mc);
        canvasH = CANVAS_H;
        canvasW = Math.max(1f, width / canvasK);   // this screen's own MC width → Club units
    }

    /** MC-unit mouse position → Club-canvas units. */
    private double cx(double mx) { return mx / canvasK; }
    private double cy(double my) { return my / canvasK; }

    private static final int GRID_COLS = 4;               // the IDEAL column count; layoutAll may drop to 3 or 2
    private static final float CARD_CHROME = 42f;         // TILE_PAD + CHIP + NAME_GAP + TILE_PAD (fixed per card)
    private static final float NAME_SLOT_MIN = 44f;       // a name slot narrower than this isn't worth a column
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
        bindListening = false; bindModule = null; bindReserved = false; keysDown.clear();
        // gui-move mirrors the raw key state and only acts on EDGES — so it must forget what it thinks is
        // held whenever the bindings can be cleared behind its back (Stage 62). setScreen() calls
        // KeyBinding.unpressAll(), and init() runs on the way back in: without this, walking into the HUD
        // editor and back left W believed-down but actually-up, and the player had to let go and press it
        // again to move at all.
        if (moveWasDown != null) java.util.Arrays.fill(moveWasDown, false);
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
    private float railYRel(int i) { return 10 + i * railRow; }   // tray top (+2) + tray padding (+8)
    private float railY(int i) { return bodyY + railYRel(i); }   // absolute row position (rows + hit-test)

    // ---- state ---------------------------------------------------------------

    /** Harness/promo seam: land on a NAMED category, not on "two tabs from wherever we happened to be".
     *  The menu deliberately reopens on the last category the player used (a static {@code lastCatIndex}),
     *  which is right for a player and useless for a scripted shot — the promo run drove Ctrl+Tab twice and
     *  landed on Player, because the previous scene had already moved it. Returns false if there is no such
     *  category, so a typo fails loudly instead of quietly photographing the wrong screen. */
    public boolean selectCategory(String name) {
        List<Category> cats = MenuContent.build(this::openHudEditor);
        for (int i = 0; i < cats.size(); i++) {
            if (cats.get(i).name().equalsIgnoreCase(name)) { setCategory(i); return true; }
        }
        return false;
    }

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
                if (idx < gridCols) { enterSearchZone(); return true; }   // top row → hop up to search
                idx -= gridCols; break;
            case GLFW_KEY_DOWN:  idx = Math.min(mods.size() - 1, idx + gridCols); break;
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
        // A LIVE sheet switching cards is a MOVE, not a close-then-open. Keep the reveal — erasing it here is
        // exactly what made the second popover's entrance look like an offcut (item 7). The position and
        // height tweens then carry the sheet to the new card on their own.
        popSwapping = popModule != null && popModule != m && !popClosing && popReveal != null;

        popModule = m; popAX = ax; popAY = ay; popAH = ah; tabIndex = 0; openDrop = null;
        resetArmed = false;                     // a fresh popover never opens pre-armed
        popClosing = false;
        if (!popSwapping) popReveal = null;     // a cold open: render() plays the grow-in on the first frame
        segSlide = new Transition(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
        rebuildPopover();
    }

    private void rebuildPopover() {
        float prevOffset = (popScroll != null) ? popScroll.scrollOffset() : 0f;   // survive dropdown-expand / tab-switch rebuild
        focus.clear();
        focus.register(search);
        // Width FIRST — the rows size their label column against it. Never wider than the WELL it lives
        // in: clamping to the window let a 236px sheet spill past the well (and invert the popX clamp)
        // once the window shrank, i.e. at GUI scale 4 (Stage 58).
        popW = Math.min(POP_W, Math.max(POP_W_MIN, wellW - 24f));
        float innerW = popW - 2 * POP_PAD;
        popInnerW = innerW;
        popCol = buildSettings(popModule);
        popContentH = popCol.measure(innerW, 99999f).h();
        popScroll = new ScrollArea(popCol);
        positionPopover();   // sets popY + popH below the card grid, capped so long content scrolls
        popScroll.scrollOffset(prevOffset);                  // restore scroll after layout has set the clamp bounds
    }

    /** The popover opens as a tidy panel BELOW the card grid — it never covers the cards (owner). Its
     *  height fits the content, capped to the room between the grid and the well's bottom; anything
     *  taller scrolls inside (ScrollArea draws the side scrollbar). X follows the clicked card's
     *  column so it reads as "this card's settings", clamped to stay in the well.
     *
     *  <p>Escape hatch (Stage 58): on a SMALL window there may be no usable room under the cards at
     *  all — at GUI scale 4 (the stock auto scale on 1080p) the whole menu shrinks to 432×222 and two
     *  card rows leave a NEGATIVE strip below. Rather than render a 1px sliver with an unreachable
     *  settings list, the sheet then takes the well and overlaps the cards: covering them is bad, but
     *  being unusable is worse.</p> */
    private void positionPopover() {
        float gridTop = wellY + 12;
        int rows = Math.max(1, (grid.children().size() + gridCols - 1) / gridCols);
        float gridBottom = gridTop + rows * TILE_H + (rows - 1) * Tokens.spacing().sm();
        float wellBottom = wellY + wellH - 8;
        float want = popContentH + 2 * POP_PAD;
        float below = wellBottom - (gridBottom + 8);          // room from under the cards to the well bottom
        if (below >= Math.min(want, POP_H_MIN)) {             // normal: a tidy panel under the cards
            popY = gridBottom + 8;
            popRoom = below;
            popH = fit(want, below);
        } else {                                              // no usable room — take the well, overlap the cards
            popRoom = wellBottom - gridTop;
            popH = Math.max(1f, fit(want, popRoom));
            popY = Math.max(gridTop, wellBottom - popH);
        }
        popX = clamp(popAX, wellX + 12, wellX + wellW - 12 - popW);
        popScroll.layout(popX + POP_PAD, popY + POP_PAD,
                Math.max(1f, popW - 2 * POP_PAD), Math.max(1f, popH - 2 * POP_PAD));
    }

    /**
     * The sheet's height: what the content WANTS, capped by the room available — and when the cap bites, it
     * lands in a gap BETWEEN rows instead of through the middle of one.
     *
     * <p>This used to be a bare {@code min(want, room)}. When the content was taller than the room, the cap
     * was an arbitrary pixel, and whichever row straddled it got sliced through its letters — the owner's
     * item 10, and he was right to call it ugly rather than to call it a scroll: a half-drawn row is neither
     * shown nor hidden, so the eye reads damage. The scrollbar still says there is more; now everything above
     * it is whole.
     */
    private float fit(float want, float room) {
        if (want <= room) return want;                        // it all fits — nothing to snap
        float snapped = popCol.snapToChild(popInnerW, 99999f, room - 2 * POP_PAD);
        return Math.min(want, snapped + 2 * POP_PAD);
    }

    /** Harness seam: the first card's box in MINECRAFT gui units — i.e. where a real mouse would have to
     *  click it. Exercises the canvas conversion end to end: if cx()/cy() were wrong, clicking here would
     *  miss (Stage 60). Returns {centreX, centreY} or null when the grid is empty. */
    public double[] firstCardCentreMc() {
        if (grid.children().isEmpty()) return null;
        var t = (ModuleTile) grid.children().get(0);
        return new double[] { (t.xLeft() + t.width() / 2f) * canvasK, (t.yTop() + t.height() / 2f) * canvasK };
    }

    /** Harness seam: the i-th card's centre in MINECRAFT gui units — where a real mouse would have to click
     *  it. Null when the index is out of range. Used to right-click one card and then another, which is the
     *  only way to exercise a popover SWITCH the way a player performs it. */
    public double[] cardCentreMc(int i) {
        if (i < 0 || i >= grid.children().size()) return null;
        var t = (ModuleTile) grid.children().get(i);
        return new double[] { (t.xLeft() + t.width() / 2f) * canvasK, (t.yTop() + t.height() / 2f) * canvasK };
    }

    /**
     * Harness seam: the index of the first card in this category that actually HAS a toggle, or -1.
     *
     * <p>The click test used to hard-code card 0, and card 0 of Visuals is Zoom — which lost its on/off
     * switch in v0.1.3 (a hold module has no off state; the key is the switch). A click on a toggle-less card
     * flips nothing, so {@code enabled()} read false before AND after, and the check went red for a reason
     * that had nothing to do with the thing it was testing: whether a real mouse click, converted out of
     * Minecraft's units and into the mod's own canvas, LANDS where it appears to.
     *
     * <p>Asking for a card that can answer is not weakening the check — the conversion it exercises is the
     * same for every tile. Hard-coding an index that happened to work was the weak part.
     */
    public int firstTogglableCard() {
        for (int i = 0; i < grid.children().size(); i++)
            if (((ModuleTile) grid.children().get(i)).m.hasToggle()) return i;
        return -1;
    }

    /** Harness seam: is the i-th card's module enabled? */
    public boolean cardEnabled(int i) {
        if (i < 0 || i >= grid.children().size()) return false;
        return ((ModuleTile) grid.children().get(i)).m.enabled();
    }

    /** Harness seam: is the first card's module enabled? */
    public boolean firstCardEnabled() {
        if (grid.children().isEmpty()) return false;
        return ((ModuleTile) grid.children().get(0)).m.enabled();
    }

    /** Harness seam: the open popover's geometry — {contentH, height, y, room below the grid}. All
     *  zeroes when no popover is open. The only way to assert the "fits below the cards / scrolls when
     *  it can't" contract from inside a running game. */
    public float[] popoverGeometry() {
        if (popModule == null) return new float[] {0, 0, 0, 0};
        return new float[] {popContentH, popH, popY, (wellY + wellH - 8) - popY};
    }

    /**
     * Harness seam: is the sheet as tall as it CAN be without cutting a row through the middle?
     *
     * <p>Two claims in one, and both have to hold or the answer is false:
     * <ul>
     *   <li><b>Whole</b> — the bottom edge lands in a gap between rows, never inside one. This is the bug the
     *       owner reported (item 10): a "Reset to Default" sliced through its letters.</li>
     *   <li><b>Maximal</b> — and it did not buy that by throwing away room. The height must be the LARGEST
     *       row-boundary that fits, not merely A row-boundary. Without this half of the check, a sheet that
     *       snapped down to a single visible row would pass.</li>
     * </ul>
     *
     * <p>It is recomputed from the children, not read back from the value {@link #fit} produced — so a
     * regression to the old bare {@code min(want, room)} turns it red instead of agreeing with itself.
     */
    /**
     * Harness seam: how far the sheet's reveal has played, 0..1. A LIVE sheet reads 1.
     *
     * <p>This exists to make item 7 falsifiable. The bug was one line — {@code openPopover} set
     * {@code popReveal = null}, so switching cards erased the sheet and grew a new one from zero — and it is
     * exactly the kind of line a future refactor puts back while "simplifying". Motion cannot be asserted
     * from a screenshot, but its ABSENCE can: right-click card A, right-click card B, and this must still
     * read 1. If the sheet died and respawned it reads near 0, and the check goes red.
     */
    public float popoverRevealProgress() {
        if (popModule == null || popReveal == null) return 0f;
        return popReveal.progress(uiCtx.time());
    }

    public boolean popoverIsMaximalAndWhole() {
        if (popModule == null || popCol == null) return true;
        float viewportH = popH - 2 * POP_PAD;
        if (popContentH <= viewportH + 0.5f) return true;          // it all fits — nothing to cut
        float roomInner = popRoom - 2 * POP_PAD;
        return Math.abs(popCol.snapToChild(popInnerW, 99999f, roomInner) - viewportH) < 0.5f;
    }

    /** Deferred close: begins the shrink-out; render() calls {@link #reallyClosePopover} once it has fully collapsed. */
    private void closePopover() {
        if (popModule != null) popClosing = true;
    }

    private void reallyClosePopover() {
        popModule = null; popCol = null; popScroll = null; openDrop = null;
        popReveal = null; popClosing = false; resetArmed = false; popResetBtn = null;
        bindListening = false; bindModule = null; bindReserved = false; popBindBtn = null;
        focus.clear();
        if (search != null) focus.register(search);
        if (popFromGrid) { popFromGrid = false; enterGridZone(); }   // hand the zone back (Space → Esc round-trip)
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /**
     * Why the key on this module's bind row will not do what the row implies — or null when it will
     * (Stage 62). Two ways a key lies, both previously silent, both reachable without touching the Club UI:
     *
     * <ul>
     *   <li><b>Shadowed.</b> A hold module (Zoom/Freelook) sits on the same key, and {@code ModuleBinds.tick}
     *       refuses to fire a toggle from it — one press must not drive two actions. Right call, but vanilla's
     *       Controls screen can create that overlap behind the popover's back, and the row went on showing a
     *       key that had quietly stopped working.</li>
     *   <li><b>Taken.</b> The key is also a vanilla (or other mod's) action. Minecraft dispatches ONE binding
     *       per physical key, so one of the two dies — arbitrarily, by HashMap order (see KeyConflicts). Bind
     *       Freelook to Left Shift from this popover and you lose sneak, with nothing anywhere saying why.</li>
     * </ul>
     *
     * We name the other action the way the player's own Controls screen names it — that is where they will go
     * to fix it. Kept short: the popover is a 236px sheet and a caption that outgrows it is clipped mid-word.
     */
    private static String bindWarning(Module m, boolean hold) {
        String shadow = hold ? null : com.club.modules.binds.ModuleBinds.shadowedBy(m.name());
        if (shadow != null) return shadow + " holds this key";
        String bound = hold ? com.club.modules.binds.HoldKeys.boundKey(m.name())
                            : com.club.modules.binds.ModuleBinds.boundKey(m.name());
        String other = com.club.modules.binds.KeyConflicts.other(bound);
        if (other == null) return null;
        if (other.length() > 22) other = other.substring(0, 21) + "…";
        return "Also: " + other;
    }

    private Column buildSettings(Module m) {
        // Tight row gap (Stage 57): the popover lives BELOW the cards now, so a simple popover must fit
        // that room without scrolling — xs keeps the rows neat but compact; only tall ones (Hands) scroll.
        Column col = new Column().gap(Tokens.spacing().xs()).crossAlign(CrossAlign.STRETCH);
        int accent = catAccent(catIndex);   // 11.9: the popover speaks its category's colour

        List<Setting> settings0;
        if (m.hasTabs()) {
            List<Tab> tbs = m.tabs();
            settings0 = tbs.get(Math.min(tabIndex, tbs.size() - 1)).settings();
        } else settings0 = m.settings();
        // A SLIDER is the one control wide enough to fight its own label for the row: at its natural
        // width it left the label a 40px slot on a squeezed window and the text ran straight over the
        // track (Stage 58 review, GUI scale 4). So slider rows get a FIXED label column — the widest
        // label in this popover, capped — and the slider FILLS what's left: labels never collide, and
        // every track in the sheet starts on the same line. Narrow controls (toggles, buttons) keep the
        // old label-fills-the-row layout: they can't crowd anything.
        float lw = 0f;
        var lblRole = Tokens.type().label();
        for (Setting s : settings0)
            if (s instanceof SliderSetting) lw = Math.max(lw, Ui.text().width(s.label(), lblRole.weight(), lblRole.size()));
        final float labelW = Math.min(Math.max(lw + 8f, 40f), Math.max(48f, popInnerW * 0.42f));

        // A module that is ON but standing down says so, at the top, before the controls it isn't applying
        // (Stage 62 — see MenuContent.notice). Hidden behind an expanded dropdown like every other row.
        if (openDrop == null) {
            String notice = MenuContent.notice(m.name());
            if (notice != null)
                col.add(new Label(notice, Tokens.type().caption()).color(Tokens.palette().stateWarn()));
        }

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
            } else if (s instanceof SliderSetting) {
                Component ctrl = buildControl(s, accent);
                Row rr = new Row().crossAlign(CrossAlign.CENTER);
                rr.add(new FixedW(new Label(s.label(), lblRole).color(Tokens.palette().textMuted()), labelW));
                rr.add(ctrl, Sizing.fill());
                col.add(new LaneRow(rr)); focus.register(ctrl);
            } else {
                Component ctrl = buildControl(s, accent);
                Row rr = new Row().crossAlign(CrossAlign.CENTER);
                rr.add(new Label(s.label(), Tokens.type().label()).color(Tokens.palette().textMuted()), Sizing.fill());
                rr.add(ctrl);
                col.add(new LaneRow(rr)); focus.register(ctrl);
            }
        }

        // A KEY ROW ONLY WHERE A KEY EARNS ONE (owner, v0.1.3 item 3.2). It used to appear on all thirteen
        // cards, which is why he called the popover a bin: nobody rebinds their hands mid-duel, picks a swing
        // animation between hits, or hot-keys the HUD editor. The list lives in ModuleBinds.KEYED, which is
        // also the gate the tick loop reads — so a row removed here cannot leave a key that still fires with
        // nowhere left to un-bind it.
        if (com.club.modules.binds.ModuleBinds.hasKeyRow(m.name()) && openDrop == null) {
            // The module's key row. TWO kinds, and saying which is which is the whole point (Stage 58):
            //   • HOLD modules (Zoom, Freelook) → "Hold key": rebinds the REAL vanilla binding you hold.
            //     They must not also have a toggle bind — binding Zoom to its own hold key made one
            //     press both zoom and switch the module off ("работает через раз", owner).
            //   • everything else → "Toggle key": a tap flips the module (ModuleBinds).
            // The field shows the assigned key ("Not set" when unbound, English names); click → listening.
            // Width pinned to the widest state so arming can't resize the popover.
            boolean hold = com.club.modules.binds.HoldKeys.isHold(m.name());
            String cur = hold ? com.club.modules.binds.HoldKeys.label(m.name())
                              : com.club.modules.binds.ModuleBinds.label(m.name());
            boolean listening = bindListening && bindModule == m;
            // While listening the field shows an ellipsis, not a sentence: the sentence is in the caption
            // below, and a field that grows to hold an instruction is a field that resizes the sheet.
            Button bind = new Button(listening ? "…" : (cur != null ? cur : "Not set"))
                    .variant(Button.Variant.VALUE).armed(listening).accent(accent).compact().hug();
            // A KEY IS A VALUE, NOT AN ACTION (owner, v0.1.3). It used to be pinned to the width of its
            // widest possible label — "Press any key…" — which, with Button's 96px alignment floor under it,
            // made an unbound key a 96×24 slab: the heaviest object in the sheet, for the control the player
            // touches least. It hugs now, and sits in the same right-hand column as a slider's number.
            //
            // The sheet still must not resize when the button arms, so the floor is the width of the widest
            // RESTING label ("Not set") — and the listening state no longer needs a wide one, because the
            // instruction moved to the caption below, which is where an instruction belongs anyway.
            bind.minWidth(Math.min(new Button("Not set").compact().hug().measure(10_000f, 22f).w(),
                                   popInnerW * 0.55f));
            bind.onClick(() -> {
                boolean was = bindListening && bindModule == m;
                bindListening = !was; bindModule = bindListening ? m : null; bindReserved = false;
                rebuildPopover();
            });
            popBindBtn = bind;
            Row rr = new Row().crossAlign(CrossAlign.CENTER);
            rr.add(new Label(hold ? "Hold key" : "Toggle key", Tokens.type().label())
                    .color(Tokens.palette().textMuted()), Sizing.fill());
            rr.add(bind);
            col.add(new LaneRow(rr)); focus.register(bind);
            // Discoverable clear (Stage 46/51): a plain-English hint appears only while listening — and
            // says so when the key you just tried is the one that opens this menu (Stage 58).
            // Both strings are kept SHORT on purpose: the popover is a fixed 236px sheet and a caption
            // that outgrows it just gets clipped mid-word (Stage 58 — caught in the harness shot).
            if (listening)
                col.add(new Label(bindReserved ? "That key opens the menu"
                                               : "Press a key · Esc cancels · Delete clears", Tokens.type().caption())
                        .color(bindReserved ? Tokens.palette().stateWarn() : Tokens.palette().textFaint()));
            else {
                // …and when NOT listening, the row admits what the key will actually do (Stage 62). All
                // three of these states were silent: the field showed a key, and the key did nothing, or
                // did something else's job. The player had no way to find that out from inside the mod.
                String warn = bindWarning(m, hold);
                if (warn != null)
                    col.add(new Label(warn, Tokens.type().caption()).color(Tokens.palette().stateWarn()));
            }
        } else popBindBtn = null;

        if (m.hasReset() && openDrop == null) {   // hidden while a dropdown is expanded (see the guard above)
            // Stage 35 (hardened in 38): a destructive action asks first. Click 1 ARMS the button — it
            // becomes the soft-accent "Confirm reset?" (Stage 50); click 2 within the hold executes.
            // Arm and decay swap IN PLACE (label/armed only, width pinned to the idle box) — a rebuild
            // here would orphan an in-flight slider drag (losing its save-on-release) and wipe keyboard
            // focus. Only the CONFIRM rebuilds (controls must re-seed to the reset values); keyboard
            // focus is handed to the fresh button so Enter-Enter works end to end.
            // WEIGHT SHOULD MATCH FREQUENCY (owner, v0.1.3 item 3.1). This was a full-width 36px bordered
            // button on the popover's darkest ground — the largest, heaviest object in the sheet, for the one
            // control a player presses least and only after they have already decided. It taught the eye the
            // wrong hierarchy: the sliders they actually drag looked lighter than the button they never touch.
            //
            // It is a TEXT button now: no ground, no edge, resting in textFaint, and it turns RED under the
            // cursor because its accent is the palette's own stateLow. Destructive, quiet, unmistakable —
            // and no bigger than the caption it sits under.
            //
            // The ARMED state keeps its full voice ("Confirm reset?" in the category accent): a confirmation
            // that whispers is a confirmation nobody reads. The width floor pins the box to the WIDER of the
            // two labels so arming cannot shift the row under the cursor mid-click.
            Button reset = new Button(resetArmed ? "Confirm reset?" : "Reset to default")
                    .variant(Button.Variant.TEXT).armed(resetArmed).compact().hug()
                    .accent(resetArmed ? accent : Tokens.palette().stateLow());
            reset.minWidth(new Button("Confirm reset?").compact().hug().measure(10_000f, 22f).w());
            reset.onClick(() -> {
                if (resetArmed) {
                    boolean kb = popResetBtn != null && popResetBtn.isFocusVisible();
                    resetArmed = false;
                    m.reset().run();
                    // Reset also restores the key (Stage 46/58): a hold module goes back to its FACTORY
                    // key (Zoom → C) — clearing it would leave the module on but unusable; a toggle bind
                    // is simply removed (it has no default).
                    if (com.club.modules.binds.HoldKeys.isHold(m.name()))
                        com.club.modules.binds.HoldKeys.reset(m.name());
                    else com.club.modules.binds.ModuleBinds.set(m.name(), null);
                    openDrop = null;
                    rebuildPopover();
                    if (kb && popResetBtn != null) focus.focusKeyboard(popResetBtn);
                } else {
                    resetArmed = true; resetArmAt = uiCtx.time();
                    // The accent swaps WITH the label: quiet red at rest, the category's own accent once
                    // armed. Both are set in place — a rebuild here would orphan an in-flight slider drag.
                    if (popResetBtn != null) popResetBtn.label("Confirm reset?").armed(true).accent(accent);
                }
            });
            popResetBtn = reset;
            // Separated by AIR, not by a line. This screen's rule since Stage 22 is that zones separate by
            // panel edges and depth — "every hairline divider is gone" (see the class header), and the owner
            // approved that board. A footer that needed a rule to be legible would be a footer that had not
            // been made quiet enough; making it quiet was the whole point. One extra step of the spacing scale
            // is all it takes now that the button no longer wears a border.
            col.add(Spacer.fixed(Tokens.spacing().sm()));
            Row rr = new Row(); rr.add(reset);
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
        updateCanvas();
        float m = 24;
        winW = Math.min(WIN_W, canvasW - 2 * m);
        winH = Math.min(WIN_H, canvasH - 2 * m);
        winX = (canvasW - winW) / 2f;                   // always dead centre (no drag, no saved position)
        winY = (canvasH - winH) / 2f + entranceYOff;
        bodyY = winY + headH; bodyH = winH - headH - footH;

        root.layout(0, 0, canvasW, canvasH);

        // Stage-22 wells: the shallow tray hugs the category list; the deep well owns the rest.
        // 16px of breathing between them (owner: two figures, not one), 12px to the frame edges.
        //
        // Everything here USED to be fixed at the 660×380 ideal — a 172px rail, a 160px tray, 4 columns.
        // But the window is clamped to the screen, and Minecraft's stock AUTO gui scale is 4 on 1080p
        // (scaled 480×270) and 3 on 720p: the window shrinks to ~432×222, while the rail, the tray and
        // the cards did not. The tray then drew its last rows OUTSIDE the window, and 4 columns left the
        // module names a 1px slot, so they collapsed to the 6px floor and smeared across each other
        // (Stage 59 audit). The rail, its rows and the column count now all follow the space available.
        railWX = winX + 12; railWY = bodyY + 2;
        railWW = clamp(winW * 0.26f, 108f, 172f);
        railRow = Math.min(RAIL_ROW, Math.max(20f, (bodyH - 20f) / cats.size()));   // rows compress before they spill
        railWH = Math.min(16 + cats.size() * railRow, bodyH - 4);
        wellX = railWX + railWW + 16; wellY = bodyY + 2;
        wellW = winX + winW - 12 - wellX;
        wellH = bodyH - 2 - 6;

        float searchH = 32;
        float searchW = clamp(wellW * 0.9f, 120f, 200f);   // never wider than the well it sits over
        // the search's RIGHT edge lands exactly on the content well's right line (header on the grid)
        search.layout(winX + winW - 12 - searchW, winY + (headH - searchH) / 2f, searchW, searchH);

        // Column count follows the width: 4 like the reference board when there's room, else 3 or 2. A
        // card needs its fixed chrome (pad+chip+gap+pad) PLUS a readable name slot — below that the grid
        // is just noise, so we drop a column instead of shrinking the type into illegibility.
        float gridW = wellW - 24;
        float sm = Tokens.spacing().sm();
        gridCols = 2;
        for (int c = GRID_COLS; c >= 2; c--) {
            float cw = (gridW - (c - 1) * sm) / c;
            if (cw - CARD_CHROME >= NAME_SLOT_MIN) { gridCols = c; break; }
        }
        grid.cols(gridCols);

        // One name size per category (11.7): the largest size <= NAME_BASE at which the LONGEST
        // module name of the category still fits the card's text slot. All visible names share it —
        // uniform look, nothing ever truncates OR overflows (the floor is sanity-only; with sane
        // names sizes stay >= ~9px). Computed over the whole category (not the search subset) so
        // the size doesn't jump while typing.
        float cellW = (gridW - (gridCols - 1) * sm) / gridCols;
        float slot = Math.max(1f, cellW - CARD_CHROME);
        cardNameSlot = slot;
        // Fit against the slot MINUS a hair: the width∝size scaling is linear but glyph advances round,
        // so an exact fit lands a pixel over and the clip shaves the last letter (Stage 59).
        float fitSlot = Math.max(1f, slot - 3f);
        float fit = NAME_BASE;
        var nameWeight = Tokens.type().heading().weight();
        for (Module mod : cats.get(catIndex).modules()) {
            float atBase = Ui.text().width(mod.name(), nameWeight, NAME_BASE);
            if (atBase > fitSlot) fit = Math.min(fit, Math.max(NAME_MIN, fitSlot * NAME_BASE / atBase));
        }
        cardNameSize = fit;

        if (gridScroll != null) gridScroll.layout(wellX + 12, wellY + 12, gridW, wellH - 24);

        if (popModule != null) positionPopover();
    }

    // ---- render --------------------------------------------------------------

    @Override public void render(DrawContext dc, int mouseX, int mouseY, float delta) {
        updateCanvas();
        // Everything below is in CLUB units: one matrix scale maps them to the screen, so the menu is
        // always the size it was designed at, whatever the player's GUI Scale is (Stage 60). Mouse
        // coordinates arrive in MC units and are converted at each input entry point (cx/cy).
        dc.getMatrices().push();
        dc.getMatrices().scale(canvasK, canvasK, 1f);
        Ui.beginFrame(dc, canvasK);
        try {
            renderCanvas(dc, (float) cx(mouseX), (float) cy(mouseY));
        } finally {
            Ui.endFrame();           // submit the batched shapes while the canvas matrix is still up
            dc.getMatrices().pop();
            Ui.beginFrame(dc, 1f);   // hand the units back — the HUD and every other screen draw in MC units
        }
    }

    private void renderCanvas(DrawContext dc, float mouseX, float mouseY) {
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
        r.rect(0, 0, canvasW, canvasH, Color.scaleAlpha(SCRIM, scrimA));

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
        r.roundedRect(railWX + 8, indY + 3, railWW - 16, railRow - 6, Tokens.radius().sm(), Tokens.surface().surfaceHi());

        // rail categories inside the tray — label colour eases on hover / active; icon rides the same ease
        for (int i = 0; i < cats.size(); i++) {
            float yy = railY(i);
            boolean active = i == catIndex;
            boolean hov = mouseX >= railWX && mouseX <= railWX + railWW && mouseY >= yy && mouseY < yy + railRow;
            railText[i].target((active || hov) ? 1f : 0f, now);
            int col = Color.scaleAlpha(
                    Color.lerp(Tokens.palette().textMuted(), Tokens.palette().textHi(), railText[i].value(now)), ep);
            float isz = 15f, iconX = railWX + 20f;
            cats.get(i).icon().draw(uiCtx, iconX, yy + (railRow - isz) / 2f, isz, col);
            float textX = iconX + isz + 8f, clipR = railWX + railWW - 10f;
            r.pushClip(textX, yy, clipR - textX, railRow);
            uiCtx.text().draw(cats.get(i).name(), textX, yy + (railRow - catLh) / 2f,
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
            r.roundedRect(railWX, indY + 4, 4, railRow - 8, 2f, railBarColor(now));

        r.border(winX, winY, winW, winH, lg, Tokens.border().thickness(), Tokens.border().strong());

        // armed reset decays back to the quiet ghost when the hold expires (Stage 35). In place —
        // NO rebuild (Stage 38): a timer-driven rebuild would orphan an in-flight slider drag
        // (freezing it and skipping its save-on-release) and silently clear keyboard focus.
        if (resetArmed && now - resetArmAt > RESET_ARM_HOLD) {
            resetArmed = false;
            if (popResetBtn != null)
                popResetBtn.label("Reset to default").armed(false).accent(Tokens.palette().stateLow());
        }

        // popover on top — grows in / shrinks out; content clipped to the eased height (also eases resize)
        if (popModule != null && popScroll != null) {
            if (popReveal == null) {
                popReveal = new Reveal(Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate(), now);
                popHTween.snap(popH, now);
                // A cold open arrives already at its card — there is nothing to glide FROM, and easing in from
                // a stale position would make the sheet fly across the well on the first frame.
                popXTween.snap(popX, now); popYTween.snap(popY, now);
            }
            popSwapping = false;        // consumed: from here the tweens carry it
            if (popClosing) popReveal.close(now);
            popHTween.set(popH, now);   // eases the popover height on open + on resize (dropdown open/close)
            popXTween.set(popX, now);   // …and its column, when the player switches cards (item 7)
            popYTween.set(popY, now);
            if (popClosing && popReveal.gone(now)) {
                reallyClosePopover();
            } else {
                float drawnH = Math.max(1f, popHTween.get(now) * popReveal.progress(now));
                float pr = Tokens.radius().md();

                // THE SHEET IS DRAWN WHERE THE TWEENS SAY, NOT WHERE THE LAYOUT SAYS. The two agree at rest;
                // during a card switch the layout is already standing at the destination while the ink is
                // still on its way there — which is the whole point of item 7.
                //
                // The CONTENT has to travel with the ground, or the sheet glides out from under its own rows.
                // It is re-laid-out only while the glide is actually in flight (they are equal at rest, so the
                // common case costs one float compare), and always to the FINAL height: the content must not
                // reflow as the sheet grows — it is CLIPPED to drawnH, which is what makes a resize read as a
                // reveal instead of a reflow.
                float popX = popXTween.get(now), popY = popYTween.get(now);
                if (Math.abs(popX - this.popX) > 0.01f || Math.abs(popY - this.popY) > 0.01f) {
                    float off = popScroll.scrollOffset();
                    popScroll.layout(popX + POP_PAD, popY + POP_PAD,
                            Math.max(1f, popW - 2 * POP_PAD), Math.max(1f, popH - 2 * POP_PAD));
                    popScroll.scrollOffset(off);
                }
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
        com.club.ui.LegacyNotice.draw(uiCtx, canvasW);   // loud fallback plaque (draws nothing on MODERN)
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

    // Every mouse entry point converts MC GUI units → Club canvas units first (Stage 60): the widgets
    // below live entirely in canvas space, so a click must be measured in the same ruler it was drawn to.
    @Override public boolean mouseClicked(double mx0, double my0, int b) {
        double mx = cx(mx0), my = cy(my0);
        if (closing) return true;   // window is fading out — swallow clicks
        gridFocused = false;        // any mouse interaction leaves the keyboard grid zone (ring hides)
        popFromGrid = false;        //   …and cancels the popover's pending zone hand-back
        if (bindListening) {        // a click cancels key capture (clicking the Bind button re-arms it)
            bindListening = false; bindModule = null; bindReserved = false;
            rebuildPopover();
        }
        focus.clickFocus(mx, my);
        if (insidePop(mx, my)) {
            popScroll.mouseClicked(mx, my, 0); pressOwner = 1; return true;   // RMB behaves as LMB inside; never closes
        }
        if (b == 0 && mx >= railWX && mx <= railWX + railWW && my >= bodyY + 10 && my < bodyY + 10 + cats.size() * railRow) {
            int i = (int) ((my - (bodyY + 10)) / railRow);
            if (i >= 0 && i < cats.size()) { if (i != catIndex) setCategory(i); return true; }
        }
        if (root.mouseClicked(mx, my, b)) { pressOwner = 2; return true; }
        closePopover();                                        // click on empty space (any button) → close
        pressOwner = 0;
        return super.mouseClicked(mx0, my0, b);
    }
    @Override public boolean mouseReleased(double mx0, double my0, int b) {
        double mx = cx(mx0), my = cy(my0);
        // inside the popover the gesture is routed as left-button (RMB acts as LMB there)
        boolean h = (pressOwner == 1) ? (popScroll != null && popScroll.mouseReleased(mx, my, 0)) : root.mouseReleased(mx, my, b);
        pressOwner = 0;
        return h || super.mouseReleased(mx0, my0, b);
    }
    @Override public boolean mouseDragged(double mx0, double my0, int b, double dx0, double dy0) {
        double mx = cx(mx0), my = cy(my0), dx = dx0 / canvasK, dy = dy0 / canvasK;   // deltas scale too
        boolean h = (pressOwner == 1) ? (popScroll != null && popScroll.mouseDragged(mx, my, 0, dx, dy))
                                      : root.mouseDragged(mx, my, b, dx, dy);
        return h || super.mouseDragged(mx0, my0, b, dx0, dy0);
    }
    @Override public void mouseMoved(double mx0, double my0) {
        double mx = cx(mx0), my = cy(my0);
        root.mouseMoved(mx, my);
        if (popScroll != null) popScroll.mouseMoved(mx, my);
    }
    @Override public boolean mouseScrolled(double mx0, double my0, double hx, double v) {
        double mx = cx(mx0), my = cy(my0);
        if (insidePop(mx, my) && popScroll != null && popScroll.mouseScrolled(mx, my, v)) return true;
        return root.mouseScrolled(mx, my, v) || super.mouseScrolled(mx0, my0, hx, v);
    }
    @Override public boolean keyPressed(int k, int scan, int mods) {
        boolean shift = (mods & GLFW_MOD_SHIFT) != 0, ctrl = (mods & GLFW_MOD_CONTROL) != 0;
        // Minecraft dispatches keyPressed for GLFW_REPEAT too, and Screen never sees the action code —
        // so a HELD key arrives as a stream of presses. Tracking the physical down-set (cleared in
        // keyReleased) is the only way to tell a fresh press from a repeat, and both routes below need
        // that: the Enter that CLICKED the Hotkey button would otherwise repeat straight into the
        // capture and bind itself, then re-arm the button it's still focused on — an oscillation that
        // rewrites options.txt at key-repeat rate (Stage 58 review).
        boolean repeat = !keysDown.add(k);

        // Keybind capture wins over EVERYTHING (incl. the menu-close key): the next key assigns,
        // Esc cancels, Backspace/Delete clears (Stage 43, hardened 45/58).
        if (bindListening && bindModule != null) {
            if (repeat) return true;   // a key still held from before the prompt never binds itself
            boolean hold = com.club.modules.binds.HoldKeys.isHold(bindModule.name());
            if (k == GLFW_KEY_UNKNOWN)
                return true;   // no GLFW keycode → would be a dead SCANCODE bind; ignore, keep listening
            if (com.club.ClubClient.openMenuKey.matchesKey(k, scan)) {
                // RESERVED. Stay in capture and SAY so — cancelling here left the key's GLFW repeat to
                // land on the close route below, which shut the menu mid-bind (owner, Stage 58).
                if (!bindReserved) { bindReserved = true; rebuildPopover(); }
                return true;
            }
            if (k == GLFW_KEY_ESCAPE) { /* cancel — keep the current bind */ }
            else if (k == GLFW_KEY_BACKSPACE || k == GLFW_KEY_DELETE) {
                if (hold) com.club.modules.binds.HoldKeys.set(bindModule.name(), null);
                else com.club.modules.binds.ModuleBinds.set(bindModule.name(), null);
            } else {
                InputUtil.Key key = InputUtil.fromKeyCode(k, scan);
                if (hold) com.club.modules.binds.HoldKeys.set(bindModule.name(), key);
                else com.club.modules.binds.ModuleBinds.set(bindModule.name(), key.getTranslationKey());
            }
            bindListening = false; bindModule = null; bindReserved = false;
            rebuildPopover();
            if (popBindBtn != null) focus.focusKeyboard(popBindBtn);   // keyboard flow continues on the Bind row
            return true;
        }

        // An ACTIVATION key that is merely repeating must not fire again: holding Enter on a card would
        // toggle its module ~15×/s, and on a button it would re-run its action (Stage 58 review). Text
        // editing still gets its repeats — those keys (Backspace, arrows, …) fall through untouched.
        if (repeat && (k == GLFW_KEY_SPACE || k == GLFW_KEY_ENTER || k == GLFW_KEY_KP_ENTER)) return true;

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
    @Override public boolean keyReleased(int k, int scan, int mods) {
        keysDown.remove(k);               // the key is up: a fresh press of it is a real press again
        return super.keyReleased(k, scan, mods);
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
    private static final float POP_W = 236f, POP_W_MIN = 140f;   // sheet width, and the floor on a squeezed window
    private static final float POP_H_MIN = 88f;                  // below this a settings sheet is not worth showing under the cards

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

            // Name: uniform per-category size (cardNameSize, auto-fit in layoutAll). The fit maths keeps
            // it inside the slot at any sane width — the clip is the hard guarantee it can NEVER bleed
            // onto the next card, which is exactly what happened once the window got squeezed and the
            // size hit its floor (Stage 59 audit).
            int nameCol = Color.lerp(Tokens.palette().textDesc(), Tokens.palette().textHi(), onv);
            float ns = cardNameSize;
            float nameLh = ctx.text().lineHeight(Tokens.type().heading().weight(), ns);
            float nameX = chipX + CHIP + NAME_GAP;
            r.pushClip(nameX, y, Math.max(1f, Math.min(cardNameSlot, x + w - TILE_PAD - nameX)), h);
            ctx.text().draw(m.name(), nameX, y + (h - nameLh) / 2f,
                    TextStyle.of(Tokens.type().heading().weight(), ns, Color.scaleAlpha(nameCol, screenAlpha * ta)));
            r.popClip();

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

    /** Pins a child to an exact width inside a Row (the label column of a slider row). */
    private static final class FixedW extends Container {
        private final Component c;
        private final float fw;
        FixedW(Component c, float fw) { this.c = c; this.fw = fw; addChild(c); }
        @Override public Size measure(float aw, float ah) { return new Size(fw, c.measure(fw, ah).h()); }
        @Override public void layout(float x, float y, float w, float h) {
            super.layout(x, y, fw, h);
            c.layout(x, y, fw, h);
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
