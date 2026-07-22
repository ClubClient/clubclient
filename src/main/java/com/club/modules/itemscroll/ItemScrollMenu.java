package com.club.modules.itemscroll;

import com.club.config.ClubConfig;
import com.club.policy.ServerFeature;
import com.club.policy.ServerPolicy;
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

    /**
     * The card, or NULL where the server we are on forbids item scrolling.
     *
     * <p>Null rather than a card that is lit and inert, and rather than a card carrying an explanatory
     * notice: on such a server this client simply IS a build without Item Scroll, which is exactly what the
     * owner asked for — minus the second jar. An absent card states nothing, so it cannot lie; a lit card
     * doing nothing is, in this menu's own words, how a mod earns a "broken" review.</p>
     *
     * <p>This is the module's FACE, not its guard. The gate that actually keeps clicks off the wire lives in
     * {@link ItemScrollModule#active()} and {@link ItemScrollHooks#act(net.minecraft.client.gui.screen.ingame.HandledScreen,
     * ScrollAction, net.minecraft.screen.slot.Slot, boolean)}. Hiding the card alone would be a barrier that
     * stands only at the door — the one mistake this module has already made once.</p>
     */
    public static MenuContent.Module card() {
        if (!ServerPolicy.allows(ServerFeature.ITEM_SCROLL)) return null;
        ClubConfig c = ClubConfig.get();
        return new MenuContent.Module("Item Scroll", "Move items by scrolling instead of clicking them.",
                IconGlyph.ITEM_SCROLL,
                () -> c.itemScroll.enabled,
                v -> { c.itemScroll.enabled = v; ClubConfig.save(); },
                // Settings only — never `enabled`. Reset restores the scroll direction and the factory gesture
                // map; whether the module runs stays where the player put it, on the card (T2).
                () -> { c.itemScroll.reverseScroll = false;
                        c.itemScroll.gestures.clear();      // back to the factory gesture map
                        ClubConfig.save(); },
                List.of(
                    new MenuContent.ToggleSetting("Reverse scroll",
                            () -> c.itemScroll.reverseScroll,
                            v -> { c.itemScroll.reverseScroll = v; ClubConfig.save(); }),
                    // The gesture matrix does not fit a 236px popover sheet, and a new row type would mean
                    // editing the frozen menu package. It opens a screen instead — the HUD editor's road.
                    // "Controls…" — plain, not "gestures" (owner, v0.1.3: "что такое gestures, это жаргон,
                    // пиши просто настройки или управление"). A player knows what "controls" are; nobody but
                    // us ever called a scroll-plus-modifier a "gesture".
                    new MenuContent.ActionSetting("Controls…", () -> {
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
     * <p>A server that forbids item scrolling needs no line here: {@link #card()} returns null there, so
     * there is no card to explain — see its javadoc.</p>
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
