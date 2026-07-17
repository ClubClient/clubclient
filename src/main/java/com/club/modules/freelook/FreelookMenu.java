package com.club.modules.freelook;

import com.club.policy.ServerFeature;
import com.club.policy.ServerPolicy;
import com.club.ui.IconGlyph;
import com.club.ui.menu.MenuContent;

import java.util.List;

/**
 * The module's face in the Club menu. Lives here, not in {@code MenuContent}: the shared file gets one line
 * under its {@code [SEAM:cards]} contract and no logic (docs/NEXT-PLAN.md §4) — the same road
 * {@code ItemScrollMenu} took.
 */
public final class FreelookMenu {
    private FreelookMenu() {}

    /**
     * The card, or NULL where the server we are on forbids freelook.
     *
     * <p>Null rather than a card that is lit and inert: on such a server this client simply IS a build
     * without Freelook. An absent card states nothing, so it cannot lie; a lit card doing nothing is how a
     * mod earns a "broken" review.</p>
     *
     * <p><b>This is the FACE, not the guard.</b> The gate that actually keeps the camera on the player's aim
     * lives in {@link FreelookModule#tick}. Hiding the card alone would leave Left Alt working on a server
     * that bans exactly that — a barrier standing at the door while the act goes on behind it. Item Scroll
     * made that mistake once already; it does not get made twice.</p>
     *
     * <p>No on/off toggle (get/set null): a hold module has no off state — the key is the switch. The card
     * exists to name the feature and to carry the "Hold key" row that rebinds Left Alt.</p>
     */
    public static MenuContent.Module card() {
        if (!ServerPolicy.allows(ServerFeature.FREELOOK)) return null;
        return new MenuContent.Module("Freelook", "Hold the freelook key to swing the camera freely.",
                IconGlyph.FREELOOK, null, null, () -> {}, List.of());
    }
}
