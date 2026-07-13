package com.club.modules.itemscroll;

import com.club.modules.itemscroll.Click.Act;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The planner is the whole engine, and every one of these cases is a way real inventories go wrong:
 * a cursor left holding a stack when the screen closes, armour quick-moved out of the player, a crafting
 * result crafted twice by a PICKUP composition, a chest "move everything" that grinds packets one item at
 * a time into an anti-cheat.
 */
class SlotPlanTest {

    // ---- a fixture that reads like the screen it describes ----------------------------------------

    /** Chest side (container) slots 0..n-1, player side slots n.. — the shape of every container screen. */
    private static final class Screen {
        final List<SlotView> slots = new ArrayList<>();

        Screen add(boolean player, Object item, int count) { return add(player, item, count, 64, true, true, false, true); }

        Screen add(boolean player, Object item, int count, int maxCount,
                   boolean enabled, boolean bulk, boolean result, boolean takeable) {
            slots.add(new SlotView(slots.size(), player, enabled, bulk, result, item, count, maxCount, takeable, true));
            return this;
        }

        /** A slot that refuses the hovered item (a fuel slot, a beacon's payment slot). */
        Screen addRefusing(boolean player, Object item, int count) {
            slots.add(new SlotView(slots.size(), player, true, true, false, item, count, 64, true, false));
            return this;
        }

        SlotView at(int id) { return slots.get(id); }
        List<Click> plan(ScrollAction a, int hovered, boolean out) { return SlotPlan.plan(a, slots, at(hovered), out); }
        List<Click> plan(ScrollAction a, int hovered) { return plan(a, hovered, true); }
    }

    private static void assertClicks(List<Click> actual, Click... expected) {
        assertEquals(List.of(expected), actual);
    }

    // ---- move stack -------------------------------------------------------------------------------

    @Test void moveStackIsASingleQuickMove() {
        Screen s = new Screen().add(false, "stone", 64).add(true, null, 0);
        assertClicks(s.plan(ScrollAction.MOVE_STACK, 0), Click.quickMove(0));
    }

    @Test void nothingHappensOverAnEmptySlot() {
        Screen s = new Screen().add(false, null, 0).add(true, "stone", 64);
        assertTrue(s.plan(ScrollAction.MOVE_STACK, 0).isEmpty());
        assertTrue(s.plan(ScrollAction.MOVE_ONE, 0).isEmpty());
        assertTrue(s.plan(ScrollAction.MOVE_MATCHING, 0).isEmpty());
        assertTrue(s.plan(ScrollAction.DROP_STACK, 0).isEmpty());
    }

    @Test void scrollingDownPullsTheMatchingStackInFromTheOtherSide() {
        // hovering my own stone, wheel down: the stone in the chest comes to me
        Screen s = new Screen().add(false, "stone", 32).add(false, "dirt", 10).add(true, "stone", 5);
        assertClicks(s.plan(ScrollAction.MOVE_STACK, 2, false), Click.quickMove(0));
    }

    // ---- move one ---------------------------------------------------------------------------------

    @Test void moveOneTakesTheStackPlacesOneAndPutsTheRestBack() {
        // The cursor MUST end empty: click 3 is not optional, and the three clicks are one atomic group.
        Screen s = new Screen().add(false, "stone", 64).add(true, null, 0);
        assertClicks(s.plan(ScrollAction.MOVE_ONE, 0),
                Click.pickupAll(0), Click.pickupOne(1), Click.pickupAll(0));
    }

    @Test void moveOnePrefersAStackItCanTopUpOverAnEmptySlot() {
        // Merging keeps the target inventory tidy and is what the player expects to see.
        Screen s = new Screen().add(false, "stone", 64).add(true, null, 0).add(true, "stone", 10);
        assertClicks(s.plan(ScrollAction.MOVE_ONE, 0),
                Click.pickupAll(0), Click.pickupOne(2), Click.pickupAll(0));
    }

    @Test void moveOneWillNotTopUpAFullStack() {
        Screen s = new Screen().add(false, "stone", 64).add(true, null, 0).add(true, "stone", 64);
        assertClicks(s.plan(ScrollAction.MOVE_ONE, 0),
                Click.pickupAll(0), Click.pickupOne(1), Click.pickupAll(0));
    }

    @Test void aStackOfOneTakesTheShortPath() {
        // PICKUP-composing a single item would be three packets for what one QUICK_MOVE does.
        Screen s = new Screen().add(false, "stone", 1).add(true, null, 0);
        assertClicks(s.plan(ScrollAction.MOVE_ONE, 0), Click.quickMove(0));
    }

    @Test void moveOneStopsWhenTheTargetIsFull() {
        // The reference mod starts moving items ONE AT A TIME here and reaches ~1000 packets (its issue
        // #70). A refusal is cheaper than a kick.
        Screen s = new Screen().add(false, "stone", 64).add(true, "dirt", 64);
        assertTrue(s.plan(ScrollAction.MOVE_ONE, 0).isEmpty());
    }

    @Test void moveOneNeverPickupComposesTheCraftingResult() {
        // Two PICKUPs on a result slot craft TWICE; QUICK_MOVE crafts as many as the inputs allow, once.
        Screen s = new Screen().add(false, "planks", 4, 64, true, true, true, true).add(true, null, 0);
        assertClicks(s.plan(ScrollAction.MOVE_ONE, 0), Click.quickMove(0));
    }

    @Test void scrollingDownMovesOneItemBackFromTheOtherSide() {
        Screen s = new Screen().add(false, "stone", 64).add(true, "stone", 10);
        assertClicks(s.plan(ScrollAction.MOVE_ONE, 1, false),
                Click.pickupAll(0), Click.pickupOne(1), Click.pickupAll(0));
    }

    // ---- move matching ----------------------------------------------------------------------------

    @Test void moveMatchingTouchesOnlyTheHoveredType() {
        Screen s = new Screen().add(false, "stone", 64).add(false, "dirt", 64).add(false, "stone", 12)
                               .add(true, "stone", 64);   // player side: not a source when moving OUT
        assertClicks(s.plan(ScrollAction.MOVE_MATCHING, 0), Click.quickMove(0), Click.quickMove(2));
    }

    @Test void moveMatchingPullsFromTheOtherSideWhenScrollingIn() {
        Screen s = new Screen().add(false, "stone", 64).add(false, "stone", 7).add(true, "stone", 1);
        assertClicks(s.plan(ScrollAction.MOVE_MATCHING, 2, false), Click.quickMove(0), Click.quickMove(1));
    }

    // ---- move everything --------------------------------------------------------------------------

    @Test void moveEverythingEmptiesTheHoveredSide() {
        Screen s = new Screen().add(false, "stone", 64).add(false, null, 0).add(false, "dirt", 3)
                               .add(true, "gold", 1);
        assertClicks(s.plan(ScrollAction.MOVE_EVERYTHING, 0), Click.quickMove(0), Click.quickMove(2));
    }

    @Test void moveEverythingWorksOverAnEmptySlot() {
        // It is the one action that does not need to know WHICH item — so an empty slot still means
        // "empty this inventory", and hovering a gap in a half-full chest must not silently do nothing.
        Screen s = new Screen().add(false, null, 0).add(false, "stone", 64).add(true, null, 0);
        assertClicks(s.plan(ScrollAction.MOVE_EVERYTHING, 0), Click.quickMove(1));
    }

    @Test void moveEverythingLeavesArmourAndOffhandAlone() {
        // "Empty my inventory into this chest" must never undress the player.
        Screen s = new Screen().add(false, null, 0)
                .add(true, "stone", 64)
                .add(true, "helmet", 1, 1, true, false, false, true)    // armour: not bulk
                .add(true, "shield", 1, 1, true, false, false, true);   // offhand: not bulk
        assertClicks(s.plan(ScrollAction.MOVE_EVERYTHING, 1), Click.quickMove(1));
    }

    @Test void moveEverythingDoesNotStartCrafting() {
        // A mass move that sweeps a crafting result would craft the whole grid out from under the player.
        Screen s = new Screen().add(false, "planks", 4, 64, true, true, true, true)   // result
                               .add(false, "log", 1)                                  // grid
                               .add(true, null, 0);
        assertClicks(s.plan(ScrollAction.MOVE_EVERYTHING, 1), Click.quickMove(1));
    }

    @Test void deadAndLockedSlotsAreNeverSourced() {
        Screen s = new Screen().add(false, "stone", 64, 64, false, true, false, true)   // disabled (loom)
                               .add(false, "dirt", 64, 64, true, true, false, false)    // cannot take (merchant)
                               .add(false, "gold", 5)
                               .add(true, null, 0);
        assertClicks(s.plan(ScrollAction.MOVE_EVERYTHING, 2), Click.quickMove(2));
    }

    @Test void moveOneNeverTargetsASlotThatRefusesTheItem() {
        Screen s = new Screen().add(false, "stone", 64);
        s.addRefusing(true, null, 0);                 // e.g. a fuel slot: canInsert(stone) == false
        assertTrue(s.plan(ScrollAction.MOVE_ONE, 0).isEmpty());
    }

    // ---- drops and drags --------------------------------------------------------------------------

    @Test void dropOneThrowsOneAndDropStackThrowsTheLot() {
        Screen s = new Screen().add(false, "stone", 64).add(true, null, 0);
        assertClicks(s.plan(ScrollAction.DROP_ONE, 0), new Click(0, 0, Act.THROW));
        assertClicks(s.plan(ScrollAction.DROP_STACK, 0), new Click(0, 1, Act.THROW));
    }

    @Test void aDraggedSlotIsJustAQuickMove() {
        Screen s = new Screen().add(false, "stone", 64).add(true, null, 0);
        assertClicks(s.plan(ScrollAction.DRAG_MOVE, 0), Click.quickMove(0));
    }

    @Test void everyPlanIsOneToThreeClicksAndNeverLeavesTheCursorLoaded() {
        // The invariant that stands between a player and a stack dropped on the floor of a server:
        // any plan that PICKUPs must PICKUP back.
        Screen s = new Screen().add(false, "stone", 64).add(true, null, 0);
        for (ScrollAction a : ScrollAction.values()) {
            List<Click> plan = s.plan(a, 0);
            long pickups = plan.stream().filter(c -> c.act() == Act.PICKUP).count();
            assertTrue(pickups == 0 || pickups == 3, a + " left the cursor holding a stack: " + plan);
        }
    }
}
