package com.club.modules.binds;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * What ELSE is sitting on a key — the one question this mod never asked.
 *
 * <p>Minecraft dispatches at most ONE binding per physical key: {@code KeyBinding.KEY_TO_BINDINGS} is a
 * single-winner map, rebuilt by {@code updateKeysByCode()} by re-putting every registered binding, so on
 * a collision the last one in HashMap order wins and the other never receives a press at all. Two keys
 * bound to the same physical key therefore do not "share" it — one of the two actions silently stops
 * working, and WHICH one loses depends on hash order over the whole registered binding set, i.e. on the
 * player's mod list. That is why vanilla's Controls screen paints duplicates red.</p>
 *
 * <p>Club's own actions never collide with each other ({@link HoldKeys} and {@link ModuleBinds} steal the
 * key from one another). But until now nothing in this mod ever read {@code options.allKeys}: the popover
 * would happily put Zoom on Left Shift and the player simply lost the ability to sneak, with no warning
 * anywhere in the Club UI. Zoom/Freelook themselves kept working — they poll GLFW directly (see
 * {@link com.club.util.Keys}) — so the mod never looked broken. Only the game did.</p>
 *
 * <p>We do not resolve the collision by unbinding vanilla's action behind the player's back: that would
 * trade one silent break for another. We NAME it, where the player is standing when they cause it.</p>
 *
 * <h2>Why the name is in English on a Russian client</h2>
 *
 * <p>{@code Text.translatable(kb.getTranslationKey())} resolves through the ACTIVE language, so this class
 * used to hand the popover "Сохранить инструменты" and the sheet read "Also: Сохранить инструменты" — one
 * Cyrillic string in an all-English menu, which reads as a bug because it is one. Club's UI is English
 * (owner: translations come when the mod grows), and {@link com.club.util.KeyNames} already settled the
 * same argument for KEY names in Stage 51.</p>
 *
 * <p>So vanilla's bindings are named from vanilla's own {@code en_us.json} — the copy that ships inside the
 * client jar, read straight off the classpath, once, on first use. Deriving the name from the translation key
 * instead ("key.saveToolbarActivator" → "Save Toolbar Activator") was rejected: it is a guess that happens to
 * be WRONG here — the string vanilla actually shows the player is "Save <b>Hotbar</b> Activator". A name the
 * player cannot find in their game is worse than no name.</p>
 *
 * <p><b>The cost, stated plainly.</b> The player fixes a conflict in Options → Controls, and THAT screen is
 * in their language: a Russian player reading "Save Hotbar Activator" here will not find that row by its
 * name. What saves this is that they do not have to — vanilla paints every duplicate binding red on the
 * Controls screen (see above), so the row finds THEM. The name's job in this popover is the judgement
 * ("do I care about losing that?"), which survives translation; the wayfinding is done by vanilla's own
 * red. That is a trade, not a free win, and it is the owner's call to reverse.</p>
 *
 * <p><b>Maintenance: none, and that is the point.</b> This was a hand-transcribed table until the 1.21.8 /
 * 1.21.11 ports, and the ports are what proved a transcription cannot be maintained by intent. 1.21.11
 * reworded three of the 34 entries and added 34 more; the copy was correct when written and wrong when
 * shipped, and no compiler, test, or reviewer would have said so — only a Russian player looking at an
 * English menu. Reading Mojang's own file removes the second copy, and with it the only thing that could
 * drift. A version bump now costs nothing here.</p>
 */
public final class KeyConflicts {
    private KeyConflicts() {}

    /** Club's own bindings — excluded from the scan; the bind namespaces already keep those unique. */
    private static final String CLUB_PREFIX = "key.club.";

    /**
     * The path vanilla's own {@code Language.create()} reads to build the default language. Measured, not
     * assumed: {@code javap -c net/minecraft/util/Language} in 1.21.1, 1.21.6, 1.21.8 and 1.21.11 all show
     * {@code ldc "/assets/minecraft/lang/en_us.json"} followed by {@code Class.getResourceAsStream}. The file
     * ships INSIDE the client jar in all four (434 / 468 / 468 / 493 KB, re-measured 2026-07-22), and it is the fallback every
     * untranslated string in the game resolves through — so if this resource were ever missing or moved, the
     * game would not reach our code to care.
     */
    private static final String VANILLA_EN_US = "/assets/minecraft/lang/en_us.json";

    /**
     * Vanilla's binding names, exactly as its own en_us.json spells them — <b>read from the game, not copied
     * from it</b>.
     *
     * <p>This used to be 34 hand-transcribed entries, and the transcription was a fair copy of 1.21.1. The
     * multiversion work is what proved a copy cannot hold: 1.21.11 renamed "Walk Forwards" to "Walk Forward"
     * and "Walk Backwards" to "Walk Backward", dropped the "(Spectators)" qualifier, and added 34 bindings
     * that did not exist when the table was written — every one of which would have fallen through to
     * {@code Text.translatable()} and printed <i>Russian in an English menu</i>, which is the single failure
     * this class exists to prevent. The table was not wrong when written; it was wrong by the time it shipped,
     * and nothing in Java could have said so.
     *
     * <p>Reading Mojang's file instead removes the drift rather than detecting it: there is no second copy to
     * disagree with the first, on any version, including the ones that do not exist yet. The names are also
     * exactly right by construction — the strings on the player's own Controls screen when that screen is in
     * English.
     *
     * <p>Loaded lazily (holder idiom) because it costs a ~0.5 MB JSON parse and a keybinding conflict is a
     * thing the player hits rarely, at menu time, never in the frame loop. Failure to read is not fatal: an
     * empty map means every name falls back exactly as an unknown mod's binding already does.</p>
     */
    private static final class Vanilla {
        static final Map<String, String> EN = load();

        private static Map<String, String> load() {
            try (InputStream in = KeyConflicts.class.getResourceAsStream(VANILLA_EN_US)) {
                if (in == null) return Map.of();
                JsonObject json = new Gson().fromJson(
                        new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
                Map<String, String> out = new HashMap<>();
                for (String k : json.keySet()) {
                    if (isBindingKey(k)) out.put(k, json.get(k).getAsString());
                }
                return Map.copyOf(out);
            } catch (Exception e) {
                return Map.of();
            }
        }
    }

    /**
     * A binding ACTION's translation key — not a key NAME ({@code key.keyboard.*}, {@code key.mouse.*}) and
     * not a category header. The header prefix is itself version-dependent: 1.21.1 spells it
     * {@code key.categories.*} and 1.21.11 spells it {@code key.category.minecraft.*}, so both are excluded
     * by name. Getting this wrong costs nothing at runtime — a header is never a binding id, so a stray entry
     * would simply never be looked up — but the map is smaller and honest this way.
     */
    static boolean isBindingKey(String k) {
        return k.startsWith("key.")
                && !k.startsWith("key.keyboard")
                && !k.startsWith("key.mouse")
                && !k.startsWith("key.categories")
                && !k.startsWith("key.category.");
    }

    /**
     * The other action already bound to {@code boundTranslationKey}, named in English, or null when the key
     * is Club's alone. Unbound keys never conflict.
     */
    public static String other(String boundTranslationKey) {
        if (boundTranslationKey == null) return null;
        if (InputUtil.UNKNOWN_KEY.getTranslationKey().equals(boundTranslationKey)) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null || mc.options.allKeys == null) return null;
        for (KeyBinding kb : mc.options.allKeys) {
            if (kb == null || kb.isUnbound()) continue;
            if (id(kb).startsWith(CLUB_PREFIX)) continue;
            if (boundTranslationKey.equals(kb.getBoundKeyTranslationKey())) return name(kb);
        }
        return null;
    }

    /**
     * A binding's name for an English UI: vanilla's own English word for it, or — for a binding belonging to
     * some OTHER mod, whose en_us we do not have — the name that mod's own game gives it.
     *
     * <p>That last case is the one place a non-English string can still reach the menu, and it is deliberate.
     * We have exactly two things we could print for a third-party binding on a Russian client: the name the
     * player's own Controls screen uses, or a de-camel-cased translation key ("key.freecam.toggle" →
     * "Freecam Toggle") that we invented and that may match nothing anywhere. Inventing a name for a string
     * we do not have is the failure this class exists to prevent, so we print the one the game itself uses.
     * It is the rare branch — the conflict the player actually hits is with vanilla, on a key vanilla already
     * owns.</p>
     */
    private static String name(KeyBinding kb) {
        String en = Vanilla.EN.get(id(kb));
        return en != null ? en : Text.translatable(id(kb)).getString();
    }

    /**
     * A binding's identifying translation key ("key.jump", "key.club.zoom").
     *
     * <p>1.21.9 renamed {@code KeyBinding.getTranslationKey()} to {@code getId()} — the same method
     * ({@code method_1431} in both), renamed rather than replaced, measured across all eleven mappings
     * 1.21.1..1.21.11. The STRING is unchanged, which is what matters here: the names map is keyed on these
     * values and {@link #CLUB_PREFIX} is matched against them, so both keep working untouched.
     *
     * <p>Note this is {@code KeyBinding}'s method, not {@code InputUtil.Key}'s — {@code Key.getTranslationKey}
     * ("key.keyboard.k") was not touched by that rename and is still called by its own name above.
     */
    private static String id(KeyBinding kb) {
        //? if <1.21.9 {
        return kb.getTranslationKey();
        //?} else {
        /*return kb.getId();*/
        //?}
    }

    /** Test seam: the names actually parsed out of the running version's en_us.json. Already immutable. */
    public static Map<String, String> vanillaNames() {
        return Vanilla.EN;
    }
}
