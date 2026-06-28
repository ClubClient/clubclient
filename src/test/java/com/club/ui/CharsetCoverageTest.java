package com.club.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CharsetCoverageTest {
    private static Set<Integer> loadCodepoints(String path) throws Exception {
        JsonObject root = JsonParser.parseReader(new FileReader(path)).getAsJsonObject();
        Set<Integer> cps = new HashSet<>();
        root.getAsJsonArray("glyphs").forEach(g -> cps.add(g.getAsJsonObject().get("unicode").getAsInt()));
        return cps;
    }

    @Test void atlasHasCyrillicAndLatin() throws Exception {
        Set<Integer> cps = loadCodepoints(
            "src/main/resources/assets/club/ui/font/msdf/inter_regular.json");
        assertTrue(cps.contains((int) 'A'), "Latin A present");
        assertTrue(cps.contains(0x0410), "Cyrillic А present");   // U+0410
        assertTrue(cps.contains(0x044F), "Cyrillic я present");   // U+044F
        assertTrue(cps.contains(0x2026), "ellipsis present");
        assertTrue(cps.contains((int) '?'), "? (U+003F) present — fallback render target");
    }

    @Test void allWeightsHaveEssentialGlyphs() throws Exception {
        Map<String, String> weights = Map.of(
            "regular",  "src/main/resources/assets/club/ui/font/msdf/inter_regular.json",
            "medium",   "src/main/resources/assets/club/ui/font/msdf/inter_medium.json",
            "semibold", "src/main/resources/assets/club/ui/font/msdf/inter_semibold.json"
        );
        for (Map.Entry<String, String> entry : weights.entrySet()) {
            String weight = entry.getKey();
            Set<Integer> cps = loadCodepoints(entry.getValue());
            assertTrue(cps.contains((int) 'A'),    weight + ": Latin A (U+0041) present");
            assertTrue(cps.contains(0x0410),        weight + ": Cyrillic А (U+0410) present");
            assertTrue(cps.contains((int) '?'),     weight + ": ? (U+003F) present — fallback render target");
        }
    }
}
