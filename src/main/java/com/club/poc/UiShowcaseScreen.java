package com.club.poc;

import com.club.gui.Icons;
import com.club.poc.render.PocRenderer;
import com.club.poc.render.PocText;
import com.club.util.ClubFont;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * UI SHOWCASE — a technology-quality proof, NOT a client design. Every element is
 * rendered twice side by side: LEFT = legacy stack ({@code RenderHelper} +
 * {@code ClubFont} = DrawContext.fill + bitmap font), RIGHT = new stack
 * ({@code PocRenderer} + {@code PocText} = SDF shapes + MSDF text). Geometry is
 * identical per element; only the backend differs.
 *
 * Pages: typography · text scaling (x1/x2/x4) · primitives · controls · HUD.
 * {@link #setZoom} drives the 400–600% close-ups. PoC-only (CLUB_POC=1).
 */
public class UiShowcaseScreen extends Screen {

    private static final int BG       = 0xFF0B111A;
    private static final int LIGHT_BG = 0xFFE8ECF2;
    private static final int SURFACE  = 0xFF131B2A;
    private static final int SURFACE2 = 0xFF18212F;
    private static final int BORDER   = 0xFF1D2536;
    private static final int TEXT     = 0xFFF4F6FA;
    private static final int DARKTEXT = 0xFF0E1421;
    private static final int MUTED    = 0xFFA6ADBB;
    private static final int DESC     = 0xFF767E8E;
    private static final int ACCENT   = 0xFF7CABFF;
    private static final int ACCENT2  = 0xFF78D7FF;
    private static final int FILL_OFF = 0xFF222A38;
    private static final int TRACK    = 0xFF1D2536;
    private static final int GOOD = 0xFF2ECC71, WARN = 0xFFE3C66A, LOW = 0xFFE06B6B;

    private static final String[] PAGE_TITLES = {
        "TYPOGRAPHY", "TEXT SCALING  ·  x1 / x2 / x4", "PRIMITIVES", "CONTROLS", "HUD COMPONENTS",
        "ZOOM 400%  ·  LEGACY vs NEW"
    };
    public static final int PAGES = 6;

    private int page = 0;
    private float zoom = 1f, zoomCx, zoomCy;

    private final ItemStack[] armor = sampleArmor();
    private final float[] armorFrac = {0.92f, 0.87f, 0.79f, 0.54f};

    public UiShowcaseScreen() { super(Text.literal("UI Showcase")); }

    public void setPage(int p) { this.page = ((p % PAGES) + PAGES) % PAGES; this.zoom = 1f; }
    public void setZoom(float z, float cx, float cy) { this.zoom = z; this.zoomCx = cx; this.zoomCy = cy; }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, BG);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        int half = width / 2;

        // header (always at native scale, outside zoom)
        PocText.drawAt(ctx, "semibold", 18, PAGE_TITLES[page], 24, 16, TEXT);
        boolean pair = page == 5;
        if (!pair) {
            PocText.drawAt(ctx, "medium", 12, "LEGACY render", 24, 42, MUTED);
            PocText.drawAt(ctx, "medium", 12, "NEW render (MSDF / SDF)", half + 24, 42, ACCENT);
        }
        if (zoom != 1f) PocText.drawAt(ctx, "medium", 12, "zoom " + Math.round(zoom * 100) + "%", half - 70, 16, ACCENT2);

        boolean zoomed = zoom != 1f;
        if (zoomed) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(zoomCx, zoomCy, 0);
            ctx.getMatrices().scale(zoom, zoom, 1f);
            ctx.getMatrices().translate(-zoomCx, -zoomCy, 0);
        }
        if (!pair) ctx.fill(half, 60, half + 1, height, 0x1AFFFFFF);

        switch (page) {
            case 0 -> { renderTypography(ctx, 0, false);  renderTypography(ctx, half, true); }
            case 1 -> { renderScaling(ctx, 0, false);     renderScaling(ctx, half, true); }
            case 2 -> { renderPrimitives(ctx, 0, false);  renderPrimitives(ctx, half, true); }
            case 3 -> { renderControls(ctx, 0, false);    renderControls(ctx, half, true); }
            case 4 -> { renderHud(ctx, 0, false);         renderHud(ctx, half, true); }
            case 5 -> renderZoomPair(ctx);
        }

        if (zoomed) ctx.getMatrices().pop();
    }

    // ---------------------------------------------------------------- pages

    private void renderTypography(DrawContext ctx, int colX, boolean n) {
        int x = colX + 28;
        txt(ctx, n, "semibold", 30, "Heading 30", x, 74, TEXT);
        txt(ctx, n, "semibold", 22, "Heading 22", x, 116, TEXT);
        txt(ctx, n, "semibold", 16, "Heading 16", x, 146, TEXT);
        txt(ctx, n, "regular", 14, "Normal text — the quick brown fox 0123456789", x, 174, MUTED);

        txt(ctx, n, "medium", 11, "on dark", x, 200, DESC);
        txt(ctx, n, "regular", 14, "Legibility on a dark surface.", x, 214, TEXT);

        txt(ctx, n, "medium", 11, "on light", x, 246, DESC);
        int lw = 250, lh = 26;
        rr(ctx, n, x, 260, lw, lh, 6, LIGHT_BG);
        txt(ctx, n, "regular", 14, "Legibility on a light surface.", x + 10, 266, DARKTEXT);
    }

    private void renderScaling(DrawContext ctx, int colX, boolean n) {
        int x = colX + 28;
        String base = "Ag 0123 — crisp?";
        scaled(ctx, n, base, x, 78, 1);
        scaled(ctx, n, base, x, 116, 2);
        scaled(ctx, n, base, x, 176, 4);
    }

    private void scaled(DrawContext ctx, boolean n, String s, int x, int y, int factor) {
        txt(ctx, n, "medium", 11, "x" + factor, x, y - 12, ACCENT2);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(factor, factor, 1f);
        if (n) PocText.drawAt(ctx, "semibold", 13, s, 0, 0, TEXT);
        else ClubFont.drawName(ctx, s, 0, 0, TEXT, false); // SemiBold 13/15 bitmap base
        ctx.getMatrices().pop();
    }

    private void renderPrimitives(DrawContext ctx, int colX, boolean n) {
        int x = colX + 28;
        // Rounded Rect
        cap(ctx, n, "Rounded Rect", x, 72);
        rr(ctx, n, x, 86, 130, 34, 10, SURFACE2);
        // Border
        cap(ctx, n, "Border", x + 170, 72);
        rb(ctx, n, x + 170, 86, 130, 34, 10, 1f, ACCENT);
        // Glow
        cap(ctx, n, "Glow", x, 138);
        glow(ctx, n, x + 22, 158, 18, 18, 9, withA(ACCENT, 0xCC), 14f);
        rr(ctx, n, x + 22, 158, 18, 18, 9, ACCENT);
        // Soft Shadow (floating panel)
        cap(ctx, n, "Soft Shadow", x + 170, 138);
        softShadow(ctx, n, x + 190, 156, 90, 30, 8);
        rr(ctx, n, x + 190, 156, 90, 30, 8, SURFACE2);
        // Gradient
        cap(ctx, n, "Gradient", x, 210);
        grad(ctx, n, x, 224, 280, 18, 9, ACCENT, ACCENT2, false);
        // Gradient vertical (surface depth)
        cap(ctx, n, "Gradient (vert)", x, 256);
        grad(ctx, n, x, 270, 280, 30, 8, SURFACE2, SURFACE, true);
    }

    private void renderControls(DrawContext ctx, int colX, boolean n) {
        int x = colX + 28;
        // Toggle ON
        cap(ctx, n, "Toggle", x, 74);
        int tgW = 38, tgH = 18, tgY = 90;
        glow(ctx, n, x, tgY, tgW, tgH, 9, withA(ACCENT, 0x88), 10f);
        grad(ctx, n, x, tgY, tgW, tgH, 9, ACCENT, ACCENT2, false);
        rr(ctx, n, x + tgW - tgH + 2, tgY + 2, tgH - 4, tgH - 4, (tgH - 4) / 2f, 0xFFFFFFFF);

        // Slider
        cap(ctx, n, "Slider", x, 128);
        int slX = x, slW = 240, slY = 148;
        rr(ctx, n, slX, slY, slW, 4, 2, TRACK);
        int fw = Math.round(slW * 0.7f);
        glow(ctx, n, slX, slY, fw, 4, 2, withA(ACCENT, 0x77), 8f);
        rr(ctx, n, slX, slY, fw, 4, 2, ACCENT);
        rr(ctx, n, slX + fw - 5, slY - 4, 10, 12, 5f, 0xFFFFFFFF);
        txt(ctx, n, "medium", 12, "70%", slX + slW + 12, slY - 6, TEXT);

        // Dropdown (closed)
        cap(ctx, n, "Dropdown", x, 182);
        int ddX = x, ddY = 198, ddW = 200, ddH = 26;
        rr(ctx, n, ddX, ddY, ddW, ddH, 6, SURFACE);
        rb(ctx, n, ddX, ddY, ddW, ddH, 6, 1f, BORDER);
        txt(ctx, n, "medium", 13, "Balanced", ddX + 10, ddY + 6, TEXT);
        Icons.chevron(ctx, ddX + ddW - 16, ddY + ddH / 2 - 2, 7, MUTED, true);

        // Array List item
        cap(ctx, n, "Array List Item", x, 246);
        int alX = x, alY = 262, alW = 150, alH = 22;
        rr(ctx, n, alX, alY, alW, alH, 4, withA(0xFFFFFF, 0x0D));
        rr(ctx, n, alX, alY, 2, alH, 1f, ACCENT);     // accent edge bar
        txt(ctx, n, "medium", 13, "KillAura", alX + 10, alY + 5, TEXT);
        txt(ctx, n, "medium", 12, "[legit]", alX + alW - (int) txtW(n, "medium", 12, "[legit]") - 8, alY + 5, ACCENT);
    }

    private void renderHud(DrawContext ctx, int colX, boolean n) {
        int x = colX + 28;
        // HUD Card
        cap(ctx, n, "HUD Card", x, 72);
        int cw = 250, ch = 64;
        rr(ctx, n, x, 86, cw, ch, 10, SURFACE);
        rb(ctx, n, x, 86, cw, ch, 10, 1f, BORDER);
        txt(ctx, n, "semibold", 14, "Card title", x + 14, 98, TEXT);
        txt(ctx, n, "regular", 12, "Supporting copy and a value.", x + 14, 116, MUTED);
        grad(ctx, n, x + 14, 86 + ch - 16, cw - 28, 6, 3, ACCENT, ACCENT2, false);

        // Armor HUD
        cap(ctx, n, "Armor HUD", x, 166);
        int valW = 0;
        for (int i = 0; i < armor.length; i++) valW = Math.max(valW, (int) txtW(n, "semibold", 15, pct(armorFrac[i])));
        for (int i = 0; i < armor.length; i++) {
            int ry = 182 + i * 20;
            ctx.drawItem(armor[i], x, ry);
            nameShadow(ctx, n, pct(armorFrac[i]), x + 22, ry + 3, TEXT);
            rr(ctx, n, x + 22 + valW + 6, ry + 6, 4, 4, 2f, state(armorFrac[i]));
        }

        // Target HUD
        cap(ctx, n, "Target HUD", x + 150, 166);
        int tx = x + 150, ty = 184, tw = 150;
        nameShadow(ctx, n, "PlayerName", tx, ty, TEXT);
        hudShadow(ctx, n, "18.6 HP", tx, ty + 16, MUTED);
        rr(ctx, n, tx, ty + 32, tw, 2, 1f, TRACK);
        rr(ctx, n, tx, ty + 32, Math.round(tw * 0.62f), 2, 1f, ACCENT);
    }

    /** Paired specimens placed close together so a 400%+ zoom magnifies BOTH at once. */
    private void renderZoomPair(DrawContext ctx) {
        int cx = width / 2, cy = height / 2;
        int specW = 150, gap = 26;
        int xL = cx - specW - gap / 2, xR = cx + gap / 2;
        int y0 = cy - 78;
        txt(ctx, false, "medium", 12, "LEGACY", xL, y0 - 18, MUTED);
        txt(ctx, true, "medium", 12, "NEW", xR, y0 - 18, ACCENT);
        specimen(ctx, false, xL, y0);
        specimen(ctx, true, xR, y0);
    }

    private void specimen(DrawContext ctx, boolean n, int x, int y) {
        txt(ctx, n, "semibold", 22, "Ag Rk", x, y, TEXT);
        txt(ctx, n, "regular", 12, "Quick 0123 92%", x, y + 28, MUTED);
        rr(ctx, n, x, y + 46, 130, 22, 8, SURFACE2);
        rb(ctx, n, x, y + 46, 130, 22, 8, 1f, ACCENT);
        txt(ctx, n, "medium", 12, "Chip", x + 10, y + 51, TEXT);
        glow(ctx, n, x + 8, y + 78, 14, 14, 7, withA(ACCENT, 0xCC), 12f);
        rr(ctx, n, x + 8, y + 78, 14, 14, 7, ACCENT);
        grad(ctx, n, x + 30, y + 80, 100, 10, 5, ACCENT, ACCENT2, false);
        rr(ctx, n, x, y + 100, 40, 40, 14, SURFACE2);
        rb(ctx, n, x, y + 100, 40, 40, 14, 1f, withA(ACCENT, 0xAA));
    }

    // ------------------------------------------------- backend-switch helpers

    private void cap(DrawContext ctx, boolean n, String s, int x, int y) { txt(ctx, n, "medium", 11, s, x, y, DESC); }

    private void rr(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, int c) {
        if (n) PocRenderer.roundedRect(ctx, x, y, w, h, r, c);
        else RenderHelper.roundedRect(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, c);
    }
    private void rb(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, float th, int c) {
        if (n) PocRenderer.roundedBorder(ctx, x, y, w, h, r, th, c);
        else RenderHelper.roundedBorder(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, c);
    }
    private void glow(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, int c, float feather) {
        if (n) PocRenderer.glow(ctx, x, y, w, h, r, feather, c);
        else RenderHelper.glow(ctx, (int) x, (int) y, (int) w, (int) h, c);
    }
    private void grad(DrawContext ctx, boolean n, float x, float y, float w, float h, float r, int a, int b, boolean vert) {
        if (n) PocRenderer.gradientRoundedRect(ctx, x, y, w, h, r, a, b, vert);
        else if (vert) RenderHelper.gradientRoundedRectV(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, a, b);
        else RenderHelper.gradientRoundedRect(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, a, b);
    }
    /** Soft shadow under a floating panel: legacy concentric rings vs new continuous halo. */
    private void softShadow(DrawContext ctx, boolean n, float x, float y, float w, float h, float r) {
        if (n) PocRenderer.glow(ctx, x, y + 2, w, h, r, 10f, 0x66000000);
        else RenderHelper.dropShadow(ctx, (int) x, (int) y, (int) w, (int) h, (int) r, 6);
    }

    private void txt(DrawContext ctx, boolean n, String weight, float size, String s, int x, int y, int c) {
        if (n) { PocText.drawAt(ctx, weight, size, s, x, y, c); return; }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        boolean big = size >= 15;
        float base = big ? 15f : 13f;
        ctx.getMatrices().scale(size / base, size / base, 1f);
        if (big) ClubFont.drawName(ctx, s, 0, 0, c, false);
        else if ("regular".equals(weight)) ClubFont.drawDesc(ctx, s, 0, 0, c, false);
        else ClubFont.drawList(ctx, s, 0, 0, c, false);
        ctx.getMatrices().pop();
    }
    private float txtW(boolean n, String weight, float size, String s) {
        if (n) return PocText.widthAt(weight, size, s);
        boolean big = size >= 15;
        float base = big ? 15f : 13f;
        int w = big ? ClubFont.widthName(s) : "regular".equals(weight) ? ClubFont.widthDesc(s) : ClubFont.widthList(s);
        return w * size / base;
    }
    private void nameShadow(DrawContext ctx, boolean n, String s, int x, int y, int c) { if (n) PocText.drawNameShadow(ctx, s, x, y, c); else ClubFont.drawNameShadow(ctx, s, x, y, c); }
    private void hudShadow(DrawContext ctx, boolean n, String s, int x, int y, int c)  { if (n) PocText.drawHudShadow(ctx, s, x, y, c); else ClubFont.drawHudShadow(ctx, s, x, y, c); }

    private static String pct(float f) { return Math.round(f * 100) + "%"; }
    private static int withA(int rgb, int a) { return (rgb & 0x00FFFFFF) | (a << 24); }
    private static int state(float f) { return f >= 0.70f ? GOOD : f >= 0.40f ? WARN : LOW; }
    private static ItemStack[] sampleArmor() {
        float[] f = {0.92f, 0.87f, 0.79f, 0.54f};
        ItemStack[] s = {
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS),
        };
        for (int i = 0; i < s.length; i++) s[i].setDamage(Math.round((1 - f[i]) * s[i].getMaxDamage()));
        return s;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { setPage(page + 1); return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)  { setPage(page - 1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean shouldPause() { return false; }
}
