package com.orijua.mitofus.core.config;

import com.orijua.mitofus.feature.equipment.ItemRating;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnhanceBalanceTest {
    private static final Map<ItemRating, Double> F = EnhanceBalance.DEFAULT.chanceFactors();

    private static EnhanceBalance make(int a, int b, int c, double bonus, double high, int from, Map<ItemRating, Double> factors,
                                       int min, int max, double drop) {
        return new EnhanceBalance(a, b, c, bonus, high, from, factors, min, max, drop);
    }

    private static EnhanceBalance withBonus(double bonus, double high, int from) {
        return make(3, 1, 0, bonus, high, from, F, 1, 3, 0.05);
    }

    private static EnhanceBalance withFactor(ItemRating rating, double factor) {
        Map<ItemRating, Double> m = new EnumMap<>(ItemRating.class);
        m.putAll(F);
        m.put(rating, factor);
        return make(3, 1, 0, 0.05, 0.07, 6, m, 1, 3, 0.05);
    }

    @Test
    void defaultsAreTheDesignedNumbers() {
        EnhanceBalance d = EnhanceBalance.DEFAULT;
        assertEquals(3, d.normalStoneFailLoss());
        assertEquals(1, d.blessedStoneFailLoss());
        assertEquals(0, d.blessedScrollFailLoss());
        assertEquals(0.05, d.bonusPerLevel());
        assertEquals(0.07, d.bonusPerLevelHigh());
        assertEquals(6, d.bonusHighFromLevel());
        assertEquals(1.0, d.chanceFactor(ItemRating.NORMAL));
        assertEquals(0.95, d.chanceFactor(ItemRating.RARE));
        assertEquals(0.85, d.chanceFactor(ItemRating.EPIC));
        assertEquals(0.7, d.chanceFactor(ItemRating.UNIQUE));
        assertEquals(0.5, d.chanceFactor(ItemRating.LEGENDARY));
        assertEquals(0.1, d.chanceFactor(ItemRating.ANCESTRAL));
        assertEquals(1.0, d.chanceFactor(null));
        assertEquals(1, d.ancientMinGain());
        assertEquals(3, d.ancientMaxGain());
        assertEquals(0.05, d.boneLordAncientScrollChance());
    }

    @Test
    void invalidNumbersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> make(-1, 1, 0, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertThrows(IllegalArgumentException.class, () -> make(3, 11, 0, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertThrows(IllegalArgumentException.class, () -> make(3, 1, 0, 0.05, 0.07, 6, F, 4, 3, 0.05));
        assertThrows(IllegalArgumentException.class, () -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 11, 0.05));
        assertThrows(IllegalArgumentException.class, () -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 3, -0.1));
        // Percentages.
        assertThrows(IllegalArgumentException.class, () -> withBonus(-0.01, 0.07, 6));
        assertThrows(IllegalArgumentException.class, () -> withBonus(1.01, 0.07, 6));
        assertThrows(IllegalArgumentException.class, () -> withBonus(Double.NaN, 0.07, 6));
        assertThrows(IllegalArgumentException.class, () -> withBonus(0.05, -0.01, 6));
        assertThrows(IllegalArgumentException.class, () -> withBonus(0.05, 1.01, 6));
        assertThrows(IllegalArgumentException.class, () -> withBonus(0.05, Double.NaN, 6));
        // Threshold.
        assertThrows(IllegalArgumentException.class, () -> withBonus(0.05, 0.07, 0));
        assertThrows(IllegalArgumentException.class, () -> withBonus(0.05, 0.07, 11));
        // Chance factors.
        assertThrows(IllegalArgumentException.class, () -> withFactor(ItemRating.RARE, -0.01));
        assertThrows(IllegalArgumentException.class, () -> withFactor(ItemRating.ANCESTRAL, 1.01));
        assertThrows(IllegalArgumentException.class, () -> withFactor(ItemRating.EPIC, Double.NaN));
        for (ItemRating missing : ItemRating.values()) {
            Map<ItemRating, Double> m = new EnumMap<>(ItemRating.class);
            m.putAll(F);
            m.remove(missing);
            assertThrows(IllegalArgumentException.class, () -> make(3, 1, 0, 0.05, 0.07, 6, m, 1, 3, 0.05));
        }
    }

    /** The constructor refuses the numbers and the message names the field. */
    private static void assertRefused(String field, Executable creation) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, creation);
        assertTrue(e.getMessage().contains(field), "the message names " + field + ": " + e.getMessage());
    }

    @Test
    void refusedNumbersNameTheirField() {
        assertRefused("normalStoneFailLoss", () -> make(-1, 1, 0, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertRefused("normalStoneFailLoss", () -> make(11, 1, 0, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertRefused("blessedStoneFailLoss", () -> make(3, -1, 0, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertRefused("blessedStoneFailLoss", () -> make(3, 11, 0, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertRefused("blessedScrollFailLoss", () -> make(3, 1, -1, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertRefused("blessedScrollFailLoss", () -> make(3, 1, 11, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertRefused("bonusPerLevel", () -> withBonus(-0.01, 0.07, 6));
        assertRefused("bonusPerLevelHigh", () -> withBonus(0.05, 1.01, 6));
        assertRefused("bonusHighFromLevel", () -> withBonus(0.05, 0.07, 0));
        assertRefused("bonusHighFromLevel", () -> withBonus(0.05, 0.07, 11));
        assertRefused("chanceFactor EPIC", () -> withFactor(ItemRating.EPIC, Double.NaN));
        assertRefused("chanceFactor ANCESTRAL", () -> withFactor(ItemRating.ANCESTRAL, 1.01));
        Map<ItemRating, Double> missing = new EnumMap<>(ItemRating.class);
        missing.putAll(F);
        missing.remove(ItemRating.UNIQUE);
        assertRefused("chanceFactors is missing the rank UNIQUE", () -> make(3, 1, 0, 0.05, 0.07, 6, missing, 1, 3, 0.05));
        assertRefused("ancientMinGain", () -> make(3, 1, 0, 0.05, 0.07, 6, F, 0, 3, 0.05));
        assertRefused("ancientMinGain", () -> make(3, 1, 0, 0.05, 0.07, 6, F, 11, 10, 0.05));
        assertRefused("ancientMinGain", () -> make(3, 1, 0, 0.05, 0.07, 6, F, 4, 3, 0.05));
        assertRefused("ancientMaxGain", () -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 0, 0.05));
        assertRefused("ancientMaxGain", () -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 11, 0.05));
        assertRefused("boneLordAncientScrollChance", () -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 3, -0.1));
        assertRefused("boneLordAncientScrollChance", () -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 3, 1.01));
        assertRefused("boneLordAncientScrollChance", () -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 3, Double.NaN));
    }

    @Test
    void theFactorsAreACopyThatCannotBeChanged() {
        Map<ItemRating, Double> mutable = new EnumMap<>(ItemRating.class);
        mutable.putAll(F);
        EnhanceBalance balance = make(3, 1, 0, 0.05, 0.07, 6, mutable, 1, 3, 0.05);
        // Changing the map after the balance is built does not change the balance.
        mutable.put(ItemRating.LEGENDARY, 0.0);
        mutable.remove(ItemRating.ANCESTRAL);
        assertEquals(0.5, balance.chanceFactor(ItemRating.LEGENDARY));
        assertEquals(0.1, balance.chanceFactor(ItemRating.ANCESTRAL));
        assertThrows(UnsupportedOperationException.class, () -> balance.chanceFactors().put(ItemRating.RARE, 0.0));
    }

    @Test
    void boundaryValuesAreAccepted() {
        assertDoesNotThrow(() -> withBonus(0, 0, 6));
        assertDoesNotThrow(() -> withBonus(1, 1, 6));
        assertDoesNotThrow(() -> withBonus(0.05, 0.07, 1));
        assertDoesNotThrow(() -> withBonus(0.05, 0.07, 10));
        for (ItemRating rating : ItemRating.values()) {
            assertDoesNotThrow(() -> withFactor(rating, 0));
            assertDoesNotThrow(() -> withFactor(rating, 1));
        }
        assertDoesNotThrow(() -> make(0, 0, 0, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertDoesNotThrow(() -> make(10, 10, 10, 0.05, 0.07, 6, F, 1, 3, 0.05));
        assertDoesNotThrow(() -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 1, 0.05));
        assertDoesNotThrow(() -> make(3, 1, 0, 0.05, 0.07, 6, F, 10, 10, 0.05));
        assertDoesNotThrow(() -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 3, 0));
        assertDoesNotThrow(() -> make(3, 1, 0, 0.05, 0.07, 6, F, 1, 3, 1));
    }

    @Test
    void copiesChangeOnlyTheirNumber() {
        EnhanceBalance noLegend = EnhanceBalance.DEFAULT.withChanceFactor(ItemRating.LEGENDARY, 0);
        assertEquals(0.0, noLegend.chanceFactor(ItemRating.LEGENDARY));
        for (ItemRating rating : ItemRating.values()) {
            if (rating != ItemRating.LEGENDARY) {
                assertEquals(EnhanceBalance.DEFAULT.chanceFactor(rating), noLegend.chanceFactor(rating));
            }
        }
        assertEquals(3, noLegend.normalStoneFailLoss());
        assertEquals(0.07, noLegend.bonusPerLevelHigh());
        assertEquals(1.0, EnhanceBalance.DEFAULT.withBoneLordAncientScrollChance(1.0).boneLordAncientScrollChance());
        // The default factor stays 0.5 on the original, and the copy keeps every other field.
        assertEquals(0.5, EnhanceBalance.DEFAULT.chanceFactor(ItemRating.LEGENDARY));
        EnhanceBalance d = EnhanceBalance.DEFAULT;
        assertEquals(new EnhanceBalance(d.normalStoneFailLoss(), d.blessedStoneFailLoss(), d.blessedScrollFailLoss(), d.bonusPerLevel(),
                        d.bonusPerLevelHigh(), d.bonusHighFromLevel(), d.chanceFactors(), d.ancientMinGain(), d.ancientMaxGain(), 1.0),
                d.withBoneLordAncientScrollChance(1.0));
    }
}
