package com.club.ui.component.widget;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pins the single-line editing model extracted from the menu SearchField (Stage 31). */
class TextEditStateTest {

    private static TextEditState of(String text) {
        TextEditState st = new TextEditState();
        st.insert(text);
        return st;
    }

    @Test void insertAtCaretAndAppend() {
        TextEditState st = of("ab");
        assertEquals("ab", st.text());
        assertEquals(2, st.caret());
        st.moveCaret(1, false);
        st.insert("XY");
        assertEquals("aXYb", st.text());
        assertEquals(3, st.caret());
    }

    @Test void insertReplacesSelection() {
        TextEditState st = of("hello world");
        st.moveCaret(0, false);
        st.moveCaret(5, true);          // select "hello"
        assertTrue(st.hasSelection());
        assertEquals("hello", st.selectedText());
        st.insert("hi");
        assertEquals("hi world", st.text());
        assertEquals(2, st.caret());
        assertFalse(st.hasSelection());
    }

    @Test void caretMovesClampAndShiftExtends() {
        TextEditState st = of("abc");
        st.left(false); st.left(false); st.left(false); st.left(false);   // past 0 — clamps
        assertEquals(0, st.caret());
        st.right(true); st.right(true);                                    // shift-extend 0→2
        assertTrue(st.hasSelection());
        assertEquals(0, st.selectionStart());
        assertEquals(2, st.selectionEnd());
        st.right(false);                                                   // plain move drops selection
        assertFalse(st.hasSelection());
        st.end(false);
        assertEquals(3, st.caret());
        st.home(true);
        assertEquals("abc", st.selectedText());
    }

    @Test void backspaceCharAndWord() {
        TextEditState st = of("one two  three");
        assertTrue(st.backspace(false));                 // char: "…thre"
        assertEquals("one two  thre", st.text());
        assertTrue(st.backspace(true));                  // word: kills "thre"
        assertEquals("one two  ", st.text());
        assertTrue(st.backspace(true));                  // word: skips spaces then kills "two"
        assertEquals("one ", st.text());
        st.home(false);
        assertFalse(st.backspace(false), "backspace at 0 is a no-op");
    }

    @Test void deleteCharAndWord() {
        TextEditState st = of("one two");
        st.home(false);
        assertTrue(st.delete(false));                    // char
        assertEquals("ne two", st.text());
        assertTrue(st.delete(true));                     // word: "ne" from caret 0
        assertEquals(" two", st.text());
        st.end(false);
        assertFalse(st.delete(false), "delete at end is a no-op");
    }

    @Test void selectionDeleteViaBackspaceAndDelete() {
        TextEditState st = of("abcdef");
        st.moveCaret(1, false); st.moveCaret(4, true);   // "bcd"
        assertTrue(st.backspace(false));
        assertEquals("aef", st.text());
        assertEquals(1, st.caret());
        st.selectAll();
        assertTrue(st.delete(false));
        assertEquals("", st.text());
        assertEquals(0, st.caret());
    }

    @Test void selectAllAndSelectedText() {
        TextEditState st = of("query");
        st.selectAll();
        assertEquals("query", st.selectedText());
        assertEquals(5, st.caret());
    }

    @Test void selectWordAtPicksWordOrSpaceRun() {
        TextEditState st = of("no bob");
        st.selectWordAt(1);                              // inside "no"
        assertEquals("no", st.selectedText());
        st.selectWordAt(4);                              // inside "bob"
        assertEquals("bob", st.selectedText());
        st.selectWordAt(2);                              // on the space run
        assertEquals(" ", st.selectedText());
        st.selectWordAt(6);                              // index == length → last word
        assertEquals("bob", st.selectedText());
    }

    @Test void selectWordAtOnEmptyIsSafe() {
        TextEditState st = new TextEditState();
        st.selectWordAt(3);
        assertFalse(st.hasSelection());
        assertEquals(0, st.caret());
    }

    @Test void clearResetsEverything() {
        TextEditState st = of("abc");
        st.selectAll();
        st.clear();
        assertEquals("", st.text());
        assertEquals(0, st.caret());
        assertFalse(st.hasSelection());
    }

    @Test void insertEmptyStillDeletesSelection() {
        TextEditState st = of("abc");
        st.selectAll();
        assertTrue(st.insert(""), "paste of empty clipboard still consumes the selection");
        assertEquals("", st.text());
        TextEditState plain = of("abc");
        assertFalse(plain.insert(""), "no selection + empty insert = no change");
    }

    /** Regression (owner, pack 6 #3): a Shift-move onto the caret plants a ZERO-WIDTH anchor; a following
     *  backspace shrank the text but left the anchor stranded past the end, and the search field's render
     *  then did {@code text.substring(0, selectionEnd())} with selectionEnd > length — crashing the game. */
    @Test void shiftThenBackspaceLeavesNoStrandedAnchor() {
        TextEditState st = of("ab");           // caret at 2 (end)
        st.right(true);                        // Shift-Right at the end: to==caret ⇒ zero-width anchor at 2
        assertTrue(st.backspace(false));       // deletes 'b' → text "a", length 1
        assertEquals("a", st.text());
        // The anchor must not survive as a bogus, out-of-range selection.
        assertFalse(st.hasSelection(), "zero-width anchor is collapsed by the destructive edit");
        assertTrue(st.selectionEnd() <= st.text().length(), "no index can overrun the text");
        assertTrue(st.caret() <= st.text().length());
        // And the exact call that crashed render is now safe.
        assertDoesNotThrow(() -> st.text().substring(0, st.selectionEnd()));
        assertDoesNotThrow(() -> st.text().substring(0, st.caret()));
    }

    @Test void repeatedBackspacesAfterShiftStayInRange() {
        TextEditState st = of("abcd");         // caret 4
        st.right(true);                        // zero-width anchor at 4
        st.backspace(false);                   // "abc"
        st.backspace(false);                   // "ab"
        assertEquals("ab", st.text());
        assertTrue(st.selectionEnd() <= st.text().length());
        assertTrue(st.caret() <= st.text().length());
        assertDoesNotThrow(() -> st.text().substring(0, st.selectionEnd()));
    }

    @Test void deleteThroughASelectionStaysInRange() {
        TextEditState st = of("abcd");
        st.moveCaret(1, false);                // caret 1
        st.moveCaret(3, true);                 // selection [1,3)
        assertTrue(st.delete(false));          // deleteSelection removes "bc" → "ad"
        assertEquals("ad", st.text());
        assertFalse(st.hasSelection());
        assertTrue(st.selectionEnd() <= st.text().length());
    }
}
