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
 * Per-module toggle keybinds (v0.1 kit, Stage 43). The config maps module NAME → InputUtil
 * translation key; every client tick with no screen open, a pressed-edge on a bound key toggles its
 * module through the same {@link MenuContent.Module} setters the menu uses (so mirrors like
 * Fullbright's static flag stay in sync and every toggle persists). Keys parse once per unique
 * translation string ({@link #KEYS} — a rebind writes a new string and naturally re-parses).
 * Keyboard keys only: the capture UI in the menu popover assigns from keyPressed.
 */
public final class ModuleBinds {
    private ModuleBinds() {}

    private static List<MenuContent.Module> modules;
    private static final Map<String, InputUtil.Key> KEYS = new HashMap<>();
    private static final Set<String> down = new HashSet<>();   // bound keys currently held (edge detect)

    /** Build the module registry once — record closures point at the live ClubConfig. */
    public static void init() {
        java.util.ArrayList<MenuContent.Module> flat = new java.util.ArrayList<>();
        for (MenuContent.Category cat : MenuContent.build(() -> {}))
            for (MenuContent.Module m : cat.modules())
                if (m.hasToggle()) flat.add(m);
        modules = flat;
    }

    /** END_CLIENT_TICK: fire pressed-edges of bound keys. Screens own the keyboard — skip. */
    public static void tick(MinecraftClient mc) {
        if (modules == null || mc.currentScreen != null) return;
        Map<String, String> binds = ClubConfig.get().moduleBinds;
        if (binds.isEmpty()) { down.clear(); return; }
        long handle = mc.getWindow().getHandle();
        for (MenuContent.Module m : modules) {
            String t = binds.get(m.name());
            if (t == null) continue;
            InputUtil.Key key = KEYS.computeIfAbsent(t, InputUtil::fromTranslationKey);
            if (key.getCategory() != InputUtil.Type.KEYSYM) continue;
            boolean isDown = InputUtil.isKeyPressed(handle, key.getCode());
            boolean was = down.contains(m.name());
            if (isDown && !was) m.setEnabled(!m.enabled());   // module setters save + sync mirrors
            if (isDown) down.add(m.name()); else down.remove(m.name());
        }
    }

    /** Human label for a bound key ("R", "Left Alt"), or null when unbound. */
    public static String label(String moduleName) {
        String t = ClubConfig.get().moduleBinds.get(moduleName);
        if (t == null) return null;
        return InputUtil.fromTranslationKey(t).getLocalizedText().getString();
    }

    /** Assign (translation key) or clear (null) a module's bind; persists immediately. */
    public static void set(String moduleName, String translationKey) {
        if (translationKey == null) ClubConfig.get().moduleBinds.remove(moduleName);
        else ClubConfig.get().moduleBinds.put(moduleName, translationKey);
        ClubConfig.save();
    }
}
