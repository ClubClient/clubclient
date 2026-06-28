package com.club.ui.layout;
import com.club.ui.component.Component;
public final class Column extends Linear {
    public Column() { super(false); }
    public Column padding(Insets p)        { setPadding(p); return this; }
    public Column gap(float g)             { setGap(g); return this; }
    public Column crossAlign(CrossAlign a) { setCrossAlign(a); return this; }
    public Column mainAlign(MainAlign a)   { setMainAlign(a); return this; }
    public Column add(Component c)             { addItem(c); return this; }
    public Column add(Component c, Sizing s)   { addItem(c, s); return this; }
}
