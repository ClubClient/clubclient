package com.club.modules.itemscroll;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a gesture into the exact list of clicks that carries it out. Pure: no Minecraft, no side effects,
 * no state — see {@link SlotView} for why.
 *
 * <p>Two regions, and the direction picks which is the source: the hovered slot's side ("out", the wheel
 * pushed up) or the other one ("in", pulled down). A click gesture has no direction and always means out.
 * The hovered stack is the only item any gesture ever moves — that is what makes a single
 * {@code acceptsHovered} flag per slot enough to target correctly.</p>
 */
public final class SlotPlan {
    private SlotPlan() {}

    /**
     * @param action  what the gesture asked for
     * @param slots   every slot of the open handler, snapshotted
     * @param hovered the slot under the cursor
     * @param out     true = move OUT of the hovered slot's region, false = pull INTO it
     * @return the clicks, in order. EMPTY means "nothing to do" — including the target-is-full case, where
     *         the reference mod starts grinding items one at a time and reaches ~1000 packets. We stop.
     */
    public static List<Click> plan(ScrollAction action, List<SlotView> slots, SlotView hovered, boolean out) {
        if (slots == null || slots.isEmpty() || hovered == null) return List.of();

        return switch (action) {
            // Dropping is about the slot under the cursor and nothing else — no region, no direction.
            case DROP_ONE   -> canTake(hovered) ? List.of(Click.throwOne(hovered.id()))   : List.of();
            case DROP_STACK -> canTake(hovered) ? List.of(Click.throwStack(hovered.id())) : List.of();

            case MOVE_STACK, DRAG_MOVE -> {
                SlotView src = out ? hovered : firstMatching(slots, !hovered.player(), hovered.item());
                yield src != null && canTake(src) ? List.of(Click.quickMove(src.id())) : List.of();
            }

            case MOVE_MATCHING -> {
                if (hovered.empty() || !hovered.bulk()) yield List.of();
                List<Click> clicks = new ArrayList<>();
                for (SlotView s : slots)
                    if (s.player() == sourceSide(hovered, out) && s.bulk() && canTake(s)
                            && Objects.equals(s.item(), hovered.item()))
                        clicks.add(Click.quickMove(s.id()));
                yield clicks;
            }

            case MOVE_EVERYTHING -> {
                // The only action that does not need to know the item — an empty hovered slot still means
                // "empty this inventory". Result slots are skipped: quick-moving one CRAFTS, and a mass
                // move must not craft the grid out from under the player. Armour and the offhand belong to
                // no region, so a bulk gesture aimed at one has nothing to mean and does nothing.
                if (!hovered.bulk()) yield List.of();
                List<Click> clicks = new ArrayList<>();
                for (SlotView s : slots)
                    if (s.player() == sourceSide(hovered, out) && s.bulk() && !s.result() && canTake(s))
                        clicks.add(Click.quickMove(s.id()));
                yield clicks;
            }

            case MOVE_ONE -> moveOne(slots, hovered, out);
        };
    }

    /** Take all → place ONE → put the rest back. Three clicks, one atomic group, cursor empty at both ends. */
    private static List<Click> moveOne(List<SlotView> slots, SlotView hovered, boolean out) {
        if (hovered.empty()) return List.of();

        SlotView src = out ? hovered : firstMatching(slots, !hovered.player(), hovered.item());
        if (src == null || !canTake(src)) return List.of();

        // A result slot is QUICK_MOVE-only: a PICKUP composition on it would craft twice (the second
        // craft lands on the cursor). A stack of one is the same click for a third of the packets.
        if (src.result() || src.count() == 1) return List.of(Click.quickMove(src.id()));

        SlotView dst = target(slots, hovered, src, out);
        if (dst == null) return List.of();   // target inventory is full — stop, do NOT grind item by item

        return List.of(Click.pickupAll(src.id()), Click.pickupOne(dst.id()), Click.pickupAll(src.id()));
    }

    /**
     * Where the single item lands: a stack of the same item with room first (merging is what the player
     * expects to see), then the first empty slot. Never a result slot — placing into it is meaningless —
     * and never a slot that refuses the item.
     */
    private static SlotView target(List<SlotView> slots, SlotView hovered, SlotView src, boolean out) {
        boolean targetSide = !sourceSide(hovered, out);
        SlotView empty = null;
        for (SlotView s : slots) {
            if (s.player() != targetSide || s.id() == src.id()) continue;
            if (!s.enabled() || !s.bulk() || s.result() || !s.acceptsHovered()) continue;
            if (!s.empty() && Objects.equals(s.item(), src.item()) && s.hasRoom()) return s;
            if (s.empty() && empty == null) empty = s;
        }
        return empty;
    }

    /** Which side the items come FROM: the hovered slot's own side when pushing out, the other when pulling in. */
    private static boolean sourceSide(SlotView hovered, boolean out) {
        return out == hovered.player();
    }

    /** The first stack of this item on the given side — the one a pull-in gesture takes from. */
    private static SlotView firstMatching(List<SlotView> slots, boolean side, Object item) {
        if (item == null) return null;
        for (SlotView s : slots)
            if (s.player() == side && s.bulk() && canTake(s) && Objects.equals(s.item(), item)) return s;
        return null;
    }

    /** A source we may actually click: alive (loom/merchant dead slots), holding something, and takeable. */
    private static boolean canTake(SlotView s) {
        return s.enabled() && !s.empty() && s.takeable();
    }
}
