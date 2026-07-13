package com.club.modules.itemscroll;

/**
 * One slot, flattened — the planner's whole view of the world.
 *
 * <p>{@code SlotPlan} must be unit-testable, and a Minecraft {@code Slot}/{@code ItemStack} cannot be
 * built outside a booted client. So the executor snapshots the live handler into these records and the
 * planner is plain Java: every region rule, every exclusion and every click order is provable at
 * {@code ./gradlew test} speed instead of by relaunching the game.</p>
 *
 * @param id      the handler slot index — what {@code clickSlot} takes
 * @param player  which region: player side ⟺ {@code slot.inventory instanceof PlayerInventory}. In the
 *                survival screen (both sides are PlayerInventory) the executor splits by index instead:
 *                hotbar 0–8 is one region, main 9–35 the other.
 * @param enabled {@code slot.isEnabled()} — a loom's or merchant's dead slot is not a target
 * @param bulk    participates in mass moves as a SOURCE or a target. False for armour 36–39 and offhand
 *                40: "move everything out of my inventory" must not undress the player.
 * @param result  a crafting-result slot. One QUICK_MOVE on it crafts REPEATEDLY until the inputs run out
 *                — which is the cheap, correct way to bulk-craft, and the reason a PICKUP-composed move
 *                must never touch it (it would craft twice and drop the second one on the cursor).
 * @param item    an opaque identity for the stack's item ({@code null} = empty). Equality of this key IS
 *                "same type" — the executor derives it from the real stack so the planner never sees MC.
 * @param count   stack size
 * @param maxCount the item's max stack size in THIS slot ({@code slot.getMaxItemCount(stack)})
 * @param takeable {@code slot.canTakeItems(player)} — a source we may not take from is skipped
 * @param acceptsHovered {@code slot.canInsert(hoveredStack)} — precomputed for the hovered item only,
 *                because that is the only item any single gesture ever moves
 */
public record SlotView(int id, boolean player, boolean enabled, boolean bulk, boolean result,
                       Object item, int count, int maxCount, boolean takeable, boolean acceptsHovered) {

    public boolean empty() { return item == null || count <= 0; }

    /** Room for at least one more of the same item. */
    public boolean hasRoom() { return count < maxCount; }
}
