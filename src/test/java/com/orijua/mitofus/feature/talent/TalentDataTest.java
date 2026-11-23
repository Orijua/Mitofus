package com.orijua.mitofus.feature.talent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orijua.mitofus.lang.LangFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JSON talent nodes are consistent: the tree of each class hangs together, has text in both languages and sane costs. */
class TalentDataTest {
    private static final Path DATA = Path.of("src/main/resources/data/mitofus/mitofus");
    private static final Set<String> EFFECT_TYPES = Set.of("stat", "skill_boost", "passive_boost", "grant_skill");
    private static final Set<String> GRADES = Set.of("apprentice", "adept", "expert", "master", "grand_master");

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String idOf(Path file) {
        String name = file.getFileName().toString();
        return "mitofus:" + name.substring(0, name.length() - 5);
    }

    private static Map<String, JsonObject> load(String folder) throws IOException {
        Map<String, JsonObject> result = new TreeMap<>();
        try (Stream<Path> files = Files.list(DATA.resolve(folder))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                result.put(idOf(file), read(file));
            }
        }
        return result;
    }

    private static List<String> strings(JsonObject node, String field) {
        List<String> result = new ArrayList<>();
        if (node.has(field)) {
            node.getAsJsonArray(field).forEach(e -> result.add(e.getAsString()));
        }
        return result;
    }

    @Test
    void everyNodeBelongsToAClassAndRequiresNodesOfTheSameClass() throws IOException {
        Map<String, JsonObject> talents = load("talent");
        Set<String> classes = load("player_class").keySet();
        TreeSet<String> problems = new TreeSet<>();
        talents.forEach((id, node) -> {
            String owner = node.get("class").getAsString();
            if (!classes.contains(owner)) {
                problems.add(id + " -> class " + owner);
            }
            for (String required : strings(node, "requires")) {
                JsonObject other = talents.get(required);
                if (other == null) {
                    problems.add(id + " -> requires " + required);
                } else if (!other.get("class").getAsString().equals(owner)) {
                    problems.add(id + " -> requires " + required + " of another class");
                }
            }
        });
        assertEquals(new TreeSet<String>(), problems);
    }

    @Test
    void everyTreeHasExactlyOneFreeRootAndNoLoops() throws IOException {
        Map<String, JsonObject> talents = load("talent");
        Map<String, List<String>> rootsByClass = new TreeMap<>();
        talents.forEach((id, node) -> {
            boolean root = strings(node, "requires").isEmpty();
            int cost = node.has("cost") ? node.get("cost").getAsInt() : 1;
            assertEquals(root, cost == 0, id + ": a node is a root exactly when it costs 0");
            if (root) {
                rootsByClass.computeIfAbsent(node.get("class").getAsString(), k -> new ArrayList<>()).add(id);
            }
        });
        talents.values().stream().map(n -> n.get("class").getAsString()).distinct().forEach(owner ->
                assertEquals(1, rootsByClass.getOrDefault(owner, List.of()).size(), owner + " needs exactly one root"));
        for (String id : talents.keySet()) {
            Set<String> seen = new HashSet<>();
            List<String> pending = new ArrayList<>(strings(talents.get(id), "requires"));
            while (!pending.isEmpty()) {
                String current = pending.remove(pending.size() - 1);
                assertTrue(!current.equals(id), id + " requires itself through a loop");
                if (seen.add(current) && talents.containsKey(current)) {
                    pending.addAll(strings(talents.get(current), "requires"));
                }
            }
        }
    }

    @Test
    void everyNodeIsReachableFromItsRoot() throws IOException {
        Map<String, JsonObject> talents = load("talent");
        Set<String> reachable = new HashSet<>();
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map.Entry<String, JsonObject> entry : talents.entrySet()) {
                List<String> requires = strings(entry.getValue(), "requires");
                if (!reachable.contains(entry.getKey()) && reachable.containsAll(requires)) {
                    reachable.add(entry.getKey());
                    grew = true;
                }
            }
        }
        assertEquals(talents.keySet(), reachable);
    }

    @Test
    void effectsGradesAndPositionsAreSane() throws IOException {
        Map<String, JsonObject> talents = load("talent");
        Set<String> skills = load("skill").keySet();
        Set<String> passives = load("passive").keySet();
        Map<String, Set<String>> cells = new TreeMap<>();
        talents.forEach((id, node) -> {
            JsonObject effects = node;
            assertTrue(effects.getAsJsonArray("effects").size() > 0 || strings(node, "requires").isEmpty(), id + " needs effects (only a root may have none)");
            for (JsonElement element : effects.getAsJsonArray("effects")) {
                JsonObject effect = element.getAsJsonObject();
                String type = effect.get("type").getAsString();
                assertTrue(EFFECT_TYPES.contains(type), id + ": unknown effect " + type);
                if (effect.has("skill")) {
                    assertTrue(skills.contains(effect.get("skill").getAsString()), id + ": missing skill");
                }
                if (effect.has("passive")) {
                    assertTrue(passives.contains(effect.get("passive").getAsString()), id + ": missing passive");
                }
            }
            if (node.has("min_grade")) {
                assertTrue(GRADES.contains(node.get("min_grade").getAsString()), id + ": unknown grade");
            }
            int cost = node.has("cost") ? node.get("cost").getAsInt() : 1;
            assertTrue(cost >= 0 && cost <= 3, id + ": cost out of range");
            String cell = node.get("class").getAsString() + " " + node.get("x").getAsInt() + "," + node.get("y").getAsInt();
            cells.computeIfAbsent(cell, k -> new TreeSet<>()).add(id);
        });
        cells.forEach((cell, ids) -> assertEquals(1, ids.size(), "nodes share the cell " + cell + ": " + ids));
    }

    @Test
    void everyNodeHasTextInBothLanguages() throws IOException {
        JsonObject en = LangFiles.load("en_us.json");
        JsonObject es = LangFiles.load("es_es.json");
        TreeSet<String> missing = new TreeSet<>();
        for (Map.Entry<String, JsonObject> entry : load("talent").entrySet()) {
            JsonObject node = entry.getValue();
            List<String> keys = List.of(
                    node.getAsJsonObject("name").get("translate").getAsString(),
                    node.getAsJsonObject("description").get("translate").getAsString(),
                    "talent_branch.mitofus." + node.get("branch").getAsString());
            for (String key : keys) {
                if (!en.has(key)) {
                    missing.add("en " + key);
                }
                if (!es.has(key)) {
                    missing.add("es " + key);
                }
            }
        }
        assertEquals(new TreeSet<String>(), missing);
    }
}
