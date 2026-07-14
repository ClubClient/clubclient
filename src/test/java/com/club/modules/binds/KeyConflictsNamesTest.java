package com.club.modules.binds;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KeyConflicts} carries its own transcription of vanilla's English keybinding names, because
 * {@code Text.translatable(...)} resolves through the player's ACTIVE language and would put a Russian
 * string in an English menu (owner, v0.1.3 item 6).
 *
 * <p>A hand-copied table is a HOPE, not an instrument. Mojang renames bindings — "Save Toolbar Activator"
 * is, in the actual client, "Save <b>Hotbar</b> Activator" — and a table that silently drifts from the game
 * would quietly start naming the wrong action in the one line whose whole job is to name the right one.
 *
 * <p>So it is asserted, not trusted. Minecraft's own {@code assets/minecraft/lang/en_us.json} ships inside
 * the client jar, which is on this module's test classpath, so the check costs no game and no harness run:
 * every entry must match vanilla verbatim, and vanilla must have no keybinding we failed to list. Zero can
 * mean broken.
 */
class KeyConflictsNamesTest {

    /** Vanilla's keybinding translation keys — everything under {@code key.} that is an ACTION, not a key
     *  NAME ({@code key.keyboard.*}, {@code key.mouse.*}) and not a category header. */
    private static boolean isBindingKey(String k) {
        return k.startsWith("key.")
                && !k.startsWith("key.keyboard")
                && !k.startsWith("key.mouse")
                && !k.startsWith("key.categories");
    }

    private static JsonObject vanillaEnUs() throws Exception {
        try (InputStream in = KeyConflictsNamesTest.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            assertNotNull(in, "vanilla en_us.json is not on the test classpath — this test cannot verify anything, "
                    + "which is worse than failing: fix the classpath rather than deleting the check");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> ourTable() throws Exception {
        Field f = KeyConflicts.class.getDeclaredField("VANILLA_EN");
        f.setAccessible(true);
        return (Map<String, String>) f.get(null);
    }

    @Test
    void everyNameWeShipIsTheNameVanillaShips() throws Exception {
        JsonObject en = vanillaEnUs();
        Map<String, String> ours = ourTable();

        List<String> wrong = ours.entrySet().stream()
                .filter(e -> !en.has(e.getKey()) || !en.get(e.getKey()).getAsString().equals(e.getValue()))
                .map(e -> e.getKey() + ": we say \"" + e.getValue() + "\", vanilla says "
                        + (en.has(e.getKey()) ? "\"" + en.get(e.getKey()).getAsString() + "\"" : "(no such key)"))
                .collect(Collectors.toList());

        assertTrue(wrong.isEmpty(),
                "KeyConflicts.VANILLA_EN has drifted from Minecraft's own en_us.json:\n  " + String.join("\n  ", wrong));
    }

    @Test
    void weNameEveryBindingVanillaHas() throws Exception {
        JsonObject en = vanillaEnUs();
        Map<String, String> ours = ourTable();

        Set<String> missing = new TreeSet<>();
        for (String k : en.keySet()) if (isBindingKey(k) && !ours.containsKey(k)) missing.add(k);

        // A binding we cannot name falls back to Text.translatable(), i.e. the player's own language — the
        // exact leak this table exists to stop. A NEW vanilla binding must therefore fail the build, not
        // quietly reintroduce Russian into an English menu.
        assertTrue(missing.isEmpty(),
                "Minecraft has keybindings KeyConflicts cannot name in English (they would fall back to the "
                        + "player's language): " + missing);

        // And the other way: an entry for a binding that no longer exists is dead weight that will never fire.
        Set<String> stale = ours.keySet().stream().filter(k -> !en.has(k)).collect(Collectors.toCollection(TreeSet::new));
        assertTrue(stale.isEmpty(), "KeyConflicts names bindings vanilla no longer has: " + stale);

        assertEquals(en.keySet().stream().filter(KeyConflictsNamesTest::isBindingKey).count(), ours.size(),
                "the table and vanilla disagree on how many keybindings exist");
    }
}
