package com.club.modules.itemscroll;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.club.modules.itemscroll.Gesture.ALT;
import static com.club.modules.itemscroll.Gesture.CTRL;
import static com.club.modules.itemscroll.Gesture.SHIFT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The binding rules. "Fully customisable gestures" is the whole point of the module, and a customisable
 * binding surface is worth nothing if two actions can end up on one gesture — both would fire, the second
 * on an inventory the first had already changed.
 */
class GesturesTest {

    private final Map<String, String> map = new HashMap<>();

    private static Gesture g(GestureInput input, int mods) { return Gesture.of(input, mods); }

    // ---- storage ----------------------------------------------------------------------------------

    @Test void aGestureSurvivesTheRoundTripThroughTheConfig() {
        Gesture all = g(GestureInput.MMB, SHIFT | CTRL | ALT);
        assertEquals(all, Gesture.parse(all.store()));
        assertEquals("CTRL+SHIFT+ALT+MMB", all.store());
    }

    @Test void modifierOrderInTheFileDoesNotMatter() {
        assertEquals(Gesture.parse("CTRL+SHIFT+SCROLL"), Gesture.parse("shift+ctrl+scroll"));
    }

    @Test void aHandEditedTypoIsUnboundRatherThanACrash() {
        // The config is a text file and text files get edited. ModuleBinds learned this the hard way:
        // InputUtil.fromTranslationKey THROWS, and the client died every tick until the file was fixed.
        for (String junk : new String[]{"", "  ", "CTRL+", "WHEEL", "SCROLL+LMB", "CTRL+SHIFT", "???"})
            assertNull(Gesture.parse(junk), "junk should read as unbound: '" + junk + "'");
    }

    @Test void theChipReadsLikeTheKeyboard() {
        assertEquals("Ctrl + Shift + Scroll", g(GestureInput.SCROLL, CTRL | SHIFT).label());
        assertEquals("Left Click", g(GestureInput.LMB, 0).label());
    }

    // ---- defaults ---------------------------------------------------------------------------------

    @Test void theDefaultsAreTheMuscleMemoryOfTheReference() {
        assertEquals(g(GestureInput.SCROLL, 0),            Gestures.get(map, ScrollAction.MOVE_ONE));
        assertEquals(g(GestureInput.SCROLL, SHIFT),        Gestures.get(map, ScrollAction.MOVE_STACK));
        assertEquals(g(GestureInput.SCROLL, CTRL),         Gestures.get(map, ScrollAction.MOVE_MATCHING));
        assertEquals(g(GestureInput.SCROLL, CTRL | SHIFT), Gestures.get(map, ScrollAction.MOVE_EVERYTHING));
        assertEquals(g(GestureInput.LMB,    SHIFT),        Gestures.get(map, ScrollAction.DRAG_MOVE));
    }

    @Test void droppingShipsUnbound() {
        // A mis-aimed default scatters a stack across someone else's floor. Opt in, or it doesn't happen.
        assertNull(Gestures.get(map, ScrollAction.DROP_ONE));
        assertNull(Gestures.get(map, ScrollAction.DROP_STACK));
    }

    @Test void aClearedGestureStaysClearedAcrossAReload() {
        // The bug this pins: "absent" and "cleared" both look like an empty map entry unless we write the
        // clear down. The player unbinds move-one, restarts, and the wheel starts moving items again.
        Gestures.clear(map, ScrollAction.MOVE_ONE);
        Map<String, String> reloaded = new HashMap<>(map);
        assertNull(Gestures.get(reloaded, ScrollAction.MOVE_ONE));
    }

    // ---- no ambiguity, ever -----------------------------------------------------------------------

    @Test void assigningAGestureStealsItFromItsPreviousOwner() {
        ScrollAction stolenFrom = Gestures.set(map, ScrollAction.MOVE_EVERYTHING, g(GestureInput.SCROLL, SHIFT));

        assertEquals(ScrollAction.MOVE_STACK, stolenFrom);
        assertNull(Gestures.get(map, ScrollAction.MOVE_STACK), "the loser must go to Not set");
        assertEquals(g(GestureInput.SCROLL, SHIFT), Gestures.get(map, ScrollAction.MOVE_EVERYTHING));
    }

    @Test void oneGestureNeverDrivesTwoActions() {
        for (ScrollAction a : ScrollAction.values()) Gestures.set(map, a, g(GestureInput.SCROLL, CTRL));
        long owners = java.util.Arrays.stream(ScrollAction.values())
                .filter(a -> g(GestureInput.SCROLL, CTRL).equals(Gestures.get(map, a))).count();
        assertEquals(1, owners);
    }

    @Test void theGestureDispatchesToExactlyOneAction() {
        assertEquals(ScrollAction.MOVE_MATCHING, Gestures.actionFor(map, g(GestureInput.SCROLL, CTRL)));
        assertNull(Gestures.actionFor(map, g(GestureInput.MMB, ALT)), "an unbound gesture fires nothing");
    }

    @Test void stealingIsNotSelfHarm() {
        Gestures.set(map, ScrollAction.MOVE_ONE, g(GestureInput.SCROLL, 0));   // its own default, again
        assertEquals(g(GestureInput.SCROLL, 0), Gestures.get(map, ScrollAction.MOVE_ONE));
    }

    // ---- what may not be bound, and what must be named --------------------------------------------

    @Test void bareLeftAndRightClickAreRefused() {
        // That is how a player picks items up and splits stacks. Binding over them bricks the inventory.
        assertTrue(Gestures.reserved(g(GestureInput.LMB, 0)));
        assertTrue(Gestures.reserved(g(GestureInput.RMB, 0)));
        assertFalse(Gestures.reserved(g(GestureInput.LMB, SHIFT)));
        assertFalse(Gestures.reserved(g(GestureInput.MMB, 0)));
        assertFalse(Gestures.allowed(ScrollAction.MOVE_STACK, g(GestureInput.LMB, 0)));
    }

    @Test void aDragCannotLiveOnTheWheel() {
        assertFalse(Gestures.allowed(ScrollAction.DRAG_MOVE, g(GestureInput.SCROLL, CTRL)));
        assertTrue(Gestures.allowed(ScrollAction.DRAG_MOVE, g(GestureInput.RMB, CTRL)));
        assertTrue(Gestures.allowed(ScrollAction.MOVE_STACK, g(GestureInput.SCROLL, CTRL)));
    }

    @Test void aGestureVanillaAlreadyUsesIsNamedNotHidden() {
        assertEquals("Quick move", Gestures.vanilla(g(GestureInput.LMB, SHIFT)));
        assertEquals("Quick move", Gestures.vanilla(g(GestureInput.RMB, SHIFT)));
        assertEquals("Clone stack (creative)", Gestures.vanilla(g(GestureInput.MMB, 0)));
        assertNull(Gestures.vanilla(g(GestureInput.SCROLL, CTRL)), "vanilla does nothing with the wheel here");
    }
}
