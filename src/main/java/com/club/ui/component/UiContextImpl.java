// UiContextImpl.java
package com.club.ui.component;
import com.club.ui.Ui;
import com.club.ui.UiContext;
import com.club.ui.UiRenderer;
import com.club.ui.UiText;

/** Concrete render context. Created once by the owning root; setTime() called once per frame. */
public final class UiContextImpl implements UiContext {
    private float time;
    public void setTime(float seconds) { this.time = seconds; }
    @Override public UiRenderer renderer() { return Ui.renderer(); }
    @Override public UiText text()         { return Ui.text(); }
    @Override public float time()          { return time; }
}
