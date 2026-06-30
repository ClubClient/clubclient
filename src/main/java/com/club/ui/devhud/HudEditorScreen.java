package com.club.ui.devhud;

import com.club.config.ClubConfig;
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
 * [DEV HUD — TEMPORARY] Functional V2 HUD editor: drag elements (edge/center snap + optional grid),
 * scale/toggle/configure via a selection popover, persist to ClubConfig. Legacy HUD is untouched.
 */
public final class HudEditorScreen extends Screen {
    private final UiContextImpl uiCtx = new UiContextImpl();
    private final FocusManager focus = new FocusManager();
    private final long start = System.nanoTime();

    private final HudCanvas canvas = new HudCanvas(true)
            .add(new EffectsElement()).add(new TargetElement()).add(new InfoElement());
    private final Pane toolbar = new Pane();
    private final Pane popover = new Pane();
    private int popX, popY, popW, popH;
    private boolean hasPopover;
    private TextStyle stTitle, stHint, stPop, stToolLabel;
    private int pressOwner;   // which surface owns the active gesture: 0 none, 1 toolbar, 2 popover, 3 canvas

    public HudEditorScreen() { super(Text.literal("HUD Editor")); }
    private ClubConfig.Hud h() { return ClubConfig.get().hud; }

    @Override protected void init() {
        canvas.onSelectionChanged = this::rebuildPopover;
        buildToolbar();
        rebuildPopover();
    }

    private void buildToolbar() {
        toolbar.clear();
        Toggle grid = new Toggle(canvas.gridSnap()).onChange(canvas::setGridSnap);
        grid.layout(226, 9, 40, 22);   // sits right of the "Grid snap" label drawn at x=160
        Button reset = new Button("Reset").variant(Button.Variant.GHOST).onClick(this::resetPositions);
        reset.layout(width - 220, 7, 96, 26);
        Button done = new Button("Done").variant(Button.Variant.PRIMARY).onClick(this::close);
        done.layout(width - 116, 7, 96, 26);
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
        }
        popH = POP_HEAD + popover.children().size() * POP_ROW + POP_PAD_B;
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
        int bx = (int) sel.xLeft(), by = (int) sel.yTop(), bw = (int) sel.width();
        int px = bx + bw + 12; if (px + popW > width - 8) px = bx - popW - 12;   // flip to the left when no room on the right
        popX = Math.max(8, Math.min(px, width - popW - 8));
        popY = Math.max(8, Math.min(by, height - popH - 8));
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
        if (e instanceof EffectsElement) h().potions = v; else if (e instanceof TargetElement) h().target = v; else h().info = v;
    }
    private void setScale(HudElement e, float v) {
        if (e instanceof EffectsElement) h().potionScale = v; else if (e instanceof TargetElement) h().targetScale = v; else h().infoScale = v;
    }
    private void save() { ClubConfig.save(); }

    private void resetPositions() {
        ClubConfig.Hud c = h();
        c.potionX = 8; c.potionY = 70; c.targetX = -1; c.targetY = -1; c.infoX = 8; c.infoY = 120;
        save(); canvas.clearSelection(); rebuildPopover();
    }

    @Override public void render(DrawContext dc, int mx, int my, float d) {
        Ui.beginFrame(dc);
        var r = Ui.renderer(); Typography ty = Tokens.type();
        if (stTitle == null) initStyles();
        r.rect(0, 0, width, height, 0xFF0A0E15);
        uiCtx.setTime((System.nanoTime() - start) / 1_000_000_000f);

        canvas.setScreen(width, height);
        canvas.layoutFromConfig(MinecraftClient.getInstance());
        Decals.watermark(uiCtx); Decals.crosshair(uiCtx, width, height);

        canvas.mouseMoved(mx, my);
        canvas.render(uiCtx);

        // toolbar bar
        float tbH = 40;
        r.rect(0, 0, width, tbH, Tokens.surface().bg2());
        r.rect(0, tbH, width, 1, Tokens.border().defaultColor());
        uiCtx.text().draw("HUD Editor", 18, (tbH - ty.title().lineHeight()) / 2f, stTitle);
        uiCtx.text().draw("Grid snap", 160, (tbH - ty.label().lineHeight()) / 2f, stToolLabel);
        toolbar.mouseMoved(mx, my); toolbar.render(uiCtx);

        // hint
        uiCtx.text().draw("Drag any element. Click it to edit. Toggle grid-snap in the toolbar.",
                width / 2f, tbH + 8, stHint);

        // popover — compact, and re-anchored each frame so it follows the selected element
        positionPopover();
        if (hasPopover) {
            r.roundedRect(popX, popY, popW, popH, Tokens.radius().md(), Tokens.surface().bg2());
            r.border(popX, popY, popW, popH, Tokens.radius().md(), 1, Tokens.border().defaultColor());
            r.roundedRect(popX + 12, popY + 11, 6, 6, 2, Tokens.accent().accent());
            uiCtx.text().draw(titleOf(canvas.selected()), popX + 24, popY + 7, stPop);
            for (Label l : popLabels) l.render(uiCtx);
            popover.mouseMoved(mx, my); popover.render(uiCtx);
        }
    }

    private String titleOf(HudElement e) {
        return e instanceof EffectsElement ? "Effects HUD" : e instanceof TargetElement ? "Target HUD" : "Coordinates HUD";
    }
    private void initStyles() {
        Typography t = Tokens.type();
        stTitle = TextStyle.of(t.title().weight(), t.title().size(), Tokens.palette().textHi());
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
    @Override public boolean shouldPause() { return false; }

    /** Free-form container (children positioned by the screen). */
    private static final class Pane extends Container {
        void add(Component c) { addChild(c); }
        void clear() { children.clear(); }
        @Override public Size measure(float aw, float ah) { return new Size(aw, ah); }
    }
}
