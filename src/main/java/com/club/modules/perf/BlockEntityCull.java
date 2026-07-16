package com.club.modules.perf;

import com.club.config.ClubConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>So there is no slider and no clamp. TWO predicates, and the interesting one is the second.
 *
 * <p>PREDICATE ONE: a renderer that asked for anything other than the default 64 blocks, or that admits it
 * draws outside its block, is NOT OURS TO TOUCH. Beacon (256/true), StructureBlock (96/true), End gateway
 * (256/false) and piston (68/false) are all covered by that one disjunction, with no per-type blacklist to
 * keep in sync with the next Minecraft version.
 *
 * <p>PREDICATE TWO, AND WHY ONE WAS NOT ENOUGH: {@code minecraft} NAMESPACE ONLY. Read that list again — it
 * is an enumeration of VANILLA. Predicate one only catches a renderer that ANNOUNCES it is unusual, and the
 * evidence that the defaults mean "ordinary" is evidence gathered entirely from vanilla's own renderers. A
 * third-party BER is perfectly free to leave both methods at their defaults (64, false) and still draw a
 * laser to the sky, a hologram over the block, a wire to the next machine — and predicate one would wave it
 * through, and we would erase it, and the player would watch it pop out of existence with no idea it was us.
 * We have already shipped this exact class of bug once: the entity culler that measured -22% and was DELETED
 * because it erased phantoms in open sky (HANDOFF.md:66). On by default, behind a card that says "nothing
 * here changes the picture", it is not a bug we get to re-ship on somebody else's mod.
 *
 * <p>So the rule is: the type must be registered under {@code minecraft} AND the renderer that draws it must
 * itself be a vanilla class. Both, not either. A mod may legally hand its OWN renderer to a VANILLA type —
 * {@code BlockEntityRendererFactories.register(BlockEntityType.CHEST, Mine::new)} is an ordinary thing to do,
 * and it is how a mod draws a label or a hologram over an ordinary chest. Such a block entity is
 * {@code minecraft:chest} by namespace, so a namespace test alone waves it through, and predicate one waves it
 * through too, because the modder had no reason to declare anything unusual. We would erase it.
 *
 * <p>It costs nothing we measured — the -5.3% / -8.1% came from a bench scene built out of vanilla chests
 * ({@code ClubBench} seeds {@code Blocks.CHEST}), drawn by vanilla's own renderer, so every number survives
 * both predicates intact.
 *
 * <p>The exact guarantee, stated no stronger than the code delivers: we only delete a block entity whose type
 * is vanilla and whose renderer class is vanilla's. What we do NOT promise is that a mixin cannot make a
 * vanilla renderer draw somewhere new — that mixin is asking to be culled, and the honest answer is that it
 * should declare {@code rendersOutsideBoundingBox}, exactly as vanilla's own four unusual renderers do.
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

    /** Both maps are memoised because {@link #skip} runs once per block entity per FRAME: a registry lookup and
     *  a string compare, thousands of times a frame, is exactly the kind of cost a performance module must not
     *  add. Both are keyed on singletons (a registered type; a renderer class), so they are bounded by what the
     *  game registered and stop growing after warm-up. ConcurrentHashMap because Iris renders block entities
     *  from its own pass and a torn HashMap is a hang, not a wrong pixel. Both mapping functions capture
     *  nothing, so they are singletons too — nothing here allocates per frame. */
    private static final Map<BlockEntityType<?>, Boolean> VANILLA_TYPE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> VANILLA_RENDERER = new ConcurrentHashMap<>();

    /** True only for types registered under {@code minecraft}. Necessary, and on its own NOT sufficient — a mod
     *  may register its own renderer for a vanilla type. See {@link #isVanillaRenderer}. */
    private static boolean isVanillaType(BlockEntityType<?> type) {
        return VANILLA_TYPE.computeIfAbsent(type, t -> {
            Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(t);
            // An unregistered type has no namespace we could trust. Unknown → not ours. Fail open, as everywhere here.
            return id != null && Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace());
        });
    }

    /** True only when the class that will actually draw this block entity is one of Minecraft's own. This is the
     *  predicate the namespace test cannot give us: {@code BlockEntityRendererFactories.register} lets any mod
     *  put ITS renderer behind a VANILLA type, and that renderer is then free to draw a hologram over an
     *  ordinary chest while declaring nothing unusual. We ask who is holding the brush, not what is being
     *  painted. */
    private static boolean isVanillaRenderer(BlockEntityRenderer<?> r) {
        return VANILLA_RENDERER.computeIfAbsent(r.getClass(), c -> c.getName().startsWith("net.minecraft."));
    }

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

        // Hands off anything that is not vanilla, in BOTH senses: a vanilla type, drawn by a vanilla renderer.
        // A modded BER can draw a beam to the sky while leaving both methods below at their defaults, and the
        // predicate under this one would never know — and it can do that behind a vanilla type. See class javadoc.
        if (!isVanillaType(be.getType()) || !isVanillaRenderer(r)) return false;

        // Hands off anything that is not an ordinary, inside-its-own-block, 64-metre renderer.
        // rendersOutsideBoundingBox dropped its argument in 1.21.6 (measured: 1.21.5 still takes the
        // BlockEntity). The question is the same one — does this renderer draw outside its own block.
        //? if <1.21.6 {
        if (r.getRenderDistance() != 64 || r.rendersOutsideBoundingBox(be)) return false;
        //?} else {
        /*if (r.getRenderDistance() != 64 || r.rendersOutsideBoundingBox()) return false;*/
        //?}

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
