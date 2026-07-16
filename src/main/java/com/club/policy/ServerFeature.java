package com.club.policy;

/**
 * A Club feature a server may forbid by rule. One constant per feature; the table in {@link ServerPolicy}
 * says where each one is restricted.
 *
 * <p>Only features that are actually forbidden somewhere belong here. This is not a capability list.</p>
 */
public enum ServerFeature {
    /** Moving items by mouse gesture — {@code com.club.modules.itemscroll}. */
    ITEM_SCROLL
}
