package com.club.mixin;

import com.club.config.ClubConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.BundleTooltipData;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;

/**
 * Shulker tooltip (v0.1.5): hover a shulker box and see what is inside it, as the same item grid vanilla
 * already draws for a bundle — no need to place the box and open it.
 *
 * <h2>Why this seam, and why it is the SAME on all three versions</h2>
 *
 * <p>The one thing measured to be identical across 1.21.1 / 1.21.8 / 1.21.11 is {@code ItemStack.getTooltipData()}
 * — {@code Optional<TooltipData>} on every one. Everything ELSE about tooltips forked hard: {@code TooltipComponent}
 * changed {@code getHeight}/{@code drawText}/{@code drawItems} between 1.21.1 and 1.21.8, and the render path
 * inverted at 1.21.5 (a hand-drawn component would risk dropping the client on an invalid pipeline). So Club draws
 * NOTHING. It hands back vanilla's own {@link BundleTooltipData}, and vanilla's {@code BundleTooltipComponent}
 * lays out the grid, each version with its own renderer. The version fork stays entirely inside Minecraft, where
 * it is already handled.</p>
 *
 * <p>A shulker's {@code getTooltipData()} is empty by default — the box's own text lines come from a different
 * path ({@code ContainerComponent.appendTooltip}). So this fills a hole rather than fighting vanilla for the slot.</p>
 *
 * <h2>What it triggers on</h2>
 *
 * <p>Any stack carrying a {@link DataComponentTypes#CONTAINER} — vanilla shulker boxes, and any modded item that
 * stores its contents there — gets the grid for free. Bundles are untouched: they store contents under a
 * different component and already have their own tooltip. Gated on {@code config.shulkerTooltip} (on by default),
 * checked first because {@code getTooltipData} runs on hover.</p>
 */
@Mixin(ItemStack.class)
public class MixinItemStackShulkerTooltip {
    @ModifyReturnValue(method = "getTooltipData", at = @At("RETURN"))
    private Optional<TooltipData> club$shulkerGrid(Optional<TooltipData> original) {
        // Only step in where vanilla had nothing to show and the feature is on. If vanilla already supplies
        // tooltip data for this stack (a real bundle), leave it exactly as it is.
        if (original.isPresent() || !ClubConfig.get().shulkerTooltip) return original;

        ContainerComponent container = ((ItemStack) (Object) this).get(DataComponentTypes.CONTAINER);
        if (container == null) return original;

        List<ItemStack> items = container.streamNonEmpty().toList();
        if (items.isEmpty()) return original;   // an empty box has nothing to preview

        return Optional.of(new BundleTooltipData(new BundleContentsComponent(items)));
    }
}
