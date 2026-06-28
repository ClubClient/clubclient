package com.club.ui.layout;
import com.club.ui.component.Component;
public final class Row extends Linear {
    public Row() { super(true); }
    public Row padding(Insets p)        { setPadding(p); return this; }
    public Row gap(float g)             { setGap(g); return this; }
    public Row crossAlign(CrossAlign a) { setCrossAlign(a); return this; }
    public Row mainAlign(MainAlign a)   { setMainAlign(a); return this; }
    public Row add(Component c)             { addItem(c); return this; }
    public Row add(Component c, Sizing s)   { addItem(c, s); return this; }
}
