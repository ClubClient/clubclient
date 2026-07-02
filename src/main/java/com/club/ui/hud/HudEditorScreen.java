package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.component.FocusManager;
import com.club.ui.component.UiContextImpl;
import com.club.ui.component.widget.Button;
import com.club.ui.component.widget.Dropdown;
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

    private final HudCanvas canvas = new HudCanvas(true)
            .add(new EffectsElement()).add(new TargetElement()).add(new InfoElement()).add(new ArmorElement());
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

    public HudEditorScreen() { this(null); }
    public HudEditorScreen(Screen parent) { super(Text.literal("HUD Editor")); this.parent = parent; }
    private ClubConfig.Hud h() { return ClubConfig.get().hud; }

    @Override protected void init() {
        canvas.onSelectionChanged = this::rebuildPopover;
        buildToolbar();
        rebuildPopover();
    }

    // Compact floating toolbar centred at the top: "Grid snap" [toggle]  [Reset]  [Done].
    // A tight overlay so the whole screen underneath stays usable for positioning HUD elements.
    private static final float TB_TOGGLE_W = 40, TB_TOGGLE_H = 22, TB_BTN_W = 92, TB_BTN_H = 26,
                              TB_GAP = 12, TB_PAD = 14, TB_LABEL_W = 66;

    private void buildToolbar() {
        toolbar.clear();
        tbH = 40;
        tbW = TB_PAD + TB_LABEL_W + 8 + TB_TOGGLE_W + TB_GAP + TB_BTN_W + TB_GAP + TB_BTN_W + TB_PAD;
        tbX = (width - tbW) / 2f;
        tbY = 8;
        float toggleX = tbX + TB_PAD + TB_LABEL_W + 8;
        float btnY = tbY + (tbH - TB_BTN_H) / 2f;
        Toggle grid = new Toggle(canvas.gridSnap()).onChange(canvas::setGridSnap);
        grid.layout(toggleX, tbY + (tbH - TB_TOGGLE_H) / 2f, TB_TOGGLE_W, TB_TOGGLE_H);
        Button reset = new Button("Reset").variant(Button.Variant.GHOST).onClick(this::resetPositions);
        reset.layout(toggleX + TB_TOGGLE_W + TB_GAP, btnY, TB_BTN_W, TB_BTN_H);
        Button done = new Button("Done").variant(Button.Variant.PRIMARY).onClick(this::close);
        done.layout(toggleX + TB_TOGGLE_W + TB_GAP + TB_BTN_W + TB_GAP, btnY, TB_BTN_W, TB_BTN_H);
        toolbar.add(grid); toolbar.add(reset); toolbar.add(done);
    }

    private static final int POP_HEAD = 28, POP_ROW = 26, POP_PAD_B = 8;
    private final java.util.List<Label> popLabels = new java.util.ArrayList<>();

    /** Build the popover's rows (label + control) for the selected element. Per-frame positioning is in positionPopover(). */
    private void rebuildPopover() {
        popover.clear(); popLabels.clear(); focus.clear(); hasPopover = canvas.selected() != null;
        if (!hasPopover) return;
        HudElement sel = canvas.selected();
        popW = 178;
        addRow("Enabled", new Toggle(sel.cfgEnabled()).onChange(v -> { setEnabled(sel, v); save(); }));
        addRow("Size", new Slider(sel.cfgScale(), 0.5f, 2f, 0.05f).onChange(v -> { setScale(sel, v); save(); }));
        if (sel instanceof EffectsElement) {
            addRow("Layout", new Dropdown(new String[]{"Column", "Row"}, h().potionHorizontal ? 1 : 0)
                    .onChange(i -> { h().potionHorizontal = (i == 1); save(); }));
        } else if (sel instanceof TargetElement) {
            addRow("Range", new Slider(h().targetDistance, 3f, 32f, 1f)
                    .onChange(v -> { h().targetDistance = Math.round(v); save(); }));
        } else if (sel instanceof ArmorElement) {
            addRow("Layout", new Dropdown(new String[]{"Vertical", "Row"}, h().armorVertical ? 0 : 1)
                    .onChange(i -> { h().armorVertical = (i == 0); save(); }));
            addRow("Value", new Dropdown(new String[]{"Percent", "Count"}, h().armorPercent ? 0 : 1)
                    .onChange(i -> { h().armorPercent = (i == 0); save(); }));
        }
        popH = POP_HEAD + popover.children().size() * POP_ROW + POP_PAD_B;
        popReveal = null;   // replay the grow-in for this (new) selection
    }

    private void addRow(String name, Component ctrl) {
        popLabels.add(new Label(name, Tokens.type().label()).color(Tokens.palette().textMuted()));
        popover.add(ctrl);
        focus.register(ctrl);
    }

    /** Re-anchor the popover beside the selected element every frame, so it follows when the element is dragged. */
    private void positionPopover() {
        if (!hasPopover) return;
        HudElement sel = canvas.selected();
        if (sel == null) { hasPopover = false; return; }
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
        else h().info = v;
    }
    private void setScale(HudElement e, float v) {
        if (e instanceof EffectsElement) h().potionScale = v;
        else if (e instanceof TargetElement) h().targetScale = v;
        else if (e instanceof ArmorElement) h().armorScale = v;
        else h().infoScale = v;
    }
    private void save() { ClubConfig.save(); }

    private void resetPositions() {
        ClubConfig.Hud c = h();
        c.potionX = 8; c.potionY = 70; c.targetX = -1; c.targetY = -1; c.infoX = 8; c.infoY = 120; c.armorX = 8; c.armorY = 8;
        save(); canvas.clearSelection(); rebuildPopover();
    }

    @Override public void render(DrawContext dc, int mx, int my, float d) {
        Ui.beginFrame(dc);
        var r = Ui.renderer(); Typography ty = Tokens.type();
        if (stHint == null) initStyles();
        r.rect(0, 0, width, height, 0xFF0A0E15);
        uiCtx.setTime((System.nanoTime() - start) / 1_000_000_000f);

        canvas.setScreen(width, height);
        canvas.layoutFromConfig(MinecraftClient.getInstance());
        Decals.watermark(uiCtx); Decals.crosshair(uiCtx, width, height);

        canvas.mouseMoved(mx, my);
        canvas.render(uiCtx);

        // compact floating toolbar — a top-centre overlay so the whole canvas underneath stays usable
        float tbR = Tokens.radius().md();
        r.roundedRect(tbX, tbY, tbW, tbH, tbR, Color.withAlpha(Tokens.surface().bg2(), 0xE6));
        r.border(tbX, tbY, tbW, tbH, tbR, Tokens.border().thickness(), Tokens.border().strong());
        uiCtx.text().draw("Grid snap", tbX + TB_PAD, tbY + (tbH - ty.label().lineHeight()) / 2f, stToolLabel);
        toolbar.mouseMoved(mx, my); toolbar.render(uiCtx);

        // hint, tucked at the very bottom (out of the way of element positioning)
        uiCtx.text().draw("Left-drag to move · Right-click for settings", width / 2f, height - 18, stHint);

        // popover — compact, re-anchored each frame; grows in on selection, content clipped to the eased height
        positionPopover();
        if (hasPopover) {
            float now = uiCtx.time();
            if (popReveal == null) { popReveal = new Reveal(Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate(), now); popHTween.snap(popH, now); }
            popHTween.set(popH, now);
            float drawnH = Math.max(1f, popHTween.get(now) * popReveal.progress(now));
            r.roundedRect(popX, popY, popW, drawnH, Tokens.radius().md(), Tokens.surface().bg2());
            r.border(popX, popY, popW, drawnH, Tokens.radius().md(), 1, Tokens.border().defaultColor());
            r.pushClip(popX, popY, popW, drawnH);
            r.roundedRect(popX + 12, popY + 11, 6, 6, 2, Tokens.accent().accent());
            uiCtx.text().draw(titleOf(canvas.selected()), popX + 24, popY + 7, stPop);
            for (Label l : popLabels) l.render(uiCtx);
            popover.mouseMoved(mx, my); popover.render(uiCtx);
            r.popClip();
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
    @Override public boolean mouseClicked(double mx, double my, int b) {
        focus.clickFocus(mx, my);
        if (toolbar.mouseClicked(mx, my, b)) { pressOwner = 1; return true; }
        if (hasPopover && mx >= popX && mx <= popX + popW && my >= popY && my <= popY + popH) { popover.mouseClicked(mx, my, b); pressOwner = 2; return true; }
        if (canvas.mouseClicked(mx, my, b)) { pressOwner = 3; return true; }
        pressOwner = 0;
        return super.mouseClicked(mx, my, b);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        int owner = pressOwner; pressOwner = 0;
        boolean h = switch (owner) {
            case 1 -> toolbar.mouseReleased(mx, my, b);
            case 2 -> popover.mouseReleased(mx, my, b);
            case 3 -> canvas.mouseReleased(mx, my, b);
            default -> false;
        };
        return h || super.mouseReleased(mx, my, b);
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        boolean h = switch (pressOwner) {
            case 1 -> toolbar.mouseDragged(mx, my, b, dx, dy);
            case 2 -> popover.mouseDragged(mx, my, b, dx, dy);
            case 3 -> canvas.mouseDragged(mx, my, b, dx, dy);
            default -> false;
        };
        return h || super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean keyPressed(int k, int scan, int mods) {
        if (k == GLFW_KEY_ESCAPE) { close(); return true; }
        return focus.keyPressed(k, scan, mods) || super.keyPressed(k, scan, mods);
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    /** Free-form container (children positioned by the screen). */
    private static final class Pane extends Container {
        void add(Component c) { addChild(c); }
        void clear() { children.clear(); }
        @Override public Size measure(float aw, float ah) { return new Size(aw, ah); }
    }
}
