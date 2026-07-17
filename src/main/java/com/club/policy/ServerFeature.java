package com.club.policy;

/**
 * A Club feature a server may forbid by rule. One constant per feature; the table in {@link ServerPolicy}
 * says where each one is restricted.
 *
 * <p>Only features that are actually forbidden somewhere belong here. This is not a capability list.</p>
 */
public enum ServerFeature {
    /** Moving items by mouse gesture — {@code com.club.modules.itemscroll}. */
    ITEM_SCROLL,

    /**
     * Swinging the camera around the player without turning them — {@code com.club.modules.freelook}.
     *
     * <p>Listed because a server names <b>Perspective Mod</b> in its ban list, and that mod's whole function
     * is this one: look around while your aim and your movement keep pointing where they were. Ours is not
     * an imitation of it — it is the same thing, so the name it ships under does not matter. What a moderator
     * sees is a player whose head is watching his back while he runs.</p>
     */
    FREELOOK
}
