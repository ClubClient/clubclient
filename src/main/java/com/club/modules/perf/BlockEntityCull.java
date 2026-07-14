package com.club.modules.perf;

import com.club.config.ClubConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Block entities that are inside a visible chunk section but off the screen.
 *
 * <p>WHAT VANILLA ACTUALLY DOES — the brief's premise was wrong and the bytecode said so:
 * {@code BlockEntityRenderDispatcher.render} already distance-culls, via
 * {@code BlockEntityRenderer.isInRenderDistance} (default 64 blocks). What it does NOT do is test the block
 * entity itself against the frustum: only the 16x16x16 SECTION is frustum-culled, so a chest in a visible
 * section but behind your head is rendered in full — model, animation, light lookup, every frame.
 *
 * <p>THE RULE THAT KEEPS THE BEACON: HANDS OFF ANYTHING UNUSUAL. The brief said "effective distance =
 * min(renderer's own distance, our slider) — we can only tighten, so we cannot break a beacon". That
 * sentence is self-refuting: min(256, 32) = 32, and a beacon 200 blocks away then fails the test and its
 * beam disappears. TIGHTENING IS THE BREAKAGE. And {@code rendersOutsideBoundingBox} is not the shield it
 * looks like either — exactly two vanilla renderers return true (Beacon, StructureBlock), while the End
 * gateway draws a beam to the world ceiling and a piston draws a block a metre from its own position, and
 * both return FALSE.
 *
 * <p>So there is no slider and no clamp. One predicate: a renderer that asked for anything other than the
 * default 64 blocks, or that admits it draws outside its block, is NOT OURS TO TOUCH. Beacon (256/true),
 * StructureBlock (96/true), End gateway (256/false) and piston (68/false) are all covered by that one
 * disjunction, with no per-type blacklist to keep in sync with the next Minecraft version.
 *
 * <p>IRIS. This dispatcher IS the seam Iris's {@code ShadowRenderer.renderBlockEntities} calls. A cull keyed
 * to the MAIN camera's frustum, firing during the shadow pass, deletes shadows. {@link IrisCompat} is asked
 * first, and it fails closed.
 */
public final class BlockEntityCull {
    private BlockEntityCull() {}

    /** Deterministic, machine-independent, and the only thing the benchmark asserts on. `blockEntityCount`
     *  is a DEAD FIELD in 1.21.1 (written 0, read by F3, incremented nowhere), so we keep our own. */
    private static long considered, skipped;

    public static long considered() { return considered; }
    public static long skipped() { return skipped; }
    public static void resetCounters() { considered = skipped = 0; }

    public static volatile boolean forceOff;

    public static boolean enabled() {
        ClubConfig.Perf p = ClubConfig.get().perf;
        return p != null && p.cullBlockEntities && !forceOff;
    }

    public static <E extends BlockEntity> boolean skip(BlockEntityRenderDispatcher dispatcher, E be) {
        if (!enabled()) return false;
        if (IrisCompat.inShadowPass()) return false;   // the sun's view is not the player's

        BlockEntityRenderer<E> r = dispatcher.get(be);
        if (r == null) return false;

        considered++;

        // Hands off anything that is not an ordinary, inside-its-own-block, 64-metre renderer.
        if (r.getRenderDistance() != 64 || r.rendersOutsideBoundingBox(be)) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return false;
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();

        Frustum f = MainFrustum.current(camPos);
        if (f == null) return false;   // no trustworthy frustum this frame → cull nothing. Fail open.

        BlockPos p = be.getPos();
        // The block's own box, with a little slack: a chest lid swings, a sign's text sits proud of the post.
        Box box = new Box(p.getX() - 0.5, p.getY() - 0.5, p.getZ() - 0.5,
                          p.getX() + 1.5, p.getY() + 1.5, p.getZ() + 1.5);
        if (f.isVisible(box)) return false;

        skipped++;
        return true;
    }
}
