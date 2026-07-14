package com.club.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The one instrument in this workstream that cannot lie: how many entities the game ACTUALLY rendered this
 * frame. It is the first number in F3's "E: x/y", it is deterministic, and it is free of every problem the
 * frame-time measurement has — it does not care how fast the machine is, how loaded the GPU is, or whether
 * the driver is queuing.
 *
 * <p>Two things about it, both learned the hard way and both load-bearing for ClubBench:
 *
 * <ul>
 *   <li>{@code regularEntityCount} is incremented in {@code WorldRenderer.render} at offset 918 — AFTER the
 *       {@code EntityRenderDispatcher.shouldRender} gate at 796 and BEFORE the {@code renderEntity} call at
 *       1074. So a culler that cancels at {@code renderEntity} would work perfectly and be INVISIBLE to this
 *       counter. Cut at {@code shouldRender} instead, and the number moves.</li>
 *   <li>{@code blockEntityCount} is a DEAD FIELD in 1.21.1 — grep of all 8269 classes in the merged jar
 *       finds exactly one reference, and it only ever stores 0. F3's "B:" is always zero. Block entities
 *       need a counter of our own, incremented in our own mixin, because with Sodium installed the vanilla
 *       BE loop does not even run.</li>
 * </ul>
 */
@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
    @Accessor("regularEntityCount")
    int club$entitiesRendered();
}
