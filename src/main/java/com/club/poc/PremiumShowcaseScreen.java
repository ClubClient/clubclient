package com.club.poc;

import com.club.poc.render.PocRenderer;
import com.club.poc.render.PocText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * PREMIUM SHOWCASE — the quality ceiling of the new stack, with NO ties to the
 * current Club design. New stack only (SDF shapes / glow / gradients + MSDF text),
 * drawn over the live game panorama at real scale, aiming at the level of modern
 * game overlays / Pulse-class visuals / premium HUD systems. Uses the menu
 * panorama backdrop directly (no title chrome). PoC-only (CLUB_POC=1).
 */
public class PremiumShowcaseScreen extends Screen {

    public PremiumShowcaseScreen() { super(Text.literal("Premium Showcase")); }

    // free palette (not the Club tokens)
    private static final int INK       = 0xF20E1422;  // panel base (slightly translucent → overlay depth)
    private static final int INK2      = 0xF2161E33;
    private static final int HAIR      = 0x26FFFFFF;
    private static final int HAIR_HI   = 0x40FFFFFF;
    private static final int TEXT      = 0xFFF2F5FB;
    private static final int MUTED     = 0xFF9AA6BC;
    private static final int A1        = 0xFF6E8BFF;   // accent A (indigo)
    private static final int A2        = 0xFF57E0FF;   // accent B (cyan)
    private static final int MAG       = 0xFFB37CFF;   // secondary highlight (violet)
    private static final int GOOD      = 0xFF49E08B;

    private float zoom = 1f, zoomCx, zoomCy;
    public void setZoom(float z, float cx, float cy) { this.zoom = z; this.zoomCx = cx; this.zoomCy = cy; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderPanoramaBackground(ctx, delta);       // rotating menu panorama, no title chrome
        renderDarkening(ctx);                        // standard menu darkening
        ctx.fill(0, 0, width, height, 0x99050810);   // extra cinematic dim

        PocText.drawAt(ctx, "semibold", 17, "PREMIUM SHOWCASE", 28, 20, TEXT);
        PocText.drawAt(ctx, "medium", 12, "new render stack — quality ceiling, no current-design constraints", 28, 42, MUTED);

        boolean zoomed = zoom != 1f;
        if (zoomed) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(zoomCx, zoomCy, 0);
            ctx.getMatrices().scale(zoom, zoom, 1f);
            ctx.getMatrices().translate(-zoomCx, -zoomCy, 0);
        }

        int cx = width / 2, cy = height / 2;
        targetCard(ctx, cx - 250, cy - 96);
        statusPills(ctx, cx - 250, cy + 44);
        moduleList(ctx, cx + 250 - 196, cy - 110);
        settingsCard(ctx, cx - 60, cy + 16);

        if (zoomed) ctx.getMatrices().pop();
    }

    // -------- a hero Target HUD card ----------------------------------------
    private void targetCard(DrawContext ctx, int x, int y) {
        int w = 230, h = 96, r = 14;
        panel(ctx, x, y, w, h, r);
        // avatar placeholder — gradient rounded square + soft glow
        int av = 58, ax = x + 16, ay = y + 19;
        PocRenderer.glow(ctx, ax, ay, av, av, 12, 14f, withA(A1, 0x66));
        PocRenderer.gradientRoundedRect(ctx, ax, ay, av, av, 12, A1, MAG, true);
        PocRenderer.roundedBorder(ctx, ax, ay, av, av, 12, 1f, HAIR_HI);
        // name + tag
        int tx = ax + av + 16;
        PocText.drawAt(ctx, "semibold", 18, "Notch", tx, y + 18, TEXT);
        PocText.drawAt(ctx, "medium", 12, "distance 3.4m  ·  42ms", tx, y + 40, MUTED);
        // smooth health bar with glow
        int bw = w - (tx - x) - 18, by = y + 60, bh = 8;
        PocRenderer.roundedRect(ctx, tx, by, bw, bh, 4, 0xFF202B40);
        int fw = Math.round(bw * 0.78f);
        PocRenderer.glow(ctx, tx, by, fw, bh, 4, 7f, withA(GOOD, 0x66));
        PocRenderer.gradientRoundedRect(ctx, tx, by, fw, bh, 4, GOOD, A2, false);
        PocText.drawAt(ctx, "medium", 12, "15.6 / 20", tx, y + 72, MUTED);
    }

    // -------- status pills --------------------------------------------------
    private void statusPills(DrawContext ctx, int x, int y) {
        pill(ctx, x, y, "FPS", "240", A2);
        pill(ctx, x + 92, y, "BPS", "12.4", A1);
        pill(ctx, x + 184, y, "PING", "42", MAG);
    }
    private void pill(DrawContext ctx, int x, int y, String k, String v, int accent) {
        int w = 84, h = 30, r = 8;
        PocRenderer.glow(ctx, x, y, w, h, r, 9f, withA(accent, 0x33));
        PocRenderer.gradientRoundedRect(ctx, x, y, w, h, r, INK2, INK, true);
        PocRenderer.roundedBorder(ctx, x, y, w, h, r, 1f, HAIR);
        PocRenderer.roundedRect(ctx, x + 10, y + h / 2f - 4, 4, 8, 2f, accent);
        PocText.drawAt(ctx, "medium", 11, k, x + 20, y + 6, MUTED);
        PocText.drawAt(ctx, "semibold", 14, v, x + 20, y + 14, TEXT);
    }

    // -------- module / array list -------------------------------------------
    private void moduleList(DrawContext ctx, int x, int y) {
        String[] mods = {"KillAura", "Velocity", "Sprint", "ESP", "Nofall"};
        boolean[] active = {true, true, false, true, false};
        int w = 196, rh = 28;
        for (int i = 0; i < mods.length; i++) {
            int ry = y + i * (rh + 6);
            if (active[i]) PocRenderer.glow(ctx, x, ry, w, rh, 8, 8f, withA(A1, 0x33));
            PocRenderer.gradientRoundedRect(ctx, x, ry, w, rh, 8, INK2, INK, true);
            PocRenderer.roundedBorder(ctx, x, ry, w, rh, 8, 1f, active[i] ? withA(A1, 0x80) : HAIR);
            // accent edge (gradient) for active
            if (active[i]) PocRenderer.gradientRoundedRect(ctx, x, ry + 5, 3, rh - 10, 1.5f, A1, A2, true);
            PocText.drawAt(ctx, "medium", 13, mods[i], x + 14, ry + 7, active[i] ? TEXT : MUTED);
            String tag = active[i] ? "on" : "off";
            PocText.drawAt(ctx, "medium", 11, tag, x + w - PocText.widthAt("medium", 11, tag) - 12, ry + 8, active[i] ? A2 : 0xFF5A6273);
        }
    }

    // -------- settings card (toggle + slider + segmented) -------------------
    private void settingsCard(DrawContext ctx, int x, int y) {
        int w = 300, h = 150, r = 16;
        panel(ctx, x, y, w, h, r);
        PocText.drawAt(ctx, "semibold", 16, "Combat", x + 18, y + 16, TEXT);
        PocText.drawAt(ctx, "regular", 12, "Tune your assist behaviour.", x + 18, y + 38, MUTED);
        hair(ctx, x + 18, y + 58, w - 36);

        // toggle ON
        PocText.drawAt(ctx, "medium", 13, "Enabled", x + 18, y + 72, TEXT);
        int tgW = 40, tgH = 20, tgX = x + w - 18 - tgW, tgY = y + 70;
        PocRenderer.glow(ctx, tgX, tgY, tgW, tgH, 10, 11f, withA(A1, 0x99));
        PocRenderer.gradientRoundedRect(ctx, tgX, tgY, tgW, tgH, 10, A1, A2, false);
        PocRenderer.roundedRect(ctx, tgX + tgW - tgH + 2, tgY + 2, tgH - 4, tgH - 4, (tgH - 4) / 2f, 0xFFFFFFFF);

        // slider
        PocText.drawAt(ctx, "medium", 13, "Range", x + 18, y + 104, TEXT);
        int slX = x + 90, slW = w - 90 - 18 - 34, slY = y + 112;
        PocRenderer.roundedRect(ctx, slX, slY, slW, 4, 2, 0xFF202B40);
        int fw = Math.round(slW * 0.66f);
        PocRenderer.glow(ctx, slX, slY, fw, 4, 2, 8f, withA(A2, 0x88));
        PocRenderer.gradientRoundedRect(ctx, slX, slY, fw, 4, 2, A1, A2, false);
        PocRenderer.glow(ctx, slX + fw - 6, slY - 4, 12, 12, 6, 8f, withA(A2, 0x88));
        PocRenderer.roundedRect(ctx, slX + fw - 6, slY - 4, 12, 12, 6f, 0xFFFFFFFF);
        PocText.drawAt(ctx, "medium", 12, "3.6", x + w - 18 - 24, y + 106, TEXT);

        // segmented
        int segX = x + 18, segY = y + 124, segW = w - 36, segH = 0; // labels only row baseline
        String[] seg = {"Legit", "Rage", "Custom"};
        int sw = (w - 36) / 3;
        int sy = y + 124;
        for (int i = 0; i < 3; i++) {
            int sx = x + 18 + i * sw;
            boolean on = i == 0;
            if (on) {
                PocRenderer.glow(ctx, sx, sy, sw - 4, 18, 6, 7f, withA(A1, 0x55));
                PocRenderer.gradientRoundedRect(ctx, sx, sy, sw - 4, 18, 6, A1, A2, false);
            } else {
                PocRenderer.roundedRect(ctx, sx, sy, sw - 4, 18, 6, 0x14FFFFFF);
            }
            int tw = (int) PocText.widthAt("medium", 11, seg[i]);
            PocText.drawAt(ctx, "medium", 11, seg[i], sx + (sw - 4 - tw) / 2, sy + 4, on ? 0xFF0E1422 : MUTED);
        }
    }

    // -------- shared premium primitives -------------------------------------
    private void panel(DrawContext ctx, int x, int y, int w, int h, int r) {
        PocRenderer.glow(ctx, x, y + 3, w, h, r, 16f, 0x73000000);            // soft drop shadow
        PocRenderer.gradientRoundedRect(ctx, x, y, w, h, r, INK2, INK, true);  // vertical depth
        PocRenderer.roundedBorder(ctx, x, y, w, h, r, 1f, HAIR);               // crisp hairline
        PocRenderer.gradientRoundedRect(ctx, x + r, y + 1, w - 2 * r, 1, 0, HAIR_HI, HAIR, false); // top sheen
    }
    private void hair(DrawContext ctx, int x, int y, int w) { PocRenderer.roundedRect(ctx, x, y, w, 1, 0, HAIR); }

    private static int withA(int rgb, int a) { return (rgb & 0x00FFFFFF) | (a << 24); }
}
