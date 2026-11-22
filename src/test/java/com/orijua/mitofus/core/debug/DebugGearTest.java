package com.orijua.mitofus.core.debug;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orijua.mitofus.feature.progression.StatType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The options that give the bonus of the debug gear exist for every stat, never roll and point at the right stat. */
class DebugGearTest {
    private static JsonObject option(String file) {
        InputStream stream = DebugGearTest.class.getResourceAsStream("/data/mitofus/mitofus/item_option/" + file + ".json");
        assertNotNull(stream, "option file not found: " + file);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + file, e);
        }
    }

    @Test
    void everyStatHasItsDebugOption() {
        for (StatType stat : StatType.values()) {
            String name = stat.getSerializedName();
            String file = DebugGear.optionId(stat).getPath();
            assertEquals("debug_" + name, file);
            JsonObject json = option(file);
            assertEquals(0, json.get("weight").getAsInt(), file + " never rolls");
            assertEquals("stat", json.getAsJsonObject("effect").get("type").getAsString(), file);
            assertEquals(name, json.getAsJsonObject("effect").get("stat").getAsString(), file);
            assertEquals("stat.mitofus." + name, json.getAsJsonObject("name").get("translate").getAsString(), file);
        }
    }
}
