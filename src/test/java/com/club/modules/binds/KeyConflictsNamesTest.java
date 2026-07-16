package com.club.modules.binds;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KeyConflicts} names vanilla's bindings in English because {@code Text.translatable(...)} resolves
 * through the player's ACTIVE language and would put a Russian string in an English menu (owner, v0.1.3
 * item 6).
 *
 * <h2>What this test used to be, and why it changed</h2>
 *
 * <p>It used to guard a hand-transcribed table of 34 names against vanilla's own {@code en_us.json}, on the
 * theory that a copy is a hope and must be asserted. The theory was right and the copy still lost: porting to
 * 1.21.11 fired this test with three renames ("Walk Forwards" → "Walk Forward") and 34 unlisted bindings. The
 * table was accurate the day it was written.
 *
 * <p>So the copy is gone — {@link KeyConflicts} now reads Mojang's file instead of mirroring it, and asserting
 * that a file equals itself would prove nothing. The instrument did its job by making the case for its own
 * removal, and what it guards now is the thing that CAN still break.
 *
 * <h2>What can still break</h2>
 *
 * <p>The loader swallows its exceptions and returns an empty map, because a keybinding name is not worth
 * crashing a client over. That is the right runtime behaviour and exactly the wrong thing to leave unwatched:
 * an empty map is SILENT. Every name would fall through to the player's language, the Russian-in-an-English-
 * menu bug would be back in full, and nothing would say a word — not javac, not mixin, not the game. The
 * resource could move, the JSON shape could change, the filter could over-exclude, and each failure looks
 * identical from the outside: zero names, no error.
 *
 * <p>So these tests exist to make zero mean broken. They run per version node, against that version's own
 * client jar, and cost no game and no harness run.
 */
class KeyConflictsNamesTest {

    private static JsonObject vanillaEnUs() throws Exception {
        try (InputStream in = KeyConflictsNamesTest.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            assertNotNull(in, "vanilla en_us.json is not on the test classpath — this test cannot verify anything, "
                    + "which is worse than failing: fix the classpath rather than deleting the check");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    /**
     * The load happened at all. This is the one that catches a moved resource, a Gson change, or any thrown
     * exception the loader quietly ate — all of which present as an empty map and no complaint.
     */
    @Test
    void weActuallyLoadedVanillasNames() {
        Map<String, String> ours = KeyConflicts.vanillaNames();
        assertFalse(ours.isEmpty(),
                "KeyConflicts loaded ZERO vanilla binding names. The loader returns an empty map on any "
                        + "failure and never throws, so this is what a moved resource, a parse error or an "
                        + "over-eager filter all look like. Every keybinding name in the Club UI would be "
                        + "falling back to the player's language.");

        // A sanity floor, not a count: vanilla has had at least the movement keys and nine hotbar slots since
        // long before 1.21, so a map that parsed but somehow yielded a handful of entries is also broken.
        assertTrue(ours.size() >= 30, "only " + ours.size() + " binding names parsed — expected vanilla's full set");
    }

    /** Names are vanilla's verbatim — no paraphrase, no prettified translation key. */
    @Test
    void everyNameIsVanillasOwnString() throws Exception {
        JsonObject en = vanillaEnUs();
        Map<String, String> ours = KeyConflicts.vanillaNames();

        String wrong = ours.entrySet().stream()
                .filter(e -> !en.has(e.getKey()) || !en.get(e.getKey()).getAsString().equals(e.getValue()))
                .map(e -> e.getKey() + ": we say \"" + e.getValue() + "\"")
                .collect(Collectors.joining("\n  "));

        assertTrue(wrong.isEmpty(), "KeyConflicts names differ from vanilla's en_us.json:\n  " + wrong);
    }

    /**
     * Every binding vanilla has, we can name. A binding we cannot name falls back to the player's language —
     * the exact leak this class exists to stop — so a NEW vanilla binding must never slip through unnamed.
     */
    @Test
    void weNameEveryBindingVanillaHas() throws Exception {
        JsonObject en = vanillaEnUs();
        Map<String, String> ours = KeyConflicts.vanillaNames();

        Set<String> missing = new TreeSet<>();
        for (String k : en.keySet()) if (KeyConflicts.isBindingKey(k) && !ours.containsKey(k)) missing.add(k);
        assertTrue(missing.isEmpty(),
                "Minecraft has keybindings KeyConflicts cannot name in English (they would fall back to the "
                        + "player's language): " + missing);

        assertEquals(en.keySet().stream().filter(KeyConflicts::isBindingKey).count(), ours.size(),
                "our map and vanilla's binding set are different sizes");
    }

    /**
     * The filter excludes what is not an action. Key NAMES ("key.keyboard.k") and category headers are not
     * bindings; a header would never be looked up, but the category prefix is itself version-dependent —
     * 1.21.1 says {@code key.categories.*}, 1.21.11 says {@code key.category.minecraft.*} — and this pins
     * both so a future rename shows up here rather than as junk in the map.
     */
    @Test
    void weDoNotMistakeKeyNamesOrHeadersForBindings() {
        Map<String, String> ours = KeyConflicts.vanillaNames();

        String junk = ours.keySet().stream()
                .filter(k -> k.startsWith("key.keyboard") || k.startsWith("key.mouse")
                        || k.startsWith("key.categories") || k.startsWith("key.category."))
                .sorted()
                .collect(Collectors.joining(", "));

        assertTrue(junk.isEmpty(), "these are not binding actions and should not be in the names map: " + junk);
    }

    /**
     * The names are the strings on the player's own Controls screen. Asserted through vanilla's file rather
     * than against a literal, because the literals are what drifted: "Walk Forwards" in 1.21.1 is "Walk
     * Forward" in 1.21.11, and pinning either one would just rebuild the table this test deleted. What is
     * version-stable is that these bindings EXIST and that we agree with the game about them.
     */
    @Test
    void theBindingsAPlayerActuallyCollidesWithAreNamed() throws Exception {
        JsonObject en = vanillaEnUs();
        Map<String, String> ours = KeyConflicts.vanillaNames();

        // Sneak/sprint/jump are the keys Club's own hold-modules (Zoom, Freelook) realistically land on.
        for (String k : new String[]{"key.sneak", "key.sprint", "key.jump", "key.forward", "key.inventory"}) {
            assertTrue(ours.containsKey(k), "no English name for " + k);
            assertEquals(en.get(k).getAsString(), ours.get(k), k + " is not named the way the game names it");
        }
    }
}
