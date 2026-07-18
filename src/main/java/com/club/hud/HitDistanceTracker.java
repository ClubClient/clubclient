package com.club.hud;

import com.club.config.ClubConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * DATA source for the Hit Distance readout ({@code com.club.ui.hud.HitDistanceElement}): the distance at
 * which the player last LANDED an attack on an entity. Unlike the crosshair-continuous Target HUD, this
 * fires on the attack itself — {@link AttackEntityCallback}, which the client raises the moment you swing at
 * an in-reach entity — so the readout answers "how far away did that hit connect?" and nothing else.
 *
 * <p>The reading lives for {@link #WINDOW_MS} after the hit; the element hides once it lapses (and the canvas
 * fades it out). A fresh hit refreshes both the value and the clock. Data-only — the fade in/out and the
 * white paint live in the element.
 */
public final class HitDistanceTracker {
    private HitDistanceTracker() {}

    /** How long the last-hit reading stays on screen with no new hit (owner: 10 s), then it dissolves. */
    public static final long WINDOW_MS = 10_000;

    private static long lastHitAt;   // wall-clock ms of the last landed attack (0 = never)
    private static double lastDist;  // eye → nearest point of the struck entity's hitbox, in blocks

    /** Register the attack hook + disconnect cleanup. Called once from {@code ClubClient.init}. */
    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // CLIENT side only (singleplayer fires this on the integrated server too), and only while the
            // readout is switched on — no reason to measure a hit nobody will see.
            if (world.isClient() && entity != null && ClubConfig.get().hud.hitDistance)
                record(nearestDistance(player.getEyePos(), entity.getBoundingBox()));
            return ActionResult.PASS;   // we only observe the attack, never consume it
        });
        // The callback stops firing outside a world; drop the stale reading so a lingering hit from the last
        // session can't flash on the next world's first frame.
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> clear());
    }

    private static void record(double dist) { lastDist = dist; lastHitAt = System.currentTimeMillis(); }

    /** True while the last hit is still within its 10 s window — the element shows only then. */
    public static boolean hasHit(long now) { return within(now, lastHitAt); }

    /** Pure window test (unit-tested): a hit at {@code lastHitAt} is live at {@code now} until 10 s pass;
     *  {@code lastHitAt == 0} means "no hit yet". */
    public static boolean within(long now, long lastHitAt) { return lastHitAt != 0 && now - lastHitAt < WINDOW_MS; }

    /** Distance in blocks of the last landed hit (eye → nearest hitbox point). Meaningful only while {@link #hasHit}. */
    public static double distance() { return lastDist; }

    public static void clear() { lastHitAt = 0; lastDist = 0; }

    /** Eye → the NEAREST point of {@code box} (component-wise clamp): the true reach distance of the hit,
     *  not the inflated centre distance. Pure — unit-tested. */
    public static double nearestDistance(Vec3d eye, Box box) {
        double cx = clamp(eye.x, box.minX, box.maxX);
        double cy = clamp(eye.y, box.minY, box.maxY);
        double cz = clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceTo(new Vec3d(cx, cy, cz));
    }
    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }

    /** Harness seam (dev only): pin a hit at a known distance so the readout can be screenshotted without a
     *  live swing. Inert in production — nothing outside com.club.harness calls it. */
    public static void recordForHarness(double dist) { record(dist); }
}
