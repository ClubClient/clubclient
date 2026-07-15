package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.component.FocusManager;
import com.club.ui.component.UiContextImpl;
import com.club.ui.UiContext;
import com.club.ui.component.widget.Button;
import com.club.ui.component.widget.Label;
import com.club.ui.component.widget.Slider;
import com.club.ui.component.widget.Toggle;
import com.club.ui.layout.Size;
import com.club.ui.motion.Reveal;
import com.club.ui.motion.ValueTween;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Functional V2 HUD editor: drag elements (edge/center snap + optional grid), scale/toggle/configure
 * via a selection popover, persist to ClubConfig. Opened from the Club menu (Misc → HUD Editor);
 * returns to its parent screen on close.
 */
public final class HudEditorScreen extends Screen {
    private final UiContextImpl uiCtx = new UiContextImpl();
    private final FocusManager focus = new FocusManager();
    private final long start = System.nanoTime();
    /** Entrance fade (owner, v0.1.3 #6: the editor "буквально в один тик резко меняется"). The menu fades
     *  its scrim in on open; the editor slammed to an opaque dark panel in a single frame. Created lazily on
     *  the first rendered frame — {@code init()} runs before the clock is meaningful. */
    private com.club.ui.motion.Transition entrance;

    private final HudCanvas canvas = new HudCanvas(true)
            .add(new EffectsElement()).add(new TargetElement()).add(new InfoElement()).add(new ArmorElement())
            .add(new SprintElement());
    private final Pane toolbar = new Pane();
    private float tbX, tbY, tbW, tbH;   // compact floating toolbar (top-centre overlay)
    private final Pane popover = new Pane();
    private int popX, popY, popW, popH;
    private boolean hasPopover;
    // Popover grow-in on (re)selection + eased resize; content clipped to the eased height.
    private Reveal popReveal;
    private final ValueTween popHTween =
            new ValueTween(0f, Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate());
    private TextStyle stHint, stPop, stToolLabel;
    private int pressOwner;   // which surface owns the active gesture: 0 none, 1 toolbar, 2 popover, 3 canvas
    private final Screen parent;

    // Toolbar Reset confirmation (Stage 35): first click arms, second executes; the arm decays.
    private static final float RESET_ARM_HOLD = 3f;
    private Button tbReset;
    private boolean tbResetArmed;
    private float tbResetArmAt;

    // Last cursor position in CLUB units (fed by render each frame) — the arrow-nudge hover target
    // resolves against it, and a cursor-follow nudge advances it (Stage 36).
    private int lastMx, lastMy;

    // ---- the Club canvas (Stage 63) — the editor edits in the same space the HUD is drawn in ----
    private float canvasW = 960f, canvasH = com.club.ui.ClubCanvas.HEIGHT, canvasK = 1f;

    private void updateCanvas() {
        MinecraftClient mc = MinecraftClient.getInstance();
        canvasK = com.club.ui.ClubCanvas.scale(mc);
        canvasW = com.club.ui.ClubCanvas.width(mc);
        canvasH = com.club.ui.ClubCanvas.HEIGHT;
    }

    /** A Minecraft-unit coordinate (or delta) → Club units. */
    private double cx(double v) { return v / canvasK; }
    private double cy(double v) { return v / canvasK; }

    private void disarmReset() {
        tbResetArmed = false;
        if (tbReset != null) tbReset.label("Reset").armed(false);
    }

    public HudEditorScreen() { this(null); }
    public HudEditorScreen(Screen parent) { super(Text.literal("HUD Editor")); this.parent = parent; }
    private ClubConfig.Hud h() { return ClubConfig.get().hud; }

    @Override protected void init() {
        updateCanvas();   // the toolbar is centred on the CLUB canvas, so resolve it before building it
        canvas.onSelectionChanged = this::rebuildPopover;
        buildToolbar();
        rebuildPopover();
    }

    // Compact floating toolbar centred at the top: "Grid snap" [toggle]  [Reset]  [Done].
    // A tight overlay so the whole screen underneath stays usable for positioning HUD elements.
    private static final float TB_TOGGLE_W = 40, TB_TOGGLE_H = 22, TB_BTN_W = 92, TB_BTN_H = 26,
                              TB_GAP = 12, TB_PAD = 14, TB_LABEL_W = 66;

    // The editor is a dark utility overlay — full-brand fills scream on it (owner: Done/Enabled
    // "бросаются в глаза"). Controls take the muted-brand TOKEN (Stage 25); GHOST for buttons.
    private static final int QUIET_ACC = Tokens.accent().accentQuiet();

    // Raised utility sheet (Stage 25): toolbar + popover share the menu popover's grammar —
    // card tone above the ground, quadratic mini-halo instead of a loud border, quiet hairline.
    private static final int[] SHEET_HALO = {8, 6, 4, 3, 2, 1};
    private void sheet(com.club.ui.UiRenderer r, float x, float y, float w, float h, float rad) {
        if (Ui.backend() == Ui.Backend.MODERN) {   // Stage 26: LEGACY renders halo rings solid — gate them
            for (int i = SHEET_HALO.length; i >= 1; i--) {
                float s = i * 2f;
                r.roundedRect(x - s, y - s, w + 2 * s, h + 2 * s, rad + s, Color.withAlpha(0xFF000000, SHEET_HALO[i - 1]));
            }
        }
        r.roundedRect(x, y, w, h, rad, Tokens.surface().surface());
        r.border(x, y, w, h, rad, Tokens.border().thickness(), Tokens.border().defaultColor());
    }

    private void buildToolbar() {
        toolbar.clear();
        tbResetArmed = false;   // a rebuilt toolbar (init/resize/F11) shows the unarmed button — the
                                // flag must match, or a click on the quiet "Reset" would fire instantly
        tbH = 40;
        tbW = TB_PAD + TB_LABEL_W + 8 + TB_TOGGLE_W + TB_GAP + TB_BTN_W + TB_GAP + TB_BTN_W + TB_PAD;
        tbX = (canvasW - tbW) / 2f;
        tbY = 8;
        float toggleX = tbX + TB_PAD + TB_LABEL_W + 8;
        float btnY = tbY + (tbH - TB_BTN_H) / 2f;
        Toggle grid = new Toggle(canvas.gridSnap()).accent(QUIET_ACC).onChange(canvas::setGridSnap);
        grid.layout(toggleX, tbY + (tbH - TB_TOGGLE_H) / 2f, TB_TOGGLE_W, TB_TOGGLE_H);
        // Stage 35: Reset wipes EVERY element's position — it asks first. Click 1 arms it to the
        // soft-accent "Confirm?" (Stage 50); click 2 within the hold executes; the arm decays back in
        // render(). Label swaps in place (fixed TB_BTN_W bounds), no toolbar rebuild.
        tbReset = new Button("Reset").variant(Button.Variant.GHOST).accent(QUIET_ACC)
                .onClick(() -> {
                    if (tbResetArmed) { disarmReset(); resetPositions(); }
                    else { tbResetArmed = true; tbResetArmAt = uiCtx.time(); tbReset.label("Confirm?").armed(true); }
                });
        tbReset.layout(toggleX + TB_TOGGLE_W + TB_GAP, btnY, TB_BTN_W, TB_BTN_H);
        Button done = new Button("Done").variant(Button.Variant.GHOST).onClick(this::close);
        done.layout(toggleX + TB_TOGGLE_W + TB_GAP + TB_BTN_W + TB_GAP, btnY, TB_BTN_W, TB_BTN_H);
        toolbar.add(grid); toolbar.add(tbReset); toolbar.add(done);
    }

    private static final int POP_HEAD = 28, POP_ROW = 26, POP_PAD_B = 8;
    private final java.util.List<Label> popLabels = new java.util.ArrayList<>();
    private float popNeeded;    // widest [label + control] row → popW grows to fit (3-way segments)
    private boolean popClosing; // deselected: the popover plays its grow-in in reverse, then drops
    private HudElement popSel;  // the element the current rows belong to — anchor/title while closing

    /** Build the popover's rows (label + control) for the selected element. Per-frame positioning is in positionPopover(). */
    private void rebuildPopover() {
        HudElement sel = canvas.selected();
        if (sel == null) {                 // deselected → reverse the reveal over the LAST rows (render drops them)
            if (hasPopover) popClosing = true;
            return;
        }
        // Same element rebuilt in place (e.g. the armor Layout switch hiding the Value row): keep the
        // reveal — popHTween eases to the new height. A new/re-selection replays the grow-in instead.
        boolean inPlace = sel == popSel && hasPopover && !popClosing;
        popSel = sel; popClosing = false;
        popover.clear(); popLabels.clear(); focus.clear(); hasPopover = true;
        popNeeded = 0;
        addRow("Enabled", new Toggle(sel.cfgEnabled()).accent(QUIET_ACC).onChange(v -> { setEnabled(sel, v); save(); }));
        // Stage 29: sliders apply live (onChange) and write to disk once per gesture (onRelease).
        addRow("Size", new Slider(sel.cfgScale(), 0.5f, 2f, 0.05f).onChange(v -> setScale(sel, v)).onRelease(this::save));
        if (sel instanceof EffectsElement) {
            addRow("Layout", new Segmented(new String[]{"Column", "Row"}, h().potionHorizontal ? 1 : 0,
                    i -> { h().potionHorizontal = (i == 1); save(); }));
        } else if (sel instanceof TargetElement) {
            // No "Range" row (owner, v0.1.3 #10): a configurable detection distance is a soft cheat — it
            // would let a player see a target named before they could reach it. Reach is a fixed 4 blocks in
            // TargetHud now. The Target element keeps only its Size, like every other chip.
        } else if (sel instanceof ArmorElement) {
            addRow("Layout", new Segmented(new String[]{"Column", "Line"},
                    com.club.config.ArmorLayout.fromIndex(h().armorLayout).index(),
                    i -> { h().armorLayout = i; save(); }));
            addRow("Value", new Segmented(new String[]{"Percent", "Count"}, h().armorPercent ? 0 : 1,
                    i -> { h().armorPercent = (i == 0); save(); }));
        }
        popW = Math.max(178, Math.round(popNeeded) + 24);
        popH = POP_HEAD + popover.children().size() * POP_ROW + POP_PAD_B;
        if (!inPlace) popReveal = null;   // replay the grow-in ONLY for a (re)selection, never mid-edit
    }

    private void addRow(String name, Component ctrl) {
        popLabels.add(new Label(name, Tokens.type().label()).color(Tokens.palette().textMuted()));
        popover.add(ctrl);
        focus.register(ctrl);
        float cw = ctrl instanceof Slider ? 88 : ctrl.measure(10_000, 22).w(); if (cw <= 0) cw = 88;
        popNeeded = Math.max(popNeeded,
                Ui.text().width(name, Tokens.type().label().weight(), Tokens.type().label().size()) + 12 + cw);
    }

    /** Re-anchor the popover beside its element every frame, so it follows when the element is dragged.
     *  Anchored to {@link #popSel} (not the live selection) — a closing popover keeps its place. */
    private void positionPopover() {
        if (!hasPopover) return;
        HudElement sel = popSel;
        if (sel == null) { hasPopover = false; popClosing = false; return; }
        // Anchor to the element's top-left (invariant under scaling) and place the popover ABOVE the element,
        // or BELOW when there's no room above. popX never depends on the element's width, so dragging the Size
        // slider neither shifts the slider (no feedback jitter) nor lets the growing element overlap the popover.
        int ex = (int) sel.xLeft(), ey = (int) sel.yTop(), eh = (int) sel.height();
        popX = Math.max(8, Math.min(ex, width - popW - 8));
        int above = ey - popH - 8;
        popY = above >= 8 ? above : Math.max(8, Math.min(height - popH - 8, ey + eh + 8));
        int ix = popX + 12, iw = popW - 24, y = popY + POP_HEAD;
        var ctrls = popover.children();
        for (int i = 0; i < ctrls.size(); i++) {
            Component ctrl = ctrls.get(i);
            if (i < popLabels.size()) popLabels.get(i).layout(ix, y + 3, iw, 14);
            float cw = ctrl instanceof Slider ? 88 : ctrl.measure(iw, 22).w(); if (cw <= 0) cw = 88;
            ctrl.layout(ix + iw - cw, y, cw, 22);
            y += POP_ROW;
        }
    }

    private void setEnabled(HudElement e, boolean v) {
        if (e instanceof EffectsElement) h().potions = v;
        else if (e instanceof TargetElement) h().target = v;
        else if (e instanceof ArmorElement) h().armor = v;
        else if (e instanceof SprintElement) h().sprint = v;
        else h().info = v;
    }
    private void setScale(HudElement e, float v) {
        if (e instanceof EffectsElement) h().potionScale = v;
        else if (e instanceof TargetElement) h().targetScale = v;
        else if (e instanceof ArmorElement) h().armorScale = v;
        else if (e instanceof SprintElement) h().sprintScale = v;
        else h().infoScale = v;
    }
    private void save() { ClubConfig.save(); }

    private void resetPositions() {
        ClubConfig.Hud c = h();
        c.potionX = 8; c.potionY = 70; c.targetX = -1; c.targetY = -1; c.infoX = 8; c.infoY = 120; c.armorX = 8; c.armorY = 8;
        c.sprintX = -1; c.sprintY = -1;
        save(); canvas.clearSelection(); rebuildPopover();
    }

    @Override public void render(DrawContext dc, int mxMc, int myMc, float d) {
        // The editor draws in CLUB units, like the HUD it edits and the menu it was opened from (Stage 63).
        // It used to work in Minecraft's GUI units — so the editor and the in-world HUD disagreed about how
        // big a pixel is, and every position you dragged came out a different size once you closed it.
        MinecraftClient mc = MinecraftClient.getInstance();
        canvasK = com.club.ui.ClubCanvas.scale(mc);
        canvasW = com.club.ui.ClubCanvas.width(mc);
        canvasH = com.club.ui.ClubCanvas.HEIGHT;
        int mx = (int) Math.round(mxMc / canvasK), my = (int) Math.round(myMc / canvasK);

        dc.getMatrices().push();
        dc.getMatrices().scale(canvasK, canvasK, 1f);
        Ui.beginFrame(dc, canvasK);
        try {
        com.club.hud.PixelIcons.set(dc);   // duotone icons draw through this DrawContext
        var r = Ui.renderer(); Typography ty = Tokens.type();
        if (stHint == null) initStyles();
        uiCtx.setTime((System.nanoTime() - start) / 1_000_000_000f);

        // Entrance: the dark ground fades IN over the world instead of snapping opaque in one frame. The
        // world shows through during the ~0.28s ramp, so opening the editor reads as a transition, not a cut
        // (owner #6). The toolbar rides a small rise on the same curve. No content alpha (text does not fade
        // through pushOpacity in this stack — Stage 9); the scrim reveal alone carries it.
        float entranceNow = uiCtx.time();
        if (entrance == null)
            entrance = new com.club.ui.motion.Transition(0f, Tokens.motion().durations().normal(),
                    Tokens.motion().easings().decelerate());
        entrance.target(1f, entranceNow);
        float ep = entrance.value(entranceNow);
        r.rect(0, 0, canvasW, canvasH, Color.scaleAlpha(0xFF0A0E15, ep));

        canvas.setScreen(Math.round(canvasW), Math.round(canvasH));
        canvas.layoutFromConfig(mc);
        Decals.watermark(uiCtx); Decals.crosshair(uiCtx, Math.round(canvasW), Math.round(canvasH));

        // armed Reset decays back to the quiet ghost when the hold expires (Stage 35)
        if (tbResetArmed && uiCtx.time() - tbResetArmAt > RESET_ARM_HOLD) disarmReset();

        lastMx = mx; lastMy = my;   // arrow-nudge hover target resolves against this (Stage 36)
        canvas.mouseMoved(mx, my);
        canvas.render(uiCtx);

        // compact floating toolbar — a top-centre overlay so the whole canvas underneath stays usable
        float tbR = Tokens.radius().md();
        sheet(r, tbX, tbY, tbW, tbH, tbR);
        uiCtx.text().draw("Grid snap", tbX + TB_PAD, tbY + (tbH - ty.label().lineHeight()) / 2f, stToolLabel);
        toolbar.mouseMoved(mx, my); toolbar.render(uiCtx);

        // hint, tucked at the very bottom (out of the way of element positioning)
        uiCtx.text().draw("Left-drag to move · Right-click for settings", canvasW / 2f, canvasH - 18, stHint);

        // popover — compact, re-anchored each frame; grows in on selection, plays the reveal in
        // reverse on deselection (dropped only once fully collapsed), content clipped to the eased height
        positionPopover();
        if (hasPopover) {
            float now = uiCtx.time();
            if (popReveal == null) { popReveal = new Reveal(Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate(), now); popHTween.snap(popH, now); }
            if (popClosing) popReveal.close(now);
            popHTween.set(popH, now);
            if (popClosing && popReveal.gone(now)) {
                // fully collapsed → really drop; focus MUST clear too, or the invisible widgets keep
                // eating Space/arrow keys and silently editing the deselected element
                hasPopover = false; popClosing = false; popReveal = null;
                popover.clear(); popLabels.clear(); popSel = null; focus.clear();
            } else {
                float drawnH = Math.max(1f, popHTween.get(now) * popReveal.progress(now));
                sheet(r, popX, popY, popW, drawnH, Tokens.radius().md());
                r.pushClip(popX, popY, popW, drawnH);
                r.roundedRect(popX + 12, popY + 11, 6, 6, 2, Tokens.accent().accent());
                uiCtx.text().draw(titleOf(popSel), popX + 24, popY + 7, stPop);
                for (Label l : popLabels) l.render(uiCtx);
                popover.mouseMoved(mx, my); popover.render(uiCtx);
                r.popClip();
            }
        }
        } finally {
            Ui.endFrame();   // submit the batched shapes — nothing else will (Stage 61)
            dc.getMatrices().pop();
        }
    }

    private String titleOf(HudElement e) { return e.displayName() + " HUD"; }
    private void initStyles() {
        Typography t = Tokens.type();
        stHint  = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textDesc()).align(Align.CENTER);
        stPop   = TextStyle.of(t.body().weight(), t.body().size(), Tokens.palette().textHi());
        stToolLabel = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textMuted());
    }

    @Override public void renderBackground(DrawContext dc, int mx, int my, float d) { }

    // input: toolbar + popover widgets first (capture), then canvas drag
    // Route the whole gesture (press→drag→release) to ONE owner so a popover/toolbar click never reaches
    // the canvas (which would otherwise deselect and close the popover on every control click).
    //
    // Mouse coordinates arrive in MINECRAFT units and everything below them lives on the Club canvas, so
    // they are converted at the boundary (Stage 63) — every one of them, including the drag DELTAS: a drag
    // measured in the wrong space is an element that lags or races the cursor by exactly the ratio between
    // the two, which is the kind of bug that gets reported as "the HUD editor feels weird".
    @Override public boolean mouseClicked(double mxMc, double myMc, int b) {
        double mx = cx(mxMc), my = cy(myMc);
        focus.clickFocus(mx, my);
        if (toolbar.mouseClicked(mx, my, b)) { pressOwner = 1; return true; }
        if (hasPopover && !popClosing && mx >= popX && mx <= popX + popW && my >= popY && my <= popY + popH) { popover.mouseClicked(mx, my, b); pressOwner = 2; return true; }
        if (canvas.mouseClicked(mx, my, b)) { pressOwner = 3; return true; }
        pressOwner = 0;
        return super.mouseClicked(mxMc, myMc, b);
    }
    @Override public boolean mouseReleased(double mxMc, double myMc, int b) {
        double mx = cx(mxMc), my = cy(myMc);
        int owner = pressOwner; pressOwner = 0;
        boolean h = switch (owner) {
            case 1 -> toolbar.mouseReleased(mx, my, b);
            case 2 -> popover.mouseReleased(mx, my, b);
            case 3 -> canvas.mouseReleased(mx, my, b);
            default -> false;
        };
        return h || super.mouseReleased(mxMc, myMc, b);
    }
    @Override public boolean mouseDragged(double mxMc, double myMc, int b, double dxMc, double dyMc) {
        double mx = cx(mxMc), my = cy(myMc), dx = cx(dxMc), dy = cy(dyMc);
        boolean h = switch (pressOwner) {
            case 1 -> toolbar.mouseDragged(mx, my, b, dx, dy);
            case 2 -> popover.mouseDragged(mx, my, b, dx, dy);
            case 3 -> canvas.mouseDragged(mx, my, b, dx, dy);
            default -> false;
        };
        return h || super.mouseDragged(mxMc, myMc, b, dxMc, dyMc);
    }
    @Override public boolean keyPressed(int k, int scan, int mods) {
        if (k == GLFW_KEY_ESCAPE) { close(); return true; }
        if (k == GLFW_KEY_TAB) { if ((mods & GLFW_MOD_SHIFT) != 0) focus.previous(); else focus.next(); return true; }
        if (focus.keyPressed(k, scan, mods)) return true;   // a focused popover control (slider arrows) wins
        if (nudgeSelected(k, mods)) return true;
        return super.keyPressed(k, scan, mods);
    }

    /** Arrow-nudge (Stage 32; hover-first + magnet since Stage 36): point at a HUD element and tap
     *  arrows — the HOVERED element moves 1px per press (Shift = the 8px grid step) with the drag's
     *  edge/centre magnetism and guide lines ({@link HudCanvas#keyNudge}). The OS cursor rides the
     *  element (same applied delta, magnet jumps included), so the hover never slips off mid-nudge.
     *  With nothing hovered, the selected element (open popover) still nudges — cursor stays put. */
    private boolean nudgeSelected(int k, int mods) {
        int dx = k == GLFW_KEY_LEFT ? -1 : k == GLFW_KEY_RIGHT ? 1 : 0;
        int dy = k == GLFW_KEY_UP   ? -1 : k == GLFW_KEY_DOWN  ? 1 : 0;
        if (dx == 0 && dy == 0) return false;
        if (canvas.dragging()) return true;   // a held drag owns the element AND the guide lines — arrows wait
        // The hover hit-test must not look THROUGH the floating overlays: a cursor resting on the
        // popover/toolbar hovers nothing (click routing agrees) — the selected element still nudges.
        HudElement hover = overOverlay(lastMx, lastMy) ? null : canvas.elementAt(lastMx, lastMy);
        HudElement target = hover != null ? hover : canvas.selected();
        if (target == null) return false;
        int step = (mods & GLFW_MOD_SHIFT) != 0 ? HudCanvas.GRID_STEP : 1;
        int[] applied = canvas.keyNudge(target, dx * step, dy * step, uiCtx.time());
        if (applied[0] != 0 || applied[1] != 0) {
            save();
            if (target == hover) {   // cursor rides the element so it can't slip off
                moveCursorBy(applied[0], applied[1]);
                lastMx += applied[0]; lastMy += applied[1];
            }
        }
        return true;
    }

    /** True when (x,y) rests on a floating overlay — the toolbar or the open settings popover. */
    private boolean overOverlay(double x, double y) {
        if (x >= tbX && x <= tbX + tbW && y >= tbY && y <= tbY + tbH) return true;
        return hasPopover && !popClosing
                && x >= popX && x <= popX + popW && y >= popY && y <= popY + popH;
    }

    /** Shift the OS cursor by a CLUB-unit delta, keeping MC's tracked position in sync so the move never
     *  lands as a phantom look/hover delta. glfwSetCursorPos fires no callback.
     *
     *  <p>Two conversions, not one (Stage 63): the nudge is measured in Club units, the cursor lives in
     *  window pixels, and Minecraft's GUI units sit between them. Skipping the canvas step made the cursor
     *  drift off the element it was supposed to be riding, by exactly the canvas ratio.</p> */
    private void moveCursorBy(int dxClub, int dyClub) {
        MinecraftClient mc = MinecraftClient.getInstance();
        var win = mc.getWindow();
        double sx = (double) win.getWidth()  / Math.max(1, win.getScaledWidth());
        double sy = (double) win.getHeight() / Math.max(1, win.getScaledHeight());
        double nx = mc.mouse.getX() + dxClub * canvasK * sx, ny = mc.mouse.getY() + dyClub * canvasK * sy;
        glfwSetCursorPos(win.getHandle(), nx, ny);
        var mouse = (com.club.mixin.MouseAccessor) mc.mouse;
        mouse.club$setX(nx); mouse.club$setY(ny);
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    /** Free-form container (children positioned by the screen). */
    private static final class Pane extends Container {
        void add(Component c) { addChild(c); }
        void clear() { children.clear(); }
        @Override public Size measure(float aw, float ah) { return new Size(aw, ah); }
    }

    private static final float SEG_H = 22f;

    /** Quiet segmented selector (the menu's SegmentRow language): one recessed track, equal
     *  segments, an eased brand-tinted pill sliding between them; active label = accent, inactive
     *  = muted. Replaces the loud Dropdowns of the first popover (owner: "кричащие"). */
    private final class Segmented extends Component {
        private final String[] labels;
        private final java.util.function.IntConsumer onChange;
        private int index;
        private final com.club.ui.motion.Transition slide;

        Segmented(String[] labels, int index, java.util.function.IntConsumer onChange) {
            this.labels = labels; this.index = index; this.onChange = onChange;
            slide = new com.club.ui.motion.Transition(index,
                    Tokens.motion().durations().normal(), Tokens.motion().easings().standard());
        }

        @Override public Size measure(float aw, float ah) {
            Typography ty = Tokens.type();
            float m = 0;
            for (String l : labels) m = Math.max(m, Ui.text().width(l, ty.label().weight(), ty.label().size()));
            return new Size(Math.min(aw, labels.length * (m + 14)), SEG_H);
        }

        @Override public void render(UiContext ctx) {
            var r = ctx.renderer();
            Typography ty = Tokens.type();
            float now = ctx.time(), rad = 7f;
            // Stage 25: quiet track from the WELL family (mirrors the menu's SegmentRow) — borderless.
            r.roundedRect(x, y, w, h, rad, Tokens.surface().well());
            float segW = w / labels.length;
            int acc = Tokens.accent().accent();
            r.roundedRect(x + slide.value(now) * segW + 2, y + 2, segW - 4, h - 4, rad - 2, Color.withAlpha(acc, 0x2E));
            float lh = ty.label().lineHeight();
            for (int i = 0; i < labels.length; i++)
                ctx.text().draw(labels[i], x + segW * i + segW / 2f, y + (h - lh) / 2f,
                        TextStyle.of(ty.label().weight(), ty.label().size(),
                                i == index ? acc : Tokens.palette().textMuted()).align(Align.CENTER));
        }

        @Override public boolean mouseClicked(double mx, double my, int b) {
            if (b != 0 || !contains(mx, my)) return false;
            int seg = Math.max(0, Math.min(labels.length - 1, (int) ((mx - x) / (w / labels.length))));
            if (seg != index) {
                index = seg;
                slide.target(seg, uiCtx.time());
                onChange.accept(seg);
            }
            return true;
        }
    }
}
