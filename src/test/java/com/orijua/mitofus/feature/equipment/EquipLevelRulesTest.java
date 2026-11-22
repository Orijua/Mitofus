package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.EquipLevelBalance;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipLevelRulesTest {
    private static final EquipLevelBalance B = EquipLevelBalance.DEFAULT;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mitofus", path);
    }

    private static int level(String material, ItemRating rating) {
        return EquipLevelRules.derivedLevel(EquipLevelRules.materialLevel(ResourceLocation.withDefaultNamespace("some_item"), material, B), rating, B);
    }

    @Test
    void theDefaultsAreTheApprovedNumbers() {
        assertEquals(Map.of("wood", 1, "gold", 1, "leather", 1, "stone", 3, "chainmail", 3, "iron", 5, "diamond", 15, "netherite", 25),
                B.materialLevel());
        assertEquals(5, B.otherMaterialLevel());
        assertEquals(Map.of(ItemRating.NORMAL, 0, ItemRating.RARE, 2, ItemRating.EPIC, 5, ItemRating.UNIQUE, 10,
                ItemRating.LEGENDARY, 20, ItemRating.ANCESTRAL, 40), B.ratingExtra());
        assertEquals(Map.of(id("wolfram_sword"), 8, id("wolfram_smithing_hammer"), 8, id("mithril_smithing_hammer"), 20), B.itemLevel());
    }

    @Test
    void theDerivedLevelIsTheMaterialPlusTheRatingExtra() {
        assertEquals(5, level("iron", ItemRating.NORMAL));
        assertEquals(7, level("iron", ItemRating.RARE));
        assertEquals(10, level("iron", ItemRating.EPIC));
        assertEquals(15, level("iron", ItemRating.UNIQUE));
        assertEquals(35, level("diamond", ItemRating.LEGENDARY));
        assertEquals(65, level("netherite", ItemRating.ANCESTRAL));
        assertEquals(1, level("wood", ItemRating.NORMAL));
        assertEquals(1, level("gold", ItemRating.NORMAL));
        assertEquals(1, level("leather", ItemRating.NORMAL));
        assertEquals(3, level("stone", ItemRating.NORMAL));
        assertEquals(3, level("chainmail", ItemRating.NORMAL));
    }

    @Test
    void anUnknownMaterialOrNoMaterialUsesTheOtherMaterialLevel() {
        assertEquals(5, level("copper", ItemRating.NORMAL));
        assertEquals(5, level(null, ItemRating.NORMAL));
        assertEquals(15, level(null, ItemRating.UNIQUE));
    }

    @Test
    void aSingleObjectReplacesTheLevelOfItsMaterialAndStillGetsTheRatingExtra() {
        int wolfram = EquipLevelRules.materialLevel(id("wolfram_sword"), "iron", B);
        assertEquals(8, wolfram);
        assertEquals(10, EquipLevelRules.derivedLevel(wolfram, ItemRating.RARE, B));
        assertEquals(20, EquipLevelRules.materialLevel(id("mithril_smithing_hammer"), null, B));
        assertEquals(8, EquipLevelRules.materialLevel(id("wolfram_smithing_hammer"), null, B));
    }

    @Test
    void theEffectiveLevelIsTheLargerOfTheStoredAndTheDerivedOne() {
        assertEquals(15, EquipLevelRules.effectiveLevel(0, 15), "no recipe: the derived level");
        assertEquals(15, EquipLevelRules.effectiveLevel(3, 15), "a recipe below the derived level does not lower it");
        assertEquals(15, EquipLevelRules.effectiveLevel(15, 15));
        assertEquals(300, EquipLevelRules.effectiveLevel(300, 15), "a recipe above it is kept");
    }


    @Test
    void theLevelsOfAPieceComeTogether() {
        EquipLevelRules.Levels none = EquipLevelRules.Levels.of(0, 15);
        assertEquals(new EquipLevelRules.Levels(0, 15, 15), none);
        assertEquals(new EquipLevelRules.Levels(300, 35, 300), EquipLevelRules.Levels.of(300, 35));
        assertEquals(new EquipLevelRules.Levels(3, 15, 15), EquipLevelRules.Levels.of(3, 15));
    }

    @Test
    void theRangeOfLevelsGoesFromTheLowestToTheHighestPossibleRating() {
        // iron (5): normal 5 ... ancestral 45
        assertEquals(new EquipLevelRules.Range(5, 45), EquipLevelRules.effectiveRange(5, ItemRating.NORMAL, ItemRating.ANCESTRAL, 0, B));
        // a single possible rating gives one number
        assertEquals(new EquipLevelRules.Range(15, 15), EquipLevelRules.effectiveRange(5, ItemRating.UNIQUE, ItemRating.UNIQUE, 0, B));
        // Guadalete-like: netherite 25, unique to legendary (35 to 45), recipe 300 wins on both ends
        assertEquals(new EquipLevelRules.Range(300, 300), EquipLevelRules.effectiveRange(25, ItemRating.UNIQUE, ItemRating.LEGENDARY, 300, B));
        // a recipe level in the middle only lifts the low end
        assertEquals(new EquipLevelRules.Range(20, 45), EquipLevelRules.effectiveRange(5, ItemRating.NORMAL, ItemRating.ANCESTRAL, 20, B));
    }
    @Test
    void parseItemLevelReadsAnIdAndAWholeNumber() {
        EquipLevelRules.ItemLevel entry = EquipLevelRules.parseItemLevel("mitofus:wolfram_sword=8").orElseThrow();
        assertEquals(id("wolfram_sword"), entry.id());
        assertEquals(8, entry.level());
        assertEquals("mitofus:wolfram_sword=8", entry.asString());
        assertEquals(3, EquipLevelRules.parseItemLevel(" minecraft:iron_sword = 3 ").orElseThrow().level());
    }

    @Test
    void parseItemLevelRejectsWrongLines() {
        assertTrue(EquipLevelRules.parseItemLevel(null).isEmpty());
        assertTrue(EquipLevelRules.parseItemLevel("").isEmpty());
        assertTrue(EquipLevelRules.parseItemLevel("=5").isEmpty());
        assertTrue(EquipLevelRules.parseItemLevel("minecraft:iron_sword").isEmpty());
        assertTrue(EquipLevelRules.parseItemLevel("minecraft:iron_sword=x").isEmpty());
        assertTrue(EquipLevelRules.parseItemLevel("minecraft:iron_sword=2.5").isEmpty());
        assertTrue(EquipLevelRules.parseItemLevel("minecraft:iron_sword=0").isEmpty());
        assertTrue(EquipLevelRules.parseItemLevel("minecraft:iron_sword=501").isEmpty());
        assertTrue(EquipLevelRules.parseItemLevel("#minecraft:swords=5").isEmpty(), "a tag is not an item");
        assertFalse(EquipLevelRules.parseItemLevel("minecraft:iron_sword=500").isEmpty());
        assertFalse(EquipLevelRules.parseItemLevel("minecraft:iron_sword=1").isEmpty());
    }

    @Test
    void itemLevelsSkipsWrongLinesAndTheFirstOfARepeatedIdWins() {
        Map<ResourceLocation, Integer> levels = EquipLevelRules.itemLevels(
                List.of("minecraft:iron_sword=3", "bad", "minecraft:iron_sword=9", "minecraft:bow=4"));
        assertEquals(Map.of(ResourceLocation.withDefaultNamespace("iron_sword"), 3, ResourceLocation.withDefaultNamespace("bow"), 4), levels);
    }

    @Test
    void theBalanceRejectsLevelsOutOfRange() {
        Map<ItemRating, Integer> extra = B.ratingExtra();
        assertThrows(IllegalArgumentException.class, () -> new EquipLevelBalance(B.materialLevel(), 0, extra, B.itemLevel()));
        assertThrows(IllegalArgumentException.class, () -> new EquipLevelBalance(Map.of("iron", 0), 5, extra, B.itemLevel()));
        assertThrows(IllegalArgumentException.class, () -> new EquipLevelBalance(B.materialLevel(), 5, Map.of(ItemRating.RARE, -1), B.itemLevel()));
        assertThrows(IllegalArgumentException.class, () -> new EquipLevelBalance(B.materialLevel(), 5, extra,
                Map.of(id("wolfram_sword"), EquipLevelBalance.MAX_LEVEL + 1)));
        assertEquals(0, new EquipLevelBalance(B.materialLevel(), 5, Map.of(ItemRating.RARE, 0), B.itemLevel()).ratingExtra().get(ItemRating.RARE));
    }

    @Test
    void currentIsTheDefaultsBeforeTheConfigLoadsAndAnOverrideAppliesOnlyWhileTheActionRuns() {
        assertEquals(B, EquipLevelBalance.current());
        EquipLevelBalance changed = new EquipLevelBalance(B.materialLevel(), 7, B.ratingExtra(), B.itemLevel());
        EquipLevelBalance[] seen = new EquipLevelBalance[1];
        EquipLevelBalance.withOverride(changed, () -> seen[0] = EquipLevelBalance.current());
        assertEquals(changed, seen[0]);
        assertEquals(B, EquipLevelBalance.current());
        assertThrows(IllegalStateException.class, () -> EquipLevelBalance.withOverride(changed, () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(B, EquipLevelBalance.current(), "the override did not leak");
    }
}
