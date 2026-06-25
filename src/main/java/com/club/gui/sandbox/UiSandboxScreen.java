package com.club.gui.sandbox;

import com.club.gui.Icons;
import com.club.util.ClubFont;
import com.club.util.RenderHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * UI Sandbox — an isolated test screen for nailing component quality before it
 * touches the real client. It does NOT use the live {@code Theme}/{@code gui.components}
 * (those drive the menu/HUD and must stay untouched); it carries its own palette
 * and its own five components built to the v2.5 component reference:
 * dark #0B0F14 surfaces, a single indigo accent #5865F2 used only for active
 * state, Inter type, no glow, no animation.
 *
 * Five components: a dot toggle, a slider, a dropdown, a button and a text input.
 * Open it with the "UI Sandbox" keybind (default K).
 */
public class UiSandboxScreen extends Screen {

    // --- local palette (reference board) ---
    private static final int BG          = 0xFF0B0F14;
    private static final int SURFACE     = 0xFF11151C;
    private static final int SURFACE_HI  = 0xFF161B24; // input/dropdown fill, one step up
    private static final int BORDER      = 0xFF1C222B;
    private static final int TEXT        = 0xFFE6EAF0;
    private static final int TEXT_SECOND = 0xFF9AA3AF;
    private static final int ACCENT      = 0xFF5865F2;
    private static final int ACCENT_HI   = 0xFF7280FF;
    private static final int TRACK       = 0xFF222A36; // slider track / off ring
    private static final int DIVIDER     = 0xFF1A2029;

    private static final int CARD_W = 420, CARD_H = 270, PAD = 18, ROW = 34, R = 6;

    // --- component state ---
    private boolean enable = true;
    private float strength = 0.70f;
    private final String[] modes = {"Performance", "Balanced", "Quality"};
    private int mode = 1;
    private boolean ddOpen = false;
    private String username = "Steve";
    private boolean userFocused = true;
    private boolean btnPressed = false;

    // --- geometry (filled by layout) ---
    private int cardX, cardY, contentX, contentRight, labelW;
    private int togDotX, togCy, togHitX;
    private int slTrackX0, slTrackX1, slCy;
    private int ddX, ddY, ddW, ddH;
    private int inX, inY, inW, inH;
    private int btnX, btnY, btnW, btnH;
    private boolean draggingSlider = false;

    public UiSandboxScreen() { super(Text.literal("UI Sandbox")); }

    @Override
    protected void init() { layout(); }

    private void layout() {
        cardX = (width - CARD_W) / 2;
        cardY = (height - CARD_H) / 2;
        contentX = cardX + PAD;
        contentRight = cardX + CARD_W - PAD;
        labelW = 90;

        int rowsTop = cardY + PAD + 44 + 14; // header + divider gap
        int controlX = contentX + labelW;

        // 0 — toggle
        togCy = rowsTop + ROW / 2;
        togDotX = contentRight - 7;
        togHitX = contentRight - 22;

        // 1 — slider
        slCy = rowsTop + ROW + ROW / 2;
        slTrackX0 = controlX;
        slTrackX1 = contentRight - 46;

        // 2 — dropdown
        ddH = 26;
        ddX = controlX;
        ddY = rowsTop + 2 * ROW + (ROW - ddH) / 2;
        ddW = contentRight - controlX;

        // 3 — text input
        inH = 26;
        inX = controlX;
        inY = rowsTop + 3 * ROW + (ROW - inH) / 2;
        inW = contentRight - controlX;

        // 4 — button
        btnW = 130; btnH = 32;
        btnX = contentRight - btnW;
        btnY = rowsTop + 4 * ROW + 8;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, BG);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        // card
        RenderHelper.roundedRect(ctx, cardX, cardY, CARD_W, CARD_H, 10, SURFACE);
        RenderHelper.roundedBorder(ctx, cardX, cardY, CARD_W, CARD_H, 10, BORDER);

        // header
        ClubFont.drawCat(ctx, "UI SANDBOX", contentX, cardY + PAD, TEXT, false);
        ClubFont.drawDesc(ctx, "Component quality reference — Inter, accent #5865F2", contentX, cardY + PAD + 21, TEXT_SECOND, false);
        ctx.fill(contentX, cardY + PAD + 44, contentRight, cardY + PAD + 45, DIVIDER);

        // rows
        label(ctx, "Enable", togCy);
        renderToggle(ctx);

        label(ctx, "Strength", slCy);
        renderSlider(ctx, mx, my);

        label(ctx, "Mode", ddY + ddH / 2);
        renderDropdownHeader(ctx, mx, my);

        label(ctx, "Username", inY + inH / 2);
        renderInput(ctx);

        renderButton(ctx, mx, my);

        // dropdown options drawn last so they overlay the rows
        if (ddOpen) renderDropdownPopup(ctx, mx, my);
    }

    private void label(DrawContext ctx, String s, int cy) {
        ClubFont.drawList(ctx, s, contentX, cy - 6, TEXT, false);
    }

    /** A — dot toggle (recommended style): filled accent dot ON, hollow ring OFF. */
    private void renderToggle(DrawContext ctx) {
        if (enable) {
            aaCircle(ctx, togDotX, togCy, 5.0, ACCENT);
        } else {
            aaCircle(ctx, togDotX, togCy, 5.0, TRACK);   // ring
            aaCircle(ctx, togDotX, togCy, 3.5, SURFACE); // punch the centre
        }
    }

    private void renderSlider(DrawContext ctx, int mx, int my) {
        int tw = slTrackX1 - slTrackX0;
        int knobX = slTrackX0 + Math.round(tw * strength);
        // track + accent fill
        RenderHelper.roundedRect(ctx, slTrackX0, slCy - 2, tw, 4, 2, TRACK);
        if (knobX > slTrackX0) RenderHelper.roundedRect(ctx, slTrackX0, slCy - 2, knobX - slTrackX0, 4, 2, ACCENT);
        // white knob with a soft dark rim for definition (no glow)
        aaCircle(ctx, knobX, slCy, 6.5, 0x66000000);
        aaCircle(ctx, knobX, slCy, 6.0, 0xFFFFFFFF);
        // value, right-aligned
        String v = Math.round(strength * 100) + "%";
        ClubFont.drawList(ctx, v, contentRight - ClubFont.widthList(v), slCy - 6, TEXT, false);
    }

    private void renderDropdownHeader(DrawContext ctx, int mx, int my) {
        boolean hover = ddOpen || inRect(mx, my, ddX, ddY, ddW, ddH);
        RenderHelper.roundedRect(ctx, ddX, ddY, ddW, ddH, R, hover ? SURFACE_HI : SURFACE);
        RenderHelper.roundedBorder(ctx, ddX, ddY, ddW, ddH, R, ddOpen ? ACCENT : BORDER);
        ClubFont.drawList(ctx, modes[mode], ddX + 10, ddY + (ddH - 13) / 2, TEXT, false);
        Icons.chevron(ctx, ddX + ddW - 16, ddY + ddH / 2 - (ddOpen ? 1 : 2), 7, ddOpen ? ACCENT : TEXT_SECOND, !ddOpen);
    }

    private void renderDropdownPopup(DrawContext ctx, int mx, int my) {
        int ih = 24, h = modes.length * ih + 6;
        int y = ddY + ddH + 4;
        RenderHelper.roundedRect(ctx, ddX, y, ddW, h, R, SURFACE_HI);
        RenderHelper.roundedBorder(ctx, ddX, y, ddW, h, R, BORDER);
        for (int i = 0; i < modes.length; i++) {
            int iy = y + 3 + i * ih;
            boolean hover = inRect(mx, my, ddX, iy, ddW, ih);
            boolean sel = i == mode;
            if (sel) RenderHelper.roundedRect(ctx, ddX + 3, iy, ddW - 6, ih, 4, withAlpha(ACCENT, 0x26));
            else if (hover) RenderHelper.roundedRect(ctx, ddX + 3, iy, ddW - 6, ih, 4, 0x0DFFFFFF);
            ClubFont.drawList(ctx, modes[i], ddX + 10, iy + (ih - 13) / 2, sel ? ACCENT : hover ? TEXT : TEXT_SECOND, false);
        }
    }

    private void renderInput(DrawContext ctx) {
        RenderHelper.roundedRect(ctx, inX, inY, inW, inH, R, SURFACE_HI);
        RenderHelper.roundedBorder(ctx, inX, inY, inW, inH, R, userFocused ? ACCENT : BORDER);
        int tx = inX + 10, ty = inY + (inH - 13) / 2;
        if (username.isEmpty() && !userFocused) {
            ClubFont.drawList(ctx, "Enter name", tx, ty, TEXT_SECOND, false);
        } else {
            ClubFont.drawList(ctx, username, tx, ty, TEXT, false);
            if (userFocused) {
                int caretX = tx + ClubFont.widthList(username) + 1;
                ctx.fill(caretX, inY + 6, caretX + 1, inY + inH - 6, ACCENT);
            }
        }
    }

    private void renderButton(DrawContext ctx, int mx, int my) {
        boolean hover = inRect(mx, my, btnX, btnY, btnW, btnH);
        int fill = btnPressed ? withAlpha(ACCENT, 0xCC) : hover ? ACCENT_HI : ACCENT;
        RenderHelper.roundedRect(ctx, btnX, btnY, btnW, btnH, R, fill);
        String s = "Save Changes";
        ClubFont.drawList(ctx, s, btnX + (btnW - ClubFont.widthList(s)) / 2, btnY + (btnH - 13) / 2, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // dropdown option click first (it overlays)
        if (ddOpen) {
            int ih = 24, y = ddY + ddH + 4;
            for (int i = 0; i < modes.length; i++) {
                int iy = y + 3 + i * ih;
                if (inRect(mx, my, ddX, iy, ddW, ih)) { mode = i; ddOpen = false; return true; }
            }
            ddOpen = false; // click outside closes
        }
        if (inRect(mx, my, ddX, ddY, ddW, ddH)) { ddOpen = !ddOpen; userFocused = false; return true; }
        if (inRect(mx, my, togHitX, togCy - 12, 22, 24)) { enable = !enable; return true; }
        if (onSliderTrack(mx, my)) { draggingSlider = true; setSlider(mx); return true; }
        if (inRect(mx, my, inX, inY, inW, inH)) { userFocused = true; return true; }
        if (inRect(mx, my, btnX, btnY, btnW, btnH)) { btnPressed = true; return true; }
        userFocused = false;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSlider) { setSlider(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingSlider = false;
        btnPressed = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (userFocused && chr >= 32 && chr != 127 && username.length() < 24) {
            username += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (userFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!username.isEmpty()) username = username.substring(0, username.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                userFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean onSliderTrack(double mx, double my) {
        return mx >= slTrackX0 - 8 && mx <= slTrackX1 + 8 && my >= slCy - 10 && my <= slCy + 10;
    }

    private void setSlider(double mx) {
        float f = (float) ((mx - slTrackX0) / Math.max(1, slTrackX1 - slTrackX0));
        strength = Math.max(0f, Math.min(1f, f));
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ------------------------------------------------------------- aa helpers

    /** Anti-aliased filled circle (per-pixel coverage) — smooth dots/knobs. */
    private static void aaCircle(DrawContext ctx, double cx, double cy, double r, int color) {
        int baseA = (color >>> 24) & 0xFF, rgb = color & 0xFFFFFF;
        int x0 = (int) Math.floor(cx - r - 1), x1 = (int) Math.ceil(cx + r + 1);
        int y0 = (int) Math.floor(cy - r - 1), y1 = (int) Math.ceil(cy + r + 1);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                double cov = r - Math.sqrt(dx * dx + dy * dy) + 0.5;
                if (cov <= 0) continue;
                if (cov > 1) cov = 1;
                int a = (int) Math.round(baseA * cov);
                if (a <= 0) continue;
                ctx.fill(x, y, x + 1, y + 1, (a << 24) | rgb);
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    @Override
    public boolean shouldPause() { return false; }
}
