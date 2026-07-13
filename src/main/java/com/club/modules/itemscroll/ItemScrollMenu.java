package com.club.modules.itemscroll;

import com.club.config.ClubConfig;
import com.club.ui.IconGlyph;
import com.club.ui.menu.MenuContent;

import java.util.List;

/**
 * The module's face in the Club menu. Lives here, not in {@code MenuContent}: the shared file gets one
 * line under its {@code [SEAM:cards]} anchor and one {@code case} in {@code notice()}, and no logic
 * (docs/NEXT-PLAN.md §4).
 */
public final class ItemScrollMenu {
    private ItemScrollMenu() {}

    public static MenuContent.Module card() {
        ClubConfig c = ClubConfig.get();
        return new MenuContent.Module("Item Scroll", "Move items by scrolling and dragging over slots.",
                IconGlyph.ITEM_SCROLL,
                () -> c.itemScroll.enabled,
                v -> { c.itemScroll.enabled = v; ClubConfig.save(); },
                () -> { c.itemScroll.enabled = true;
                        c.itemScroll.reverseScroll = false;
                        c.itemScroll.gestures.clear();      // back to the factory gesture map
                        ClubConfig.save(); },
                List.of(new MenuContent.ToggleSetting("Reverse scroll",
                        () -> c.itemScroll.reverseScroll,
                        v -> { c.itemScroll.reverseScroll = v; ClubConfig.save(); })));
    }

    /**
     * What the card would otherwise lie about. Two truths, in priority order: a sibling mod owning the
     * same gestures means we are deliberately inert (a lit card doing nothing is how a mod earns a
     * "broken" review), and creative is not handled at all — its screen keeps fake slots behind a
     * different click path, and a half-working creative is worse than an honest gap.
     */
    public static String notice() {
        String sibling = ItemScrollModule.sibling();
        if (sibling != null) return "Idle — " + sibling + " is installed";
        return ClubConfig.get().itemScroll.enabled ? "Creative inventory: not handled" : null;
    }
}
