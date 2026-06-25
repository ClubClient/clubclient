package com.club.hud;

import com.club.config.ClubConfig;
import com.club.gui.Theme;
import com.club.util.ClubFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.StatusEffectSpriteManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.List;

/**
 * Active effects — vanilla effect sprite + name + countdown, no card. Column
 * (default) right-aligns the time on each line; horizontal lays the effects in
 * a row. Name and time are plain white for instant reading; only the timer
 * picks up a soft-red tint as the effect expires. No gradient on game info.
 */
public final class PotionHud {
    private PotionHud() {}

    private static final int ICON = 18, GAP = 8, MID = 18, ROW = 22, HGAP = 18;

    private static List<StatusEffectInstance> effects(MinecraftClient mc) {
        List<StatusEffectInstance> list = new ArrayList<>(mc.player.getStatusEffects());
        list.sort((a, b) -> Integer.compare(a.getDuration(), b.getDuration())); // expiring first
        return list;
    }

    /** Representative effects for the editor preview. */
    private static List<StatusEffectInstance> sampleEffects() {
        List<StatusEffectInstance> l = new ArrayList<>();
        l.add(new StatusEffectInstance(StatusEffects.STRENGTH, 30 * 20, 1));
        l.add(new StatusEffectInstance(StatusEffects.SPEED, 45 * 20, 1));
        l.add(new StatusEffectInstance(StatusEffects.RESISTANCE, 80 * 20, 0));
        return l;
    }

    private static int nameMax(List<StatusEffectInstance> fx) {
        int w = 0; for (StatusEffectInstance e : fx) w = Math.max(w, ClubFont.widthName(title(e))); return w;
    }
    private static int timeMax(List<StatusEffectInstance> fx) {
        int w = 0; for (StatusEffectInstance e : fx) w = Math.max(w, ClubFont.widthHud(time(e))); return w;
    }

    private static int[] sizeOf(ClubConfig.Hud cfg, List<StatusEffectInstance> fx) {
        if (fx.isEmpty()) return new int[]{0, 0};
        if (cfg.potionHorizontal) {
            int w = 0;
            for (StatusEffectInstance e : fx) w += ICON + 6 + ClubFont.widthName(title(e)) + 8 + ClubFont.widthHud(time(e)) + HGAP;
            return new int[]{Math.max(0, w - HGAP), ICON};
        }
        return new int[]{ICON + GAP + nameMax(fx) + MID + timeMax(fx), (fx.size() - 1) * ROW + ICON};
    }

    /** Content [width, height] for the live effects (0,0 if none) — used by the editor. */
    public static int[] size(ClubConfig.Hud cfg, MinecraftClient mc) { return sizeOf(cfg, effects(mc)); }

    /** Content [width, height] for the editor preview. */
    public static int[] sampleSize(ClubConfig.Hud cfg) { return sizeOf(cfg, sampleEffects()); }

    private static void drawTime(DrawContext ctx, StatusEffectInstance e, int x, int y) {
        // Timer: light grey, always — no coloured letters. The name leads (white);
        // the timer sits a step quieter. Soft dark shadow for legibility.
        ClubFont.drawHudShadow(ctx, time(e), x, y, Theme.TEXT_MUTED);
    }

    private static void renderAt(DrawContext ctx, ClubConfig.Hud cfg, List<StatusEffectInstance> fx) {
        StatusEffectSpriteManager sprites = MinecraftClient.getInstance().getStatusEffectSpriteManager();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cfg.potionX, cfg.potionY, 0);
        ctx.getMatrices().scale(cfg.potionScale, cfg.potionScale, 1f);

        if (cfg.potionHorizontal) {
            int x = 0;
            for (StatusEffectInstance e : fx) {
                Sprite sp = sprites.getSprite(e.getEffectType());
                ctx.drawSprite(x, 0, 0, ICON, ICON, sp);
                int tx = x + ICON + 6, ty = (ICON - 13) / 2;
                String name = title(e);
                ClubFont.drawNameShadow(ctx, name, tx, ty, Theme.TEXT);
                int timeX = tx + ClubFont.widthName(name) + 8;
                drawTime(ctx, e, timeX, ty);
                x = timeX + ClubFont.widthHud(time(e)) + HGAP;
            }
        } else {
            int totalW = ICON + GAP + nameMax(fx) + MID + timeMax(fx);
            int row = 0;
            for (StatusEffectInstance e : fx) {
                int y = row * ROW, ty = y + (ICON - 13) / 2;
                ctx.drawSprite(0, y, 0, ICON, ICON, sprites.getSprite(e.getEffectType()));
                ClubFont.drawNameShadow(ctx, title(e), ICON + GAP, ty, Theme.TEXT);
                drawTime(ctx, e, totalW - ClubFont.widthHud(time(e)), ty);
                row++;
            }
        }
        ctx.getMatrices().pop();
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        List<StatusEffectInstance> fx = effects(mc);
        if (fx.isEmpty()) return;
        renderAt(ctx, ClubConfig.get().hud, fx);
    }

    /** Editor preview at the configured position/scale. */
    public static void drawSample(DrawContext ctx) {
        renderAt(ctx, ClubConfig.get().hud, sampleEffects());
    }

    private static String title(StatusEffectInstance e) {
        String name = e.getEffectType().value().getName().getString();
        int amp = e.getAmplifier();
        return amp > 0 ? name + " " + roman(amp + 1) : name;
    }

    private static String time(StatusEffectInstance e) {
        if (e.isInfinite()) return "∞";
        int s = e.getDuration() / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static String roman(int n) {
        switch (n) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(n);
        }
    }
}
