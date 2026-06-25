package com.club.gui;

import com.club.config.ClubConfig;
import com.club.gui.components.ButtonC;
import com.club.gui.components.SegmentedWidget;
import com.club.gui.components.SliderWidget;
import com.club.gui.components.ToggleWidget;
import com.club.hud.ArmorHud;
import com.club.hud.HudStyle;
import com.club.hud.PotionHud;
import com.club.hud.TargetHud;
import com.club.util.ClubFont;
import com.club.util.Mth;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor: drag the Armor / Potion / Target elements to position them (they
 * snap to edges and centres), and click one to open a floating settings panel
 * with its size, orientation and value options. All changes write straight to
 * the config. Representative previews are shown so elements are always
 * positionable even with no armor / effects / target present.
 */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private static final int MARGIN = 4, SNAP = 6;

    private int pressedHud = -1;     // hud under the press, or -1
    private boolean moved = false;   // press turned into a drag
    private boolean widgetPress = false; // gesture started on a widget / popover
    private int dragOffX, dragOffY;
    private int selected = -1;       // hud whose popover is open
    private int guideX = Integer.MIN_VALUE, guideY = Integer.MIN_VALUE;

    private int popX, popY, popW, popH;
    private final List<int[]> popLabels = new ArrayList<>(); // {x, y, labelId}
    private static final String[] POP_LABEL = {"Orientation", "Value"};
    private static final String[] HUD_NAME = {"Armor HUD", "Potion HUD", "Target HUD"};

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD Editor"));
        this.parent = parent;
    }

    private ClubConfig.Hud hud() { return ClubConfig.get().hud; }

    @Override
    protected void init() { buildWidgets(); }

    // --------------------------------------------------------------- geometry

    private int[] box(int id) {
        ClubConfig.Hud h = hud();
        switch (id) {
            case 0 -> { return scaledBox(h.armorX, h.armorY, ArmorHud.sampleSize(h), h.armorScale); }
            case 1 -> { return scaledBox(h.potionX, h.potionY, PotionHud.sampleSize(h), h.potionScale); }
            default -> {
                int x = h.targetX >= 0 ? h.targetX : TargetHud.autoX(client);
                int y = h.targetY >= 0 ? h.targetY : TargetHud.autoY(client);
                return scaledBox(x, y, TargetHud.sampleSize(), h.targetScale);
            }
        }
    }

    private int[] scaledBox(int x, int y, int[] s, float sc) {
        return new int[]{x, y, Math.max(8, Math.round(s[0] * sc)), Math.max(8, Math.round(s[1] * sc))};
    }

    private int hudAt(double mx, double my) {
        for (int id = 0; id < 3; id++) {
            int[] b = box(id);
            if (mx >= b[0] - 4 && mx <= b[0] + b[2] + 4 && my >= b[1] - 4 && my <= b[1] + b[3] + 4) return id;
        }
        return -1;
    }

    // ---------------------------------------------------------------- widgets

    private int popoverH(int id) {
        int base = 34 + 30 + 32 + 8; // top+title, enabled, size, bottom
        return switch (id) { case 0 -> base + 46 + 46; case 1 -> base + 46; default -> base + 32; };
    }

    private void buildWidgets() {
        clearChildren();
        popLabels.clear();

        int barY = height - 48;
        addDrawableChild(new ButtonC(width / 2 - 104, barY, 96, 26, "Reset", this::resetPositions));
        addDrawableChild(new ButtonC(width / 2 + 8, barY, 96, 26, "Done", this::close).primary());

        if (selected >= 0) buildPopover(selected);
    }

    private void buildPopover(int id) {
        ClubConfig.Hud h = hud();
        int[] b = box(id);
        popW = 200; popH = popoverH(id);
        int px = b[0] + b[2] + 14;
        if (px + popW > width - 8) px = b[0] - popW - 14;
        popX = Mth.clamp(px, 8, Math.max(8, width - popW - 8));
        popY = Mth.clamp(b[1] - 4, 8, Math.max(8, height - popH - 8));

        int ix = popX + 14, iw = popW - 28;
        int y = popY + 34;

        addDrawableChild(new ToggleWidget(ix, y, iw, 20, "Enabled", enabledOf(id), v -> { setHudEnabled(id, v); save(); })); y += 30;
        addDrawableChild(new SliderWidget(ix, y, iw, 24, "Size", 0.5f, 2.0f, 0.05f, scaleOf(id),
                v -> { setScale(id, v); save(); }).format(v -> String.format(java.util.Locale.US, "%.2f×", v)).trackWidth(84)); y += 32;

        switch (id) {
            case 0 -> {
                popLabels.add(new int[]{ix, y, 0}); // Orientation
                addDrawableChild(new SegmentedWidget(ix, y + 12, iw, 22, new String[]{"Vertical", "Row"}, h.armorVertical ? 0 : 1,
                        i -> { h.armorVertical = (i == 0); save(); })); y += 46;
                popLabels.add(new int[]{ix, y, 1}); // Value
                addDrawableChild(new SegmentedWidget(ix, y + 12, iw, 22, new String[]{"%", "Count"}, h.armorPercent ? 0 : 1,
                        i -> { h.armorPercent = (i == 0); save(); })); y += 46;
            }
            case 1 -> {
                popLabels.add(new int[]{ix, y, 0}); // Orientation
                addDrawableChild(new SegmentedWidget(ix, y + 12, iw, 22, new String[]{"Column", "Row"}, h.potionHorizontal ? 1 : 0,
                        i -> { h.potionHorizontal = (i == 1); save(); })); y += 46;
            }
            default -> addDrawableChild(new SliderWidget(ix, y, iw, 24, "Range", 3f, 32f, 1f, h.targetDistance,
                    v -> { h.targetDistance = Math.round(v); save(); }).format(v -> Math.round(v) + "m").trackWidth(84));
        }
    }

    private boolean enabledOf(int id) { ClubConfig.Hud h = hud(); return id == 0 ? h.armor : id == 1 ? h.potions : h.target; }
    private void setHudEnabled(int id, boolean v) { ClubConfig.Hud h = hud(); if (id == 0) h.armor = v; else if (id == 1) h.potions = v; else h.target = v; }
    private float scaleOf(int id) { ClubConfig.Hud h = hud(); return id == 0 ? h.armorScale : id == 1 ? h.potionScale : h.targetScale; }
    private void setScale(int id, float v) { ClubConfig.Hud h = hud(); if (id == 0) h.armorScale = v; else if (id == 1) h.potionScale = v; else h.targetScale = v; }
    private void save() { ClubConfig.save(); }

    // ----------------------------------------------------------------- render

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) { /* drawn below */ }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xBC050810);

        int cx = width / 2, cy = height / 2;
        ctx.fill(cx - 5, cy, cx + 6, cy + 1, Theme.TEXT_FAINT);
        ctx.fill(cx, cy - 5, cx + 1, cy + 6, Theme.TEXT_FAINT);

        // previews + outlines + labels
        ArmorHud.drawSample(ctx);
        PotionHud.drawSample(ctx);
        ctx.getMatrices().push();
        ClubConfig.Hud h = hud();
        ctx.getMatrices().translate(h.targetX >= 0 ? h.targetX : TargetHud.autoX(client),
                h.targetY >= 0 ? h.targetY : TargetHud.autoY(client), 0);
        ctx.getMatrices().scale(h.targetScale, h.targetScale, 1f);
        TargetHud.drawSample(ctx);
        ctx.getMatrices().pop();

        for (int id = 0; id < 3; id++) {
            int[] b = box(id);
            boolean sel = id == selected;
            boolean hover = !sel && hudAt(mx, my) == id;
            RenderHelper.roundedBorder(ctx, b[0] - 5, b[1] - 5, b[2] + 10, b[3] + 10, Theme.RADIUS_SM,
                    sel ? Theme.ACCENT : hover ? Theme.HAIR_STRONG : Theme.HAIR);
            String lbl = id == 0 ? "ARMOR" : id == 1 ? "POTIONS" : "TARGET";
            ClubFont.drawSmall(ctx, lbl, b[0] - 5, b[1] - 18, sel ? Theme.ACCENT_SOFT : Theme.TEXT_FAINT, false);
        }

        if (guideX != Integer.MIN_VALUE) ctx.fill(guideX, 0, guideX + 1, height, RenderHelper.withAlpha(0x7CABFF, 0xAA));
        if (guideY != Integer.MIN_VALUE) ctx.fill(0, guideY, width, guideY + 1, RenderHelper.withAlpha(0x7CABFF, 0xAA));

        // title + hint — flat white, never a gradient on text
        String title = "HUD EDITOR";
        int tw = ClubFont.widthCat(title);
        ClubFont.drawCat(ctx, title, cx - tw / 2, 20, Theme.TEXT, false);
        String hint = "Drag any element to move it. Click it to open its settings.";
        ClubFont.drawDesc(ctx, hint, cx - ClubFont.widthDesc(hint) / 2, 42, Theme.TEXT_MUTED, false);

        // popover surface + labels (widgets drawn by super.render on top)
        if (selected >= 0) {
            HudStyle.popover(ctx, popX, popY, popW, popH);
            ClubFont.drawCat(ctx, HUD_NAME[selected], popX + 14, popY + 12, Theme.TEXT, false);
            for (int[] l : popLabels) ClubFont.drawSmall(ctx, POP_LABEL[l[2]], l[0], l[1], Theme.TEXT_FAINT, false);
        }

        super.render(ctx, mx, my, delta);
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) { widgetPress = true; return true; }
        if (selected >= 0 && mx >= popX && mx <= popX + popW && my >= popY && my <= popY + popH) { widgetPress = true; return true; }
        widgetPress = false;
        moved = false;
        pressedHud = hudAt(mx, my);
        if (pressedHud >= 0) { int[] b = box(pressedHud); dragOffX = (int) mx - b[0]; dragOffY = (int) my - b[1]; }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (widgetPress) return super.mouseDragged(mx, my, btn, dx, dy);
        if (pressedHud >= 0) { moved = true; moveHud(pressedHud, (int) mx - dragOffX, (int) my - dragOffY); return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (widgetPress) { widgetPress = false; return super.mouseReleased(mx, my, btn); }
        boolean handled = false;
        guideX = guideY = Integer.MIN_VALUE;
        if (pressedHud >= 0) {
            if (!moved) toggleSelect(pressedHud); else save();
            handled = true;
        } else if (!moved) {
            if (selected >= 0) { selected = -1; buildWidgets(); }
            handled = true;
        }
        pressedHud = -1; moved = false;
        return handled || super.mouseReleased(mx, my, btn);
    }

    private void toggleSelect(int id) {
        selected = (selected == id) ? -1 : id;
        buildWidgets();
    }

    private void moveHud(int id, int nx, int ny) {
        int[] b = box(id);
        nx = Mth.clamp(snapX(nx, b[2]), 0, width - b[2]);
        ny = Mth.clamp(snapY(ny, b[3]), 0, height - b[3]);
        ClubConfig.Hud h = hud();
        if (id == 0) { h.armorX = nx; h.armorY = ny; }
        else if (id == 1) { h.potionX = nx; h.potionY = ny; }
        else { h.targetX = nx; h.targetY = ny; }
    }

    private int snapX(int x, int w) {
        guideX = Integer.MIN_VALUE;
        int[] targets = {MARGIN, (width - w) / 2, width - w - MARGIN};
        int[] guides = {MARGIN, width / 2, width - MARGIN};
        for (int i = 0; i < targets.length; i++) if (Math.abs(x - targets[i]) <= SNAP) { guideX = guides[i]; return targets[i]; }
        return x;
    }

    private int snapY(int y, int h) {
        guideY = Integer.MIN_VALUE;
        int[] targets = {MARGIN, (height - h) / 2, height - h - MARGIN};
        int[] guides = {MARGIN, height / 2, height - MARGIN};
        for (int i = 0; i < targets.length; i++) if (Math.abs(y - targets[i]) <= SNAP) { guideY = guides[i]; return targets[i]; }
        return y;
    }

    private void resetPositions() {
        ClubConfig.Hud h = hud();
        h.armorX = 8; h.armorY = 8; h.potionX = 8; h.potionY = 70; h.targetX = -1; h.targetY = -1;
        save();
        buildWidgets();
    }

    @Override
    public void close() {
        save();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
