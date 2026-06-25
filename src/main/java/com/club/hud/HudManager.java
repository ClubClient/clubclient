package com.club.hud;

import com.club.config.ClubConfig;
import com.club.modules.screenstretch.ScreenStretchModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Registers and dispatches all Club HUD elements. */
public final class HudManager {
    private HudManager() {}

    public static void init() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            if (mc.currentScreen != null && mc.currentScreen.shouldPause()) return;

            ClubConfig cfg = ClubConfig.get();
            float tickDelta = tickCounter.getTickDelta(false);

            drawBlackBars(ctx, mc);

            if (cfg.hud.armor)   ArmorHud.render(ctx);
            if (cfg.hud.potions) PotionHud.render(ctx);
            if (cfg.hud.target)  TargetHud.render(ctx, tickDelta);
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