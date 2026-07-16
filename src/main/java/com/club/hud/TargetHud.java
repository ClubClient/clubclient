package com.club.hud;

import com.club.config.ClubConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Crosshair-target DATA source for the V2 Target HUD ({@code com.club.ui.hud.TargetElement}): raycasts the
 * living entity under the crosshair within the configured range. Data-only — rendering lives in V2 (the
 * legacy render layer was removed once the V2 HUD landed).
 *
 * <p>Stage 23: resolved ONCE per frame — {@link HudManager} calls {@link #frame} with the real partial tick
 * before the canvas renders; the element reads {@link #current}. Blocks occlude the ray (vanilla-crosshair
 * semantics — no targets through walls); armor stands (canHit() only rejects MARKER stands in 1.21.1, so
 * they're excluded explicitly), invisible entities, spectators and non-hittables are not targets. A short
 * grace window holds a just-lost target so a crosshair slipping off for a frame doesn't flicker the chip
 * (after the grace the canvas plays its normal fade-out).
 */
public final class TargetHud {
    private TargetHud() {}

    /** How long a lost target is held before the chip is allowed to fade (crosshair-slip forgiveness). */
    private static final long GRACE_MS = 200;

    private static LivingEntity current;
    private static long lastSeenAt;   // wall-clock ms of the last frame the raycast actually hit

    /** Resolve the crosshair target once for this frame. Called by {@link HudManager} before the HUD canvas
     *  lays out / renders, with the frame's real tick delta. */
    public static void frame(MinecraftClient mc, float tickDelta) {
        // a target from another world is never valid (dimension change / respawn) — drop it immediately
        // Entity.getWorld() was deleted in 1.21.9 and getEntityWorld() took over — and NOT as a rename:
        // getEntityWorld existed on Entity through 1.21.5, was GONE for 1.21.6..1.21.8, then came back in
        // 1.21.9 from a new interface (HeldItemContext) with a different intermediary. Neither name spans
        // every version, so this is a real fork, measured across all eleven mappings.
        //? if <1.21.9 {
        if (current != null && (mc.world == null || current.getWorld() != mc.world)) current = null;
        //?} else {
        /*if (current != null && (mc.world == null || current.getEntityWorld() != mc.world)) current = null;*/
        //?}
        // Nobody is watching: don't ray the world. This ran every frame regardless of whether the Target
        // chip was even switched on — a world raycast + entity-box sweep that no one was going to read
        // (Stage 59). A player who turns the chip off now pays nothing for it.
        if (!ClubConfig.get().hud.target) { current = null; return; }
        LivingEntity hit = raycastTarget(mc, tickDelta);
        long now = System.currentTimeMillis();
        if (hit != null) { current = hit; lastSeenAt = now; return; }
        // no hit this frame: hold the previous target through the grace window, then release it
        if (current != null && (now - lastSeenAt > GRACE_MS || !current.isAlive() || current.isRemoved()))
            current = null;
    }

    /** The frame's crosshair target (or the grace-held one), or null. Stable within a frame. */
    public static LivingEntity current() { return current; }

    /** Drop the held target. Called on disconnect — the HUD callback stops firing outside a world, so
     *  without this the static reference would pin the unloaded ClientWorld at the title screen. */
    public static void clear() { current = null; }

    private static LivingEntity raycastTarget(MinecraftClient mc, float tickDelta) {
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.world == null) return null;
        // Fixed reach, NOT a setting (owner, v0.1.3 #10: "убери настройку дистанции, засчитают как софт").
        // A configurable detection range is a soft cheat: crank it up and the HUD names an opponent long
        // before you could touch them. 4 blocks is just past vanilla's 3-block attack reach — the chip
        // appears the moment a target is within a step of being hittable, and no further. hud.targetDistance
        // stays in the config for old files, but nothing reads it and nothing writes it any more.
        double reach = 4.0;
        Vec3d start = camera.getCameraPosVec(tickDelta);
        Vec3d dir = camera.getRotationVec(tickDelta);
        Vec3d end = start.add(dir.multiply(reach));
        // vanilla-crosshair semantics: the first block hit clamps the entity ray — no targets through walls
        double maxSq = reach * reach;
        HitResult block = camera.raycast(reach, tickDelta, false);
        if (block.getType() != HitResult.Type.MISS) maxSq = block.getPos().squaredDistanceTo(start);
        Box box = camera.getBoundingBox().stretch(dir.multiply(reach)).expand(1.0);
        // canHit() alone keeps regular armor stands (it only rejects MARKER stands), so they're excluded
        // explicitly; invisible entities are skipped too — the HUD must not reveal what the eye can't see.
        EntityHitResult hit = ProjectileUtil.raycast(camera, start, end, box,
                e -> e instanceof LivingEntity && e != camera && !e.isSpectator() && e.isAlive() && e.canHit()
                        && !(e instanceof ArmorStandEntity) && !e.isInvisible(),
                maxSq);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
        return null;
    }
}
