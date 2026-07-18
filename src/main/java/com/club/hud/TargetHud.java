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

    // Hit Distance HUD (com.club.ui.hud.HitDistanceElement): the same crosshair raycast, but held out to a
    // longer 5.0 so the readout can turn RED past the 3.0 reach — a target capped at the Target chip's 4.0
    // could never read out-of-range. Its own held target + distance + grace, independent of the chip above.
    private static LivingEntity hitEntity;
    private static double hitDist;    // eye → target bounding-box centre, in blocks (stable; never under-reports)
    private static long hitSeenAt;

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
        if (hitEntity != null && (mc.world == null || hitEntity.getWorld() != mc.world)) hitEntity = null;
        //?} else {
        /*if (current != null && (mc.world == null || current.getEntityWorld() != mc.world)) current = null;
        if (hitEntity != null && (mc.world == null || hitEntity.getEntityWorld() != mc.world)) hitEntity = null;*/
        //?}
        // Nobody is watching: don't ray the world. This ran every frame regardless of whether the Target
        // chip was even switched on — a world raycast + entity-box sweep that no one was going to read
        // (Stage 59). A player who turns BOTH readouts off now pays nothing for it.
        ClubConfig.Hud hud = ClubConfig.get().hud;
        if (!hud.target && !hud.hitDistance) { current = null; hitEntity = null; return; }
        long now = System.currentTimeMillis();
        // ONE raycast for both readouts, out to the longer range either of them needs (5.0 for Hit Distance;
        // the Target chip re-applies its own 4.0 cap below, so its behaviour is unchanged). The ray returns
        // the NEAREST hittable living entity; each consumer then decides if it's within its own reach.
        Entity camera = mc.getCameraEntity();
        Vec3d start = camera != null ? camera.getCameraPosVec(tickDelta) : null;
        EntityHitResult hit = raycastTarget(mc, tickDelta, 5.0);
        LivingEntity raw = (hit != null && hit.getEntity() instanceof LivingEntity le) ? le : null;
        // Target's acceptance uses the RAY-HIT distance — exactly the criterion the old 4.0-reach raycast
        // applied — so the chip appears/disappears at the same moment it always did (the ray-hit point is on
        // the .expand(1.0) box, so it is the generous distance vanilla-crosshair semantics already used).
        double rayHitDist = (hit != null && start != null) ? start.distanceTo(hit.getPos()) : Double.MAX_VALUE;
        // Hit Distance's READOUT uses eye → bounding-box CENTRE: stable (independent of where on the hitbox
        // the ray crossed) and it never UNDER-reports, so green ("you can reach") is the conservative side.
        double centerDist = (raw != null && start != null)
                ? start.distanceTo(raw.getBoundingBox().getCenter()) : Double.MAX_VALUE;

        // Target chip: unchanged — accept only within the fixed 4.0 ray reach, else hold through the grace.
        if (hud.target) {
            if (raw != null && rayHitDist <= 4.0) { current = raw; lastSeenAt = now; }
            else if (current != null && (now - lastSeenAt > GRACE_MS || !current.isAlive() || current.isRemoved()))
                current = null;
        } else current = null;

        // Hit Distance readout: gate on the CENTRE distance (not just the ray hit), so the number never
        // exceeds the 5.0 it promises — a wide entity's near face can sit within 5.0 while its centre is past
        // it. Held through the same grace window (crosshair-slip forgiveness) so a target sweeping in and out
        // does not strobe between X.XX and 0.00.
        if (hud.hitDistance) {
            if (raw != null && centerDist <= 5.0) { hitEntity = raw; hitDist = centerDist; hitSeenAt = now; }
            else if (hitEntity != null && (now - hitSeenAt > GRACE_MS || !hitEntity.isAlive() || hitEntity.isRemoved()))
                hitEntity = null;
        } else hitEntity = null;
    }

    /** The frame's crosshair target (or the grace-held one), or null. Stable within a frame. */
    public static LivingEntity current() { return current; }

    // Harness-only override for the Hit Distance readout (dev instrument): the raycast can't be aimed at a
    // known distance from a self-driving test, so the harness pins entity+distance to screenshot the green
    // and red states deterministically. Inert in production — nothing outside com.club.harness sets it.
    private static LivingEntity harnessHit;
    private static double harnessHitDist;
    private static boolean harnessActive;
    public static void harnessHit(LivingEntity e, double dist) { harnessHit = e; harnessHitDist = dist; harnessActive = true; }
    public static void clearHarnessHit() { harnessActive = false; harnessHit = null; }

    /** The Hit Distance readout's held target (≤5.0, grace-held), or null when nothing is aimed at. */
    public static LivingEntity hitEntity() { return harnessActive ? harnessHit : hitEntity; }

    /** Distance in blocks (eye → target bounding-box centre) of {@link #hitEntity()}. Only meaningful when
     *  {@code hitEntity() != null}; the element shows {@code 0.00} otherwise. */
    public static double hitDistance() { return harnessActive ? harnessHitDist : hitDist; }

    /** Drop the held targets. Called on disconnect — the HUD callback stops firing outside a world, so
     *  without this the static references would pin the unloaded ClientWorld at the title screen. */
    public static void clear() { current = null; hitEntity = null; }

    private static EntityHitResult raycastTarget(MinecraftClient mc, float tickDelta, double reach) {
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.world == null) return null;
        // Reach is a fixed argument, NOT a setting (owner, v0.1.3 #10: "убери настройку дистанции, засчитают
        // как софт"). A configurable detection range is a soft cheat: crank it up and the HUD names an
        // opponent long before you could touch them. The Target chip passes 4.0 (just past vanilla's 3-block
        // attack reach); Hit Distance passes 5.0 so its readout can go red past reach. hud.targetDistance
        // stays in the config for old files, but nothing reads it and nothing writes it any more.
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
        // The predicate guarantees a non-null hit's entity is a LivingEntity — the caller casts it safely.
        return ProjectileUtil.raycast(camera, start, end, box,
                e -> e instanceof LivingEntity && e != camera && !e.isSpectator() && e.isAlive() && e.canHit()
                        && !(e instanceof ArmorStandEntity) && !e.isInvisible(),
                maxSq);
    }
}
