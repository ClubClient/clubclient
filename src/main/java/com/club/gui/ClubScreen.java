package com.club.gui;

import com.club.config.ClubConfig;
import com.club.config.ClubConfig.HandSide;
import com.club.gui.components.ButtonC;
import com.club.gui.components.DropdownWidget;
import com.club.gui.components.SegmentedWidget;
import com.club.gui.components.SliderWidget;
import com.club.gui.components.ToggleWidget;
import com.club.modules.animations.AnimationType;
import com.club.modules.screenstretch.StretchPreset;
import com.club.util.ClubFont;
import com.club.util.Mth;
import com.club.util.RenderHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Redesigned CLUB menu: a top category bar, a left module list, and a right
 * settings panel — a premium desktop layout. Deep-navy window with a barely-
 * there vertical gradient and near-invisible edge glow (depth, not a flat
 * rectangle), a soft blue→cyan accent reserved for active controls, large crisp
 * Inter type, and thin hairlines dividing the three zones. There is no wordmark
 * and no window buttons — the menu closes on ESC. Per-HUD settings live in the
 * HUD editor.
 */
public class ClubScreen extends Screen {
    // Tightened from the previous sparse box — denser padding so the window reads
    // as an intentional, compact surface.
    private static final int BASE_W = 520, BASE_H = 300;

    // --- module registry (names/descriptions; icons are setting-row only) ---
    private final String[] catNames = {"Combat", "Visuals", "Player", "Misc"};
    private final String[][] modNames = {
        {"Animations"},
        {"Screen Stretch", "No Hurt Cam", "No Fire Overlay", "No Bobbing"},
        {"Hands"},
        {"HUD Editor", "Hide Vanilla Effects"},
    };
    private final String[][] modDesc = {
        {"Custom first-person attack animation."},
        {"Stretch the view to a target aspect ratio.", "Removes the red damage screen tilt.",
         "Hides the first-person flames while burning.", "Stops the view bobbing as you walk."},
        {"Reposition and scale the first-person hands."},
        {"Position and configure your HUD elements.", "Hide the vanilla status-effect overlay."},
    };

    private int activeCat = 1; // Visuals
    private int sel = 0;       // selected module within the category
    private boolean editLeft = false; // Hands: which side the sliders edit

    private float s = 1f;
    private int W, H, winX, winY, TH;
    private int listX, listW, listTop, panelX, panelTop, panelRight, panelW, footerY, dividerX;
    private final int[][] tabRect = new int[4][4];

    private final List<DropdownWidget> dropdowns = new ArrayList<>();

    private long openStart;
    private static final float OPEN_MS = 180f;

    public ClubScreen() { super(Text.literal("Club")); }

    private int px(int v) { return Math.round(v * s); }
    private float openProgress() { return Mth.smooth(Mth.clamp((System.currentTimeMillis() - openStart) / OPEN_MS, 0f, 1f)); }
    private ClubConfig cfg() { return ClubConfig.get(); }

    @Override
    protected void init() {
        if (openStart == 0) openStart = System.currentTimeMillis();
        layout();
        buildWidgets();
    }

    private void layout() {
        s = Math.min(1.15f, Math.min((width - 24f) / BASE_W, (height - 24f) / BASE_H));
        W = px(BASE_W); H = px(BASE_H);
        winX = (width - W) / 2; winY = (height - H) / 2;
        TH = px(40);
        int padOuter = px(16);
        // Navigation reads as a narrow ~25% rail; the panel is the ~75% work area.
        // A 1px hairline between them divides the two zones. Padding is tightened for
        // a denser, game-client feel.
        listX = winX + padOuter;
        listW = px(110);
        listTop = winY + TH + px(10);
        dividerX = listX + listW + px(10);
        panelX = dividerX + px(12);
        panelTop = winY + TH + px(10);
        panelRight = winX + W - padOuter;
        panelW = panelRight - panelX;
        footerY = winY + H - px(30);

        // top-bar category tabs, vertically centred on the bar — no wordmark, the
        // tabs are the primary nav block and start flush at the left padding.
        int barMid = winY + TH / 2;
        int ty = barMid - px(8);
        int tx = winX + padOuter;
        for (int i = 0; i < catNames.length; i++) {
            int w = ClubFont.widthTab(catNames[i]);
            tabRect[i] = new int[]{tx, ty, w, px(16)};
            tx += w + px(18);
        }
    }

    private boolean hasToggle(int cat, int mod) { return !(cat == 3 && mod == 0); } // all but HUD Editor

    private boolean enabledOf(int cat, int mod) {
        ClubConfig c = cfg();
        return switch (cat) {
            case 0 -> c.animations.enabled;
            case 1 -> switch (mod) { case 0 -> c.screenStretch.enabled; case 1 -> c.noHurtCam; case 2 -> c.noFireOverlay; default -> c.noBobbing; };
            case 2 -> c.hands.enabled;
            default -> mod == 1 && c.hud.hideVanillaEffects;
        };
    }

    private void setEnabled(int cat, int mod, boolean v) {
        ClubConfig c = cfg();
        switch (cat) {
            case 0 -> c.animations.enabled = v;
            case 1 -> { switch (mod) { case 0 -> c.screenStretch.enabled = v; case 1 -> c.noHurtCam = v; case 2 -> c.noFireOverlay = v; default -> c.noBobbing = v; } }
            case 2 -> c.hands.enabled = v;
            default -> { if (mod == 1) c.hud.hideVanillaEffects = v; }
        }
        ClubConfig.save();
    }

    // ---------------------------------------------------------------- widgets

    private void buildWidgets() {
        clearChildren();
        dropdowns.clear();
        ClubConfig c = cfg();

        // master toggle (header, top-right) — quiet, vertically aligned to the
        // centre of the module title.
        if (hasToggle(activeCat, sel)) {
            int tw0 = px(38), th0 = px(20);
            int fcat = activeCat, fmod = sel;
            addDrawableChild(new ToggleWidget(panelRight - tw0, panelTop - px(2), tw0, th0, "",
                    enabledOf(activeCat, sel), v -> setEnabled(fcat, fmod, v)));
        }

        // Controls sit in a compact vertical block under the header divider.
        int gx = panelX, gw = panelW;
        int y = panelTop + px(40);
        int gap = px(9);

        if (activeCat == 0) { // Animations
            AnimationType[] anims = AnimationType.values();
            String[] names = new String[anims.length]; int s0 = 0;
            for (int i = 0; i < anims.length; i++) { names[i] = anims[i].label(); if (anims[i].name().equals(c.animations.type)) s0 = i; }
            DropdownWidget dd = new DropdownWidget(gx, y, gw, px(18), names, s0, i -> { c.animations.type = anims[i].name(); ClubConfig.save(); });
            addDrawableChild(dd); dropdowns.add(dd); y += px(18) + gap;
            addDrawableChild(new SliderWidget(gx, y, gw, px(22), "Speed", 0.5f, 2.0f, 0.01f, c.animations.speed,
                    v -> { c.animations.speed = v; ClubConfig.save(); }).format(v -> String.format(java.util.Locale.US, "%.2f×", v))); y += px(22) + gap;
            addDrawableChild(new SliderWidget(gx, y, gw, px(22), "Amplitude", 0.5f, 1.5f, 0.01f, c.animations.amplitude,
                    v -> { c.animations.amplitude = v; ClubConfig.save(); }));
        } else if (activeCat == 1 && sel == 0) { // Screen Stretch
            StretchPreset[] ps = StretchPreset.values();
            String[] names = new String[ps.length]; int s0 = 0;
            for (int i = 0; i < ps.length; i++) { names[i] = ps[i].label(); if (ps[i].name().equals(c.screenStretch.preset)) s0 = i; }
            DropdownWidget dd = new DropdownWidget(gx, y, gw, px(18), names, s0, i -> { c.screenStretch.preset = ps[i].name(); ClubConfig.save(); });
            addDrawableChild(dd); dropdowns.add(dd); y += px(18) + gap;
            addDrawableChild(new ToggleWidget(gx, y, gw, px(20), "Black Bars", c.screenStretch.blackBars,
                    v -> { c.screenStretch.blackBars = v; ClubConfig.save(); }));
        } else if (activeCat == 2) { // Hands
            // compact Left/Right segmented selector — not a full-width bar
            addDrawableChild(new SegmentedWidget(panelX, y, px(148), px(20), new String[]{"Left", "Right"}, editLeft ? 0 : 1,
                    i -> { editLeft = (i == 0); buildWidgets(); }));
            y += px(20) + gap;
            HandSide hs = editLeft ? c.hands.leftHand : c.hands.rightHand;
            addDrawableChild(new SliderWidget(gx, y, gw, px(22), "Scale", 0.5f, 2.0f, 0.01f, hs.scale,
                    v -> { hs.scale = v; ClubConfig.save(); }).format(v -> String.format(java.util.Locale.US, "%.2f×", v))); y += px(22) + gap;
            addDrawableChild(new SliderWidget(gx, y, gw, px(22), "Offset X", -1.0f, 1.0f, 0.01f, hs.offsetX,
                    v -> { hs.offsetX = v; ClubConfig.save(); })); y += px(22) + gap;
            addDrawableChild(new SliderWidget(gx, y, gw, px(22), "Offset Y", -1.0f, 1.0f, 0.01f, hs.offsetY,
                    v -> { hs.offsetY = v; ClubConfig.save(); })); y += px(22) + gap;
            addDrawableChild(new SliderWidget(gx, y, gw, px(22), "Offset Z", -1.0f, 1.0f, 0.01f, hs.offsetZ,
                    v -> { hs.offsetZ = v; ClubConfig.save(); }));
        } else if (activeCat == 3 && sel == 0) { // HUD Editor (action)
            addDrawableChild(new ButtonC(panelX, y, px(150), px(26), "Open Editor",
                    () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen(this))).primary());
        }
        // simple-toggle modules (No Hurt Cam / Fire / Bobbing / Hide Vanilla Effects) have no extra rows
    }

    // ----------------------------------------------------------------- render

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) { /* drawn in render() */ }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        float a = openProgress();
        ctx.fill(0, 0, width, height, RenderHelper.scaleAlpha(0xCC06090F, a)); // dim the world (translucent tint, not a black wall)

        // Open animation: a gentle upward slide only, snapped to whole pixels. We
        // deliberately do NOT scale the window — scaling matrix-samples every glyph
        // and is the classic cause of "the font looks resized". Text stays crisp.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, Math.round((1f - a) * px(6)), 0);

        // Window depth: base navy + a barely-there vertical gradient (#0B1730→
        // #071120), then two near-invisible colour volumes at the corners. No
        // glass, no blur — the user should feel depth, not see an effect.
        RenderHelper.gradientRoundedRectV(ctx, winX, winY, W, H, Theme.RADIUS, Theme.BG_WIN_TOP, Theme.BG_WIN_BOT);
        RenderHelper.radialGlow(ctx, winX, winY, W, H, Theme.RADIUS,
                winX + W * 0.08, winY + H * 0.04, W * 0.78, Theme.GLOW_TL);
        RenderHelper.radialGlow(ctx, winX, winY, W, H, Theme.RADIUS,
                winX + W * 0.94, winY + H * 0.98, W * 0.78, Theme.GLOW_BR);
        RenderHelper.roundedBorder(ctx, winX, winY, W, H, Theme.RADIUS, Theme.HAIR);

        renderTopBar(ctx, mx, my);
        // Thin vertical hairline divides the nav rail from the settings panel.
        ctx.fill(dividerX, winY + TH + px(8), dividerX + 1, footerY + px(4), Theme.DIVIDER);
        renderList(ctx, mx, my);
        renderPanel(ctx, mx, my);
        renderFooter(ctx, mx, my);

        super.render(ctx, mx, my, delta);
        for (DropdownWidget d : dropdowns) d.renderPopup(ctx, mx, my);

        ctx.getMatrices().pop();
    }

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        for (int i = 0; i < catNames.length; i++) {
            int[] r = tabRect[i];
            boolean active = i == activeCat;
            boolean hover = inRect(mx, my, r);
            // Active tab: accent text + a thin accent underline (the underline is one
            // of the two places a gradient is allowed). Idle tabs recede to muted;
            // hover lifts toward white.
            int col = active ? Theme.ACCENT : hover ? Theme.TEXT : Theme.TEXT_MUTED;
            ClubFont.drawTab(ctx, catNames[i], r[0], r[1], col, false);
            if (active)
                RenderHelper.gradientRoundedRect(ctx, r[0], r[1] + px(17), r[2], Math.max(2, px(2)), 1, Theme.GRAD_A, Theme.GRAD_B);
        }

        // Header divider — separates the nav bar from the body.
        ctx.fill(winX + px(20), winY + TH, winX + W - px(20), winY + TH + 1, Theme.DIVIDER);
    }

    private int rowPitch() { return px(25); }

    /**
     * The module list reads as one tight navigation block — no cards, no fills,
     * no "On/Off" tags. The active module is marked by a small accent dot in the
     * gutter (● Name); inactive rows carry no bullet so the names stay aligned.
     * Enabled state is conveyed by text brightness; rows are separated by air.
     */
    private void renderList(DrawContext ctx, int mx, int my) {
        String[] mods = modNames[activeCat];
        for (int i = 0; i < mods.length; i++) {
            int ry = listTop + i * rowPitch();
            int rh = rowPitch();
            boolean active = i == sel;
            boolean hover = mx >= listX && mx <= dividerX && my >= ry && my <= ry + rh;
            boolean on = !hasToggle(activeCat, i) || enabledOf(activeCat, i);
            int cy = ry + rh / 2;

            // Active module: a very subtle row wash + a small flat accent dot in the
            // gutter (the dot is the only accent; no gradient, no plate).
            if (active) {
                RenderHelper.roundedRect(ctx, listX - px(3), ry + px(2), listW + px(6), rh - px(4), Theme.RADIUS_XS, Theme.FILL_SUBTLE);
                int dotN = px(5);
                Icons.brandDot(ctx, listX + px(2), cy - dotN / 2, dotN);
            }

            // active/hover → white; otherwise brightness carries the enabled state.
            int tcol = active || hover ? Theme.TEXT : on ? Theme.TEXT_MUTED : Theme.TEXT_FAINT;
            ClubFont.drawList(ctx, mods[i], listX + px(14), cy - px(6), tcol, false);
        }
    }

    private void renderPanel(DrawContext ctx, int mx, int my) {
        String title = modNames[activeCat][sel];
        String desc = modDesc[activeCat][sel];
        // The module name is the focal point — drawn large and crisp at its native
        // size (never matrix-scaled), white. The description sits below it in a
        // low-contrast tone so it recedes. A hairline closes the header and opens
        // the compact settings block beneath it.
        ClubFont.drawCat(ctx, title, panelX, panelTop, Theme.TEXT, false);
        ClubFont.drawDesc(ctx, desc, panelX, panelTop + px(20), Theme.TEXT_DESC, false);
        ctx.fill(panelX, panelTop + px(32), panelRight, panelTop + px(32) + 1, Theme.DIVIDER);
    }

    private void renderFooter(DrawContext ctx, int mx, int my) {
        // One quiet text action, bottom-right — no rule, no icon. Reset lives here
        // because it is destructive-ish and should sit apart from the controls.
        String reset = "Reset to Default";
        int rw = ClubFont.widthSmall(reset);
        boolean hov = inResetRect(mx, my);
        ClubFont.drawSmall(ctx, reset, panelRight - rw, footerY + px(12), hov ? Theme.TEXT : Theme.TEXT_FAINT, false);
    }

    // ----------------------------------------------------------------- input

    private boolean inRect(double mx, double my, int[] r) { return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]; }
    private boolean inResetRect(double mx, double my) {
        int rw = ClubFont.widthSmall("Reset to Default");
        return mx >= panelRight - rw - px(6) && mx <= panelRight && my >= footerY + px(6) && my <= footerY + px(24);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // open dropdown option click first
        for (DropdownWidget d : dropdowns) if (d.open) { int i = d.optionAt(mx, my); if (i >= 0) { d.select(i); d.open = false; return true; } }
        for (DropdownWidget d : dropdowns) if (d.inHeader(mx, my)) { boolean was = d.open; closeDropdowns(); d.open = !was; return true; }

        for (int i = 0; i < catNames.length; i++) {
            int[] r = tabRect[i]; int[] hit = {r[0] - px(6), winY, r[2] + px(12), TH};
            if (inRect(mx, my, hit)) { if (activeCat != i) { activeCat = i; sel = 0; buildWidgets(); } return true; }
        }

        // module rows
        String[] mods = modNames[activeCat];
        for (int i = 0; i < mods.length; i++) {
            int ry = listTop + i * rowPitch();
            if (mx >= listX && mx <= dividerX && my >= ry && my <= ry + rowPitch()) {
                if (sel != i) { sel = i; buildWidgets(); }
                return true;
            }
        }

        if (inResetRect(mx, my)) { resetModule(); return true; }

        boolean anyOpen = false; for (DropdownWidget d : dropdowns) if (d.open) anyOpen = true;
        if (anyOpen) { closeDropdowns(); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    private void closeDropdowns() { for (DropdownWidget d : dropdowns) d.open = false; }

    private void resetModule() {
        ClubConfig c = cfg();
        if (activeCat == 0) { c.animations.type = "CLASSIC"; c.animations.speed = 1.0f; c.animations.amplitude = 1.0f; c.animations.enabled = true; }
        else if (activeCat == 1 && sel == 0) { c.screenStretch.preset = "R16_9"; c.screenStretch.blackBars = true; c.screenStretch.enabled = true; }
        else if (activeCat == 1) { setEnabled(1, sel, true); }
        else if (activeCat == 2) { c.hands.enabled = true; reset(c.hands.leftHand); reset(c.hands.rightHand); }
        else if (activeCat == 3 && sel == 1) { c.hud.hideVanillaEffects = true; }
        ClubConfig.save();
        buildWidgets();
    }

    private void reset(HandSide h) { h.scale = 1.0f; h.offsetX = 0f; h.offsetY = 0f; h.offsetZ = 0f; }

    @Override
    public boolean shouldPause() { return false; }
}
