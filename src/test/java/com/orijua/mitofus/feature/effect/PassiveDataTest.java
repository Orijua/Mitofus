package com.orijua.mitofus.feature.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orijua.mitofus.lang.LangFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JSON passives are consistent: every class passive exists, every passive has text and a known kind. */
class PassiveDataTest {
    private static final Path DATA = Path.of("src/main/resources/data/mitofus/mitofus");

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> ids(String folder) throws IOException {
        Set<String> result = new TreeSet<>();
        try (Stream<Path> files = Files.list(DATA.resolve(folder))) {
            files.filter(f -> f.toString().endsWith(".json")).forEach(f -> {
                String name = f.getFileName().toString();
                result.add("mitofus:" + name.substring(0, name.length() - 5));
            });
        }
        return result;
    }

    @Test
    void everyClassPassiveExists() throws IOException {
        Set<String> passives = ids("passive");
        TreeSet<String> missing = new TreeSet<>();
        try (Stream<Path> classes = Files.list(DATA.resolve("player_class"))) {
            for (Path file : classes.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject definition = read(file);
                List<String> used = new ArrayList<>();
                if (definition.has("passives")) {
                    for (JsonElement entry : definition.getAsJsonArray("passives")) {
                        used.add(entry.getAsString());
                    }
                }
                if (definition.has("third_slot_passive")) {
                    used.add(definition.get("third_slot_passive").getAsString());
                }
                for (String id : used) {
                    if (!passives.contains(id)) {
                        missing.add(file.getFileName() + " -> " + id);
                    }
                }
            }
        }
        assertEquals(new TreeSet<String>(), missing);
    }

    @Test
    void everyPassiveHasTextAndKnownKinds() throws IOException {
        JsonObject en = LangFiles.load("en_us.json");
        Set<String> kinds = new TreeSet<>();
        for (BonusType kind : BonusType.values()) {
            kinds.add(kind.getSerializedName());
        }
        try (Stream<Path> files = Files.list(DATA.resolve("passive"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject passive = read(file);
                assertTrue(en.has(passive.getAsJsonObject("name").get("translate").getAsString()), file + " name");
                assertTrue(en.has(passive.getAsJsonObject("description").get("translate").getAsString()), file + " description");
                JsonArray bonuses = passive.getAsJsonArray("bonuses");
                assertTrue(bonuses.size() > 0, file + " has no bonuses");
                for (JsonElement bonus : bonuses) {
                    assertTrue(kinds.contains(bonus.getAsJsonObject().get("type").getAsString()), file + " type");
                }
            }
        }
    }
}
