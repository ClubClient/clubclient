package com.club.modules.itemscroll;

import java.util.Map;

/**
 * The gesture map: which gesture drives which action, and the rules that make an ambiguous layout
 * impossible. Pure — it works on the raw {@code Map<String,String>} the config persists, so every rule is
 * provable in a unit test without a booted client. {@link ItemScrollBinds} is the live wrapper that hands
 * it the real map and saves.
 */
public final class Gestures {
    private Gestures() {}

    /** Absent key = never touched, use the default. Empty value = the player cleared this row on purpose. */
    private static final String UNBOUND = "";

    /** The reference mod's muscle memory, 1:1 — a player who has scrolled items before must not relearn. */
    public static Gesture defaultFor(ScrollAction action) {
        return switch (action) {
            case MOVE_ONE        -> Gesture.of(GestureInput.SCROLL, 0);
            case MOVE_STACK      -> Gesture.of(GestureInput.SCROLL, Gesture.SHIFT);
            case MOVE_MATCHING   -> Gesture.of(GestureInput.SCROLL, Gesture.CTRL);
            case MOVE_EVERYTHING -> Gesture.of(GestureInput.SCROLL, Gesture.CTRL | Gesture.SHIFT);
            case DRAG_MOVE       -> Gesture.of(GestureInput.LMB,    Gesture.SHIFT);
            // Dropping ships unbound on purpose: it is the one destructive action here, and a mis-aimed
            // default scatters a stack across the floor of someone else's base.
            case DROP_ONE, DROP_STACK -> null;
        };
    }

    /** The gesture on this action, or null if unbound — cleared by the player, or junk in the file. */
    public static Gesture get(Map<String, String> map, ScrollAction action) {
        String stored = map.get(action.name());
        if (stored == null) return defaultFor(action);   // never touched
        if (UNBOUND.equals(stored)) return null;         // cleared — do NOT hand the default back
        return Gesture.parse(stored);                    // junk parses to null, and never throws
    }

    /** Which action this gesture fires, or null — the dispatch side of {@link #get}. */
    public static ScrollAction actionFor(Map<String, String> map, Gesture gesture) {
        if (gesture == null) return null;
        for (ScrollAction a : ScrollAction.values())
            if (gesture.equals(get(map, a))) return a;
        return null;
    }

    /**
     * Assign a gesture, STEALING it from whatever else held it: one input must never drive two actions —
     * both would fire, and the second would act on an inventory the first had already emptied. This is
     * {@code ModuleBinds.set}'s rule, in mouse form, and the row that loses the gesture goes to "Not set".
     *
     * @return the action it was taken from, or null if it was free
     */
    public static ScrollAction set(Map<String, String> map, ScrollAction action, Gesture gesture) {
        if (gesture == null) { clear(map, action); return null; }
        ScrollAction stolenFrom = null;
        for (ScrollAction other : ScrollAction.values()) {
            if (other == action) continue;
            if (gesture.equals(get(map, other))) { clear(map, other); stolenFrom = other; }
        }
        map.put(action.name(), gesture.store());
        return stolenFrom;
    }

    /** Unbind — written down explicitly, so a reload does not resurrect the default. */
    public static void clear(Map<String, String> map, ScrollAction action) {
        map.put(action.name(), UNBOUND);
    }

    /**
     * A gesture we refuse to bind at all: bare left/right click is how a player picks items up and splits
     * stacks. Binding over it turns the inventory into a brick, and no setting is worth that — the same
     * ground the keybind popover stands on when it refuses the key that opens the menu.
     */
    public static boolean reserved(Gesture gesture) {
        return gesture != null && gesture.mods() == 0
                && (gesture.input() == GestureInput.LMB || gesture.input() == GestureInput.RMB);
    }

    /** Whether this action can sit on this gesture: a drag needs a button to HOLD, and the wheel is not one. */
    public static boolean allowed(ScrollAction action, Gesture gesture) {
        if (gesture == null || reserved(gesture)) return false;
        return !action.dragOnly() || gesture.input().click();
    }

    /**
     * What VANILLA already does with this gesture, or null. We do not refuse these — replacing quick-move
     * with move-all-matching is a legitimate thing to want — but the row must NAME what it is taking, the
     * way {@code KeyConflicts} names the vanilla action a keybind overrides. Club says what it takes.
     */
    public static String vanilla(Gesture gesture) {
        if (gesture == null) return null;
        if (gesture.mods() == Gesture.SHIFT
                && (gesture.input() == GestureInput.LMB || gesture.input() == GestureInput.RMB))
            return "Quick move";
        if (gesture.mods() == 0 && gesture.input() == GestureInput.MMB)
            return "Clone stack (creative)";
        return null;
    }
}
