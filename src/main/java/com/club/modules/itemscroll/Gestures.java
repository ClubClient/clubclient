package com.club.modules.itemscroll;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The gesture map: which gesture drives which action, and the rules that make an ambiguous layout
 * impossible. Pure — it works on the raw {@code Map<String,String>} the config persists, so every rule is
 * provable in a unit test without a booted client. {@link ItemScrollBinds} is the live wrapper that hands
 * it the real map and saves.
 *
 * <p>Pure but for ONE piece of state, named here rather than hidden: {@link #RESERVED_BY_CODE}, the record
 * of which reserved gestures were installed by CODE rather than read out of a file. It exists because the
 * read path has to tell those two apart — see {@link #actionFor}.</p>
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

    /**
     * Which action this gesture fires, or null — the dispatch side of {@link #get}, and the only door
     * between a mouse event and an item moving.
     *
     * <p><b>This is where a reserved gesture dies.</b> {@link GestureScreen} refuses to BIND bare LMB/RMB,
     * but a ban at the door is not a ban in the act: {@code config.json} is a text file, text files get
     * edited by hand, and they also arrive from other versions of the mod. One line —
     * {@code "MOVE_EVERYTHING": "LMB"} — parses perfectly, and then bare left click stops picking items up
     * in every container in the game, because the click hook eats it. The player's inventory is bricked and
     * the screen that could undo it is the last place they would look. So the READ path asks too, and a
     * reserved gesture that came out of a FILE resolves to no action however it got in there.</p>
     */
    public static ScrollAction actionFor(Map<String, String> map, Gesture gesture) {
        if (gesture == null) return null;
        for (ScrollAction a : ScrollAction.values()) {
            if (!gesture.equals(get(map, a))) continue;
            // …unless CODE put it there through set(), which is not a door a config file can walk through
            // (see RESERVED_BY_CODE). Keep looking rather than giving up: a hand-edited file is free to name
            // the same gesture on two rows, and the licensed one may be the later of them.
            if (reserved(gesture) && !installedByCode(map, a, gesture)) continue;
            return a;
        }
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
        // This row's provenance is now this call, whatever it was before: any licence it held dies with the
        // gesture it was granted for, and a new one is minted only for a gesture the read path would
        // otherwise refuse. See RESERVED_BY_CODE.
        revoke(map, action);
        if (reserved(gesture))
            RESERVED_BY_CODE.computeIfAbsent(map, m -> new HashSet<>()).add(licence(action, gesture));
        return stolenFrom;
    }

    /** Unbind — written down explicitly, so a reload does not resurrect the default. */
    public static void clear(Map<String, String> map, ScrollAction action) {
        map.put(action.name(), UNBOUND);
        revoke(map, action);   // the row holds nothing now, so nothing it once held stays licensed
    }

    // ---- provenance: who put a RESERVED gesture in this map ----------------------------------------
    //
    // A reserved gesture cannot be BOUND in the editor — GestureScreen.capture() refuses it — so there are
    // exactly two ways one reaches a map: somebody edited config.json, or code called set() on purpose.
    // Those are not the same act and must not get the same answer. The in-game harness binds DRAG_MOVE to a
    // BARE LEFT CLICK deliberately (it cannot hold Shift — GLFW key state is physical — and bare LMB is also
    // the harshest case, the very button vanilla drags with), and that drag test is the instrument this
    // module's claims rest on. So set() IS the seam: a reserved gesture that came through it keeps working;
    // one that came out of a file does not.
    //
    // Keyed by the map INSTANCE (identity: two maps with equal contents are still two maps, so a test's map
    // and the live config can never lend each other a licence), and only ever written by a reserved set() —
    // which means that in a normal session this stays EMPTY for the life of the client. Client thread only,
    // like every other caller of this class.

    private static final Map<Map<String, String>, Set<String>> RESERVED_BY_CODE = new IdentityHashMap<>();

    private static String licence(ScrollAction action, Gesture gesture) {
        return action.name() + '=' + gesture.store();
    }

    /** Did code install exactly this reserved gesture on exactly this row of this map, this session? */
    private static boolean installedByCode(Map<String, String> map, ScrollAction action, Gesture gesture) {
        Set<String> granted = RESERVED_BY_CODE.get(map);
        return granted != null && granted.contains(licence(action, gesture));
    }

    /** A licence is granted for one gesture on one row: rebind or clear the row and it is gone. */
    private static void revoke(Map<String, String> map, ScrollAction action) {
        Set<String> granted = RESERVED_BY_CODE.get(map);
        if (granted == null) return;
        granted.removeIf(l -> l.startsWith(action.name() + '='));
        if (granted.isEmpty()) RESERVED_BY_CODE.remove(map);
    }

    /**
     * A gesture we refuse to bind — AND refuse to act on ({@link #actionFor}), because a file can carry one
     * we never agreed to. Bare left/right click is how a player picks items up and splits stacks: binding
     * over it turns the inventory into a brick, and no setting is worth that — the same ground the keybind
     * popover stands on when it refuses the key that opens the menu.
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
