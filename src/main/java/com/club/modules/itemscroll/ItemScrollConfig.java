package com.club.modules.itemscroll;

import java.util.HashMap;
import java.util.Map;

/**
 * The Item Scroll config section, hung off {@link com.club.config.ClubConfig} under its
 * {@code [SEAM:config]} anchor. Lives in the module's own package so two parallel workstreams add one
 * line each to the shared file instead of both editing the same block.
 *
 * <p>No config {@code version} bump goes with this: a section missing from an older file keeps the
 * defaults its field initialisers set, so Gson migrates it for free — and two branches both bumping
 * 9 → 10 would be a guaranteed conflict on top of a broken migrate() chain.</p>
 */
public final class ItemScrollConfig {
    public boolean enabled = true;         // owner's call: on by default, like the mod everyone already runs
    public boolean reverseScroll = false;  // up = out of the hovered inventory; this flips it

    /**
     * {@link ScrollAction} name → {@link Gesture} string ("CTRL+SHIFT+SCROLL"). Two states are NOT the
     * same and the map has to tell them apart: a key that is <b>absent</b> means "never touched, use the
     * default", while a key mapped to the <b>empty string</b> means the player deliberately cleared that
     * row. Without the empty-string form, clearing a gesture would silently resurrect its default on the
     * next load.
     */
    public Map<String, String> gestures = new HashMap<>();
}
