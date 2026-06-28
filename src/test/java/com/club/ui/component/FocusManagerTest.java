package com.club.ui.component;
import com.club.ui.UiContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FocusManagerTest {
    static class KeyProbe extends Component {
        int keys; boolean accept = true;
        @Override public void render(UiContext ctx) { }
        @Override public boolean keyPressed(int k, int s, int m) { keys++; return accept; }
    }
    @Test void nextWrapsAndSetsFlag() {
        FocusManager fm = new FocusManager();
        KeyProbe a = new KeyProbe(), b = new KeyProbe();
        fm.register(a); fm.register(b);
        fm.next(); assertSame(a, fm.focused()); assertTrue(a.isFocused());
        fm.next(); assertSame(b, fm.focused()); assertFalse(a.isFocused());
        fm.next(); assertSame(a, fm.focused());                 // wrap
        fm.previous(); assertSame(b, fm.focused());             // wrap back
    }
    @Test void keyRoutesToFocused() {
        FocusManager fm = new FocusManager();
        KeyProbe a = new KeyProbe(); fm.register(a);
        assertFalse(fm.keyPressed(1, 0, 0));                    // nothing focused
        fm.focus(a);
        assertTrue(fm.keyPressed(1, 0, 0));
        assertEquals(1, a.keys);
    }
    @Test void clickFocusSelectsHit() {
        FocusManager fm = new FocusManager();
        KeyProbe a = new KeyProbe(); a.layout(0, 0, 10, 10);
        KeyProbe b = new KeyProbe(); b.layout(20, 0, 10, 10);
        fm.register(a); fm.register(b);
        fm.clickFocus(25, 5);
        assertSame(b, fm.focused());
        fm.clickFocus(100, 100);                               // miss → clears focus
        assertNull(fm.focused());
    }
}
