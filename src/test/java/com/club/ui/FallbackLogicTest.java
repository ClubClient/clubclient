package com.club.ui;

import com.club.ui.text.Charset;
import com.club.ui.text.MsdfMetrics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.FileReader;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class FallbackLogicTest {

    /** Every codepoint the committed atlas of this weight actually carries. */
    private static Set<Integer> atlasCodepoints(String weight) throws Exception {
        String path = "src/main/resources/assets/club/ui/font/msdf/inter_" + weight + ".json";
        JsonObject root = JsonParser.parseReader(new FileReader(path)).getAsJsonObject();
        Set<Integer> cps = new TreeSet<>();
        root.getAsJsonArray("glyphs").forEach(g -> cps.add(g.getAsJsonObject().get("unicode").getAsInt()));
        return cps;
    }

    @Test void missingGlyphIsNullNotCrash() {
        MsdfMetrics m = MsdfMetrics.parse(MsdfMetricsTest.JSON);
        assertNull(m.get(0x4E2D));               // CJK 中 absent
        assertEquals(0f, m.width("中", 16f), 1e-6);
    }

    /**
     * Charset is a hand-written claim about a generated file, so the only thing worth testing is whether the
     * claim is still TRUE. It was not: it promised all of Latin-1, all of Cyrillic and U+221E, and 184 of
     * those codepoints have never been in the atlas. The old test spot-checked contains(0x0410) and passed
     * happily throughout — a spot-check cannot catch an over-claim, because the spots it picks are the ones
     * that are there. So: no spot-checks. Compare the whole set, both directions, against every weight.
     */
    @Test void charsetMatchesCommittedAtlasExactly() throws Exception {
        // 0x0000-0x2FFF spans every block either side can name: ASCII, Latin-1, Cyrillic, and the punctuation
        // range holding both the 9 symbols we ship and the U+221E we do not. Any atlas codepoint ABOVE this
        // range is still caught below — it lands in presentButDenied, because nothing here claims it.
        Set<Integer> claimed = new TreeSet<>();
        for (int cp = 0; cp <= 0x2FFF; cp++) {
            if (Charset.contains(cp)) claimed.add(cp);
        }
        assertEquals(Charset.GLYPH_COUNT, claimed.size(), "Charset.contains() must claim exactly GLYPH_COUNT codepoints");

        for (String weight : new String[]{"regular", "medium", "semibold"}) {
            Set<Integer> atlas = atlasCodepoints(weight);
            assertEquals(Charset.GLYPH_COUNT, atlas.size(), weight + ": glyphs in the committed atlas");

            Set<Integer> promisedButAbsent = new TreeSet<>(claimed);
            promisedButAbsent.removeAll(atlas);
            Set<Integer> presentButDenied = new TreeSet<>(atlas);
            presentButDenied.removeAll(claimed);

            assertTrue(promisedButAbsent.isEmpty(),
                weight + ": Charset promises glyphs the atlas does not have: " + hex(promisedButAbsent));
            assertTrue(presentButDenied.isEmpty(),
                weight + ": atlas has glyphs Charset denies: " + hex(presentButDenied));
        }
    }

    @Test void charsetExcludesOutOfRange() {
        assertFalse(Charset.contains(0x4E2D));   // CJK 中 — never generated, and outside the scanned range
        assertTrue(Charset.contains(0x0410));    // Cyrillic А
    }

    private static String hex(Set<Integer> cps) {
        StringBuilder sb = new StringBuilder();
        for (int cp : cps) sb.append(String.format("U+%04X ", cp));
        return sb.toString().trim();
    }
}
