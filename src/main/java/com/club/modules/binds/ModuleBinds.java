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

    /**
     * The modules that get a key at all. THE OWNER'S RULE, VERBATIM: a key earns its row only if a player
     * would plausibly flip it MID-FIGHT, without opening a menu — "она нужна только для функций, которые
     * вероятно будут переключать во время боя".
     *
     * <p>A "Toggle key" row on all thirteen cards was clutter for its own sake. Nobody rebinds their hands
     * mid-duel, picks a new swing animation between hits, or hot-keys the HUD editor. Three modules survive
     * that test, and Zoom and Freelook get a HOLD key instead (see {@link HoldKeys}) — which is not a toggle
     * at all: the key IS the feature.
     *
     * <p><b>This set is also the READ gate, not just the door.</b> {@link #init} registers only these, so a
     * bind left in an old config for a module that no longer has a row is INERT — it cannot fire. That is not
     * tidiness, it is the difference between removing a control and creating a trap: a key that still works
     * with no UI left to un-bind it is exactly the "the menu can no longer be locked away from you" bug that
     * v0.1.2 shipped a fix for, wearing a new costume. The migration (ClubConfig v10) then deletes those
     * entries outright, so the config does not carry a lie either.
     */
    public static final Set<String> KEYED = Set.of("Toggle Sprint", "Fullbright");

    /** Does this module show a key row in its popover? True for {@link #KEYED} and for the HOLD modules,
     *  whose row rebinds the real vanilla binding they are held on. The single source of truth for the UI. */
    public static boolean hasKeyRow(String moduleName) {
        return KEYED.contains(moduleName) || HoldKeys.isHold(moduleName);
    }

    /** Build the module registry once — record closures point at the live ClubConfig. HOLD modules
     *  (Zoom, Freelook — see {@link HoldKeys}) are excluded: their key is the hold key, and a toggle
     *  bind on the SAME key flipped the module off on the very press that was meant to act (Stage 58).
     *  Excluding them here also makes any stale config entry inert, not just unreachable. */
    public static void init() {
        java.util.ArrayList<MenuContent.Module> flat = new java.util.ArrayList<>();
        for (MenuContent.Category cat : MenuContent.build(() -> {}))
            for (MenuContent.Module m : cat.modules())
                if (m.hasToggle() && KEYED.contains(m.name()) && !HoldKeys.isHold(m.name())) flat.add(m);
        modules = flat;
    }

    private static boolean reconciled;

    /** Reconcile the two namespaces ONCE, on the first tick. An old file can hold e.g.
     *  {"Fullbright":"key.keyboard.c"} while Zoom is held with C — one press would zoom AND toggle
     *  Fullbright, the very bug Stage 58 closes. The hold key wins (it has a factory default to fall
     *  back on); the toggle bind is dropped.
     *
     *  <p>Deliberately NOT in init() (Stage 59 audit): a client entrypoint runs while the game is still
     *  starting, and the KeyBinding may still carry its FACTORY key rather than the one from options.txt
     *  — we'd reconcile against C / Left Alt and delete a bind the player legitimately owns. By the
     *  first client tick the options are applied and the bound keys are the real ones. */
    private static void reconcileOnce() {
        reconciled = true;
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
        if (!reconciled) reconcileOnce();   // first tick: the bound keys are the real (options.txt) ones now
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

    /** Human label for a bound key ("K", "Left Shift", "Space"), or null when unbound / unparseable /
     *  unfireable. ENGLISH, derived from the translation key (see {@link com.club.util.KeyNames}).
     *
     *  <p>The KEYSYM check mirrors {@link #tick}'s (Stage 62): tick() only ever fires keyboard keys, so a
     *  hand-edited {@code "key.mouse.4"} used to render as a live "Mouse 4" on the row while nothing on
     *  earth could make it toggle. A key the row cannot fire is not a bind — it reads "Not set".</p> */
    public static String label(String moduleName) {
        String t = ClubConfig.get().moduleBinds.get(moduleName);
        InputUtil.Key k = key(t);                        // key() also validates (unparseable → unbound)
        if (k == null || k.getCategory() != InputUtil.Type.KEYSYM) return null;
        return com.club.util.KeyNames.english(t);
    }

    /** The translation key this module's toggle sits on, or null — for the conflict scan (KeyConflicts). */
    public static String boundKey(String moduleName) {
        String t = ClubConfig.get().moduleBinds.get(moduleName);
        return key(t) == null ? null : t;
    }

    /** The HOLD module currently sitting on this module's toggle key ("Zoom" / "Freelook"), or null.
     *
     *  <p>{@link #tick} refuses to fire a toggle whose key a hold module owns — correct (one press must
     *  not drive two actions), but it did it SILENTLY: vanilla's Controls screen can move Zoom onto a key
     *  a toggle bind already had, and the popover kept displaying that key as if tapping it still worked.
     *  The row has to say who took it (Stage 62). */
    public static String shadowedBy(String moduleName) {
        String t = boundKey(moduleName);
        if (t == null) return null;
        for (String name : HoldKeys.NAMES) {
            net.minecraft.client.option.KeyBinding kb = HoldKeys.of(name);
            if (kb != null && !kb.isUnbound() && t.equals(kb.getBoundKeyTranslationKey())) return name;
        }
        return null;
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
