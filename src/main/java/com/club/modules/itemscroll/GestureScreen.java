package com.club.modules.itemscroll;

import com.club.ui.Color;
import com.club.ui.Ui;
import com.club.ui.UiRenderer;
import com.club.ui.component.Component;
import com.club.ui.component.Container;
import com.club.ui.component.FocusManager;
import com.club.ui.component.UiContextImpl;
import com.club.ui.component.widget.Button;
import com.club.ui.layout.Size;
import com.club.ui.text.Align;
import com.club.ui.text.TextStyle;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The gesture editor: seven rows, one gesture each, and no way to end up with an ambiguous layout.
 *
 * <p>It lives in the module's package rather than {@code com.club.ui.menu} — that package is frozen, and
 * a screen is not a reason to thaw it. It READS the shared UI (widgets, tokens, canvas), which is what
 * that layer is for; it changes none of it. The precedent is the HUD editor: a settings row that opens a
 * screen of its own.</p>
 *
 * <p><b>The arming-click trap.</b> Arming a row is itself a left click, so a capture that starts on the
 * arming press binds every row to LMB the instant you touch it. Club's widget layer already answers this:
 * {@code Control} commits on RELEASE (press captures, release-inside activates), so a row is armed by the
 * release of the arming click and the next PRESS is genuinely the player's gesture. This is load-bearing —
 * a future Control that fired on press would resurrect the bug — so the harness pins it: it arms a row
 * with a real click and then demands the row still says "Press a key or scroll…" rather than "Left Click".</p>
 */
public final class GestureScreen extends Screen {

    private static final float SHEET_W = 372, ROW_H = 36, HEAD_H = 46, FOOT_H = 44,
                               PAD = 18, CHIP_W = 150, CHIP_H = 22;
    private static final int SCRIM = 0x9006090C;    // the menu's own ~56% ink veil — one grammar, one screen
    private static final float NOTICE_HOLD = 3.5f;  // a "taken from…" line is news, not furniture

    private final Screen parent;
    private final UiContextImpl uiCtx = new UiContextImpl();
    private final FocusManager focus = new FocusManager();
    private final long start = System.nanoTime();
    private final Pane rows = new Pane();
    private final Map<ScrollAction, Button> chips = new EnumMap<>(ScrollAction.class);

    /** The row waiting for a gesture, or null. */
    private ScrollAction capturing;
    /** The release belonging to a press we already turned into a gesture — it goes nowhere. */
    private boolean swallowRelease;

    // A transient line under one row: what we just took, or why we refused.
    private ScrollAction noticeRow;
    private String noticeText;
    private boolean noticeWarn;
    private float noticeAt;

    private float canvasW = 960, canvasH = com.club.ui.ClubCanvas.HEIGHT, canvasK = 1;
    private float sheetX, sheetY, sheetH;
    private TextStyle stTitle, stHint, stAction, stCaption, stWarn;

    public GestureScreen(Screen parent) {
        super(Text.literal("Item Scroll — Controls"));
        this.parent = parent;
    }

    @Override protected void init() {
        updateCanvas();
        build();
    }

    private void updateCanvas() {
        MinecraftClient mc = MinecraftClient.getInstance();
        canvasK = com.club.ui.ClubCanvas.scale(mc);
        canvasW = com.club.ui.ClubCanvas.width(mc);
        canvasH = com.club.ui.ClubCanvas.HEIGHT;
    }

    private double cx(double v) { return v / canvasK; }

    private void build() {
        rows.clear(); chips.clear(); focus.clear();
        sheetH = HEAD_H + ScrollAction.values().length * ROW_H + FOOT_H;
        sheetX = (canvasW - SHEET_W) / 2f;
        sheetY = (canvasH - sheetH) / 2f;

        float y = sheetY + HEAD_H;
        for (ScrollAction action : ScrollAction.values()) {
            Button chip = new Button(chipLabel(action)).variant(Button.Variant.GHOST).compact()
                    .minWidth(CHIP_W)
                    .onClick(() -> arm(action));
            chip.layout(sheetX + SHEET_W - PAD - CHIP_W, y + (ROW_H - CHIP_H) / 2f - 4, CHIP_W, CHIP_H);
            chips.put(action, chip);
            rows.add(chip);
            focus.register(chip);
            y += ROW_H;
        }

        Button done = new Button("Done").variant(Button.Variant.GHOST)
                .onClick(this::close);
        done.layout(sheetX + SHEET_W - PAD - 86, sheetY + sheetH - FOOT_H + 8, 86, 26);
        rows.add(done);
        focus.register(done);
    }

    private String chipLabel(ScrollAction action) {
        if (capturing == action) return "Press a key or scroll…";
        Gesture g = ItemScrollBinds.get(action);
        return g == null ? "Not set" : g.label();
    }

    // ---- capture ----------------------------------------------------------------------------------

    /** Called from the chip's onClick — which Control fires on RELEASE, so the arming click is already
     *  spent by the time we get here and the next press is the player's gesture. */
    private void arm(ScrollAction action) {
        capturing = action;
        clearNotice();
        refreshChips();
    }

    private void disarm() {
        capturing = null;
        refreshChips();
    }

    /**
     * The gesture the player just performed lands here. Three answers: refuse it and say why, take it (and
     * name whoever it was taken from), or ignore an input this action cannot carry.
     */
    private void capture(Gesture gesture) {
        ScrollAction action = capturing;
        if (action == null) return;

        if (Gestures.reserved(gesture)) {
            notice(action, "That's how you pick items up", true);   // bare LMB/RMB: bricking the inventory is not a setting
            disarm();
            return;
        }
        if (!Gestures.allowed(action, gesture)) {
            notice(action, "A drag needs a button to hold", true);  // the wheel cannot express one
            disarm();
            return;
        }
        ScrollAction stolenFrom = ItemScrollBinds.set(action, gesture);
        if (stolenFrom != null) notice(action, "Taken from " + stolenFrom.label(), false);
        else clearNotice();
        disarm();
    }

    private void notice(ScrollAction row, String text, boolean warn) {
        noticeRow = row; noticeText = text; noticeWarn = warn; noticeAt = uiCtx.time();
    }

    private void clearNotice() { noticeRow = null; noticeText = null; }

    private void refreshChips() {
        for (Map.Entry<ScrollAction, Button> e : chips.entrySet())
            e.getValue().label(chipLabel(e.getKey())).armed(capturing == e.getKey());
    }

    private int mods() {
        return Gesture.mods(hasShiftDown(), hasControlDown(), hasAltDown());
    }

    // ---- input ------------------------------------------------------------------------------------

    @Override public boolean mouseClicked(double mxMc, double myMc, int button) {
        double mx = cx(mxMc), my = cx(myMc);

        if (capturing != null) {
            swallowRelease = true;   // this press became a gesture; its release must not click anything
            GestureInput input = switch (button) {
                case GLFW_MOUSE_BUTTON_LEFT   -> GestureInput.LMB;
                case GLFW_MOUSE_BUTTON_RIGHT  -> GestureInput.RMB;
                case GLFW_MOUSE_BUTTON_MIDDLE -> GestureInput.MMB;
                default -> null;     // a side button is not a gesture — but it does not fall through either
            };
            if (input != null) capture(new Gesture(mods(), input));
            return true;
        }

        focus.clickFocus(mx, my);
        if (rows.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mxMc, myMc, button);
    }

    @Override public boolean mouseReleased(double mxMc, double myMc, int button) {
        if (swallowRelease) { swallowRelease = false; return true; }
        return rows.mouseReleased(cx(mxMc), cx(myMc), button) || super.mouseReleased(mxMc, myMc, button);
    }

    @Override public boolean mouseScrolled(double mxMc, double myMc, double horizontal, double vertical) {
        if (capturing != null && vertical != 0) {
            capture(new Gesture(mods(), GestureInput.SCROLL));   // one input, both directions — direction is the action's argument
            return true;
        }
        return super.mouseScrolled(mxMc, myMc, horizontal, vertical);
    }

    @Override public boolean keyPressed(int key, int scancode, int modifiers) {
        if (capturing != null) {
            if (key == GLFW_KEY_ESCAPE) { disarm(); return true; }
            if (key == GLFW_KEY_DELETE || key == GLFW_KEY_BACKSPACE) {
                ItemScrollBinds.clear(capturing);
                disarm();
                return true;
            }
            return true;   // while armed, the keyboard is not driving anything else
        }
        if (key == GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    /** Where a row's chip actually sits, in Minecraft units — so the harness can click the REAL button
     *  (and walk into the arming-click trap if it ever comes back) instead of poking at private state. */
    public double[] chipCentre(ScrollAction action) {
        int row = action.ordinal();
        double x = sheetX + SHEET_W - PAD - CHIP_W / 2f;
        double y = sheetY + HEAD_H + row * ROW_H + (ROW_H - CHIP_H) / 2f - 4 + CHIP_H / 2f;
        return new double[] { x * canvasK, y * canvasK };
    }

    // ---- paint ------------------------------------------------------------------------------------

    @Override public void renderBackground(DrawContext dc, int mx, int my, float delta) { }

    @Override public void render(DrawContext dc, int mxMc, int myMc, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        updateCanvas();
        int mx = Math.round(mxMc / canvasK), my = Math.round(myMc / canvasK);

        com.club.compat.Mtx.push(dc);
        com.club.compat.Mtx.scale(dc, canvasK);
        Ui.beginFrame(dc, canvasK);
        try {
            UiRenderer r = Ui.renderer();
            if (stTitle == null) initStyles();
            uiCtx.setTime((System.nanoTime() - start) / 1_000_000_000f);
            if (noticeText != null && !noticeWarn && uiCtx.time() - noticeAt > NOTICE_HOLD) clearNotice();

            r.rect(0, 0, canvasW, canvasH, SCRIM);

            float rad = Tokens.radius().md();
            r.roundedRect(sheetX, sheetY, SHEET_W, sheetH, rad, Tokens.surface().surface());
            r.border(sheetX, sheetY, SHEET_W, sheetH, rad, Tokens.border().thickness(), Tokens.border().defaultColor());

            // header: the accent tick + the title, exactly as the menu's popover states itself
            r.roundedRect(sheetX + PAD, sheetY + 19, 6, 6, 2, Tokens.accent().accent());
            uiCtx.text().draw("Item Scroll — Controls", sheetX + PAD + 14, sheetY + 15, stTitle);

            float y = sheetY + HEAD_H;
            for (ScrollAction action : ScrollAction.values()) {
                uiCtx.text().draw(action.label(), sheetX + PAD, y + 4, stAction);

                // the caption line under the name: what vanilla already does with this gesture, or what we
                // just did to another row. Club names what it takes — silence here is how a mod earns
                // "it broke my shift-click".
                String caption = null;
                boolean warn = false;
                if (noticeRow == action && noticeText != null) {
                    caption = noticeText; warn = noticeWarn;
                } else {
                    String vanilla = Gestures.vanilla(ItemScrollBinds.get(action));
                    if (vanilla != null) { caption = "Overrides: " + vanilla; warn = true; }
                }
                if (caption != null)
                    uiCtx.text().draw(caption, sheetX + PAD, y + 18, warn ? stWarn : stCaption);

                y += ROW_H;
            }

            uiCtx.text().draw(capturing != null
                            ? "Scroll or click · Esc to cancel · Delete to remove"
                            : "Click a row to change it",
                    sheetX + PAD, sheetY + sheetH - FOOT_H + 14, stHint);

            rows.mouseMoved(mx, my);
            rows.render(uiCtx);
        } finally {
            Ui.endFrame();
            com.club.compat.Mtx.pop(dc);
        }
    }

    private void initStyles() {
        Typography t = Tokens.type();
        stTitle   = TextStyle.of(t.body().weight(), t.body().size(), Tokens.palette().textHi());
        stAction  = TextStyle.of(t.body().weight(), t.body().size(), Tokens.palette().textHi());
        stCaption = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textFaint());
        stWarn    = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().stateWarn());
        stHint    = TextStyle.of(t.label().weight(), t.label().size(), Tokens.palette().textDesc())
                .align(Align.LEFT);
    }

    /** A container that holds the rows' widgets and nothing else — the module's own, because the UI
     *  package is frozen and duplicating six lines is cheaper than thawing what the owner froze. */
    private static final class Pane extends Container {
        void add(Component c) { addChild(c); }
        void clear() { children().clear(); }
        @Override public Size measure(float availW, float availH) { return new Size(availW, availH); }
    }
}
