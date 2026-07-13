package com.club.modules.itemscroll;

/** The seven things a gesture can ask for. This is the whole feature set — nothing else moves items. */
public enum ScrollAction {
    MOVE_ONE("Move one"),
    MOVE_STACK("Move stack"),
    MOVE_MATCHING("Move matching"),
    MOVE_EVERYTHING("Move everything"),
    DROP_ONE("Drop one"),
    DROP_STACK("Drop stack"),
    DRAG_MOVE("Drag move");

    private final String label;

    ScrollAction(String label) { this.label = label; }

    /** The row's name in the gesture editor (English — UI strings always are). */
    public String label() { return label; }

    /** A drag needs a button to be HELD: the wheel cannot express it, so its input domain is clicks only. */
    public boolean dragOnly() { return this == DRAG_MOVE; }
}
