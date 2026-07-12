package com.club.modules.binds;

import com.club.config.ClubConfig;
import com.club.ui.menu.MenuContent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-module toggle keybinds (v0.1 kit, Stage 43; hardened Stage 45). The config maps module NAME →
 * InputUtil translation key; every client tick a pressed-EDGE on a bound key toggles its module
 * through the same {@link MenuContent.Module} setters the menu uses (so mirrors like Fullbright's
 * static flag stay in sync and every toggle persists).
 *
 * <p>Robustness (Stage 45): {@code InputUtil.fromTranslationKey} THROWS on a malformed string, so a
 * hand-edited/typo'd bind value would otherwise crash the client every tick. Parsing goes through
 * {@link #key} which caches, catches, and drops a bad value from the config once (self-heal — the
 * codebase already self-heals hand-edited config elsewhere). The held-key edge set is tracked EVEN
 * while a screen is open (toggles only fire in-world), so closing a screen with a bound key still
 * held never reads as a fresh press.</p>
 */
public final class ModuleBinds {
    private ModuleBinds() {}

    private static List<MenuContent.Module> modules;
    private static final Map<String, InputUtil.Key> KEYS = new HashMap<>();
    private static final Set<String> BAD = new HashSet<>();       // strings that failed to parse (don't retry)
    private static final Set<String> down = new HashSet<>();      // bound keys currently held (edge detect)

    /** Build the module registry once — record closures point at the live ClubConfig. HOLD modules
     *  (Zoom, Freelook — see {@link HoldKeys}) are excluded: their key is the hold key, and a toggle
     *  bind on the SAME key flipped the module off on the very press that was meant to act (Stage 58).
     *  Excluding them here also makes any stale config entry inert, not just unreachable. */
    public static void init() {
        java.util.ArrayList<MenuContent.Module> flat = new java.util.ArrayList<>();
        for (MenuContent.Category cat : MenuContent.build(() -> {}))
            for (MenuContent.Module m : cat.modules())
                if (m.hasToggle() && !HoldKeys.isHold(m.name())) flat.add(m);
        modules = flat;
        // Reconcile the two namespaces once the hold keys exist (the config migration can't: it runs
        // before the bindings are registered). An OLD file can hold e.g. {"Fullbright":"key.keyboard.c"}
        // while Zoom is held with C — one press would zoom AND toggle Fullbright, the very bug Stage 58
        // closes. The hold key wins (it has a factory default to fall back on); the toggle bind is dropped.
        for (String hold : HoldKeys.NAMES) {
            var kb = HoldKeys.of(hold);
            if (kb != null && !kb.isUnbound()) releaseKey(kb.getBoundKeyTranslationKey());
        }
    }

    /** Parse a translation key, cached; a malformed value is remembered as BAD and treated as unbound
     *  (never throws — a typo in the config file must not crash the client). */
    private static InputUtil.Key key(String t) {
        if (t == null || BAD.contains(t)) return null;
        InputUtil.Key k = KEYS.get(t);
        if (k != null) return k;
        try { k = InputUtil.fromTranslationKey(t); }
        catch (RuntimeException e) { BAD.add(t); return null; }
        KEYS.put(t, k);
        return k;
    }

    /** END_CLIENT_TICK: fire pressed-edges of bound keys. Toggles only in-world (no screen); the held
     *  set is tracked always, so a screen close with a key still held is not a fresh press. */
    public static void tick(MinecraftClient mc) {
        if (modules == null) return;
        Map<String, String> binds = ClubConfig.get().moduleBinds;
        if (binds.isEmpty()) { down.clear(); return; }
        boolean screen = mc.currentScreen != null;
        long handle = mc.getWindow().getHandle();
        boolean healed = false;
        for (MenuContent.Module m : modules) {
            String t = binds.get(m.name());
            if (t == null) { down.remove(m.name()); continue; }
            InputUtil.Key key = key(t);
            if (key == null) { binds.remove(m.name()); healed = true; down.remove(m.name()); continue; }  // drop junk
            if (key.getCategory() != InputUtil.Type.KEYSYM) { down.remove(m.name()); continue; }
            // A key a HOLD module is using never toggles as well: vanilla's Controls screen can rebind
            // zoom/freelook onto a key a toggle bind owns, behind the popover's back (Stage 58).
            if (HoldKeys.usesKey(t)) { down.remove(m.name()); continue; }
            boolean isDown = InputUtil.isKeyPressed(handle, key.getCode());
            boolean was = down.contains(m.name());
            if (isDown && !was && !screen) m.setEnabled(!m.enabled());   // module setters save + sync mirrors
            if (isDown) down.add(m.name()); else down.remove(m.name());
        }
        if (healed) ClubConfig.save();
    }

    /** Human label for a bound key ("K", "Left Shift", "Space"), or null when unbound / unparseable.
     *  ENGLISH, derived from the translation key (see {@link com.club.util.KeyNames}). */
    public static String label(String moduleName) {
        String t = ClubConfig.get().moduleBinds.get(moduleName);
        if (t == null || key(t) == null) return null;   // key() also validates (unparseable → unbound)
        return com.club.util.KeyNames.english(t);
    }

    /** Assign (translation key) or clear (null) a module's bind; persists immediately. Assigning a
     *  key already held by ANOTHER Club action steals it (Stage 46, widened to hold keys in 58) —
     *  one press must never drive two things (the tick loop would fire both). */
    public static void set(String moduleName, String translationKey) {
        Map<String, String> binds = ClubConfig.get().moduleBinds;
        if (translationKey == null) binds.remove(moduleName);
        else {
            binds.entrySet().removeIf(e -> !e.getKey().equals(moduleName) && translationKey.equals(e.getValue()));
            HoldKeys.releaseKey(translationKey);   // …and from Zoom/Freelook's hold key, if it was theirs
            binds.put(moduleName, translationKey);
        }
        down.remove(moduleName);   // a fresh bind must not inherit a stale held-edge
        ClubConfig.save();
    }

    /** True if this module can carry a toggle bind at all (hold modules can't — see {@link HoldKeys}). */
    public static boolean tracks(String moduleName) {
        if (modules == null) return false;
        for (MenuContent.Module m : modules) if (m.name().equals(moduleName)) return true;
        return false;
    }

    /** Drop every toggle bind on {@code translationKey} — called when a HOLD key claims it. */
    public static void releaseKey(String translationKey) {
        if (translationKey == null) return;
        Map<String, String> binds = ClubConfig.get().moduleBinds;
        if (binds.values().removeIf(translationKey::equals)) {
            down.clear();
            ClubConfig.save();
        }
    }
}
