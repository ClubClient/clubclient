package com.club.ui.layout;
import com.club.ui.UiContext;
import com.club.ui.component.Component;

public final class Spacer extends Component {
    private final float length;
    private final Sizing sizing;
    private Spacer(float length, Sizing sizing) { this.length = length; this.sizing = sizing; }
    public static Spacer fixed(float px)  { return new Spacer(px, Sizing.fixed()); }
    public static Spacer fill()           { return new Spacer(0f, Sizing.fill()); }
    public static Spacer weight(float w)  { return new Spacer(0f, Sizing.weight(w)); }
    public float length()  { return length; }
    public Sizing sizing() { return sizing; }
    @Override public Size measure(float availW, float availH) { return new Size(0, 0); }
    @Override public void render(UiContext ctx) { }
}
