package com.club.modules.itemscroll;

import com.club.config.ClubConfig;

import java.util.Map;

/**
 * The live gesture map: {@link Gestures}' rules over the real config, persisted on every change. Thin on
 * purpose — everything worth testing lives in {@link Gestures}, which never touches Minecraft.
 */
public final class ItemScrollBinds {
    private ItemScrollBinds() {}

    private static Map<String, String> map() { return ClubConfig.get().itemScroll.gestures; }

    /** The gesture bound to this action, or null when unbound. */
    public static Gesture get(ScrollAction action) { return Gestures.get(map(), action); }

    /** The action this gesture fires, or null. */
    public static ScrollAction actionFor(Gesture gesture) { return Gestures.actionFor(map(), gesture); }

    /** Assign, stealing the gesture from any other action. Returns the action it was taken from, or null. */
    public static ScrollAction set(ScrollAction action, Gesture gesture) {
        ScrollAction stolenFrom = Gestures.set(map(), action, gesture);
        ClubConfig.save();
        return stolenFrom;
    }

    /** Unbind this action. */
    public static void clear(ScrollAction action) {
        Gestures.clear(map(), action);
        ClubConfig.save();
    }
}
