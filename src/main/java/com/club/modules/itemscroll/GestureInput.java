package com.club.modules.itemscroll;

/**
 * The four inputs a gesture can sit on. The middle button is an INPUT only — PICKUP and QUICK_MOVE reject
 * any button other than 0 and 1, so a middle click can never be a slot action; we translate it into 0/1
 * clicks like every other gesture.
 */
public enum GestureInput {
    SCROLL("Scroll"),
    LMB("Left Click"),
    RMB("Right Click"),
    MMB("Middle Click");

    private final String label;

    GestureInput(String label) { this.label = label; }

    public String label() { return label; }

    /** A held button — the only kind of input a drag can be built on. */
    public boolean click() { return this != SCROLL; }

    public static GestureInput parse(String s) {
        for (GestureInput i : values()) if (i.name().equals(s)) return i;
        return null;
    }
}
