package com.orijua.mitofus.feature.smithing;

import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.feature.equipment.ItemRating;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HammerRulesTest {
    @Test
    void aBetterHammerAddsMoreSkill() {
        assertEquals(0, HammerRules.ratingBonus(ItemRating.NORMAL));
        assertEquals(2, HammerRules.ratingBonus(ItemRating.RARE));
        assertEquals(4, HammerRules.ratingBonus(ItemRating.EPIC));
        assertEquals(7, HammerRules.ratingBonus(ItemRating.UNIQUE));
        assertEquals(11, HammerRules.ratingBonus(ItemRating.LEGENDARY));
        assertEquals(16, HammerRules.ratingBonus(ItemRating.ANCESTRAL));
        assertEquals(5, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 5, ItemRating.NORMAL, 0, 0), 1e-6, "a plain iron hammer");
        assertEquals(17.5, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.EPIC, 0, 3.5), 1e-6, "material + rating + options at +0");
        assertEquals(36, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 20, ItemRating.ANCESTRAL, 0, 0), 1e-6, "a Ancestral mithril hammer at +0");
    }

    @Test
    void theBalanceGivenIsTheOneUsed() {
        EnhanceBalance tenPercent = new EnhanceBalance(3, 1, 0, 0.10, 0.10, 6, EnhanceBalance.DEFAULT.chanceFactors(), 1, 3, 0.05);
        // (10 + 4) * 1.10^2 = 16.94, against 14 * 1.05^2 = 15.435 with the default balance.
        assertEquals(16.94, HammerRules.skillBonus(tenPercent, 10, ItemRating.EPIC, 2, 0), 1e-6);
        assertEquals(15.435, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.EPIC, 2, 0), 1e-6);
    }

    @Test
    void aNegativeLevelCountsAsPlusZero() {
        // EnhanceRules.bonusFactor returns 1.0 below +0 (see EnhanceRulesTest), so the hammer gives its plain bonus.
        assertEquals(HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.EPIC, 0, 1),
                HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.EPIC, -1, 1), 1e-9);
    }

    @Test
    void formatUsesADotAndDropsWholeDecimals() {
        java.util.Locale previous = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"));
        try {
            assertEquals("+10", HammerRules.format(10));
            assertEquals("+12.8", HammerRules.format(12.7628156));
            assertEquals("+17.9", HammerRules.format(17.9005091));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void enhancementMultipliesMaterialAndRatingWithTheCompoundingFactor() {
        assertEquals(6.3814078, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 5, ItemRating.NORMAL, 5, 0), 1e-6, "+5");
        assertEquals(33.4588956, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 20, ItemRating.NORMAL, 9, 0), 1e-6, "+9");
        assertEquals(17.9005091, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.NORMAL, 10, 0), 1e-6, "+10");
        assertEquals(17.9005091, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.NORMAL, 11, 0), 1e-6, "level 11 counts as +10");
    }

    @Test
    void optionsAreNotMultipliedButRatingIs() {
        assertEquals(10.9502546, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 5, ItemRating.NORMAL, 10, 2), 1e-6, "options added after the factor");
        assertEquals(18.935, HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.EPIC, 2, 3.5), 1e-6, "(10 + 4) * 1.05^2 + 3.5");
    }

    @Test
    void everyLevelGivesMoreSkill() {
        for (int level = 1; level <= 10; level++) {
            assertTrue(HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.NORMAL, level, 0)
                    > HammerRules.skillBonus(EnhanceBalance.DEFAULT, 10, ItemRating.NORMAL, level - 1, 0), "+" + level + " beats +" + (level - 1));
        }
    }

    @Test
    void everyBetterRatingGivesMoreSkill() {
        ItemRating[] ratings = ItemRating.values();
        for (int i = 1; i < ratings.length; i++) {
            assertTrue(HammerRules.ratingBonus(ratings[i]) > HammerRules.ratingBonus(ratings[i - 1]), ratings[i] + " beats " + ratings[i - 1]);
        }
    }

    @Test
    void experienceRoundsAndNeverDropsBelowTheBase() {
        assertEquals(2, HammerRules.smithingXp(1, 0.5), "1.5 rounds up");
        assertEquals(0, HammerRules.smithingXp(0, 0.5));
        assertEquals(10, HammerRules.smithingXp(10, 0));
    }

    @Test
    void learningOptionsGiveMoreExperience() {
        assertEquals(20, HammerRules.smithingXp(20, 0));
        assertEquals(23, HammerRules.smithingXp(20, 0.15));
    }
}
