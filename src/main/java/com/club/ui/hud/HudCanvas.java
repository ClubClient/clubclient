package com.club.ui.hud;

import com.club.config.ClubConfig;
import com.club.ui.Color;
import com.club.ui.UiContext;
import com.club.ui.UiRenderer;
import com.club.ui.component.Container;
import com.club.ui.layout.Size;
import com.club.ui.motion.Transition;
import com.club.ui.theme.Tokens;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Editor affordance easing (Stage 9.3, render-only — drag/snap logic is untouched). Outlines and guides
    // fade in/out instead of popping; last-position fields let a deselect/release play its fade-out.
    private final Map<HudElement, Transition> hoverAnim = new HashMap<>();
    private final Map<HudElement, Transition> alphaAnim = new HashMap<>();   // in-world appear/disappear fade
    private final Transition selAnim =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard());
    private final Transition guideXAnim =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
    private final Transition guideYAnim =
            new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().decelerate());
    private HudElement lastSelected;
    private int lastGuideX = HudSnap.NO_GUIDE, lastGuideY = HudSnap.NO_GUIDE;

    public HudCanvas(boolean editor) { this.editor = editor; }

    public HudCanvas add(HudElement e) { if (editor) e.setForceSample(true); elements.add(e); addChild(e); return this; }
    public void setScreen(int w, int h) { this.screenW = w; this.screenH = h; }
    public void setGridSnap(boolean on) { this.gridSnap = on; }
    public boolean gridSnap() { return gridSnap; }
    public void saver(Runnable r) { this.saver = r; }
    public HudElement selected() { return selected; }
    public void clearSelection() { if (selected != null) { selected = null; onSelectionChanged.run(); } }
    public int guideX() { return guideX; }
    public int guideY() { return guideY; }

    public void layoutFromConfig(MinecraftClient mc) { for (HudElement e : elements) e.layoutFromConfig(mc); }

    /** Top-most element under (mx,my) with the same 4px grace the click hit-test uses (package-private:
     *  the editor resolves its arrow-nudge target from the hover position, Stage 36). */
    HudElement elementAt(double mx, double my) {
        for (int i = elements.size() - 1; i >= 0; i--) {              // top-most first
            HudElement e = elements.get(i);
            if (mx >= e.xLeft() - 4 && mx <= e.xLeft() + e.width() + 4
             && my >= e.yTop()  - 4 && my <= e.yTop()  + e.height() + 4) return e;
        }
        return null;
    }

    // Keyboard-nudge guides (Stage 36): a nudge has no "release" to clear the lines, so they hold
    // for a beat and fade out on their own; any drag takes over the guide state as before.
    private static final float GUIDE_HOLD = 0.6f;
    private float guideHold;

    /**
     * Keyboard nudge (Stage 36): move {@code e} by (dx,dy) GUI px with the drag's edge/centre
     * magnetism — a snapped axis shows its guide line for {@link #GUIDE_HOLD}. Only a MOVED axis
     * snaps, and only when the magnet pulls IN the nudge direction: stepping off a line the element
     * sits on escapes it (1px out), and a magnet behind the movement never yanks it backwards.
     * Mirrors the drag path (cfgX/cfgY + layout); returns the APPLIED delta {dx,dy} ({0,0} = clamped
     * into place, nothing moved). Grid snap is not applied — Shift already steps by the grid.
     */
    int[] keyNudge(HudElement e, int dx, int dy, float now) {
        int w = (int) e.width(), h = (int) e.height();
        int ox = (int) e.xLeft(), oy = (int) e.yTop();
        int nx = ox + dx, ny = oy + dy;
        int gx = HudSnap.NO_GUIDE, gy = HudSnap.NO_GUIDE;
        if (dx != 0) {
            HudSnap.Snap s = HudSnap.snapAxis(nx, w, screenW);
            if (s.guide() != HudSnap.NO_GUIDE && (s.pos() - ox) * dx > 0) { nx = s.pos(); gx = s.guide(); }
        }
        if (dy != 0) {
            HudSnap.Snap s = HudSnap.snapAxis(ny, h, screenH);
            if (s.guide() != HudSnap.NO_GUIDE && (s.pos() - oy) * dy > 0) { ny = s.pos(); gy = s.guide(); }
        }
        nx = HudSnap.clampAxis(nx, w, screenW);
        ny = HudSnap.clampAxis(ny, h, screenH);
        guideX = gx; guideY = gy;
        guideHold = (gx != HudSnap.NO_GUIDE || gy != HudSnap.NO_GUIDE) ? now + GUIDE_HOLD : 0f;
        if (nx == ox && ny == oy) return new int[]{0, 0};
        e.cfgX(nx); e.cfgY(ny);
        e.layout(nx, ny, w, h);
        return new int[]{nx - ox, ny - oy};
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (!editor) return false;
        if (button == 1) {                        // RIGHT button → open/close the element's settings
            HudElement hit = elementAt(mx, my);
            if (hit != null) { selected = (selected == hit) ? null : hit; onSelectionChanged.run(); return true; }
            if (selected != null) { selected = null; onSelectionChanged.run(); return true; }   // RMB on empty closes the popover
            return false;
        }
        if (button != 0) return false;
        moved = false;                            // LEFT button → move only (drag); a tap does nothing
        pressed = elementAt(mx, my);
        if (pressed != null) { grabX = (int) mx - (int) pressed.xLeft(); grabY = (int) my - (int) pressed.yTop(); return true; }
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!editor || pressed == null) return false;
        moved = true;
        int w = (int) pressed.width(), h = (int) pressed.height();
        int nx = (int) mx - grabX, ny = (int) my - grabY;

        // Edge/center magnetism first (always shows guide lines); grid is the fallback lattice when grid-snap is on.
        HudSnap.Snap sx = HudSnap.snapAxis(nx, w, screenW);
        HudSnap.Snap sy = HudSnap.snapAxis(ny, h, screenH);
        guideX = sx.guide(); guideY = sy.guide();
        nx = sx.guide() != HudSnap.NO_GUIDE ? sx.pos() : (gridSnap ? HudSnap.snapToGrid(nx, GRID_STEP) : nx);
        ny = sy.guide() != HudSnap.NO_GUIDE ? sy.pos() : (gridSnap ? HudSnap.snapToGrid(ny, GRID_STEP) : ny);
        nx = HudSnap.clampAxis(nx, w, screenW);
        ny = HudSnap.clampAxis(ny, h, screenH);
        pressed.cfgX(nx); pressed.cfgY(ny);
        pressed.layout(nx, ny, w, h);            // keep bounds in sync for continued hit-test
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (!editor || button != 0) return false;
        guideX = guideY = HudSnap.NO_GUIDE;
        guideHold = 0f;
        boolean handled = pressed != null;
        if (pressed != null && moved) saver.run();   // commit a left-drag move; a tap (no move) does nothing
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
        MinecraftClient mc = MinecraftClient.getInstance();
        float now = ctx.time();
        // nudge guides expire after their hold (a drag owns the guide state while pressed)
        if (editor && guideHold > 0f && now >= guideHold && pressed == null) {
            guideX = guideY = HudSnap.NO_GUIDE;
            guideHold = 0f;
        }
        for (HudElement e : elements) {
            if (editor) { e.renderPlaceholder(ctx); continue; }   // editor = clean layout map: area + name, no content
            // in-world: fade the element in when it gains content, out when it loses it (panel + text)
            boolean show = e.cfgEnabled() && e.hasContent(mc);
            Transition a = alphaAnim.computeIfAbsent(e, k -> new Transition(show ? 1f : 0f,
                    Tokens.motion().durations().normal(), Tokens.motion().easings().decelerate()));
            a.target(show ? 1f : 0f, now);
            float av = a.value(now);
            if (av <= 0.001f) continue;   // fully hidden (or never shown) → skip render
            e.alpha = av;
            // keep the element fully on-screen even if its live content is wider than at edit time (fixes values
            // spilling off the edge when the panel was positioned under a narrower editor sample)
            int cx = HudSnap.clampAxis((int) e.xLeft(), (int) e.width(), screenW);
            int cy = HudSnap.clampAxis((int) e.yTop(),  (int) e.height(), screenH);
            if (cx != (int) e.xLeft() || cy != (int) e.yTop()) e.layout(cx, cy, e.width(), e.height());
            e.render(ctx);
        }
        if (!editor) return;

        // hover affordance: a faint padded outline (clamped to the screen) that fades in/out — reads as clickable
        for (HudElement e : elements) {
            boolean hov = e != selected && e.isHovered();
            Transition ha = hoverAnim.computeIfAbsent(e,
                    k -> new Transition(0f, Tokens.motion().durations().fast(), Tokens.motion().easings().standard()));
            ha.target(hov ? 1f : 0f, now);
            float a = ha.value(now);
            if (a > 0.001f) outline(r, e, 4f, 1f, Color.scaleAlpha(Tokens.border().strong(), a));
        }
        // selection border (accent) — fades in on select, out on deselect (drawn around the last selection while fading)
        if (selected != null) lastSelected = selected;
        selAnim.target(selected != null ? 1f : 0f, now);
        float sa = selAnim.value(now);
        if (lastSelected != null && sa > 0.001f)
            outline(r, lastSelected, 4f, 1.5f, Color.scaleAlpha(Tokens.accent().accent(), sa));

        // alignment guides (1px accent, ~0xAA alpha) — fade in while snapping, fade out on release
        if (guideX != HudSnap.NO_GUIDE) lastGuideX = guideX;
        if (guideY != HudSnap.NO_GUIDE) lastGuideY = guideY;
        guideXAnim.target(guideX != HudSnap.NO_GUIDE ? 1f : 0f, now);
        guideYAnim.target(guideY != HudSnap.NO_GUIDE ? 1f : 0f, now);
        int g = Color.withAlpha(Tokens.accent().accent(), 0xAA);
        float gxa = guideXAnim.value(now), gya = guideYAnim.value(now);
        if (gxa > 0.001f && lastGuideX != HudSnap.NO_GUIDE) r.rect(lastGuideX, 0, 1, screenH, Color.scaleAlpha(g, gxa));
        if (gya > 0.001f && lastGuideY != HudSnap.NO_GUIDE) r.rect(0, lastGuideY, screenW, 1, Color.scaleAlpha(g, gya));
    }

    /** A padded outline around an element. Padding shows in the interior, but on any side whose element edge
     *  sits within the snap margin of the screen, the outline hugs the element edge instead — so a snapped
     *  element's outline lands exactly on the magnet line and never spills past it, while edge/grid elements
     *  at x/y 0..MARGIN keep their content (text) inside the frame. Always within the screen. */
    private void outline(UiRenderer r, HudElement e, float pad, float thick, int color) {
        int m = HudSnap.MARGIN;
        float ex = e.xLeft(), ey = e.yTop(), ew = e.width(), eh = e.height();
        float l = ex <= m ? ex : ex - pad;
        float t = ey <= m ? ey : ey - pad;
        float rt = ex + ew >= screenW - m ? ex + ew : ex + ew + pad;
        float b = ey + eh >= screenH - m ? ey + eh : ey + eh + pad;
        l = Math.max(0, l); t = Math.max(0, t);
        rt = Math.min(screenW, rt); b = Math.min(screenH, b);
        r.border(l, t, rt - l, b - t, Tokens.radius().sm(), thick, color);
    }
}
