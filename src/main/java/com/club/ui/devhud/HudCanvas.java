package com.club.ui.devhud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.component.Container;
import com.club.ui.layout.Size;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Hosts HUD elements and, in editor mode, owns selection + grab-offset drag + snap + guides + persistence
 * (centralized exactly like the legacy gui/HudEditorScreen, but on the V2 stack). Screen dims are injected
 * via setScreen() so the drag/snap math is Minecraft-free and unit-tested.
 */
public final class HudCanvas extends Container {
    public static final int GRID_STEP = 8;

    private final boolean editor;
    private final List<HudElement> elements = new ArrayList<>();
    private int screenW, screenH;
    private boolean gridSnap;
    private Runnable saver = ClubConfig::save;
    public Runnable onSelectionChanged = () -> {};

    private HudElement selected, pressed;
    private int grabX, grabY;
    private boolean moved;
    private int guideX = HudSnap.NO_GUIDE, guideY = HudSnap.NO_GUIDE;

    public HudCanvas(boolean editor) { this.editor = editor; }

    public HudCanvas add(HudElement e) { elements.add(e); addChild(e); return this; }
    public void setScreen(int w, int h) { this.screenW = w; this.screenH = h; }
    public void setGridSnap(boolean on) { this.gridSnap = on; }
    public boolean gridSnap() { return gridSnap; }
    public void saver(Runnable r) { this.saver = r; }
    public HudElement selected() { return selected; }
    public void clearSelection() { if (selected != null) { selected = null; onSelectionChanged.run(); } }
    public int guideX() { return guideX; }
    public int guideY() { return guideY; }
    public List<HudElement> elements() { return elements; }

    public void layoutFromConfig(MinecraftClient mc) { for (HudElement e : elements) e.layoutFromConfig(mc); }

    private HudElement elementAt(double mx, double my) {
        for (int i = elements.size() - 1; i >= 0; i--) {              // top-most first
            HudElement e = elements.get(i);
            if (mx >= e.xLeft() - 4 && mx <= e.xLeft() + e.width() + 4
             && my >= e.yTop()  - 4 && my <= e.yTop()  + e.height() + 4) return e;
        }
        return null;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (!editor || button != 0) return false;
        moved = false;
        pressed = elementAt(mx, my);
        if (pressed != null) { grabX = (int) mx - (int) pressed.xLeft(); grabY = (int) my - (int) pressed.yTop(); return true; }
        return selected != null;   // capture an empty click only to allow deselect-on-release (popover clicks are routed elsewhere)
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!editor || pressed == null) return false;
        moved = true;
        int w = (int) pressed.width(), h = (int) pressed.height();
        int nx = (int) mx - grabX, ny = (int) my - grabY;

        if (gridSnap) { nx = HudSnap.snapToGrid(nx, GRID_STEP); ny = HudSnap.snapToGrid(ny, GRID_STEP); guideX = guideY = HudSnap.NO_GUIDE; }
        else {
            HudSnap.Snap sx = HudSnap.snapAxis(nx, w, screenW); nx = sx.pos(); guideX = sx.guide();
            HudSnap.Snap sy = HudSnap.snapAxis(ny, h, screenH); ny = sy.pos(); guideY = sy.guide();
        }
        nx = HudSnap.clampAxis(nx, w, screenW);
        ny = HudSnap.clampAxis(ny, h, screenH);
        pressed.cfgX(nx); pressed.cfgY(ny);
        pressed.layout(nx, ny, w, h);            // keep bounds in sync for continued hit-test
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (!editor || button != 0) return false;
        guideX = guideY = HudSnap.NO_GUIDE;
        boolean handled = false;
        if (pressed != null) {
            if (moved) saver.run();
            else { selected = (selected == pressed) ? null : pressed; onSelectionChanged.run(); }
            handled = true;
        } else if (!moved && selected != null) { selected = null; onSelectionChanged.run(); handled = true; }
        pressed = null; moved = false;
        return handled;
    }

    @Override public Size measure(float aw, float ah) { return new Size(aw, ah); }

    @Override public void render(UiContext ctx) {
        var r = ctx.renderer();
        // faint "graph-paper" grid underlay while grid-snap is on (editor only)
        if (editor && gridSnap) {
            int gc = Color.withAlpha(Tokens.palette().textFaint(), 0x22);
            for (int gx = 0; gx <= screenW; gx += GRID_STEP) r.rect(gx, 0, 1, screenH, gc);
            for (int gy = 0; gy <= screenH; gy += GRID_STEP) r.rect(0, gy, screenW, 1, gc);
        }
        for (HudElement e : elements) {
            if (!editor && !e.cfgEnabled()) continue;        // preview/in-world hides disabled; editor shows all
            e.render(ctx);
        }
        if (!editor) return;

        // hover affordance: a faint outline on the hovered (non-selected) element so it reads as clickable
        for (HudElement e : elements) {
            if (e == selected || !e.isHovered()) continue;
            r.border(e.xLeft() - 4, e.yTop() - 4, e.width() + 8, e.height() + 8, Tokens.radius().sm(), 1f, Tokens.border().strong());
        }
        // selection border (accent) over the selected element's box
        if (selected != null) {
            r.border(selected.xLeft() - 4, selected.yTop() - 4, selected.width() + 8, selected.height() + 8, Tokens.radius().sm(), 1.5f, Tokens.accent().accent());
        }
        // alignment guides (1px accent, ~0xAA alpha) — ports legacy guideX/guideY
        int g = Color.withAlpha(Tokens.accent().accent(), 0xAA);
        if (guideX != HudSnap.NO_GUIDE) r.rect(guideX, 0, 1, screenH, g);
        if (guideY != HudSnap.NO_GUIDE) r.rect(0, guideY, screenW, 1, g);
    }
}
