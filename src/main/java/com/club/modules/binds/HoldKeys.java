package com.club.modules.binds;

import com.club.ClubClient;
import com.club.util.KeyNames;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * HOLD modules (Zoom, Freelook) — the ones whose key is held to act, not tapped to toggle. Their key
 * IS a real vanilla {@link KeyBinding} (so it also shows up in Options → Controls → Club), and the
 * "Hold key" row in their popover rebinds THAT binding.
 *
 * <p>Why this class exists (Stage 58, owner bug: "зум через раз прожимается / отлагивает туда сюда"):
 * before, every module's popover offered a per-module TOGGLE bind ({@link ModuleBinds}). Binding Zoom
 * to C — the very key you hold to zoom — meant one press both zoomed AND flipped the module off, so
 * zoom worked on every OTHER press and visibly bounced back mid-ease. A hold module must not have a
 * toggle bind at all: its key is the hold key. The two namespaces now steal from each other, so one
 * physical key can never drive two Club actions.</p>
 */
public final class HoldKeys {
    private HoldKeys() {}

    /** The hold modules, by menu name. */
    static final String[] NAMES = {"Zoom", "Freelook"};

    /** Module names whose "hotkey" is a hold key, not a toggle bind. */
    public static boolean isHold(String moduleName) {
        for (String n : NAMES) if (n.equals(moduleName)) return true;
        return false;
    }

    /** The vanilla binding a hold module acts on, or null. Resolved lazily — the bindings only exist
     *  after {@code ClubClient.onInitializeClient} has registered them. */
    public static KeyBinding of(String moduleName) {
        return switch (moduleName) {
            case "Zoom" -> ClubClient.zoomKey;
            case "Freelook" -> ClubClient.freelookKey;
            default -> null;
        };
    }

    /** English name of the currently bound key ("C", "Left Alt"), or null when unbound. */
    public static String label(String moduleName) {
        KeyBinding kb = of(moduleName);
        if (kb == null || kb.isUnbound()) return null;
        return KeyNames.english(kb.getBoundKeyTranslationKey());
    }

    /** Bind the hold key to {@code key} (null / UNKNOWN = unbind), stealing it from any other Club
     *  action that already uses it, then persist options.txt. */
    public static void set(String moduleName, InputUtil.Key key) {
        KeyBinding kb = of(moduleName);
        if (kb == null) return;
        InputUtil.Key k = (key == null) ? InputUtil.UNKNOWN_KEY : key;
        if (k != InputUtil.UNKNOWN_KEY) steal(moduleName, k.getTranslationKey());
        apply(kb, k);
    }

    /** Restore the hold key to its factory default (Zoom → C, Freelook → Left Alt). */
    public static void reset(String moduleName) {
        KeyBinding kb = of(moduleName);
        if (kb == null) return;
        InputUtil.Key def = kb.getDefaultKey();
        steal(moduleName, def.getTranslationKey());
        apply(kb, def);
    }

    /** The translation key this hold module's key sits on, or null when unbound — for the conflict scan. */
    public static String boundKey(String moduleName) {
        KeyBinding kb = of(moduleName);
        return (kb == null || kb.isUnbound()) ? null : kb.getBoundKeyTranslationKey();
    }

    /**
     * One key = one CLUB action, enforced every tick instead of only at capture (Stage 62).
     *
     * <p>The popover refuses to bind a module to the key that opens the menu — but vanilla's own Controls
     * screen lists all three Club bindings side by side and will happily put Zoom on Right Shift. That
     * used to be a trap with no way out: the menu key and the hold key then raced for vanilla's
     * single-winner dispatch map, and if the menu key lost, the menu could not be opened — and the bind
     * could only be fixed from the menu. The menu key wins here because it is the only way back in.</p>
     */
    public static void reconcileMenuKey() {
        KeyBinding menu = ClubClient.openMenuKey;
        if (menu == null || menu.isUnbound()) return;
        String t = menu.getBoundKeyTranslationKey();
        for (String name : NAMES) {
            KeyBinding kb = of(name);
            if (kb != null && !kb.isUnbound() && t.equals(kb.getBoundKeyTranslationKey()))
                apply(kb, InputUtil.UNKNOWN_KEY);
        }
        ModuleBinds.releaseKey(t);
    }

    /** True if a hold key currently sits on this physical key. */
    public static boolean usesKey(String translationKey) {
        if (translationKey == null) return false;
        for (String name : NAMES) {
            KeyBinding kb = of(name);
            if (kb != null && !kb.isUnbound() && translationKey.equals(kb.getBoundKeyTranslationKey()))
                return true;
        }
        return false;
    }

    /** Unbind any hold key using {@code translationKey} — called when a module TOGGLE bind claims it. */
    public static void releaseKey(String translationKey) {
        if (translationKey == null) return;
        for (String name : NAMES) {
            KeyBinding kb = of(name);
            if (kb != null && translationKey.equals(kb.getBoundKeyTranslationKey()))
                apply(kb, InputUtil.UNKNOWN_KEY);
        }
    }

    /** One key = one Club action: drop the toggle bind on this key and unbind the OTHER hold key using it. */
    private static void steal(String moduleName, String translationKey) {
        ModuleBinds.releaseKey(translationKey);
        for (String other : new String[] {"Zoom", "Freelook"}) {
            if (other.equals(moduleName)) continue;
            KeyBinding kb = of(other);
            if (kb != null && translationKey.equals(kb.getBoundKeyTranslationKey()))
                apply(kb, InputUtil.UNKNOWN_KEY);
        }
    }

    /** Rebind + refresh vanilla's key→binding map + persist (mirrors what the Controls screen does).
     *  A no-op rebind returns early: {@code options.write()} is a SYNCHRONOUS options.txt rewrite, and
     *  nothing above should be able to drive it in a loop. */
    private static void apply(KeyBinding kb, InputUtil.Key key) {
        if (key.getTranslationKey().equals(kb.getBoundKeyTranslationKey())) return;   // already there
        kb.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) mc.options.write();
    }
}
