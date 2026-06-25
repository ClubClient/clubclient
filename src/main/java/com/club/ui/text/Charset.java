package com.club.ui.text;

/** Frozen codepoint coverage for the MSDF atlases: Latin + Latin-1 + Cyrillic + punctuation/symbols. */
public final class Charset {
    private Charset() {}
    public static boolean contains(int cp) {
        if (cp >= 0x20 && cp <= 0x7E) return true;        // ASCII
        if (cp >= 0xA0 && cp <= 0xFF) return true;        // Latin-1 supplement
        if (cp >= 0x0400 && cp <= 0x04FF) return true;    // Cyrillic
        switch (cp) {                                      // common punctuation/symbols
            case 0x2013: case 0x2014: case 0x2018: case 0x2019:
            case 0x201C: case 0x201D: case 0x2022: case 0x2026:
            case 0x2192: case 0x221E: return true;
        }
        return false;
    }
}
