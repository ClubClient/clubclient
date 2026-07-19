package com.club.combat;

import com.club.config.ClubConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Client-tick driver for the Hitboxes module: pushes the live config into {@link HitboxState} (which the
 * per-version render mixins read) and, only while enabled, resolves whether a PLAYER is under the crosshair
 * so the outline can switch colour ("On player" vs "Default").
 *
 * <p>The raycast mirrors {@link com.club.hud.TargetHud}'s crosshair ray — fixed 4-block reach (a configurable
 * range would be a soft cheat, owner) and blocks occlude the ray — but filters to players only. It is
 * version-blind: it passes a fixed end-of-tick partial ({@code 1.0f}), so it needs none of the
 * {@code getTickDelta}/{@code getTickProgress} guard, and every MC call it makes is stable across
 * 1.21.1..1.21.11 (the same set TargetHud uses with no guard).</p>
 */
public final class HitboxModule {
    private HitboxModule() {}

    /** Fixed reach, deliberately NOT a setting — see TargetHud (owner: a configurable range is a soft cheat). */
    private static final double REACH = 4.0;

    /** Registered once from {@link com.club.ClubClient} at [SEAM:init]. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(HitboxModule::tick);
    }

    private static void tick(MinecraftClient mc) {
        ClubConfig c = ClubConfig.get();
        HitboxState.enabled = c.hitboxes.enabled;
        HitboxState.cleanLines = c.hitboxes.cleanLines;
        HitboxState.argbA = HitboxColors.argb(c.hitboxes.colorA);
        HitboxState.argbB = HitboxColors.argb(c.hitboxes.colorB);
        HitboxState.lineWidth = c.hitboxes.lineWidth;
        // Cheap early-out: while disabled nobody reads crosshairOnPlayer and the ray is a wasted box sweep.
        HitboxState.crosshairOnPlayer = c.hitboxes.enabled && playerUnderCrosshair(mc);
    }

    /** True when the crosshair is on a live, visible player within reach (blocks occlude — no colour through
     *  walls). Mirrors {@link com.club.hud.TargetHud}'s ray, filtered to players and the camera excluded. */
    private static boolean playerUnderCrosshair(MinecraftClient mc) {
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.world == null || mc.player == null) return false;
        // End-of-tick partial: this runs at a tick boundary (20 Hz), so 1.0 is the entity's settled position.
        float tickDelta = 1.0f;
        Vec3d start = camera.getCameraPosVec(tickDelta);
        Vec3d dir = camera.getRotationVec(tickDelta);
        Vec3d end = start.add(dir.multiply(REACH));
        double maxSq = REACH * REACH;
        HitResult block = camera.raycast(REACH, tickDelta, false);
        if (block.getType() != HitResult.Type.MISS) maxSq = block.getPos().squaredDistanceTo(start);
        Box box = camera.getBoundingBox().stretch(dir.multiply(REACH)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(camera, start, end, box,
                e -> e instanceof PlayerEntity && e != camera && !e.isSpectator() && e.isAlive()
                        && e.canHit() && !e.isInvisible(),
                maxSq);
        return hit != null && hit.getEntity() instanceof PlayerEntity;
    }
}
