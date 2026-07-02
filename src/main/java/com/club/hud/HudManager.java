package com.club.hud;

import com.club.config.ClubConfig;
import com.club.modules.screenstretch.ScreenStretchModule;
import com.club.ui.Ui;
import com.club.ui.component.UiContextImpl;
import com.club.ui.hud.ArmorElement;
import com.club.ui.hud.EffectsElement;
import com.club.ui.hud.HudCanvas;
import com.club.ui.hud.InfoElement;
import com.club.ui.hud.TargetElement;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Registers and dispatches the Club HUD: the V2 HUD canvas (Effects / Target / Info / Armor) on the frozen
 * UI render stack. Armor draws its vanilla sprites through the {@link HudSprites} DrawContext seam. The
 * elements read the same {@link ClubConfig.Hud} positions/scales as the editor and hide when disabled or empty.
 */
public final class HudManager {
    private HudManager() {}

    // The in-world V2 HUD: a non-editor canvas (real data, hides disabled/empty elements), built once.
    private static final HudCanvas CANVAS = new HudCanvas(false)
            .add(new EffectsElement()).add(new TargetElement()).add(new InfoElement()).add(new ArmorElement());
    private static final UiContextImpl UI = new UiContextImpl();
    private static final long START = System.nanoTime();

    public static void init() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            if (mc.currentScreen != null && mc.currentScreen.shouldPause()) return;

            drawBlackBars(ctx, mc);

            // V2 HUD (Effects / Target / Info / Armor) — frozen UI stack; the canvas hides disabled/empty elements.
            Ui.beginFrame(ctx);
            UI.setTime((System.nanoTime() - START) / 1_000_000_000f);
            CANVAS.setScreen(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            CANVAS.layoutFromConfig(mc);
            CANVAS.render(UI);
        });
    }

    private static void drawBlackBars(DrawContext ctx, MinecraftClient mc) {
        if (!ScreenStretchModule.isActive() || !ScreenStretchModule.blackBars()) return;
        int gw = mc.getWindow().getScaledWidth();
        int gh = mc.getWindow().getScaledHeight();
        boolean[] vertical = {false};
        int bar = ScreenStretchModule.barThickness(gh, gw, vertical);
        if (bar <= 0) return;
        int black = 0xFF000000;
        if (vertical[0]) {
            ctx.fill(0, 0, bar, gh, black);
            ctx.fill(gw - bar, 0, gw, gh, black);
        } else {
            ctx.fill(0, 0, gw, bar, black);
            ctx.fill(0, gh - bar, gw, gh, black);
        }
    }
}