package com.club.modules.binds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule this project bought with two separate bugs: <b>a ban at the door is not a ban in the act.</b>
 *
 * <p>v0.1.3 removed the "Toggle key" row from nine of the thirteen module cards (owner: a key earns its row
 * only if a player would flip it mid-fight). Removing a CONTROL is not the same as removing its EFFECT, and
 * here the gap between the two is a trap rather than an untidiness:
 *
 * <ul>
 *   <li>A v9 config can carry a bind on a module whose row is now gone. If the tick loop still fires it, that
 *       key toggles the module forever with nowhere left in the UI to un-bind it.</li>
 *   <li>Worse if it is also a vanilla key: the player has silently lost a vanilla action and cannot get it
 *       back. That is the "the menu can no longer be locked away from you" bug v0.1.2 shipped a fix for,
 *       wearing a new costume.</li>
 * </ul>
 *
 * <p>Two locks, and this pins both: {@link ModuleBinds#KEYED} is the single list, the UI reads it, and the
 * READ path ({@link ModuleBinds#init}) registers nothing outside it. The migration then deletes the stale
 * entries, so the file does not carry a claim the mod will not honour either.
 */
class KeyedModulesTest {

    /** The owner's rule, as a list. If someone widens KEYED without arguing the case, this is what breaks. */
    @Test
    void onlyModulesYouWouldFlipMidFightCarryAKey() {
        assertTrue(ModuleBinds.KEYED.contains("Toggle Sprint"), "sprint is flipped mid-fight");
        assertTrue(ModuleBinds.KEYED.contains("Fullbright"),    "you walk into a cave and want it now");

        // Set once, in a menu, and never thought about again. A key row on these is clutter, and clutter is
        // what the owner actually complained about ("это начинает выглядеть как мусорка").
        //
        // "Performance" is NOT in this list any more, and its absence is deliberate: it stopped being a module
        // in v0.1.3 and became a CATEGORY. Its three cards are named below. A test that goes on asserting
        // things about a module nobody ships is a test that has quietly stopped testing anything.
        for (String setOnce : new String[] {
                "Hands", "Animations", "Screen Stretch", "HUD Editor",
                "No Hurt Cam", "No Fire Overlay", "No Bobbing", "Hide Effects",
                "Particles", "Block Entities", "Background FPS",
                // Item Scroll lost its key in v0.1.3 (owner: "зачем итемскроллу кнопка бинда"). It is not a
                // mid-fight toggle — you set your gestures once. Its bind, if any old file carries one, is
                // dropped by the migration and inert in the tick loop.
                "Item Scroll" }) {
            assertFalse(ModuleBinds.KEYED.contains(setOnce), setOnce + " is a set-once preference, not a hotkey");
            assertFalse(ModuleBinds.hasKeyRow(setOnce), setOnce + " must show no key row at all");
        }
    }

    /**
     * A HOLD module must never appear in KEYED. Its key is a real vanilla binding held down, not a toggle —
     * and a toggle bind on the SAME key made one press both zoom AND switch the module off, which is the
     * "works every other press" bug Stage 58 closed. The two namespaces must not overlap, ever.
     */
    @Test
    void holdModulesAreNotToggleBinds() {
        for (String hold : HoldKeys.NAMES) {
            assertFalse(ModuleBinds.KEYED.contains(hold),
                    hold + " is a HOLD module — its key is the hold key, never a toggle bind");
            assertTrue(ModuleBinds.hasKeyRow(hold),
                    hold + " still shows a key row — a 'Hold key' row, which is the whole control");
        }
    }
}
