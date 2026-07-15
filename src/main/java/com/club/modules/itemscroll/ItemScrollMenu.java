package com.club.modules.itemscroll;

import com.club.config.ClubConfig;
import com.club.ui.IconGlyph;
import com.club.ui.menu.MenuContent;
import net.minecraft.client.MinecraftClient;

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
        return new MenuContent.Module("Item Scroll", "Move items by scrolling instead of clicking them.",
                IconGlyph.ITEM_SCROLL,
                () -> c.itemScroll.enabled,
                v -> { c.itemScroll.enabled = v; ClubConfig.save(); },
                () -> { c.itemScroll.enabled = true;
                        c.itemScroll.reverseScroll = false;
                        c.itemScroll.gestures.clear();      // back to the factory gesture map
                        ClubConfig.save(); },
                List.of(
                    new MenuContent.ToggleSetting("Reverse scroll",
                            () -> c.itemScroll.reverseScroll,
                            v -> { c.itemScroll.reverseScroll = v; ClubConfig.save(); }),
                    // The gesture matrix does not fit a 236px popover sheet, and a new row type would mean
                    // editing the frozen menu package. It opens a screen instead — the HUD editor's road.
                    // "Change gestures…", not "Edit gestures…" (owner, v0.1.3 #5: "кто это поймёт?"): "edit"
                    // is what you do to a file; "change" is what a player does to a control.
                    new MenuContent.ActionSetting("Change gestures…", () -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        mc.setScreen(new GestureScreen(mc.currentScreen));   // …and back to the menu on close
                    })));
    }

    /**
     * What the card would otherwise lie about. Two truths, in priority order: a sibling mod owning the
     * same gestures means we are deliberately inert (a lit card doing nothing is how a mod earns a
     * "broken" review), and creative is not handled at all — its screen keeps fake slots behind a
     * different click path, and a half-working creative is worse than an honest gap.
     *
     * <p>Both lines are written for a PLAYER, not for us. "Creative inventory: not handled" was the old
     * second line: "handled" is a word about our code, and it left the player to guess whether that meant
     * broken, unsupported, or dangerous. It means the scroll does nothing there — so it says that. Likewise
     * "X is installed" told the player a fact they already knew (they installed it); what they did not know
     * is why OUR card has gone quiet, which is that X already does this.</p>
     */
    public static String notice() {
        String sibling = ItemScrollModule.sibling();
        if (sibling != null) return "Idle — " + sibling + " does this";
        return ClubConfig.get().itemScroll.enabled ? "Doesn't work in the creative inventory" : null;
    }
}
