package com.club.ui;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureRuleTest {
    @Test void noLowLevelRenderOutsideBackend() throws Exception {
        Path root = Paths.get("src/main/java/com/club/ui");
        List<String> banned = List.of("RenderSystem", "BufferBuilder", "Tessellator", "BufferRenderer",
                "GlUniform", ".fill(", ".drawText(", "ClubFont", "RenderHelper");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith("backend/")) continue;          // backend may use low-level calls
                String src = Files.readString(p);
                for (String b : banned) if (src.contains(b)) offenders.add(rel + " :: " + b);
            }
        }
        assertTrue(offenders.isEmpty(), "Hard-rule violations: " + offenders);
    }
}
