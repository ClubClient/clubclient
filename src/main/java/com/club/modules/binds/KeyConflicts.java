package com.club.modules.binds;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

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
 */
public final class KeyConflicts {
    private KeyConflicts() {}

    /** Club's own bindings — excluded from the scan; the bind namespaces already keep those unique. */
    private static final String CLUB_PREFIX = "key.club.";

    /**
     * The other action already bound to {@code boundTranslationKey}, named the way the player's own
     * Controls screen names it (their language, not ours — they will go there to fix it), or null when
     * the key is Club's alone. Unbound keys never conflict.
     */
    public static String other(String boundTranslationKey) {
        if (boundTranslationKey == null) return null;
        if (InputUtil.UNKNOWN_KEY.getTranslationKey().equals(boundTranslationKey)) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null || mc.options.allKeys == null) return null;
        for (KeyBinding kb : mc.options.allKeys) {
            if (kb == null || kb.isUnbound()) continue;
            if (kb.getTranslationKey().startsWith(CLUB_PREFIX)) continue;
            if (boundTranslationKey.equals(kb.getBoundKeyTranslationKey()))
                return Text.translatable(kb.getTranslationKey()).getString();
        }
        return null;
    }
}
