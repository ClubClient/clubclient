package com.club.ui.hud;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import net.minecraft.client.MinecraftClient;

/**
 * One positionable HUD element on the V2 stack. Bounds are the element's scaled box at its configured
 * top-left; the canvas lays elements out from config every frame so external edits / resizes reflect.
 * Scale is baked into geometry (the V2 renderer has no matrix). Concrete elements supply config binding,
 * content size, and a flat-language paint. Mirrors the legacy hud/* size()+scaledBox() contract.
 */
public abstract class HudElement extends Component {
    public final String id;
    protected HudElement(String id) { this.id = id; }

    // Shared "light structure" geometry (Stage 10): every element sits on the same padded flat ground with the
    // same corner, and stacked rows share one row height — so the four elements read as one product, not a set
    // of boxes. Tight padding + a soft corner so the panels hug their content and don't read as bulky.
    private static final float PANEL_PAD = 6f, PANEL_RADIUS = 6f;
    /** One row height for every stacked HUD list (Effects, Armor) — a single vertical grid across the HUD. */
    public static final int LIST_ROW = 18;
    /** Element appear/disappear fade in [0,1] (Stage 10.3); 1 = fully shown. Set by the canvas in-world. */
    protected float alpha = 1f;

    // --- config binding (each concrete element wires these to ClubConfig.Hud) ---
    public abstract int   cfgX();
    public abstract int   cfgY();
    public abstract void  cfgX(int v);
    public abstract void  cfgY(int v);
    public abstract float cfgScale();
    public abstract boolean cfgEnabled();

    // --- auto-position (default: none; e.g. TargetElement centers on the crosshair) ---
    public int autoX(MinecraftClient mc) { return -1; }
    public int autoY(MinecraftClient mc) { return -1; }

    // --- content + paint (concrete elements implement) ---
    /** Unscaled {w,h} of the content for the given data mode (live vs representative sample). */
    public abstract int[] contentSize(MinecraftClient mc, boolean live);
    /** Draw flat-language content with its top-left at (ox,oy), every dimension multiplied by scale. */
    public abstract void paint(UiContext ctx, MinecraftClient mc, float ox, float oy, float scale, boolean live);

    // --- geometry (pure; unit-tested) ---
    /** {x, y, max(8,round(cw*scale)), max(8,round(ch*scale))} — ports legacy HudEditorScreen.scaledBox. */
    public static int[] scaledBox(int x, int y, int cw, int ch, float scale) {
        return new int[]{ x, y, Math.max(8, Math.round(cw * scale)), Math.max(8, Math.round(ch * scale)) };
    }

    public int resolveX(MinecraftClient mc) { int x = cfgX(); if (x >= 0) return x; int a = autoX(mc); return a >= 0 ? a : 0; }
    public int resolveY(MinecraftClient mc) { int y = cfgY(); if (y >= 0) return y; int a = autoY(mc); return a >= 0 ? a : 0; }

    /** Scaled box at the resolved position, sized from the data mode that will actually be shown.
     *  Includes the shared panel padding on every side so the drag/selection box frames the panel. */
    public int[] box(MinecraftClient mc) {
        boolean live = live(mc);
        int[] cs = contentSize(mc, live);
        int[] b = scaledBox(resolveX(mc), resolveY(mc), cs[0], cs[1], cfgScale());
        int pad = Math.round(PANEL_PAD * cfgScale());
        b[2] += 2 * pad; b[3] += 2 * pad;
        return b;
    }

    /** Assign Component bounds from the current config (called by the canvas each frame). */
    public void layoutFromConfig(MinecraftClient mc) {
        int[] b = box(mc);
        super.layout(b[0], b[1], b[2], b[3]);
    }

    /** Live data is used when a player exists; otherwise representative sample data (editor on title screen). */
    protected boolean live(MinecraftClient mc) { return mc != null && mc.player != null; }

    /** In-world (non-editor) visibility: an element with no real data hides instead of falling back to its
     *  editor sample. The editor always shows all elements (sample) so they stay positionable. Default: shown. */
    public boolean hasContent(MinecraftClient mc) { return true; }

    @Override public Size measure(float availW, float availH) { return new Size(w, h); }
    @Override public void render(UiContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float s = cfgScale(), pad = PANEL_PAD * s;
        HudPaint.panel(ctx, x, y, w, h, PANEL_RADIUS * s, alpha);   // shared "light structure" backdrop
        paint(ctx, mc, x + pad, y + pad, s, live(mc));               // content inset by the panel padding
    }
}
