package com.club.mixin;

import com.club.config.ClubConfig;
import com.club.tooltip.ShulkerTooltipData;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;

/**
 * Shulker tooltip (v0.1.5): hover a shulker box and see what is inside it, as a plain item grid — no name, no
 * text list, just the slots (owner: "remove the name and that plate, leave only the slots").
 *
 * <h2>Two halves, both here on ItemStack</h2>
 *
 * <ul>
 *   <li><b>The grid</b> — {@code getTooltipData()} is {@code Optional<TooltipData>} on all three versions, so
 *       filling its empty Optional with our {@link ShulkerTooltipData} is version-agnostic. The render half
 *       (forked at 1.21.8) lives in {@code com.club.tooltip.ShulkerTooltipComponent}, and a Fabric
 *       {@code TooltipComponentCallback} maps the data to it (registered in {@code ClubClient}). It first
 *       shipped as vanilla's {@code BundleTooltipData}, but that dragged in the bundle chrome — dark cells, a
 *       fill bar, a "Full" label — so it draws its own chest grid now.</li>
 *   <li><b>The text</b> — {@code getTooltip()} (same signature on all three) returns EMPTY whenever the grid
 *       shows, dropping the item name and the vanilla contents list in one hook. This replaced two forked
 *       appendTooltip mixins (the contents line came from {@code ShulkerBoxBlock} on 1.21.1 and
 *       {@code ContainerComponent} on 1.21.2+); clearing the whole return needs neither split.</li>
 * </ul>
 *
 * <p>Triggers on any stack with a {@link DataComponentTypes#CONTAINER} (vanilla shulkers + modded items that
 * store contents there). Bundles keep their own tooltip. Gated on {@code config.shulkerTooltip}.</p>
 */
@Mixin(ItemStack.class)
public class MixinItemStackShulkerTooltip {
    @ModifyReturnValue(method = "getTooltipData", at = @At("RETURN"))
    private Optional<TooltipData> club$shulkerGrid(Optional<TooltipData> original) {
        if (original.isPresent()) return original;   // a real bundle already carries its data — leave it
        List<ItemStack> items = club$gridItems();
        return items == null ? original : Optional.of(new ShulkerTooltipData(items));
    }

    @ModifyReturnValue(method = "getTooltip", at = @At("RETURN"))
    private List<Text> club$hideTextWhenGridShows(List<Text> original) {
        // The grid replaces the words entirely: no name, no contents list. Only when the grid is actually
        // shown — feature off / empty box / non-container all keep vanilla's text.
        return club$gridItems() != null ? List.of() : original;
    }

    /**
     * The non-empty container items our grid would show, or null when there is no grid (feature off, not a
     * container, or empty). One source of truth for both the grid and the text-hiding, so they can never
     * disagree — a box that shows a grid always hides its text, and vice versa.
     */
    private List<ItemStack> club$gridItems() {
        if (!ClubConfig.get().shulkerTooltip) return null;
        ContainerComponent container = ((ItemStack) (Object) this).get(DataComponentTypes.CONTAINER);
        if (container == null) return null;
        List<ItemStack> items = container.streamNonEmpty().toList();
        return items.isEmpty() ? null : items;
    }
}
