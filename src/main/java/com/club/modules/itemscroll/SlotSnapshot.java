package com.club.modules.itemscroll;

import net.minecraft.component.ComponentMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place Minecraft's slots become {@link SlotView}s. Everything downstream ({@link SlotPlan}) is
 * pure Java, so this class carries all of the game's awkwardness on its own.
 *
 * <p><b>Two regions, decided here.</b> A gesture always moves items between exactly two regions, and which
 * two depends on the screen — this is where the mod-screen-proof rule lives, because index arithmetic
 * ("slots 0..53 are the chest") is a lie on half the modded containers:</p>
 * <ul>
 *   <li><b>Any container screen:</b> player side ⟺ {@code slot.inventory instanceof PlayerInventory}, and
 *       the container is everything else.</li>
 *   <li><b>The survival inventory</b> ({@link PlayerScreenHandler}), where BOTH sides are PlayerInventory
 *       and that rule would put every slot in one region: the two regions are <b>hotbar</b> (0–8) and
 *       <b>main</b> (9–35) — which is also exactly what vanilla's own quick-move does there. The crafting
 *       grid and its result are the container side.</li>
 * </ul>
 *
 * <p>Armour (36–39) and the offhand (40) are marked non-bulk: "empty my inventory into this chest" must
 * never undress the player. They can still be scrolled one at a time by hovering them directly.</p>
 */
public final class SlotSnapshot {
    private SlotSnapshot() {}

    /** Item identity: same item AND same components — two differently enchanted swords are not "matching". */
    private record ItemKey(Item item, ComponentMap components) {}

    /**
     * Snapshots every slot of the handler, with the regions resolved relative to {@code hovered}.
     * The returned list is indexed by slot id (position i == slot i), which is what the planner emits.
     */
    public static List<SlotView> of(ScreenHandler handler, Slot hovered, PlayerEntity player) {
        ItemStack hoveredStack = hovered.getStack();
        boolean survival = handler instanceof PlayerScreenHandler;
        boolean hoveredInPlayerInv = hovered.inventory instanceof PlayerInventory;
        // In the survival screen the hovered slot's OWN group (hotbar or main) is one region and the rest
        // is the other; anywhere else the player's inventory is one region and the container the other.
        boolean hoveredHotbar = survival && hoveredInPlayerInv && hotbar(hovered);

        List<SlotView> views = new ArrayList<>(handler.slots.size());
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            boolean inPlayerInv = slot.inventory instanceof PlayerInventory;
            boolean equipment = inPlayerInv && slot.getIndex() >= 36;     // armour 36–39, offhand 40

            boolean region;
            if (!survival) {
                region = inPlayerInv;
            } else if (hoveredInPlayerInv) {
                // hotbar ↔ main. The crafting slots are neither, so park them opposite the hovered group
                // as non-bulk: they must never be swept into, or out of, a player-side gesture.
                region = inPlayerInv && !equipment && hotbar(slot) == hoveredHotbar;
            } else {
                region = !inPlayerInv;                                     // hovering the grid: grid ↔ inventory
            }

            boolean bulk = !equipment && !(survival && hoveredInPlayerInv && !inPlayerInv);
            boolean result = slot.inventory instanceof CraftingResultInventory;

            views.add(new SlotView(
                    slot.id,
                    region,
                    slot.isEnabled(),
                    bulk,
                    result,
                    stack.isEmpty() ? null : new ItemKey(stack.getItem(), stack.getComponents()),
                    stack.getCount(),
                    slot.getMaxItemCount(stack),
                    slot.canTakeItems(player),
                    !hoveredStack.isEmpty() && slot.canInsert(hoveredStack)));
        }
        return views;
    }

    private static boolean hotbar(Slot slot) { return slot.getIndex() <= 8; }
}
