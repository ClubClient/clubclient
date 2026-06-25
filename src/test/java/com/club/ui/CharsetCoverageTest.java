package com.club.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CharsetCoverageTest {
    @Test void atlasHasCyrillicAndLatin() throws Exception {
        JsonObject root = JsonParser.parseReader(
            new FileReader("src/main/resources/assets/club/ui/font/msdf/inter_regular.json")).getAsJsonObject();
        Set<Integer> cps = new HashSet<>();
        root.getAsJsonArray("glyphs").forEach(g -> cps.add(g.getAsJsonObject().get("unicode").getAsInt()));
        assertTrue(cps.contains((int) 'A'), "Latin A present");
        assertTrue(cps.contains(0x0410), "Cyrillic А present");   // U+0410
        assertTrue(cps.contains(0x044F), "Cyrillic я present");   // U+044F
        assertTrue(cps.contains(0x2026), "ellipsis present");
    }
}
