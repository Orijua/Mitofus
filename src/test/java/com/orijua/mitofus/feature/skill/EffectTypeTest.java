package com.orijua.mitofus.feature.skill;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orijua.mitofus.feature.effect.EffectType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectTypeTest {
    private static final Path SKILLS = Path.of("src/main/resources/data/mitofus/mitofus/skill");

    @Test
    void damagingKindsAreClassified() {
        assertTrue(EffectType.AREA_DAMAGE.dealsDamage());
        assertTrue(EffectType.PROJECTILE.dealsDamage());
        assertFalse(EffectType.SELF_BUFF.dealsDamage());
    }

    @Test
    void everyKindAnswers() {
        Map<EffectType, Boolean> expected = Map.of(
                EffectType.AREA_DAMAGE, true,
                EffectType.PROJECTILE, true,
                EffectType.SELF_BUFF, false);
        assertEquals(expected.keySet(), Set.of(EffectType.values()), "a new kind must be classified in dealsDamage() and here");
        for (Map.Entry<EffectType, Boolean> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), entry.getKey().dealsDamage(), entry.getKey() + " dealsDamage()");
        }
    }

    @Test
    void theShippedSkillsAreClassified() throws IOException {
        Map<String, Boolean> expected = Map.of(
                "rending_slash", true, "sancho_link", true, "bisection", true, "flame_bolt", true,
                "iron_guard", false, "blacksmith_rage", false, "heavy_strike", true,
                "war_cry", false, "mana_shield", false);
        Map<String, EffectType> found = new HashMap<>();
        List<Path> files;
        try (Stream<Path> stream = Files.list(SKILLS)) {
            files = stream.filter(path -> path.toString().endsWith(".json")).toList();
        }
        for (Path file : files) {
            JsonObject skill;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                skill = JsonParser.parseReader(reader).getAsJsonObject();
            }
            String type = skill.getAsJsonArray("effects").get(0).getAsJsonObject().get("type").getAsString();
            EffectType kind = null;
            for (EffectType candidate : EffectType.values()) {
                if (candidate.getSerializedName().equals(type)) {
                    kind = candidate;
                }
            }
            assertTrue(kind != null, file.getFileName() + " has an unknown effect type " + type);
            String id = file.getFileName().toString().replace(".json", "");
            found.put(id, kind);
        }
        assertEquals(expected.keySet(), found.keySet(), "a new skill must be classified here (does it hurt others?)");
        for (Map.Entry<String, Boolean> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), found.get(entry.getKey()).dealsDamage(), entry.getKey() + " dealsDamage()");
        }
    }
}
