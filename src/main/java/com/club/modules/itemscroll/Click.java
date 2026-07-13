package com.club.modules.itemscroll;

/**
 * One slot click — one {@code ClickSlotC2SPacket}. There is no batch packet in the protocol: every item
 * a gesture moves is paid for in packets, and the plan is the exact, minimal list of them.
 *
 * <p>{@link Act} is Club's own name for the three {@code SlotActionType}s we use, so the planner stays
 * free of Minecraft classes. The executor maps them 1:1. {@code button} is 0 or 1 and nothing else —
 * PICKUP and QUICK_MOVE reject any other value outright (verified in {@code internalOnSlotClick}), which
 * is why a middle mouse button can only ever be an INPUT, never a slot action.</p>
 */
public record Click(int slotId, int button, Act act) {

    public enum Act { PICKUP, QUICK_MOVE, THROW }

    public static Click quickMove(int slotId)   { return new Click(slotId, 0, Act.QUICK_MOVE); }
    public static Click pickupAll(int slotId)   { return new Click(slotId, 0, Act.PICKUP); }   // take/place whole
    public static Click pickupOne(int slotId)   { return new Click(slotId, 1, Act.PICKUP); }   // take half / place one
    public static Click throwOne(int slotId)    { return new Click(slotId, 0, Act.THROW); }
    public static Click throwStack(int slotId)  { return new Click(slotId, 1, Act.THROW); }
}
