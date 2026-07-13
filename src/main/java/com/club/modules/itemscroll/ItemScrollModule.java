package com.club.modules.itemscroll;

import com.club.config.ClubConfig;
import com.club.modules.ModuleNotices;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Item Scroll — moving items by mouse gesture inside any container screen (the behaviour players call
 * "item scroller"). Instant, on by default, no warnings: it is a utility, not a cheat.
 *
 * <p>This class is the module's state: is it allowed to act at all. The gesture grammar lives in
 * {@link Gesture}/{@link ScrollAction}, the slot maths in {@code SlotPlan}, the event wiring in
 * {@code ItemScrollHooks}.</p>
 */
public final class ItemScrollModule {
    private ItemScrollModule() {}

    /**
     * Mods that already own these gestures. If one is installed we stand down completely — both mods
     * would move the same stack on one Shift+scroll, and a double move is a desync the player reads as
     * Club's bug. The popover says so out loud rather than sitting there lit and doing nothing.
     */
    private static final String[][] SIBLINGS = {
            {"itemscroller",           "Item Scroller"},
            {"mousetweaks",            "Mouse Tweaks"},
            {"inventoryprofilesnext",  "Inventory Profiles Next"},
            {"mousewheelie",           "Mouse Wheelie"},
    };

    /** Client init: hook the screen events, and contribute the card's honesty line from here so that
     *  MenuContent stays untouched. */
    public static void init() {
        ModuleNotices.register("Item Scroll", ItemScrollMenu::notice);
        ItemScrollHooks.register();
    }

    private static String sibling;
    private static boolean siblingResolved;

    /** The name of the installed sibling mod, or null. Resolved once — the mod list cannot change at runtime. */
    public static String sibling() {
        if (!siblingResolved) {
            siblingResolved = true;
            FabricLoader loader = FabricLoader.getInstance();
            for (String[] s : SIBLINGS) if (loader.isModLoaded(s[0])) { sibling = s[1]; break; }
        }
        return sibling;
    }

    /** Whether a gesture may fire right now: the module is on and no sibling mod owns the same gestures. */
    public static boolean active() {
        return ClubConfig.get().itemScroll.enabled && sibling() == null;
    }
}
