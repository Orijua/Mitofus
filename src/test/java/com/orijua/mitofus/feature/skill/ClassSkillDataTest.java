package com.orijua.mitofus.feature.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every skill a class gives or teaches ("skills" and "skill_unlocks") exists as a skill JSON. */
class ClassSkillDataTest {
    private static final Path DATA = Path.of("src/main/resources/data/mitofus/mitofus");

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> skillIds() throws IOException {
        Set<String> result = new TreeSet<>();
        try (Stream<Path> files = Files.list(DATA.resolve("skill"))) {
            files.filter(f -> f.toString().endsWith(".json")).forEach(f -> {
                String name = f.getFileName().toString();
                result.add("mitofus:" + name.substring(0, name.length() - 5));
            });
        }
        return result;
    }

    @Test
    void everyClassSkillAndUnlockExists() throws IOException {
        Set<String> skills = skillIds();
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> classes = Files.list(DATA.resolve("player_class"))) {
            for (Path file : classes.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject definition = read(file);
                String owner = file.getFileName().toString();
                if (definition.has("skills")) {
                    for (JsonElement id : definition.getAsJsonArray("skills")) {
                        if (!skills.contains(id.getAsString())) {
                            missing.add(owner + " skills: " + id.getAsString());
                        }
                    }
                }
                if (definition.has("skill_unlocks")) {
                    for (JsonElement entry : definition.getAsJsonArray("skill_unlocks")) {
                        String id = entry.getAsJsonObject().get("skill").getAsString();
                        if (!skills.contains(id)) {
                            missing.add(owner + " skill_unlocks: " + id);
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "skills missing: " + missing);
    }
}
