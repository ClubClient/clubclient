package com.club.hud;

import com.club.config.ClubConfig;
import com.club.util.Mth;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Crosshair-target DATA source for the V2 Target HUD ({@code com.club.ui.hud.TargetElement}): raycasts the
 * living entity under the crosshair within the configured range. Data-only — rendering lives in V2 (the
 * legacy render layer was removed once the V2 HUD landed).
 */
public final class TargetHud {
    private TargetHud() {}

    public static LivingEntity raycastTarget(MinecraftClient mc, float tickDelta) {
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
