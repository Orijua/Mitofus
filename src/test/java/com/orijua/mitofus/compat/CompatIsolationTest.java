package com.orijua.mitofus.compat;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JEI and Jade are optional: the mod must start without them. Their APIs may only appear in compat/<mod>/, which the
 * mods themselves load through their plugin annotations, and nothing else in the mod may point into compat/ (that
 * would load a class that needs them).
 */
class CompatIsolationTest {
    private static final Path ROOT = Path.of("src", "main", "java");
    private static final List<String> OPTIONAL_APIS = List.of("mezz.jei.", "snownee.jade.");

    @Test
    void optionalModApisStayInsideTheCompatPackage() throws Exception {
        TreeSet<String> leaks = new TreeSet<>();
        for (Path file : javaFiles()) {
            if (inCompat(file)) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            for (String api : OPTIONAL_APIS) {
                if (source.contains(api)) {
                    leaks.add(file.getFileName() + " uses " + api);
                }
            }
            if (source.contains("mitofus.compat.")) {
                leaks.add(file.getFileName() + " points into compat/");
            }
        }
        assertTrue(leaks.isEmpty(), "Code outside compat/ that would need an optional mod: " + leaks);
    }

    @Test
    void eachCompatPackageOnlyUsesItsOwnMod() throws Exception {
        TreeSet<String> crossed = new TreeSet<>();
        for (Path file : javaFiles()) {
            if (!inCompat(file)) {
                continue;
            }
            String path = file.toString().replace(File.separatorChar, '/');
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (path.contains("/compat/jei/") && source.contains("snownee.jade.")
                    || path.contains("/compat/jade/") && source.contains("mezz.jei.")) {
                crossed.add(file.getFileName().toString());
            }
        }
        assertTrue(crossed.isEmpty(), "A compat package uses another optional mod: " + crossed);
    }

    private static boolean inCompat(Path file) {
        return file.toString().replace(File.separatorChar, '/').contains("/mitofus/compat/");
    }

    private static List<Path> javaFiles() throws Exception {
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
