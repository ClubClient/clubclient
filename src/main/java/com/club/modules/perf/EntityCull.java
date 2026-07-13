package com.club.modules.perf;

import com.club.config.ClubConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Stop drawing the mobs the terrain is already hiding — for the players who do not run Sodium.
 *
 * <p>THE PREMISE IS NOT A GUESS. Vanilla frustum-culls entities and does not occlusion-cull them: in the
 * benchmark arena all 150 mobs are rendered every frame, INCLUDING the 75 that a solid stone wall hides
 * completely. That is the vanilla entity counter saying it, not us.
 *
 * <p>Every frame {@code WorldRenderer.applyFrustum} refills {@code builtChunks} with the sections the
 * occlusion graph kept. An entity whose box touches none of them cannot be seen. That is Sodium-grade
 * culling with no raycaster — and it is exactly what Sodium already does, which is why:
 *
 * <ul>
 *   <li><b>SODIUM IS A HARD GATE, NOT A COURTESY.</b> Sodium @Redirects the built-chunk storage to null
 *       ({@code nullifyBuiltChunkStorage}); our set would be permanently empty and we would cull EVERY
 *       ENTITY IN THE WORLD. Off when Sodium is present. Its own cull is better integrated anyway.</li>
 *   <li><b>THE EMPTY-SET SELF-CHECK.</b> Any other mod that owns the terrain path can do the same thing
 *       without announcing it. If the set is empty while the player stands in a loaded chunk, the culler
 *       DISABLES ITSELF for the session and says so once. An invisible-mobs bug reads like a malfunctioning
 *       cheat client, and it would be our fault.</li>
 *   <li><b>THE THREE ESCAPE HATCHES, COPIED FROM SODIUM.</b> A GLOWING mob is drawn THROUGH walls — that is
 *       the entire point of Glowing and of spectral arrows. A named mob shows its tag through terrain. And a
 *       very large entity (the dragon; a modded contraption) is drawn far outside anything we can reason
 *       about. Cull any of those three and you delete exactly the entities the player deliberately marked.</li>
 *   <li><b>NEVER THE CAMERA, THE RIDE, OR ANYTHING CLOSE.</b> Plus a 2-block margin, because mods render
 *       outside their bounding boxes and we are not going to win that argument.</li>
 * </ul>
 *
 * <p>IRIS. {@code EntityRenderDispatcher.shouldRender} is called by Iris's {@code ShadowRenderer} with the
 * SHADOW frustum — the recon claimed otherwise and the bytecode disagreed. Culling by the main camera's
 * visible sections while the shadow map is being drawn deletes the shadows of everything off-screen. Both
 * the capture and the cull are gated on {@link IrisCompat}, which fails closed.
 */
public final class EntityCull {
    private EntityCull() {}

    /** Slack, in blocks, on the entity's own box. Mods draw outside it; so does a leash. */
    private static final double MARGIN = 2.0;
    /** Never cull anything nearer than this. Cheap insurance against every edge case at once. */
    private static final double NEAR = 8.0;
    /** Sodium's own bail-out: an entity this big is not something a section test can reason about. */
    private static final double HUGE_VOLUME = 61440.0;

    private static final LongOpenHashSet VISIBLE = new LongOpenHashSet();
    private static boolean captured;

    private static long considered, culled;
    private static boolean disabledForSession;

    public static long considered() { return considered; }
    public static long culled() { return culled; }
    public static void resetCounters() { considered = culled = 0; }

    public static volatile boolean forceOff;

    /** Why the cull is doing nothing — the benchmark prints this instead of guessing. */
    public static String debug() {
        return "enabled=" + enabled() + " sodium=" + sodiumPresent() + " captured=" + captured
                + " visibleSections=" + VISIBLE.size() + " disabledForSession=" + disabledForSession
                + " cfg=" + (ClubConfig.get().perf != null && ClubConfig.get().perf.cullEntities);
    }

    /** Sodium's cull is this cull. Running both is a fight we would lose and a risk we would ship. */
    private static Boolean sodium;
    public static boolean sodiumPresent() {
        if (sodium == null) sodium = FabricLoader.getInstance().isModLoaded("sodium");
        return sodium;
    }

    public static boolean enabled() {
        if (disabledForSession || forceOff || sodiumPresent()) return false;
        ClubConfig.Perf p = ClubConfig.get().perf;
        return p != null && p.cullEntities;
    }

    /** The sections the occlusion graph kept this frame. Captured at applyFrustum, never in a shadow pass. */
    public static void sections(List<ChunkBuilder.BuiltChunk> chunks) {
        VISIBLE.clear();
        for (ChunkBuilder.BuiltChunk c : chunks) {
            var o = c.getOrigin();
            VISIBLE.add(ChunkSectionPos.asLong(o.getX() >> 4, o.getY() >> 4, o.getZ() >> 4));
        }
        captured = true;

        // THE SELF-CHECK — and the threshold matters as much as the rule.
        //
        // The rule: an empty visible-section set while the world is loaded means another mod owns the terrain
        // path and builtChunks is never being filled. Culling on that would make EVERY ENTITY IN THE WORLD
        // vanish, which reads to a player like a malfunctioning cheat client.
        //
        // The threshold: the first cut fired on the first empty frame — and every world starts that way,
        // because the occlusion graph has nothing in it until the first chunks are built. The culler shot
        // itself dead during the loading screen and reported "0 entities considered" for the rest of the
        // session. A guard that cannot tell "not yet" from "never" is not a guard, it is a fuse.
        if (VISIBLE.isEmpty()) emptyFrames++; else emptyFrames = 0;
        if (emptyFrames > EMPTY_LIMIT && !disabledForSession) {
            disabledForSession = true;
            System.err.println("[club.perf] the visible-section set has been empty for " + EMPTY_LIMIT
                    + " frames while the world is loaded — another mod owns the terrain path. "
                    + "Entity culling is OFF for this session.");
        }
    }

    /** Consecutive frames with nothing visible. Every world starts with a few; none has three hundred. */
    private static final int EMPTY_LIMIT = 300;
    private static int emptyFrames;

    /**
     * True if this entity is in no visible section and may be skipped. Called from the
     * {@code EntityRenderDispatcher.shouldRender} injection — the seam where cancelling ALSO moves vanilla's
     * own {@code regularEntityCount}, which is the only reason the benchmark can prove any of this.
     */
    public static boolean skip(Entity e) {
        if (!enabled()) return false;
        if (IrisCompat.inShadowPass()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.gameRenderer == null) return false;

        // Nothing captured yet (the loading screen), or nothing visible this frame: cull nothing. The
        // session-level self-check lives in sections(), where it can count consecutive empty frames instead
        // of firing on the first one — see the comment there.
        if (!captured || VISIBLE.isEmpty()) return false;

        considered++;

        var cam = mc.gameRenderer.getCamera();
        if (cam == null) return false;
        if (e == cam.getFocusedEntity()) return false;                 // never the camera's own entity
        if (e.ignoreCameraFrustum) return false;                       // it asked not to be culled
        if (e.hasPassenger(mc.player) || mc.player.hasPassenger(e)) return false;   // what you are riding

        // The three Sodium copies, and they are not optional: a GLOWING mob is drawn THROUGH walls, a named
        // mob shows its tag through terrain, and a huge entity is drawn far outside any box we could test.
        if (mc.hasOutline(e)) return false;
        if (e.shouldRenderName()) return false;

        Box box = e.getVisibilityBoundingBox();
        double volume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
        if (volume > HUGE_VOLUME) return false;

        Vec3d eye = cam.getPos();
        if (box.expand(NEAR).contains(eye)) return false;              // anything close: leave it alone

        if (anySectionVisible(box.expand(MARGIN))) return false;

        culled++;
        return true;
    }

    private static boolean anySectionVisible(Box box) {
        int x0 = ChunkSectionPos.getSectionCoord(box.minX), x1 = ChunkSectionPos.getSectionCoord(box.maxX);
        int y0 = ChunkSectionPos.getSectionCoord(box.minY), y1 = ChunkSectionPos.getSectionCoord(box.maxY);
        int z0 = ChunkSectionPos.getSectionCoord(box.minZ), z1 = ChunkSectionPos.getSectionCoord(box.maxZ);
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    if (VISIBLE.contains(ChunkSectionPos.asLong(x, y, z))) return true;
        return false;
    }
}
