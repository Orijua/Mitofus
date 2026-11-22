package com.orijua.mitofus.feature.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MitofusOnlyTest {
    private static ResourceLocation mod(String path) {
        return ResourceLocation.fromNamespaceAndPath("mitofus", path);
    }

    @Test
    void theHelmetIsNotAMitofusItem() {
        assertFalse(MitofusOnly.isMitofusItem(mod("helmet")));
    }

    @Test
    void theTerminalAndThePhoneAreNotMitofusItemsAndOnlyTheTerminalCanBePlaced() {
        assertFalse(MitofusOnly.isMitofusItem(mod("account_terminal")));
        assertFalse(MitofusOnly.isMitofusItem(mod("phone")));
        assertFalse(MitofusOnly.blocksPlacement(mod("account_terminal")));
        // The phone is only an item: it is not in the list of blocks that can be placed in the real world.
        assertTrue(MitofusOnly.blocksPlacement(mod("phone")));
    }

    @Test
    void theModsToolsAndStationsAreMitofusItems() {
        assertTrue(MitofusOnly.isMitofusItem(mod("smithing_anvil")));
        assertTrue(MitofusOnly.isMitofusItem(mod("smelter")));
        assertTrue(MitofusOnly.isMitofusItem(mod("apprentice_contract")));
        assertTrue(MitofusOnly.isMitofusItem(mod("wolfram_sword")));
        assertTrue(MitofusOnly.isMitofusItem(mod("respec_scroll")));
        assertTrue(MitofusOnly.isMitofusItem(mod("gold_coin")));
    }

    @Test
    void vanillaItemsAreNeverMitofusItems() {
        assertFalse(MitofusOnly.isMitofusItem(ResourceLocation.withDefaultNamespace("iron_sword")));
        assertFalse(MitofusOnly.isMitofusItem(ResourceLocation.fromNamespaceAndPath("othermod", "smelter")));
    }

    @Test
    void theOresAreInertInTheRealWorldToo() {
        for (String path : new String[]{"wolfram_ore", "deepslate_wolfram_ore", "mithril_ore", "deepslate_mithril_ore",
                "azurite_ore", "deepslate_azurite_ore", "raw_wolfram", "wolfram_ingot", "raw_mithril",
                "mithril_ingot", "raw_azurite", "azurite_ingot"}) {
            assertTrue(MitofusOnly.isMitofusItem(mod(path)), path);
            assertTrue(MitofusOnly.blocksPlacement(mod(path)), path);
        }
    }

    @Test
    void theOrePlacedFeaturesAreFilteredToMitofus() throws Exception {
        for (String ore : new String[]{"ore_wolfram", "ore_mithril", "ore_azurite"}) {
            try (InputStream in = MitofusOnlyTest.class.getResourceAsStream("/data/mitofus/worldgen/placed_feature/" + ore + ".json")) {
                assertNotNull(in, ore);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                boolean filtered = false;
                for (var modifier : json.getAsJsonArray("placement")) {
                    filtered |= "mitofus:mitofus_only".equals(modifier.getAsJsonObject().get("type").getAsString());
                }
                assertTrue(filtered, ore + " must carry the mitofus_only placement modifier");
            }
        }
    }

    @Test
    void theAnvilAndTheSmelterCannotBePlacedButTheBodyAndTheOresCan() {
        assertTrue(MitofusOnly.blocksPlacement(mod("smithing_anvil")));
        assertTrue(MitofusOnly.blocksPlacement(mod("smelter")));
        assertTrue(MitofusOnly.blocksPlacement(mod("golden_table")));
        assertFalse(MitofusOnly.blocksPlacement(mod("body")));
        assertFalse(MitofusOnly.blocksPlacement(ResourceLocation.withDefaultNamespace("stone")));
    }

    @Test
    void theModsEntitiesAreMitofusEntitiesButVanillaAndOtherModsAreNot() {
        assertTrue(MitofusOnly.isMitofusEntity(mod("blacksmith")));
        assertTrue(MitofusOnly.isMitofusEntity(mod("velasco")));
        assertFalse(MitofusOnly.isMitofusEntity(ResourceLocation.withDefaultNamespace("villager")));
        assertFalse(MitofusOnly.isMitofusEntity(ResourceLocation.fromNamespaceAndPath("othermod", "x")));
    }

    @Test
    void theWarningKeyExistsInBothLangFiles() throws Exception {
        for (String file : new String[]{"en_us.json", "es_es.json"}) {
            try (InputStream stream = MitofusOnlyTest.class.getResourceAsStream("/assets/mitofus/lang/" + file)) {
                assertNotNull(stream, "lang file not found: " + file);
                JsonObject lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(lang.has(MitofusOnly.WARNING_KEY), file + " lacks " + MitofusOnly.WARNING_KEY);
            }
        }
    }
}
