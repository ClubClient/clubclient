package com.club.ui.devgallery;

import com.club.ui.Ui;
import com.club.ui.component.Component;
import com.club.ui.component.FocusManager;
import com.club.ui.component.UiContextImpl;
import com.club.ui.component.widget.Button;
import com.club.ui.component.widget.Card;
import com.club.ui.component.widget.Checkbox;
import com.club.ui.component.widget.Divider;
import com.club.ui.component.widget.Label;
import com.club.ui.component.widget.Panel;
import com.club.ui.component.widget.ScrollArea;
import com.club.ui.component.widget.Slider;
import com.club.ui.component.widget.Toggle;
import com.club.ui.component.widget.Window;
import com.club.ui.layout.Column;
import com.club.ui.layout.CrossAlign;
import com.club.ui.layout.Insets;
import com.club.ui.layout.Row;
import com.club.ui.layout.Sizing;
import com.club.ui.theme.Tokens;
import com.club.ui.theme.Typography;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.glfw.GLFW.*;

/**
 * TEMPORARY M2.2 dev gallery — visual acceptance of the new {@code com.club.ui} widgets at 1x and zoom.
 *
 * <p><b>Gated dev tool — NOT part of the client. DELETE before merge</b> (frozen §8.1): this whole
 * package and the {@code ClubClient} keybind that opens it are removed once acceptance is signed off.
 * Renders the new stack via {@link Ui#beginFrame} + {@link UiContextImpl} (no {@code DrawContext.fill}/
 * legacy paths); forwards input to a root {@link ScrollArea}. Click a widget to focus it (focus-ring),
 * Tab cycles focus, Space/Enter/arrows drive the focused control.
 */
public final class WidgetGalleryScreen extends Screen {

    private static boolean uiInit;
    private final UiContextImpl uiCtx = new UiContextImpl();
    private final FocusManager focus = new FocusManager();
    private final List<Component> focusables = new ArrayList<>();
    private final long startNanos = System.nanoTime();
    private Component root;

    public WidgetGalleryScreen() { super(Text.literal("Widget Gallery")); }

    @Override protected void init() {
        if (!uiInit) { Ui.init(); uiInit = true; }
        focusables.clear();
        focus.clear();
        root = buildRoot();
        for (Component c : focusables) focus.register(c);
        layoutRoot();
    }

    private void layoutRoot() {
        float m = Tokens.spacing().lg();
        root.layout(m, m, width - 2 * m, height - 2 * m);
    }

    // ---- content ---------------------------------------------------------------

    private Component buildRoot() {
        Column col = new Column()
                .gap(Tokens.spacing().lg())
                .padding(Insets.all(Tokens.spacing().xl()))
                .crossAlign(CrossAlign.STRETCH);

        col.add(title("Buttons"));
        col.add(rowGap(
                track(new Button("Primary")),
                track(new Button("Ghost").variant(Button.Variant.GHOST)),
                disabled(track(new Button("Disabled")))));

        col.add(title("Toggles"));
        col.add(rowGap(
                label("On"), track(new Toggle(true)),
                label("Off"), track(new Toggle(false)),
                disabled(track(new Toggle(true)))));

        col.add(title("Checkboxes"));
        col.add(rowGap(
                track(new Checkbox(true)), label("Checked"),
                track(new Checkbox(false)), label("Unchecked"),
                disabled(track(new Checkbox(true)))));

        col.add(title("Sliders"));
        col.add(track(new Slider(50, 0, 100, 1)));
        col.add(track(new Slider(0.5f, 0f, 1f, 0f).showValue(false)));
        col.add(disabled(track(new Slider(25, 0, 100, 5))));

        col.add(title("Panel / Card"));
        col.add(new Panel(new Label("Panel — flat grouping surface")).padding(Insets.all(Tokens.spacing().md())));
        col.add(new Card(new Label("Card content"))
                .header(new Label("Card header", Tokens.type().heading()))
                .footer(new Label("Card footer", Tokens.type().caption())));

        col.add(title("Window (chrome + content, elevation)"));
        Window win = new Window("Settings").size(360, 120)
                .content(new Panel(new Label("Window content")).padding(Insets.all(Tokens.spacing().md())));
        col.add(win);

        return new ScrollArea(col);
    }

    // ---- builders / helpers ----------------------------------------------------

    /** Register an interactive widget for focus, and return it for placement. */
    private <T extends Component> T track(T w) { focusables.add(w); return w; }

    private static <T extends Component> T disabled(T w) { w.enabled = false; return w; }

    private static Label label(String s) { return new Label(s); }

    private static Label title(String s) {
        Typography.Role r = Tokens.type().title();
        return new Label(s, r).color(Tokens.palette().textHi());
    }

    private static Component rowGap(Component... items) {
        Row row = new Row().gap(Tokens.spacing().md()).crossAlign(CrossAlign.CENTER);
        for (Component c : items) row.add(c);
        return row;
    }

    // ---- render ----------------------------------------------------------------

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.beginFrame(ctx);
        // opaque background (covers the world; shouldPause()==false) — drawn via the new stack, not ctx.fill
        Ui.renderer().rect(0, 0, width, height, Tokens.surface().bg0());

        uiCtx.setTime((System.nanoTime() - startNanos) / 1_000_000_000f);
        root.mouseMoved(mouseX, mouseY);   // keep hover fresh each frame
        root.render(uiCtx);
    }

    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { /* handled in render */ }

    // ---- input forwarding ------------------------------------------------------

    @Override public boolean mouseClicked(double mx, double my, int button) {
        focus.clickFocus(mx, my);
        return root.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        boolean h = root.mouseReleased(mx, my, button);
        return h || super.mouseReleased(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return root.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public void mouseMoved(double mx, double my) { root.mouseMoved(mx, my); }
    @Override public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        return root.mouseScrolled(mx, my, vert) || super.mouseScrolled(mx, my, horiz, vert);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW_KEY_TAB) { if ((mods & GLFW_MOD_SHIFT) != 0) focus.previous(); else focus.next(); return true; }
        return focus.keyPressed(key, scan, mods) || super.keyPressed(key, scan, mods);
    }
    @Override public boolean charTyped(char chr, int mods) {
        return focus.charTyped(chr, mods) || super.charTyped(chr, mods);
    }

    @Override public boolean shouldPause() { return false; }
}
