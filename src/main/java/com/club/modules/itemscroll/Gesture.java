package com.club.modules.itemscroll;

import java.util.Locale;

/**
 * A gesture: modifiers plus one mouse input. {@code SCROLL} is ONE input covering both wheel directions —
 * the direction is an argument to the action ("out of this slot" / "into this inventory"), not part of the
 * binding's identity. Split it into SCROLL_UP/SCROLL_DOWN and the reference's own grammar ("scroll = move
 * one") stops being expressible as a single binding, and every conflict rule doubles.
 *
 * <p>Stored as a string in the same shape as {@code moduleBinds} ("CTRL+SHIFT+SCROLL"), and parsed the
 * same way: a hand-edited typo returns null instead of throwing. The config file is a text file, and a
 * text file will be edited by hand.</p>
 */
public record Gesture(int mods, GestureInput input) {

    public static final int SHIFT = 1, CTRL = 2, ALT = 4;

    public static Gesture of(GestureInput input, int mods) { return new Gesture(mods, input); }

    public boolean shift() { return (mods & SHIFT) != 0; }
    public boolean ctrl()  { return (mods & CTRL)  != 0; }
    public boolean alt()   { return (mods & ALT)   != 0; }

    /** The modifier mask of what is held right now, in the same bit order. */
    public static int mods(boolean shift, boolean ctrl, boolean alt) {
        return (shift ? SHIFT : 0) | (ctrl ? CTRL : 0) | (alt ? ALT : 0);
    }

    /** Config form: "CTRL+SHIFT+SCROLL". Canonical order, so two equal gestures never differ as strings. */
    public String store() {
        StringBuilder sb = new StringBuilder();
        if (ctrl())  sb.append("CTRL+");
        if (shift()) sb.append("SHIFT+");
        if (alt())   sb.append("ALT+");
        return sb.append(input.name()).toString();
    }

    /** Reads any order and any case; returns null for junk (a broken config line is not a crash). */
    public static Gesture parse(String s) {
        if (s == null || s.isBlank()) return null;
        int mods = 0;
        GestureInput input = null;
        for (String part : s.toUpperCase(Locale.ROOT).split("\\+")) {
            switch (part.trim()) {
                case "CTRL", "CONTROL" -> mods |= CTRL;
                case "SHIFT"           -> mods |= SHIFT;
                case "ALT"             -> mods |= ALT;
                case ""                -> { }
                default -> {
                    GestureInput i = GestureInput.parse(part.trim());
                    if (i == null || input != null) return null;   // junk, or two inputs in one gesture
                    input = i;
                }
            }
        }
        return input == null ? null : new Gesture(mods, input);
    }

    /** What the chip in the editor reads: "Ctrl + Shift + Scroll". */
    public String label() {
        StringBuilder sb = new StringBuilder();
        if (ctrl())  sb.append("Ctrl + ");
        if (shift()) sb.append("Shift + ");
        if (alt())   sb.append("Alt + ");
        return sb.append(input.label()).toString();
    }
}
