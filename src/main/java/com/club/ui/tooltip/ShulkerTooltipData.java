package com.club.ui.tooltip;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;

import java.util.List;

/**
 * The payload the shulker-tooltip mixin hands back instead of vanilla's {@link net.minecraft.item.tooltip.BundleTooltipData}.
 *
 * <p>{@link TooltipData} is a marker (no methods) and lives in a common package, so this is side-safe: it is
 * produced by the client-only {@code MixinItemStackShulkerTooltip}, but the type itself would load anywhere.
 * The reason it exists at all is that a bundle's data drags in the bundle's tooltip CHROME — the dark slot
 * cells, the fill bar and the "Full" label, all wrong for a shulker (a box of 64-stacks blows past a bundle's
 * 64-weight capacity, so it always read "Full"). Our own data maps to our own {@link ShulkerTooltipComponent},
 * which draws only the item grid on the normal tooltip background.</p>
 */
public record ShulkerTooltipData(List<ItemStack> items) implements TooltipData {}
