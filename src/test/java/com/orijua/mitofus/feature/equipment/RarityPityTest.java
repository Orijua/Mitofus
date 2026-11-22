package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.RarityBalance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RarityPityTest {
    private static final RarityBalance B = RarityBalance.DEFAULT;

    @Test
    void theDefaultsAreTheApprovedTable() {
        assertEquals(100.0, B.normal() + B.rare() + B.epic() + B.unique() + B.legendary() + B.ancestral(), 1e-9);
        assertEquals(60.0, B.skillScale());
        assertTrue(B.pityEnabled());
        assertEquals(30, B.pityStart());
        assertEquals(1.0, B.pitySkillPerRoll());
        assertEquals(20.0, B.pityCap());
    }

    @Test
    void thereIsNoBonusBeforeThirtyMisses() {
        assertEquals(0.0, RarityPity.bonus(B, 0));
        assertEquals(0.0, RarityPity.bonus(B, 29));
    }

    @Test
    void afterThirtyMissesEachRollAddsOneUpToTheCap() {
        assertEquals(1.0, RarityPity.bonus(B, 30));
        assertEquals(2.0, RarityPity.bonus(B, 31));
        assertEquals(20.0, RarityPity.bonus(B, 49));
        assertEquals(20.0, RarityPity.bonus(B, 5_000), "the cap holds");
    }

    @Test
    void aSystemThatIsOffAddsNothingAndCountsNothing() {
        RarityBalance off = new RarityBalance(86, 10.5, 3, 0.45, 0.045, 0.005, 60, false, 30, 3, 60);
        assertEquals(0.0, RarityPity.bonus(off, 500));
        assertEquals(0, RarityPity.next(10, ItemRating.NORMAL, off));
    }

    @Test
    void anEpicOrBetterResetsAndAnythingElseCounts() {
        assertEquals(0, RarityPity.next(40, ItemRating.EPIC, B));
        assertEquals(0, RarityPity.next(40, ItemRating.ANCESTRAL, B));
        assertEquals(41, RarityPity.next(40, ItemRating.RARE, B));
        assertEquals(1, RarityPity.next(0, ItemRating.NORMAL, B));
    }

    @Test
    void theCountersAreSeparatePerWay() {
        RarityPity pity = RarityPity.EMPTY.after(RarityPity.Way.FORGE, ItemRating.NORMAL, B)
                .after(RarityPity.Way.FORGE, ItemRating.RARE, B).after(RarityPity.Way.LOOT, ItemRating.NORMAL, B);
        assertEquals(2, pity.forge());
        assertEquals(1, pity.loot());
        RarityPity reset = pity.after(RarityPity.Way.FORGE, ItemRating.LEGENDARY, B);
        assertEquals(0, reset.forge());
        assertEquals(1, reset.loot(), "the other way is untouched");
    }

    @Test
    void thirtyBadForgesRaiseTheOddsOfAnEpicAndAGoodOneResets() {
        double base = epicOrBetter(RatingRoller.odds(0, 0));
        double afterThirty = epicOrBetter(RatingRoller.odds(0, 0, RarityPity.bonus(B, 30)));
        double atTheCap = epicOrBetter(RatingRoller.odds(0, 0, RarityPity.bonus(B, 49)));
        assertTrue(afterThirty > base && atTheCap > afterThirty, base + " < " + afterThirty + " < " + atTheCap);
    }

    /** The bonus is a secret nudge: it helps up to Unique and never gives Legendary or Ancestral (they keep their base odds). */
    @Test
    void theBonusNeverRaisesLegendaryOrAncestral() {
        double[] plain = RatingRoller.odds(0, 0);
        double[] boosted = RatingRoller.odds(0, 0, 60.0);
        assertTrue(boosted[ItemRating.EPIC.ordinal()] > plain[ItemRating.EPIC.ordinal()], "the roll with skill raises Epic");
        // Same roll with the bonus as pity: the number that decides Legendary and Ancestral must not move.
        double top = plain[ItemRating.LEGENDARY.ordinal()] + plain[ItemRating.ANCESTRAL.ordinal()];
        int legendaryOrBetter = 0;
        int plainLegendaryOrBetter = 0;
        int rolls = 200_000;
        for (int i = 0; i < rolls; i++) {
            double r = (i + 0.5) / rolls;
            if (RatingRoller.roll(0, 0, 0, 60.0, ItemRating.NORMAL, ItemRating.ANCESTRAL, r).ordinal() >= ItemRating.LEGENDARY.ordinal()) {
                legendaryOrBetter++;
            }
            if (RatingRoller.roll(0, 0, 0, 0.0, ItemRating.NORMAL, ItemRating.ANCESTRAL, r).ordinal() >= ItemRating.LEGENDARY.ordinal()) {
                plainLegendaryOrBetter++;
            }
        }
        assertTrue(legendaryOrBetter <= plainLegendaryOrBetter, "with pity: " + legendaryOrBetter + ", without: " + plainLegendaryOrBetter);
        assertTrue(legendaryOrBetter / (double) rolls <= top * 1.0001, "never above the base odds");
    }

    @Test
    void theLootRollAlsoKeepsLegendaryAndAncestralAtTheirBaseOdds() {
        int rolls = 200_000;
        int withPity = 0;
        int without = 0;
        for (int i = 0; i < rolls; i++) {
            double r = (i + 0.5) / rolls;
            withPity += RatingRoller.rollLoot(0, 60.0, ItemRating.NORMAL, r).ordinal() >= ItemRating.LEGENDARY.ordinal() ? 1 : 0;
            without += RatingRoller.rollLoot(0, ItemRating.NORMAL, r).ordinal() >= ItemRating.LEGENDARY.ordinal() ? 1 : 0;
        }
        assertTrue(withPity <= without, "with pity: " + withPity + ", without: " + without);
    }

    @Test
    void theBalanceRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new RarityBalance(0, 0, 0, 0, 0, 0, 60, true, 30, 3, 60));
        assertThrows(IllegalArgumentException.class, () -> new RarityBalance(-1, 1, 1, 1, 1, 1, 60, true, 30, 3, 60));
        assertThrows(IllegalArgumentException.class, () -> new RarityBalance(1, 1, 1, 1, 1, 1, 0, true, 30, 3, 60));
        assertThrows(IllegalArgumentException.class, () -> new RarityBalance(1, 1, 1, 1, 1, 1, 60, true, -1, 3, 60));
        assertThrows(IllegalArgumentException.class, () -> new RarityBalance(1, 1, 1, 1, Double.NaN, 1, 60, true, 30, 3, 60));
    }

    private static double epicOrBetter(double[] odds) {
        double sum = 0;
        for (int i = ItemRating.EPIC.ordinal(); i < odds.length; i++) {
            sum += odds[i];
        }
        return sum;
    }
}
