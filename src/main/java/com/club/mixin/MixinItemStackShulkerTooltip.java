package com.club.mixin;

import com.club.config.ClubConfig;
import com.club.tooltip.ShulkerTooltipData;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;

/**
 * Shulker tooltip (v0.1.5): hover a shulker box and see what is inside it, as a plain item grid — no need to
 * place the box and open it.
 *
 * <h2>Why this seam</h2>
 *
 * <p>{@code ItemStack.getTooltipData() -> Optional<TooltipData>} is identical across 1.21.1 / 1.21.8 / 1.21.11,
 * so this half is version-agnostic: fill the empty Optional with our {@link ShulkerTooltipData}. The RENDER
 * half forked hard ({@code TooltipComponent.getHeight}/{@code drawItems} and the count-overlay method all
 * changed at 1.21.8) and lives in {@code com.club.tooltip.ShulkerTooltipComponent}, on a single measured
 * {@code //?} boundary. It first shipped handing back vanilla's {@code BundleTooltipData} to dodge that fork
 * entirely, but the bundle renderer's chrome — dark slot cells, a fill bar, a "Full" label a box of 64-stacks
 * always tripped — was wrong for a shulker, so the grid is drawn by our own component now (through vanilla's
 * {@code DrawContext.drawItem}, so still no custom pipeline and no 1.21.5+ crash risk).</p>
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

        // Our OWN data, not vanilla's BundleTooltipData: the bundle type drags in the bundle renderer's chrome
        // (dark slot cells, a fill bar, a "Full" label that a box of 64-stacks always trips). ShulkerTooltipData
        // maps to ShulkerTooltipComponent — a plain grid — via MixinTooltipComponentOf.
        return Optional.of(new ShulkerTooltipData(items));
    }
}
