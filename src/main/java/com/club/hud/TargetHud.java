package com.club.hud;

import com.club.config.ClubConfig;
import com.club.gui.Theme;
import com.club.util.ClubFont;
import com.club.util.Mth;
import com.club.util.RenderHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Entity under the crosshair — name, HP and one thin HP line. Built for instant
 * PvP readability: the name (white SemiBold) leads, the HP value (muted) sits
 * below it, and a single 2px accent line shows the health fraction (the only
 * accent; it turns soft-red when low). No coloured text, no gradient, no card.
 * Auto-positions to the right of the crosshair until dragged.
 */
public final class TargetHud {
    private TargetHud() {}

    private static final int NAME_Y = 0, HP_Y = 16, BAR_Y = 32, HEIGHT = 34, MIN_W = 72;
    private static final String SAMPLE_NAME = "PlayerName", SAMPLE_HP = "18.6 HP";

    private static int[] sizeOf(String name, String hp) {
        int w = Math.max(MIN_W, Math.max(ClubFont.widthName(name), ClubFont.widthHud(hp)));
        return new int[]{w, HEIGHT};
    }

    /** Representative box for the editor preview (no live target there). */
    public static int[] sampleSize() { return sizeOf(SAMPLE_NAME, SAMPLE_HP); }

    public static int autoX(MinecraftClient mc) { return mc.getWindow().getScaledWidth() / 2 + 16; }
    public static int autoY(MinecraftClient mc) { return mc.getWindow().getScaledHeight() / 2 - sampleSize()[1] / 2; }

    /** Sample card drawn at local origin — used by the HUD editor. */
    public static void drawSample(DrawContext ctx) {
        draw(ctx, SAMPLE_NAME, 18f, 20f);
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        LivingEntity target = raycastTarget(mc, tickDelta);
        if (target == null) return;

        ClubConfig.Hud cfg = ClubConfig.get().hud;
        int x = cfg.targetX >= 0 ? cfg.targetX : autoX(mc);
        int y = cfg.targetY >= 0 ? cfg.targetY : autoY(mc);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(cfg.targetScale, cfg.targetScale, 1f);
        draw(ctx, target.getName().getString(), target.getHealth(), target.getMaxHealth());
        ctx.getMatrices().pop();
    }

    /** Name + HP value + a single thin HP line, at local origin. */
    public static void draw(DrawContext ctx, String name, float hp, float maxHp) {
        String shown = name.length() > 18 ? name.substring(0, 17) + "…" : name;
        String hpText = trim(hp) + " HP";
        int w = Math.max(MIN_W, Math.max(ClubFont.widthName(shown), ClubFont.widthHud(hpText)));

        // Name and HP are plain text — white name leads, muted HP follows. Never a
        // gradient and never a coloured number.
        ClubFont.drawNameShadow(ctx, shown, 0, NAME_Y, Theme.TEXT);
        ClubFont.drawHudShadow(ctx, hpText, 0, HP_Y, Theme.TEXT_MUTED);

        // The only accent: one thin 2px HP line (soft-red when low).
        float frac = maxHp > 0 ? Mth.clamp(hp / maxHp, 0f, 1f) : 0f;
        RenderHelper.roundedRect(ctx, 0, BAR_Y, w, 2, 1, Theme.FILL_TRACK);
        int fw = Math.round(w * frac);
        if (fw > 0) RenderHelper.roundedRect(ctx, 0, BAR_Y, fw, 2, 1, frac < 0.30f ? Theme.STATE_LOW : Theme.ACCENT);
    }

    private static String trim(float v) {
        return (Math.abs(v - Math.round(v)) < 0.05f) ? String.valueOf(Math.round(v)) : String.format("%.1f", v);
    }

    private static LivingEntity raycastTarget(MinecraftClient mc, float tickDelta) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) return null;
        double reach = Mth.clamp(ClubConfig.get().hud.targetDistance, 3, 64);
        Vec3d start = camera.getCameraPosVec(tickDelta);
        Vec3d dir = camera.getRotationVec(tickDelta);
        Vec3d end = start.add(dir.multiply(reach));
        Box box = camera.getBoundingBox().stretch(dir.multiply(reach)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(camera, start, end, box,
                e -> e instanceof LivingEntity && e != camera && !e.isSpectator() && e.isAlive(),
                reach * reach);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
        return null;
    }
}
