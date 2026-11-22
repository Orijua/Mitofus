package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.feature.equipment.EliteDropRules.Material;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliteDropRulesTest {
    @Test
    void weightsAddUpTo100AtEveryLevel() {
        for (int level = 0; level <= 120; level++) {
            double sum = EliteDropRules.weights(level).values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(100.0, sum, 1e-9, "level " + level);
        }
    }

    @Test
    void theAnchorLevelsGiveTheTableValues() {
        assertEquals(59.5, EliteDropRules.weight(Material.WOOD_LEATHER, 5), 1e-9);
        assertEquals(40.0, EliteDropRules.weight(Material.IRON, 27), 1e-9);
        assertEquals(42.0, EliteDropRules.weight(Material.NETHERITE, 55), 1e-9);
        assertEquals(42.0, EliteDropRules.weight(Material.NETHERITE, 99), 1e-9, "flat above the last anchor");
        assertEquals(59.5, EliteDropRules.weight(Material.WOOD_LEATHER, 1), 1e-9, "flat below the first anchor");
    }

    @Test
    void betweenAnchorsTheWeightsAreInterpolatedWithoutJumps() {
        assertEquals(34.0, EliteDropRules.weight(Material.IRON, 21), 1e-9, "halfway between 28 and 40");
        double previous = EliteDropRules.weight(Material.DIAMOND, 1);
        for (int level = 2; level <= 60; level++) {
            double now = EliteDropRules.weight(Material.DIAMOND, level);
            assertTrue(now >= previous - 1e-9, "diamond never gets rarer as the level rises, level " + level);
            previous = now;
        }
    }

    @Test
    void theHigherTheLevelTheLessWoodAndTheMoreNetherite() {
        assertTrue(EliteDropRules.weight(Material.WOOD_LEATHER, 60) < EliteDropRules.weight(Material.WOOD_LEATHER, 5));
        assertTrue(EliteDropRules.weight(Material.NETHERITE, 60) > EliteDropRules.weight(Material.NETHERITE, 5));
        assertEquals(0.0, EliteDropRules.weight(Material.NETHERITE, 5), 1e-9, "no netherite from a level 5 elite");
        assertTrue(EliteDropRules.weight(Material.DIAMOND, 5) > 0, "but a diamond piece is just very rare");
    }

    @Test
    void pickMaterialFollowsTheWeightsAndSkipsWhatIsNotAvailable() {
        Set<Material> all = EnumSet.allOf(Material.class);
        assertEquals(Optional.of(Material.WOOD_LEATHER), EliteDropRules.pickMaterial(5, all, 0.0));
        assertEquals(Optional.of(Material.STONE_CHAIN), EliteDropRules.pickMaterial(5, all, 0.70));
        assertEquals(Optional.of(Material.DIAMOND), EliteDropRules.pickMaterial(5, all, 0.999));
        Set<Material> onlyIronAndGold = EnumSet.of(Material.IRON, Material.GOLD);
        assertEquals(Optional.of(Material.IRON), EliteDropRules.pickMaterial(5, onlyIronAndGold, 0.0));
        assertEquals(Optional.of(Material.GOLD), EliteDropRules.pickMaterial(5, onlyIronAndGold, 0.999));
        assertEquals(Optional.of(Material.NETHERITE), EliteDropRules.pickMaterial(5, EnumSet.of(Material.NETHERITE), 0.5),
                "a zero weight still comes out when it is all there is");
        assertEquals(Optional.empty(), EliteDropRules.pickMaterial(5, EnumSet.noneOf(Material.class), 0.5));
    }

    @Test
    void theMinimumRatingRisesWithTheLevel() {
        assertEquals(ItemRating.NORMAL, EliteDropRules.minimumRating(1));
        assertEquals(ItemRating.NORMAL, EliteDropRules.minimumRating(34));
        assertEquals(ItemRating.RARE, EliteDropRules.minimumRating(35));
        assertEquals(ItemRating.RARE, EliteDropRules.minimumRating(49));
        assertEquals(ItemRating.EPIC, EliteDropRules.minimumRating(50));
    }

    @Test
    void theLuckIsTheLevelWithinBounds() {
        assertEquals(0, EliteDropRules.luck(-3));
        assertEquals(30, EliteDropRules.luck(30));
        assertEquals(EliteDropRules.MAX_LUCK, EliteDropRules.luck(500));
    }

    @Test
    void theMaterialComesFromTheItemId() {
        assertEquals(Optional.of(Material.WOOD_LEATHER), Material.ofItemPath("leather_boots"));
        assertEquals(Optional.of(Material.WOOD_LEATHER), Material.ofItemPath("wooden_sword"));
        assertEquals(Optional.of(Material.STONE_CHAIN), Material.ofItemPath("chainmail_helmet"));
        assertEquals(Optional.of(Material.GOLD), Material.ofItemPath("golden_axe"));
        assertEquals(Optional.of(Material.NETHERITE), Material.ofItemPath("netherite_chestplate"));
        assertEquals(Optional.empty(), Material.ofItemPath("wolfram_sword"));
    }

    @Test
    void theDropChanceFallsWithEveryLevelWithoutACapAndTendsToTheFloor() {
        double base = 0.25, floor = 0.00005, decay = 0.92;
        assertEquals(base, EliteDropRules.chance(base, floor, decay, 1), 1e-12);
        assertEquals(base, EliteDropRules.chance(base, floor, decay, 0), 1e-12, "a level below 1 counts as 1");
        double previous = base;
        for (int level = 2; level <= 2000; level++) {
            double now = EliteDropRules.chance(base, floor, decay, level);
            assertTrue(level > 300 ? now <= previous : now < previous, "it falls at every level (up to where doubles can no longer tell it from the floor), level " + level);
            assertTrue(now >= floor, "never below the floor, level " + level);
            previous = now;
        }
        assertEquals(floor, EliteDropRules.chance(base, floor, decay, 2000), 1e-12, "tends to the floor, whatever the level");
        assertTrue(EliteDropRules.chance(base, floor, decay, 50) < 0.005 && EliteDropRules.chance(base, floor, decay, 50) > 0.003,
                "about 0.4% at level 50");
    }

    @Test
    void theFloorNeverRaisesTheChance() {
        assertEquals(0.0, EliteDropRules.chance(0.0, 0.00005, 0.92, 30), 1e-12, "a base chance of 0 stays 0");
        assertEquals(1.0, EliteDropRules.chance(1.0, 1.0, 0.92, 30), 1e-12, "base and floor at 1 make it flat");
        assertEquals(0.001, EliteDropRules.chance(0.001, 0.5, 0.92, 30), 1e-12, "a floor above the base is capped by it");
        assertEquals(0.25, EliteDropRules.chance(0.25, 0.00005, 1.0, 400), 1e-12, "a decay of 1 never falls");
    }
}
