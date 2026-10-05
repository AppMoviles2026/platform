package com.collabtech.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Enforces direct source-import boundaries of the inner layers during this modular bootstrap. */
class ArchitectureBoundariesTests {
    private static final String ROOT = "com.collabtech.platform.";
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.]+);");

    @Test
    void containsOnlyReportedContextsAndSharedWithFourLayers() throws IOException {
        Path source = Path.of("src/main/java/com/collabtech/platform");
        Set<String> expectedContexts = Set.of("identity", "campaign", "collaboration", "billing", "performance", "shared");
        try (var paths = Files.list(source)) {
            var actual = paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertEquals(expectedContexts, actual);
        }
        for (String context : expectedContexts) {
            try (var paths = Files.list(source.resolve(context))) {
                var layers = paths.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString()).collect(Collectors.toSet());
                assertEquals(Set.of("domain", "application", "infrastructure", "interfaces"), layers, context);
            }
        }
    }

    @Test
    void innerLayersDependOnlyOnTheirOwnContextAndSharedKernel() throws IOException {
        List<String> violations = new ArrayList<>();
        Path source = Path.of("src/main/java/com/collabtech/platform");
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                var parts = source.relativize(file).iterator();
                if (!parts.hasNext()) continue;
                String context = parts.next().toString();
                if (!parts.hasNext()) continue;
                String layer = parts.next().toString();
                if (!layer.equals("domain") && !layer.equals("application")) continue;
                var imports = IMPORT.matcher(Files.readString(file));
                while (imports.find()) {
                    String dependency = imports.group(1);
                    boolean allowed = dependency.startsWith("java.")
                            || dependency.startsWith(ROOT + context + ".domain.")
                            || dependency.startsWith(ROOT + "shared.domain.")
                            || layer.equals("application") && (
                                    dependency.startsWith(ROOT + context + ".application.")
                                    || dependency.startsWith(ROOT + "shared.application."));
                    if (!allowed) violations.add(file + " -> " + dependency);
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }
}
