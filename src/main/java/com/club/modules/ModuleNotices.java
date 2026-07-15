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
 * ModuleNotices.register("My Module", () ->
 *         FabricLoader.getInstance().isModLoaded("sodium") ? null : "Install Sodium for entity culling");
 * }</pre>
 *
 * <p>(No module registers a notice today — the perf cards that once did, Particles and Block Entities, are
 * baked in and gone from the menu. The mechanism stays for the next module that needs it.)</p>
 *
 * <p>The supplier is called every frame the popover is open, so it must be cheap and it must read LIVE state —
 * a notice that goes stale is worse than no notice, because the card is then lying with confidence.</p>
 *
 * <h2>How a notice is written</h2>
 *
 * <p>The example above used to read {@code "Entity culling: handled by Sodium"}, and it was the template every
 * new module copied — which is how a whole menu ends up speaking in a register no player uses. A notice is a
 * line of ENGLISH addressed to the person playing:</p>
 * <ul>
 *   <li><b>A consequence, not an implementation.</b> "Creative inventory: not handled" says something about our
 *       code. "Doesn't work in the creative inventory" says what happens to them. "Handled", "supported",
 *       "unimplemented", "N/A" are words about us.</li>
 *   <li><b>Short.</b> It renders as a single-line 12px caption in a 220px sheet ({@code Label} does not wrap),
 *       so a long sentence is not shortened — it is cut off. Roughly forty characters is the room, and the
 *       shipped notices sit under it. The paragraph explaining WHY belongs in the changelog.</li>
 *   <li><b>"Idle — …" when the module is doing nothing at all</b>, matching {@code MenuContent.notice}'s own
 *       lines ("Idle — vanilla Sprint: Toggle is on"). A notice that only warns of a gap does not take the
 *       prefix; the module is still working.</li>
 *   <li>No exclamation marks, no apology, no emoji. The mod states facts calmly and lets the player decide.</li>
 * </ul>
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
