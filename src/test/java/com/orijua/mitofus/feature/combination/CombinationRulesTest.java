package com.orijua.mitofus.feature.combination;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombinationRulesTest {
    private static final ResourceLocation ARCHER = ResourceLocation.parse("mitofus:archer");
    private static final ResourceLocation MAGE = ResourceLocation.parse("mitofus:mage");
    private static final ResourceLocation SWORD = ResourceLocation.parse("mitofus:swordsman");

    @Test
    void theSlotOrderDoesNotChangeTheKey() {
        assertEquals(CombinationRules.key(List.of(SWORD, MAGE, ARCHER)), CombinationRules.key(List.of(ARCHER, SWORD, MAGE)));
        assertEquals(List.of(ARCHER, MAGE, SWORD), CombinationRules.key(List.of(SWORD, MAGE, ARCHER)), "branches are always listed in the same order");
    }

    @Test
    void aRepeatedBranchCountsOnce() {
        assertEquals(List.of(MAGE), CombinationRules.key(List.of(MAGE, MAGE)));
    }

    @Test
    void aWrittenCombinationIsFoundWhateverTheOrder() {
        Map<List<ResourceLocation>, String> written = Map.of(CombinationRules.key(List.of(SWORD, MAGE)), "Spellsword");
        assertEquals(Optional.of("Spellsword"), CombinationRules.find(written, List.of(MAGE, SWORD)));
        assertEquals(Optional.of("Spellsword"), CombinationRules.find(written, List.of(SWORD, MAGE)));
        assertTrue(CombinationRules.find(written, List.of(SWORD, MAGE, ARCHER)).isEmpty(), "a triple is not the pair");
        assertTrue(CombinationRules.find(written, List.of()).isEmpty());
    }

    @Test
    void thereAreFortyOneCombinationsOfSixBranches() {
        List<ResourceLocation> six = List.of("archer", "mage", "necromancer", "spearman", "squire", "swordsman").stream()
                .map(name -> ResourceLocation.parse("mitofus:" + name)).toList();
        assertEquals(41, CombinationRules.allKeys(six).size());
    }

    @Test
    void validatorFlagsUnknownBranchesBadSizesAndRepeatedSets() {
        Set<ResourceLocation> known = Set.of(ARCHER, MAGE, SWORD);
        var facts = List.of(
                new CombinationRules.Facts(ResourceLocation.parse("mitofus:ok"), List.of(SWORD, MAGE)),
                new CombinationRules.Facts(ResourceLocation.parse("mitofus:again"), List.of(MAGE, SWORD)),
                new CombinationRules.Facts(ResourceLocation.parse("mitofus:ghost"), List.of(ResourceLocation.parse("mitofus:ghost"))),
                new CombinationRules.Facts(ResourceLocation.parse("mitofus:four"), List.of(ARCHER, MAGE, SWORD, ResourceLocation.parse("mitofus:x"))),
                new CombinationRules.Facts(ResourceLocation.parse("mitofus:empty"), List.of()));
        assertEquals(4, CombinationRules.validate(known, facts).size(), CombinationRules.validate(known, facts).toString());
    }
}
