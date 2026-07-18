package com.club.tooltip;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;

import java.util.List;

/**
 * The payload the shulker-tooltip mixin hands back instead of vanilla's {@link net.minecraft.item.tooltip.BundleTooltipData}.
 *
 * <p>Lives in {@code com.club.tooltip}, NOT {@code com.club.ui}: this is a vanilla-integration type rendered by
 * vanilla's own tooltip system with a vanilla {@code DrawContext}, so it sits outside the Club UI design
 * system and its "everything through the backend" architecture rule ({@code ArchitectureRuleTest}).</p>
 *
 * <p>{@link TooltipData} is a marker (no methods) in a common package, so this is side-safe. It exists because
 * vanilla's {@code BundleTooltipData} drags in the bundle's tooltip CHROME — dark slot cells, a fill bar and a
 * "Full" label a box of 64-stacks always tripped. Our own data maps to our own {@link ShulkerTooltipComponent}.</p>
 */
public record ShulkerTooltipData(List<ItemStack> items) implements TooltipData {}
