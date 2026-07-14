package com.club.ui.text;

/**
 * The codepoints the committed MSDF atlases actually contain: {@value #GLYPH_COUNT} per weight.
 *
 * <p>MEASURED, NOT DECLARED. This set was read back out of the committed
 * {@code assets/club/ui/font/msdf/inter_{regular,medium,semibold}.json} — all three weights carry the same
 * {@value #GLYPH_COUNT} codepoints. {@code FallbackLogicTest} re-derives the set from those three files on
 * every run and fails if this class and the atlas disagree by a single codepoint, so the two cannot drift
 * apart in silence.
 *
 * <p>WHY THAT TEST EXISTS: this class used to promise the whole of Latin-1 (U+00A0–U+00FF), the whole of
 * Cyrillic (U+0400–U+04FF) and U+221E. The atlas has never held that — 184 of the codepoints it promised
 * are not in the file. No wrong pixel ever shipped, because the render path asks {@code MsdfMetrics.get(cp)}
 * and that reads the atlas and falls back to '?' — but the promise was false, and the old test asserted
 * {@code contains(0x0410)} and passed happily while it was false. Coverage is a SUBSET of Latin-1 and a
 * SUBSET of Cyrillic. The gaps below are real; do not "tidy" them back into clean ranges.
 *
 * <p>Nothing in the render path calls this. It is the checked, human-readable statement of what the frozen
 * atlas holds. For a runtime answer, ask {@code MsdfMetrics}, which reads the atlas itself.
 */
public final class Charset {
    private Charset() {}

    /** Codepoints in each committed atlas. Pinned against the JSON by {@code FallbackLogicTest}. */
    public static final int GLYPH_COUNT = 273;

    public static boolean contains(int cp) {
        // ASCII (95) — the only block that is complete.
        if (cp >= 0x20 && cp <= 0x7E) return true;

        // Latin-1 supplement (87 of 96). These nine were never generated into the atlas:
        // ¤ ¦ ¬ SHY µ ¹ ¼ ½ ¾.
        if (cp >= 0xA0 && cp <= 0xFF) {
            switch (cp) {
                case 0xA4: case 0xA6: case 0xAC: case 0xAD:
                case 0xB5: case 0xB9: case 0xBC: case 0xBD: case 0xBE:
                    return false;
                default:
                    return true;
            }
        }

        // Cyrillic (82 of 256): the Russian core, plus the few Ukrainian/Belarusian/Serbian letters the font
        // was generated with. The rest of U+0400–U+04FF — historic, Asian Cyrillic, combining marks — is absent.
        if (cp >= 0x0410 && cp <= 0x044F) return true;   // А–я, contiguous
        switch (cp) {
            case 0x0401: case 0x0451:                                          // Ё ё
            case 0x0404: case 0x0405: case 0x0406: case 0x0407: case 0x0408:   // Є Ѕ І Ї Ј
            case 0x0454: case 0x0455: case 0x0456: case 0x0457: case 0x0458:   // є ѕ і ї ј
            case 0x040E: case 0x045E:                                          // Ў ў
            case 0x0490: case 0x0491:                                          // Ґ ґ
            case 0x04C1: case 0x04C2:                                          // Ӂ ӂ
                return true;
        }

        // Punctuation and symbols (9). U+221E (∞) was declared here for a long time and has never been in the
        // atlas — putting it back means regenerating the font, not editing this line.
        switch (cp) {
            case 0x2013: case 0x2014:   // – —
            case 0x2018: case 0x2019:   // ‘ ’
            case 0x201C: case 0x201D:   // “ ”
            case 0x2022: case 0x2026:   // • …
            case 0x2192:                // →
                return true;
        }
        return false;
    }
}
