package com.club.mixin;

import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * One door onto {@code HungerManager.exhaustion} — the only piece of AppleSkin's data that vanilla does not
 * hand a client through a public getter. Opened for the Saturation module's food/health restoration preview.
 *
 * <p><b>Why an accessor, measured not assumed.</b> {@code getExhaustion()} is {@code public} on 1.21.1 but
 * was removed from the API on 1.21.6/1.21.8/1.21.11 (javap, 2026-07-25). The backing field
 * {@code private float exhaustion} survives on all four with the same name, so a single {@code @Accessor}
 * reads it uniformly — no per-version split, and one code path instead of a `//?` fork for the one version
 * that still has the getter.
 *
 * <p><b>The honest limit this cannot fix.</b> Reading the field is not the same as the field being true.
 * Vanilla never SYNCS exhaustion to a remote client ({@code HealthUpdateS2CPacket} carries only health, food
 * and saturation), so on a multiplayer server this reads ~0 regardless of the player's real exhaustion.
 * AppleSkin only shows a truthful exhaustion display because it ships a server-side companion that pushes the
 * value; Club is client-only.
 *
 * <p>Because of that limit, the module draws <b>no exhaustion line</b> (it could only ever be honest in
 * single-player, and a feature that appears and vanishes with the connection was judged worse than none). The
 * sole remaining reader is {@link com.club.modules.saturation.FoodHelper#estimatedHealthIncrement}, the
 * health-regen preview, where exhaustion is one input among health/food/saturation and degrades to ~0 on a
 * server — an approximation, exactly as AppleSkin's own preview behaves without its companion.
 */
@Mixin(HungerManager.class)
public interface MixinHungerManagerAccessor {
    @Accessor("exhaustion")
    float club$exhaustion();
}
