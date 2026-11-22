package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.RarityBalance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RatingRollerTest {
    @Test
    void lowRollsAreNormalAndTheHighestAreAncestral() {
        assertEquals(ItemRating.NORMAL, RatingRoller.roll(0, 0, 0.0));
        assertEquals(ItemRating.ANCESTRAL, RatingRoller.roll(0, 0, 0.99999));
    }

    @Test
    void novicesFollowTheBaseOdds() {
        // Base weights: 86 % Normal, 10.5 % Rare...
        assertEquals(ItemRating.NORMAL, RatingRoller.roll(0, 0, 0.85));
        assertEquals(ItemRating.RARE, RatingRoller.roll(0, 0, 0.87));
    }

    @Test
    void skillShiftsTheOddsUp() {
        double novice = averageTier(0, 0);
        double master = averageTier(50, 10);
        assertTrue(master > novice, "master " + master + " should beat novice " + novice);
    }

    @Test
    void lootWithoutLuckOrMinimumFollowsTheBaseOdds() {
        for (int i = 0; i < 1000; i++) {
            double random01 = (i + 0.5) / 1000;
            assertEquals(RatingRoller.roll(0, 0, random01), RatingRoller.rollLoot(0, ItemRating.NORMAL, random01));
        }
    }

    @Test
    void lootNeverRollsBelowTheMinimum() {
        for (int i = 0; i < 1000; i++) {
            ItemRating rating = RatingRoller.rollLoot(0, ItemRating.RARE, (i + 0.5) / 1000);
            assertTrue(rating.ordinal() >= ItemRating.RARE.ordinal(), "got " + rating);
        }
        assertEquals(ItemRating.RARE, RatingRoller.rollLoot(0, ItemRating.RARE, 0.0), "the lowest roll lands on the minimum");
    }

    @Test
    void lootLuckShiftsTheOddsUp() {
        double plain = 0;
        double lucky = 0;
        for (int i = 0; i < 10_000; i++) {
            double random01 = (i + 0.5) / 10_000;
            plain += RatingRoller.rollLoot(0, ItemRating.NORMAL, random01).ordinal();
            lucky += RatingRoller.rollLoot(40, ItemRating.NORMAL, random01).ordinal();
        }
        assertTrue(lucky > plain, "luck 40 (" + lucky + ") should beat luck 0 (" + plain + ")");
    }

    @Test
    void oddsAddUpToOneAndMatchTheRoll() {
        double[] odds = RatingRoller.odds(20, 3);
        double sum = 0;
        for (double odd : odds) {
            sum += odd;
        }
        assertEquals(1.0, sum, 1e-9);
        // Rolling just below the Normal share still gives Normal; just above, Rare.
        assertEquals(ItemRating.NORMAL, RatingRoller.roll(20, 3, odds[0] - 1e-6));
        assertEquals(ItemRating.RARE, RatingRoller.roll(20, 3, odds[0] + 1e-6));
    }

    @Test
    void novicesSeeTheBaseOdds() {
        double[] odds = RatingRoller.odds(0, 0);
        assertEquals(0.86, odds[ItemRating.NORMAL.ordinal()], 1e-9);
        assertEquals(0.105, odds[ItemRating.RARE.ordinal()], 1e-9);
        assertEquals(0.00005, odds[ItemRating.ANCESTRAL.ordinal()], 1e-9);
    }

    @Test
    void aRatingRangeOnlyGivesTiersInsideIt() {
        // Guadalete: always Unique or Legendary, never Normal-Epic nor Ancestral, however the roll goes.
        for (int i = 0; i < 1000; i++) {
            ItemRating rating = RatingRoller.roll(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY, (i + 0.5) / 1000);
            assertTrue(rating == ItemRating.UNIQUE || rating == ItemRating.LEGENDARY, "got " + rating);
        }
        assertEquals(ItemRating.UNIQUE, RatingRoller.roll(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY, 0.0));
        assertEquals(ItemRating.LEGENDARY, RatingRoller.roll(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY, 0.99999));
    }

    @Test
    void aRatingRangeNeverFallsBackToAncestralAtTheEdgeOfTheRoll() {
        assertEquals(ItemRating.LEGENDARY, RatingRoller.roll(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY, 1.0));
        assertEquals(ItemRating.LEGENDARY,
                RatingRoller.roll(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY, Math.nextDown(1.0)));
        assertEquals(ItemRating.LEGENDARY, RatingRoller.roll(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY, 5.0));
        assertEquals(ItemRating.UNIQUE, RatingRoller.roll(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY, Double.NaN));
        assertEquals(ItemRating.UNIQUE, RatingRoller.roll(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY, 0.0));
        assertEquals(ItemRating.ANCESTRAL, RatingRoller.roll(0, 0, 1.0), "without a limit the top roll is still Ancestral");
    }

    @Test
    void aRatingRangeKeepsTheRelativeOdds() {
        // Novice base weights: Unique 0.45, Legendary 0.045, so Legendary is 0.045 / 0.495 of the range.
        double[] odds = RatingRoller.odds(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY);
        assertEquals(0, odds[ItemRating.EPIC.ordinal()], 1e-9);
        assertEquals(0, odds[ItemRating.ANCESTRAL.ordinal()], 1e-9);
        assertEquals(0.045 / 0.495, odds[ItemRating.LEGENDARY.ordinal()], 1e-9);
        assertEquals(1.0, odds[ItemRating.UNIQUE.ordinal()] + odds[ItemRating.LEGENDARY.ordinal()], 1e-9);
    }

    @Test
    void skillRaisesTheChanceOfTheTopOfARange() {
        double[] novice = RatingRoller.odds(0, 0, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY);
        double[] master = RatingRoller.odds(50, 10, 0, ItemRating.UNIQUE, ItemRating.LEGENDARY);
        assertTrue(master[ItemRating.LEGENDARY.ordinal()] > novice[ItemRating.LEGENDARY.ordinal()]);
    }

    @Test
    void theBaseOddsFollowTheTableAndAnExpertIsNoLongerLegendaryHalfTheTime() {
        double[] odds = RatingRoller.odds(0, 0);
        assertEquals(0.03, odds[ItemRating.EPIC.ordinal()], 1e-9);
        assertEquals(0.0045, odds[ItemRating.UNIQUE.ordinal()], 1e-9);
        assertEquals(0.00045, odds[ItemRating.LEGENDARY.ordinal()], 1e-9);
        double[] expert = RatingRoller.odds(50, 10);
        double epicOrBetter = 0;
        for (int i = ItemRating.EPIC.ordinal(); i < expert.length; i++) {
            epicOrBetter += expert[i];
        }
        assertTrue(epicOrBetter < 0.45, "an expert's Epic or better is " + epicOrBetter + ", under the old 45 %");
    }

    @Test
    void theWeightsAndTheSkillScaleComeFromTheConfig() {
        RarityBalance flat = new RarityBalance(1, 1, 1, 1, 1, 1, 60.0, true, 30, 3.0, 60.0);
        RarityBalance.withOverride(flat, () -> {
            double[] odds = RatingRoller.odds(0, 0);
            assertEquals(1.0 / 6.0, odds[ItemRating.NORMAL.ordinal()], 1e-9);
            assertEquals(1.0 / 6.0, odds[ItemRating.ANCESTRAL.ordinal()], 1e-9);
        });
        RarityBalance easy = new RarityBalance(86, 10.5, 3, 0.45, 0.045, 0.005, 10.0, true, 30, 3.0, 60.0);
        double normal = RatingRoller.odds(20, 0)[ItemRating.NORMAL.ordinal()];
        RarityBalance.withOverride(easy, () -> assertTrue(RatingRoller.odds(20, 0)[ItemRating.NORMAL.ordinal()] < normal,
                "a smaller scale makes skill matter more"));
    }

    private static double averageTier(int dexterity, int smithingLevel) {
        int samples = 10_000;
        double sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += RatingRoller.roll(dexterity, smithingLevel, (i + 0.5) / samples).ordinal();
        }
        return sum / samples;
    }
}
