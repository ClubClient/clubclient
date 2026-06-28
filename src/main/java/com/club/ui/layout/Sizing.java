package com.club.ui.layout;
public sealed interface Sizing permits Sizing.Fixed, Sizing.Fill, Sizing.Weight {
    record Fixed()  implements Sizing {}
    record Fill()   implements Sizing {}
    record Weight(float value) implements Sizing {}
    Fixed FIXED = new Fixed();
    Fill  FILL  = new Fill();
    static Sizing fixed()        { return FIXED; }
    static Sizing fill()         { return FILL; }
    static Sizing weight(float w) { return new Weight(w); }
}
