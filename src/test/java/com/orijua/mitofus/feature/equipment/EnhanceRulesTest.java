package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.feature.equipment.EnhanceMaterial.Kind;
import com.orijua.mitofus.feature.equipment.EnhanceMaterial.Target;
import com.orijua.mitofus.feature.equipment.EnhanceRules.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnhanceRulesTest {
    private static final EnhanceBalance E = EnhanceBalance.DEFAULT;
    private static final Balance B = Balance.DEFAULT;

    @Test
    void levelsLostByKind() {
        assertEquals(3, EnhanceRules.levelsLost(E, Kind.STONE));
        assertEquals(1, EnhanceRules.levelsLost(E, Kind.BLESSED_STONE));
        assertEquals(0, EnhanceRules.levelsLost(E, Kind.BLESSED_SCROLL));
        assertEquals(0, EnhanceRules.levelsLost(E, Kind.ANCIENT_SCROLL));
    }

    @Test
    void aFailureNeverGoesBelowZero() {
        assertEquals(2, EnhanceRules.levelAfterFailure(5, 3));
        assertEquals(0, EnhanceRules.levelAfterFailure(2, 3));
        assertEquals(0, EnhanceRules.levelAfterFailure(0, 3));
        assertEquals(0, EnhanceRules.levelAfterFailure(1, 1));
        assertEquals(7, EnhanceRules.levelAfterFailure(7, 0));
    }

    @Test
    void ancientGainIsUniformBetweenMinAndMax() {
        assertEquals(1, EnhanceRules.ancientGain(E, 0.0));
        assertEquals(2, EnhanceRules.ancientGain(E, 0.34));
        assertEquals(3, EnhanceRules.ancientGain(E, 0.67));
        assertEquals(3, EnhanceRules.ancientGain(E, 0.9999));
        assertEquals(3, EnhanceRules.ancientGain(E, 1.0));
        EnhanceBalance fixed = new EnhanceBalance(3, 1, 0, 0.05, 0.07, 6, E.chanceFactors(), 2, 2, 0.05);
        assertEquals(2, EnhanceRules.ancientGain(fixed, 0.0));
        assertEquals(2, EnhanceRules.ancientGain(fixed, 0.99));
    }

    @Test
    void aSuccessNeverGoesAboveTheMaximum() {
        assertEquals(10, EnhanceRules.levelAfterSuccess(9, 3));
        assertEquals(10, EnhanceRules.levelAfterSuccess(10, 1));
        assertEquals(4, EnhanceRules.levelAfterSuccess(3, 1));
    }

    @Test
    void chanceOfEachMaterial() {
        for (int level = 0; level < 10; level++) {
            double normal = EquipmentRules.enhancementChance(B, level, 5);
            // No rank counts as Normal; the ancient scroll is 1.0 with any rank.
            assertEquals(normal, EnhanceRules.chance(B, E, Kind.STONE, level, 5, null));
            assertEquals(normal, EnhanceRules.chance(B, E, Kind.BLESSED_STONE, level, 5, null));
            assertEquals(1.0, EnhanceRules.chance(B, E, Kind.ANCIENT_SCROLL, level, 0, null));
            for (ItemRating rating : ItemRating.values()) {
                double factor = E.chanceFactor(rating);
                assertEquals(1.0, EnhanceRules.chance(B, E, Kind.ANCIENT_SCROLL, level, 0, rating));
                assertEquals(normal * factor, EnhanceRules.chance(B, E, Kind.STONE, level, 5, rating), 1e-12);
                assertEquals(normal * factor, EnhanceRules.chance(B, E, Kind.BLESSED_STONE, level, 5, rating), 1e-12);
                assertEquals(normal * factor, EnhanceRules.chance(B, E, Kind.BLESSED_SCROLL, level, 5, rating), 1e-12);
            }
            EnhanceBalance noLegend = E.withChanceFactor(ItemRating.LEGENDARY, 0);
            assertEquals(0.0, EnhanceRules.chance(B, noLegend, Kind.STONE, level, 5, ItemRating.LEGENDARY));
            assertEquals(0.0, EnhanceRules.chance(B, noLegend, Kind.BLESSED_SCROLL, level, 5, ItemRating.LEGENDARY));
            // With a factor of 0 only the ancient scroll still has a chance.
            assertEquals(1.0, EnhanceRules.chance(B, noLegend, Kind.ANCIENT_SCROLL, level, 0, ItemRating.LEGENDARY));
            assertEquals(0.0, EnhanceRules.chance(B, noLegend, Kind.BLESSED_STONE, level, 5, ItemRating.LEGENDARY));
        }
        // A factor of 0 on a Ancestral piece: only the ancient scroll still has a chance.
        EnhanceBalance noAncestral = E.withChanceFactor(ItemRating.ANCESTRAL, 0);
        assertEquals(0.0, EnhanceRules.chance(B, noAncestral, Kind.STONE, 0, 5, ItemRating.ANCESTRAL));
        assertEquals(0.0, EnhanceRules.chance(B, noAncestral, Kind.BLESSED_STONE, 0, 5, ItemRating.ANCESTRAL));
        assertEquals(0.0, EnhanceRules.chance(B, noAncestral, Kind.BLESSED_SCROLL, 0, 5, ItemRating.ANCESTRAL));
        assertEquals(1.0, EnhanceRules.chance(B, noAncestral, Kind.ANCIENT_SCROLL, 0, 5, ItemRating.ANCESTRAL));
    }

    @Test
    void chanceWithDefaultsInLiterals() {
        // Level 0, DEX 5: 0.95 - 0 + 0.025 = 0.975, capped at the base chance 0.95. Literals, not the production formula.
        Kind[] kinds = {Kind.STONE, Kind.BLESSED_STONE, Kind.BLESSED_SCROLL};
        for (Kind kind : kinds) {
            assertEquals(0.95, EnhanceRules.chance(B, E, kind, 0, 5, null), 1e-12);
            assertEquals(0.95, EnhanceRules.chance(B, E, kind, 0, 5, ItemRating.NORMAL), 1e-12);
            assertEquals(0.9025, EnhanceRules.chance(B, E, kind, 0, 5, ItemRating.RARE), 1e-12);
            assertEquals(0.8075, EnhanceRules.chance(B, E, kind, 0, 5, ItemRating.EPIC), 1e-12);
            assertEquals(0.665, EnhanceRules.chance(B, E, kind, 0, 5, ItemRating.UNIQUE), 1e-12);
            assertEquals(0.475, EnhanceRules.chance(B, E, kind, 0, 5, ItemRating.LEGENDARY), 1e-12);
            assertEquals(0.095, EnhanceRules.chance(B, E, kind, 0, 5, ItemRating.ANCESTRAL), 1e-12);
            // Level 3, DEX 5: 0.95 - 0.27 + 0.0125 = 0.6925.
            assertEquals(0.6925, EnhanceRules.chance(B, E, kind, 3, 5, null), 1e-12);
            assertEquals(0.06925, EnhanceRules.chance(B, E, kind, 3, 5, ItemRating.ANCESTRAL), 1e-12);
        }
    }

    @Test
    void theFactorIsAppliedAfterTheMinimumAndTheCap() {
        Balance low = B.withEnhanceChances(0.5, 0.2);
        // Level 9, DEX 0: 0.5 - 0.81 is under the minimum, so the minimum 0.2 is used and then the factor.
        assertEquals(0.2, EquipmentRules.enhancementChance(low, 9, 0), 1e-12);
        assertEquals(0.2, EnhanceRules.chance(low, E, Kind.STONE, 9, 0, ItemRating.NORMAL), 1e-12);
        assertEquals(0.1, EnhanceRules.chance(low, E, Kind.STONE, 9, 0, ItemRating.LEGENDARY), 1e-12);
        assertEquals(0.02, EnhanceRules.chance(low, E, Kind.STONE, 9, 0, ItemRating.ANCESTRAL), 1e-12);
        // The cap: a lot of DEX never goes above the base chance.
        assertEquals(0.95, EnhanceRules.chance(B, E, Kind.STONE, 0, 100, ItemRating.NORMAL), 1e-12);
        assertEquals(0.475, EnhanceRules.chance(B, E, Kind.STONE, 0, 100, ItemRating.LEGENDARY), 1e-12);
        assertEquals(0.5, EnhanceRules.chance(low, E, Kind.STONE, 0, 100, ItemRating.NORMAL), 1e-12);
        assertEquals(0.25, EnhanceRules.chance(low, E, Kind.STONE, 0, 100, ItemRating.LEGENDARY), 1e-12);
        // No DEX at level 0 is already the base chance.
        assertEquals(0.95, EnhanceRules.chance(B, E, Kind.STONE, 0, 0, ItemRating.NORMAL), 1e-12);
        assertEquals(0.095, EnhanceRules.chance(B, E, Kind.STONE, 0, 0, ItemRating.ANCESTRAL), 1e-12);
    }

    @Test
    void bonusFactorCompounds() {
        double f5 = Math.pow(1.05, 5);
        assertEquals(1.0, EnhanceRules.bonusFactor(E, 0));
        assertEquals(1.2762815625, EnhanceRules.bonusFactor(E, 5), 1e-9);
        assertEquals(f5 * 1.07, EnhanceRules.bonusFactor(E, 6), 1e-12);
        double f9 = EnhanceRules.bonusFactor(E, 9);
        assertEquals(1.6729448, f9, 1e-6);
        assertEquals(67, Math.round((f9 - 1) * 100));
        assertEquals(1.7900509, EnhanceRules.bonusFactor(E, 10), 1e-6);
        assertEquals(1.0, EnhanceRules.bonusFactor(E, -3));
        assertEquals(EnhanceRules.bonusFactor(E, 10), EnhanceRules.bonusFactor(E, 15));
        // The same levels as literals: +6 is the first one at 7 %.
        assertEquals(1.05, EnhanceRules.bonusFactor(E, 1), 1e-9);
        assertEquals(1.365621271875, EnhanceRules.bonusFactor(E, 6), 1e-9);
        assertEquals(1.46121476090625, EnhanceRules.bonusFactor(E, 7), 1e-9);
        assertEquals(1.5634997941696875, EnhanceRules.bonusFactor(E, 8), 1e-9);
    }

    @Test
    void bonusFactorWithOtherConfigs() {
        EnhanceBalance all7 = new EnhanceBalance(3, 1, 0, 0.05, 0.07, 1, E.chanceFactors(), 1, 3, 0.05);
        assertEquals(Math.pow(1.07, 4), EnhanceRules.bonusFactor(all7, 4), 1e-12);
        assertEquals(Math.pow(1.07, 10), EnhanceRules.bonusFactor(all7, 10), 1e-12);
        EnhanceBalance only10 = new EnhanceBalance(3, 1, 0, 0.05, 0.07, 10, E.chanceFactors(), 1, 3, 0.05);
        assertEquals(Math.pow(1.05, 9), EnhanceRules.bonusFactor(only10, 9), 1e-12);
        assertEquals(Math.pow(1.05, 9) * 1.07, EnhanceRules.bonusFactor(only10, 10), 1e-12);
        EnhanceBalance none = new EnhanceBalance(3, 1, 0, 0, 0, 6, E.chanceFactors(), 1, 3, 0.05);
        assertEquals(1.0, EnhanceRules.bonusFactor(none, 10));
        // The same as literals.
        assertEquals(1.31079601, EnhanceRules.bonusFactor(all7, 4), 1e-9);
        assertEquals(1.9671514, EnhanceRules.bonusFactor(all7, 10), 1e-6);
        assertEquals(1.5513282, EnhanceRules.bonusFactor(only10, 9), 1e-6);
        assertEquals(1.6599212, EnhanceRules.bonusFactor(only10, 10), 1e-6);
    }

    @Test
    void bonusAmountIsAPercentageOfTheBase() {
        assertEquals(0.788125, EnhanceRules.bonusAmount(E, 5, 3), 1e-9);
        assertEquals(0.0, EnhanceRules.bonusAmount(E, 0, 5));
        assertEquals(0.0, EnhanceRules.bonusAmount(E, -2, 5));
        assertEquals(0.0, EnhanceRules.bonusAmount(E, 5, 0));
        // A fractional base, a level past the maximum (the same as +10) and a negative base at level 0.
        assertEquals(0.3940625, EnhanceRules.bonusAmount(E, 2.5, 3), 1e-9);
        assertEquals(3.9502545, EnhanceRules.bonusAmount(E, 5, 15), 1e-6);
        assertEquals(0.0, EnhanceRules.bonusAmount(E, -1, 0));
    }

    @Test
    void noChanceIsASingleRule() {
        assertFalse(EnhanceRules.hasChance(0.0));
        assertTrue(EnhanceRules.hasChance(1e-12));
        assertTrue(EnhanceRules.hasChance(1.0));
    }

    @Test
    void oneLevelUsesTheSingularKey() {
        assertEquals("k_one", EnhanceRules.levelsKey("k", 1));
        assertEquals("k", EnhanceRules.levelsKey("k", 3));
        assertEquals("k", EnhanceRules.levelsKey("k", 0));
    }

    @Test
    void bonusesAreFormattedWithoutNoise() {
        assertEquals("1.5", EquipmentRules.formatBonus(1.5));
        assertEquals("2", EquipmentRules.formatBonus(2.0));
        assertEquals("0.7", EquipmentRules.formatBonus(7 * 0.1));
        assertEquals("0.25", EquipmentRules.formatBonus(0.25));
        assertEquals("0.13", EquipmentRules.formatBonus(0.125));
    }

    @Test
    void percentagesAreFormattedWithTheSignAndEnoughDecimals() {
        assertEquals("60%", EquipmentRules.formatPercent(0.6));
        assertEquals("4.0%", EquipmentRules.formatPercent(0.04));
        assertEquals("0.10%", EquipmentRules.formatPercent(0.001));
        assertEquals("0.40%", EquipmentRules.formatPercent(0.004));
        assertEquals("<0.01%", EquipmentRules.formatPercent(0.00001));
        assertEquals("0.00%", EquipmentRules.formatPercent(0.0));
        assertEquals("100%", EquipmentRules.formatPercent(1.0));
        assertEquals("16%", EquipmentRules.formatPercent(1.157625 - 1));
        assertEquals("2.5%", EquipmentRules.formatPercent(0.025));
    }

    @Test
    void whichMaterialsFitWhichPiece() {
        assertTrue(EnhanceRules.accepts(Target.WEAPON, PieceType.WEAPON));
        assertFalse(EnhanceRules.accepts(Target.WEAPON, PieceType.ARMOR));
        assertTrue(EnhanceRules.accepts(Target.ARMOR, PieceType.ARMOR));
        assertFalse(EnhanceRules.accepts(Target.ARMOR, PieceType.WEAPON));
        assertTrue(EnhanceRules.accepts(Target.ANY, PieceType.WEAPON));
        assertTrue(EnhanceRules.accepts(Target.ANY, PieceType.ARMOR));
        assertEquals(EnhanceMaterial.WEAPON_STONE, EnhanceMaterial.normalStoneFor(PieceType.WEAPON));
        assertEquals(EnhanceMaterial.ARMOR_STONE, EnhanceMaterial.normalStoneFor(PieceType.ARMOR));
        assertTrue(EnhanceRules.accepts(EnhanceMaterial.ANCIENT_ARMOR_SCROLL.target(), PieceType.ARMOR));
        assertFalse(EnhanceRules.accepts(EnhanceMaterial.ANCIENT_ARMOR_SCROLL.target(), PieceType.WEAPON));
    }
}
