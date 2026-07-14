package com.club.modules.binds;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

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
 * <p>So vanilla's bindings are named from OUR OWN copy of vanilla's {@code en_us.json} — the table below,
 * transcribed from {@code assets/minecraft/lang/en_us.json} in the 1.21.1 client jar this mod is pinned to.
 * It costs one hash lookup and about a kilobyte of constants: no language file is loaded, no resource pack
 * is read, nothing is parsed at runtime. Deriving the name from the translation key instead
 * ("key.saveToolbarActivator" → "Save Toolbar Activator") was rejected: it is a guess that happens to be
 * WRONG here — the string vanilla actually shows the player is "Save Hotbar Activator". A name the player
 * cannot find in their game is worse than no name.</p>
 *
 * <p><b>The cost, stated plainly.</b> The player fixes a conflict in Options → Controls, and THAT screen is
 * in their language: a Russian player reading "Save Hotbar Activator" here will not find that row by its
 * name. What saves this is that they do not have to — vanilla paints every duplicate binding red on the
 * Controls screen (see above), so the row finds THEM. The name's job in this popover is the judgement
 * ("do I care about losing that?"), which survives translation; the wayfinding is done by vanilla's own
 * red. That is a trade, not a free win, and it is the owner's call to reverse.</p>
 *
 * <p><b>Maintenance.</b> The table is a copy of another project's data, so it can go stale in exactly one
 * way: a Minecraft version bump that reworded a binding. Re-extract it from the client jar when the target
 * version changes. {@link #vanillaNames()} exposes it so the harness can assert every entry still equals
 * {@code Text.translatable(key).getString()} on an en_us client — an instrument that fails loudly on the
 * version bump instead of quietly showing last year's word.</p>
 */
public final class KeyConflicts {
    private KeyConflicts() {}

    /** Club's own bindings — excluded from the scan; the bind namespaces already keep those unique. */
    private static final String CLUB_PREFIX = "key.club.";

    /**
     * Vanilla's binding names, exactly as its own en_us.json spells them (Minecraft 1.21.1). Not a
     * paraphrase and not a prettified translation key — these are the strings on the player's Controls
     * screen when that screen is in English.
     */
    private static final Map<String, String> VANILLA_EN = Map.ofEntries(
            Map.entry("key.attack",              "Attack/Destroy"),
            Map.entry("key.use",                 "Use Item/Place Block"),
            Map.entry("key.forward",             "Walk Forwards"),
            Map.entry("key.back",                "Walk Backwards"),
            Map.entry("key.left",                "Strafe Left"),
            Map.entry("key.right",               "Strafe Right"),
            Map.entry("key.jump",                "Jump"),
            Map.entry("key.sneak",               "Sneak"),
            Map.entry("key.sprint",              "Sprint"),
            Map.entry("key.drop",                "Drop Selected Item"),
            Map.entry("key.inventory",           "Open/Close Inventory"),
            Map.entry("key.chat",                "Open Chat"),
            Map.entry("key.playerlist",          "List Players"),
            Map.entry("key.pickItem",            "Pick Block"),
            Map.entry("key.command",             "Open Command"),
            Map.entry("key.socialInteractions",  "Social Interactions Screen"),
            Map.entry("key.screenshot",          "Take Screenshot"),
            Map.entry("key.togglePerspective",   "Toggle Perspective"),
            Map.entry("key.smoothCamera",        "Toggle Cinematic Camera"),
            Map.entry("key.fullscreen",          "Toggle Fullscreen"),
            Map.entry("key.spectatorOutlines",   "Highlight Players (Spectators)"),
            Map.entry("key.swapOffhand",         "Swap Item With Off Hand"),
            Map.entry("key.saveToolbarActivator","Save Hotbar Activator"),   // NOT "Save Toolbar Activator"
            Map.entry("key.loadToolbarActivator","Load Hotbar Activator"),
            Map.entry("key.advancements",        "Advancements"),
            Map.entry("key.hotbar.1",            "Hotbar Slot 1"),
            Map.entry("key.hotbar.2",            "Hotbar Slot 2"),
            Map.entry("key.hotbar.3",            "Hotbar Slot 3"),
            Map.entry("key.hotbar.4",            "Hotbar Slot 4"),
            Map.entry("key.hotbar.5",            "Hotbar Slot 5"),
            Map.entry("key.hotbar.6",            "Hotbar Slot 6"),
            Map.entry("key.hotbar.7",            "Hotbar Slot 7"),
            Map.entry("key.hotbar.8",            "Hotbar Slot 8"),
            Map.entry("key.hotbar.9",            "Hotbar Slot 9"));

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
            if (kb.getTranslationKey().startsWith(CLUB_PREFIX)) continue;
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
        String en = VANILLA_EN.get(kb.getTranslationKey());
        return en != null ? en : Text.translatable(kb.getTranslationKey()).getString();
    }

    /** Harness seam: the transcribed table, so a test can prove it still matches vanilla's live en_us. */
    public static Map<String, String> vanillaNames() {
        return VANILLA_EN;   // Map.ofEntries is already immutable
    }
}
