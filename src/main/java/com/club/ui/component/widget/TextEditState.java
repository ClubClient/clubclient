package com.club.ui.component.widget;

/**
 * Pure single-line text-editing model (Stage 31): text + caret + selection span and every operation
 * on them — insert (replacing a selection), char/word backspace and delete, caret moves with
 * Shift-extension, Home/End, select-all, word selection. No rendering, no GLFW, no Minecraft:
 * extracted from the menu's SearchField so the editing state machine is unit-testable (the field
 * keeps only pixel concerns — hit-testing a caret from a click and drawing). Clipboard strings go
 * through {@link #selectedText()} / {@link #insert}; the caller owns the actual clipboard.
 */
public final class TextEditState {
    private String text = "";
    private int caret;            // caret index in [0, text.length()]
    private int selAnchor = -1;   // selection origin; -1 = no selection (caret is a point)

    public String text() { return text; }
    public int caret() { return caret; }

    public boolean hasSelection() { return selAnchor >= 0 && selAnchor != caret; }
    public int selectionStart() { return Math.min(selAnchor, caret); }
    public int selectionEnd() { return Math.max(selAnchor, caret); }
    public String selectedText() { return hasSelection() ? text.substring(selectionStart(), selectionEnd()) : ""; }

    /** Reset to empty (clear button / Esc). */
    public void clear() { text = ""; caret = 0; selAnchor = -1; }

    /** Replace the whole text, clamping the caret (external programmatic set). */
    public void set(String t) {
        text = t == null ? "" : t;
        caret = Math.min(caret, text.length());
        selAnchor = -1;
    }

    /** Delete the current selection if any; returns true if it removed anything. */
    public boolean deleteSelection() {
        if (!hasSelection()) return false;
        int lo = selectionStart(), hi = selectionEnd();
        text = text.substring(0, lo) + text.substring(hi);
        caret = lo; selAnchor = -1;
        return true;
    }

    /** Insert {@code s} at the caret, replacing any selection. Returns true if the text changed. */
    public boolean insert(String s) {
        if (s == null || s.isEmpty()) return deleteSelection();
        deleteSelection();
        text = text.substring(0, caret) + s + text.substring(caret);
        caret += s.length(); selAnchor = -1;
        return true;
    }

    /** Move the caret to {@code to} (clamped), extending the selection when {@code shift} else dropping it. */
    public void moveCaret(int to, boolean shift) {
        to = Math.max(0, Math.min(text.length(), to));
        if (shift) { if (selAnchor < 0) selAnchor = caret; }
        else selAnchor = -1;
        caret = to;
    }

    public void left(boolean shift)  { moveCaret(caret - 1, shift); }
    public void right(boolean shift) { moveCaret(caret + 1, shift); }
    public void home(boolean shift)  { moveCaret(0, shift); }
    public void end(boolean shift)   { moveCaret(text.length(), shift); }

    public void selectAll() { selAnchor = 0; caret = text.length(); }

    /** Select the word under {@code index} (double-click). Whitespace runs select themselves. */
    public void selectWordAt(int index) {
        index = Math.max(0, Math.min(text.length(), index));
        if (text.isEmpty()) { selAnchor = -1; caret = 0; return; }
        int probe = Math.min(index, text.length() - 1);
        boolean space = text.charAt(probe) == ' ';
        int lo = probe, hi = probe;
        while (lo > 0 && (text.charAt(lo - 1) == ' ') == space) lo--;
        while (hi < text.length() && (text.charAt(hi) == ' ') == space) hi++;
        selAnchor = lo; caret = hi;
    }

    /** Backspace: selection, else the word left of the caret when {@code word}, else one char.
     *  Returns true if the text changed. */
    public boolean backspace(boolean word) {
        if (deleteSelection()) return true;
        if (caret == 0) return false;
        int from = word ? wordStart(caret) : caret - 1;
        text = text.substring(0, from) + text.substring(caret);
        caret = from;
        return true;
    }

    /** Delete: selection, else the word right of the caret when {@code word}, else one char.
     *  Returns true if the text changed. */
    public boolean delete(boolean word) {
        if (deleteSelection()) return true;
        if (caret >= text.length()) return false;
        int to = word ? wordEnd(caret) : caret + 1;
        text = text.substring(0, caret) + text.substring(to);
        return true;
    }

    /** Start of the word left of {@code i} (skip spaces, then non-spaces) — Ctrl+Backspace. */
    int wordStart(int i) {
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return i;
    }

    /** End of the word right of {@code i} — Ctrl+Delete. */
    int wordEnd(int i) {
        while (i < text.length() && text.charAt(i) == ' ') i++;
        while (i < text.length() && text.charAt(i) != ' ') i++;
        return i;
    }
}
