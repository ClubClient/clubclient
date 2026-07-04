package com.club.ui.hud;

import com.club.ui.UiContext;
import com.club.ui.component.Component;
import com.club.ui.layout.Size;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
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

    // Element ground hooks. Since the V4 "Chips" language (Stage 13+) no element draws a shared
    // backdrop here — capsules/grounds are painted inside paint() per role — so the default is bare.
    /** Horizontal inner padding (unscaled). Override per role. */
    protected float panelPadX() { return 6f; }
    /** Vertical inner padding (unscaled). Override per role. */
    protected float panelPadY() { return 6f; }
    /** Panel corner radius (unscaled). Override per role. */
    protected float panelRadius() { return 6f; }
    /** Draw the element's backdrop (already scaled/positioned). Default: none (V4 — grounds live in paint()). */
    protected void drawPanel(UiContext ctx, float x, float y, float w, float h, float radius, float a) { }
    /** Element appear/disappear fade in [0,1] (Stage 10.3); 1 = fully shown. Set by the canvas in-world. */
    protected float alpha = 1f;
    /** Editor forces representative sample data so every element always has an area (e.g. Armor with no armor
     *  worn still shows its placeholder). Set by the editor canvas; never set in-world. */
    private boolean forceSample;
    public void setForceSample(boolean v) { this.forceSample = v; }

    /** Short human name shown as the editor placeholder label (e.g. "Target", "Armor"). */
    public abstract String displayName();

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
        int padX = Math.round(panelPadX() * cfgScale());
        int padY = Math.round(panelPadY() * cfgScale());
        b[2] += 2 * padX; b[3] += 2 * padY;
        return b;
    }

    /** Assign Component bounds from the current config (called by the canvas each frame). */
    public void layoutFromConfig(MinecraftClient mc) {
        int[] b = box(mc);
        super.layout(b[0], b[1], b[2], b[3]);
    }

    /** Live data is used when a player exists AND we're not forcing sample (editor); otherwise sample data. */
    protected boolean live(MinecraftClient mc) { return !forceSample && mc != null && mc.player != null; }

    /** Editor placeholder: the element's area + name — no sample content, so the editor reads as a
     *  clean layout map and every element is always visible/positionable. Stage 25 (owner board,
     *  variant A): the ground is the exact {@link HudPaint#chip} capsule — borderless, translucent,
     *  chip radius — so the editor reads as "the same capsules, just empty", not older boxed chrome. */
    public void renderPlaceholder(UiContext ctx) {
        HudPaint.chip(ctx, x, y, w, h, HudPaint.CHIP_RAD * cfgScale(), 1f);
        Typography.Role role = Tokens.type().body();
        float ty = y + (h - role.lineHeight()) * 0.5f;
        ctx.text().draw(displayName(), x + w * 0.5f, ty,
                TextStyle.of(role.weight(), role.size(), Tokens.palette().textMuted()).align(Align.CENTER));
    }

    /** In-world (non-editor) visibility: an element with no real data hides instead of falling back to its
     *  editor sample. The editor always shows all elements (sample) so they stay positionable. Default: shown. */
    public boolean hasContent(MinecraftClient mc) { return true; }

    /** Transient visual scale around the box centre (e.g. a pop on target change). 1 = none. Render-only:
     *  does not affect the layout/hit box, so the editor drag target stays full-size while a pop plays. */
    protected float visualScale(UiContext ctx) { return 1f; }

    @Override public Size measure(float availW, float availH) { return new Size(w, h); }
    @Override public void render(UiContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float s = cfgScale(), vs = visualScale(ctx);
        float bx = x, by = y, bw = w, bh = h;
        if (vs != 1f) {                                         // scale the whole element about its centre
            float cx = x + w * 0.5f, cy = y + h * 0.5f;
            bw = w * vs; bh = h * vs; bx = cx - bw * 0.5f; by = cy - bh * 0.5f;
        }
        float px = panelPadX() * s * vs, py = panelPadY() * s * vs;
        drawPanel(ctx, bx, by, bw, bh, panelRadius() * s * vs, alpha);   // backdrop (shared, or a role override)
        paint(ctx, mc, bx + px, by + py, s * vs, live(mc));             // content inset by the panel padding
    }
}
