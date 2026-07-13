package com.club.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Why a module is standing down, contributed BY THE MODULE — not by the menu.
 *
 * <p>{@code MenuContent.notice()} started life as a switch over module names (Stage 62: a card that is lit but
 * idle has to say why). That was fine while the menu owned every module. It stops being fine the moment two
 * workstreams add modules in parallel: each of them needs a line in that switch, the switch lives in a file the
 * owner has frozen, and two branches editing two adjacent lines of the same block is exactly the merge fight the
 * anchors exist to prevent.</p>
 *
 * <p>So the switch keeps its hard-coded cases (they are the menu's own business) and falls through to here. A
 * module registers its own notice from its own package, at init, and the menu file is never touched again:</p>
 *
 * <pre>{@code
 * ModuleNotices.register("Performance", () ->
 *         FabricLoader.getInstance().isModLoaded("sodium") ? "Entity culling: handled by Sodium" : null);
 * }</pre>
 *
 * <p>The supplier is called every frame the popover is open, so it must be cheap and it must read LIVE state —
 * a notice that goes stale is worse than no notice, because the card is then lying with confidence.</p>
 */
public final class ModuleNotices {
    private ModuleNotices() {}

    private static final Map<String, Supplier<String>> NOTICES = new LinkedHashMap<>();

    /** Register (or replace) the notice line for a module. {@code notice} returns null when all is well. */
    public static void register(String moduleName, Supplier<String> notice) {
        if (moduleName == null || notice == null) return;
        NOTICES.put(moduleName, notice);
    }

    /** The module's current notice, or null. Never throws: a broken notice must not take the menu down with it. */
    public static String get(String moduleName) {
        Supplier<String> s = NOTICES.get(moduleName);
        if (s == null) return null;
        try { return s.get(); } catch (Throwable t) { return null; }
    }
}
