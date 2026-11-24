package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.equipment.ItemRating;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprenticeRulesTest {
    @Test
    void theFirstApprenticeComesAtFiftyAndOneMoreEveryTen() {
        assertEquals(0, ApprenticeRules.allowedApprentices(49));
        assertEquals(1, ApprenticeRules.allowedApprentices(50));
        assertEquals(1, ApprenticeRules.allowedApprentices(59));
        assertEquals(2, ApprenticeRules.allowedApprentices(60));
        assertEquals(5, ApprenticeRules.allowedApprentices(90));
        assertEquals(5, ApprenticeRules.allowedApprentices(400), "five at most for now");
        assertEquals(50, ApprenticeRules.levelForApprentice(0));
        assertEquals(90, ApprenticeRules.levelForApprentice(4));
        assertEquals(-1, ApprenticeRules.levelForApprentice(5), "no sixth");
    }

    @Test
    void anApprenticeNeverReachesHisMaster() {
        assertEquals(19, ApprenticeRules.workingLevel(30, 20, NpcGrade.NAMED));
        assertEquals(12, ApprenticeRules.workingLevel(12, 50, NpcGrade.NAMED));
        assertEquals(0, ApprenticeRules.workingLevel(5, 0, NpcGrade.NAMED));
    }

    @Test
    void hisGradeCapsHisLevelAndScalesWhatHeLearns() {
        assertEquals(40, ApprenticeRules.workingLevel(65, 90, NpcGrade.NORMAL), "a normal NPC stops at 40");
        assertEquals(65, ApprenticeRules.workingLevel(65, 90, NpcGrade.SEMI_NAMED));
        assertEquals(70, ApprenticeRules.workingLevel(85, 90, NpcGrade.SEMI_NAMED), "a semi-named one at 70");
        assertEquals(89, ApprenticeRules.workingLevel(200, 90, NpcGrade.NAMED), "a named one only by his master");
        assertEquals(10, ApprenticeRules.xpGained(10, NpcGrade.NORMAL));
        assertEquals(15, ApprenticeRules.xpGained(10, NpcGrade.SEMI_NAMED));
        assertEquals(20, ApprenticeRules.xpGained(10, NpcGrade.NAMED));
    }

    @Test
    void gradesAreRolledNinetyNineOne() {
        assertEquals(NpcGrade.NAMED, NpcGrade.roll(0.0));
        assertEquals(NpcGrade.NAMED, NpcGrade.roll(0.0099));
        assertEquals(NpcGrade.SEMI_NAMED, NpcGrade.roll(0.01));
        assertEquals(NpcGrade.SEMI_NAMED, NpcGrade.roll(0.0999));
        assertEquals(NpcGrade.NORMAL, NpcGrade.roll(0.10));
        assertEquals(NpcGrade.NORMAL, NpcGrade.roll(0.9999));
    }

    @Test
    void masteryFollowsTheLevel() {
        assertEquals(MasteryStage.BEGINNER, MasteryStage.of(19));
        assertEquals(MasteryStage.INTERMEDIATE, MasteryStage.of(20));
        assertEquals(MasteryStage.ADVANCED, MasteryStage.of(59));
        assertEquals(MasteryStage.MASTER, MasteryStage.of(60));
        assertTrue(MasteryStage.MASTER.atLeast(MasteryStage.ADVANCED));
        assertFalse(MasteryStage.INTERMEDIATE.atLeast(MasteryStage.ADVANCED));
    }

    @Test
    void onlyAMasterBecomesACraftsman() {
        assertFalse(ApprenticeRules.earnsCraftsman(MasteryStage.ADVANCED, ItemRating.ANCESTRAL, true), "mastery first");
        assertTrue(ApprenticeRules.earnsCraftsman(MasteryStage.MASTER, ItemRating.UNIQUE, true), "his own masterpiece");
        assertFalse(ApprenticeRules.earnsCraftsman(MasteryStage.MASTER, ItemRating.EPIC, true), "an Epic is not one");
        assertTrue(ApprenticeRules.earnsCraftsman(MasteryStage.MASTER, ItemRating.LEGENDARY, false), "illumination by a Legendary");
        assertFalse(ApprenticeRules.earnsCraftsman(MasteryStage.MASTER, ItemRating.UNIQUE, false), "watching a Unique is not enough");
    }

    @Test
    void practiceMakesFewerBotchesAndFasterPieces() {
        assertEquals(ApprenticeRules.MAX_FAIL_CHANCE, ApprenticeRules.failChance(0), 1e-9);
        assertEquals(0.10, ApprenticeRules.failChance(50), 1e-9);
        assertEquals(ApprenticeRules.MIN_FAIL_CHANCE, ApprenticeRules.failChance(100), 1e-9, "never sure");
        assertEquals(ApprenticeBalance.DEFAULT.slowestSecondsPerPiece() * 20, ApprenticeRules.ticksPerPiece(0));
        assertEquals(700, ApprenticeRules.ticksPerPiece(50));
        assertEquals(ApprenticeBalance.DEFAULT.fastestSecondsPerPiece() * 20, ApprenticeRules.ticksPerPiece(100));
    }

    @Test
    void theWageAndTheContractAreAThirdOfTheOldPricesInEmeralds() {
        assertEquals(Money.goldFromEmeralds(3), ApprenticeBalance.DEFAULT.dailyWage());
        assertEquals(Money.goldFromEmeralds(64), ApprenticeBalance.DEFAULT.contractPrice());
        assertEquals(1, ApprenticeBalance.DEFAULT.dailyWage());
        assertEquals(21, ApprenticeBalance.DEFAULT.contractPrice());
    }

    @Test
    void theWageIsPaidInWholeDaysUpToTwoWeeksAhead() {
        assertEquals(9, ApprenticeRules.daysBought(9, 0), "one gold a day");
        assertEquals(10, ApprenticeRules.daysBought(10, 0));
        assertEquals(0, ApprenticeRules.daysBought(0, 0), "not even a day");
        assertEquals(ApprenticeBalance.DEFAULT.maxPaidDays(), ApprenticeRules.daysBought(20, 0), "more gold than the two weeks ahead");
        assertEquals(4, ApprenticeRules.daysBought(64, 10), "up to fourteen days ahead");
        assertEquals(0, ApprenticeRules.daysBought(64, ApprenticeBalance.DEFAULT.maxPaidDays()));
    }

    @Test
    void aWageOfSeveralGoldBuysWholeDaysOnly() {
        assertEquals(3, ApprenticeRules.daysBought(9, 3, 14, 0));
        assertEquals(2, ApprenticeRules.daysBought(8, 3, 14, 0), "the rest does not buy a day");
        assertEquals(0, ApprenticeRules.daysBought(2, 3, 14, 0));
        assertEquals(14, ApprenticeRules.daysBought(1000, 3, 14, 0), "capped at the days ahead allowed");
        assertEquals(4, ApprenticeRules.daysBought(1000, 3, 14, 10));
        assertEquals(30, ApprenticeRules.daysBought(1000, 3, 30, 0), "a server that allows a month ahead");
    }

    @Test
    void aServerCanChangeHowManyApprenticesAndWhen() {
        ApprenticeBalance early = new ApprenticeBalance(20, 5, 8, 1, 14, 21, 60, 20);
        assertEquals(0, ApprenticeRules.allowedApprentices(19, early));
        assertEquals(1, ApprenticeRules.allowedApprentices(20, early));
        assertEquals(3, ApprenticeRules.allowedApprentices(30, early));
        assertEquals(8, ApprenticeRules.allowedApprentices(500, early), "never above the maximum");
        assertEquals(25, ApprenticeRules.levelForApprentice(1, early));
        assertEquals(-1, ApprenticeRules.levelForApprentice(8, early));
        ApprenticeBalance none = new ApprenticeBalance(50, 10, 0, 1, 14, 21, 60, 20);
        assertEquals(0, ApprenticeRules.allowedApprentices(400, none), "0 turns new hires off");
        assertEquals(-1, ApprenticeRules.levelForApprentice(0, none));
    }

    @Test
    void theSpeedFollowsTheConfiguredRange() {
        ApprenticeBalance quick = new ApprenticeBalance(50, 10, 5, 1, 14, 21, 30, 10);
        assertEquals(600, ApprenticeRules.ticksPerPiece(0, quick));
        assertEquals(500, ApprenticeRules.ticksPerPiece(10, quick), "half a second less per level");
        assertEquals(200, ApprenticeRules.ticksPerPiece(100, quick), "never faster than the fastest");
        ApprenticeBalance swapped = new ApprenticeBalance(50, 10, 5, 1, 14, 21, 10, 30);
        assertEquals(30, swapped.slowestSecondsPerPiece(), "a swapped range swaps back");
        assertEquals(10, swapped.fastestSecondsPerPiece());
    }

    @Test
    void theDefaultsAreTheOldNumbers() {
        assertEquals(new ApprenticeBalance(50, 10, 5, 1, 14, 21, 60, 20), ApprenticeBalance.DEFAULT);
        assertEquals(1200, ApprenticeRules.ticksPerPiece(0, ApprenticeBalance.DEFAULT));
        assertEquals(400, ApprenticeRules.ticksPerPiece(100, ApprenticeBalance.DEFAULT));
    }

    @Test
    void anOutOfRangeNumberIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ApprenticeBalance(50, 0, 5, 1, 14, 21, 60, 20), "0 levels per apprentice");
        assertThrows(IllegalArgumentException.class, () -> new ApprenticeBalance(50, 10, 5, 0, 14, 21, 60, 20), "a free wage");
    }

    @Test
    void aClickBuysTheRequestedDaysWhenTheBalanceAndTheLimitAllow() {
        assertEquals(1, ApprenticeRules.daysToPay(1, 100, 100_000, 14, 0));
        assertEquals(5, ApprenticeRules.daysToPay(5, 100, 100_000, 14, 0));
        assertEquals(10, ApprenticeRules.daysToPay(10, 100, 100_000, 14, 0));
    }

    @Test
    void theBalanceLimitsTheDaysPaid() {
        assertEquals(3, ApprenticeRules.daysToPay(10, 100, 350, 14, 0), "it pays the days it can");
        assertEquals(0, ApprenticeRules.daysToPay(5, 100, 99, 14, 0), "not even a day");
    }

    @Test
    void theDaysAheadLimitTheDaysPaid() {
        assertEquals(4, ApprenticeRules.daysToPay(10, 100, 100_000, 14, 10));
        assertEquals(0, ApprenticeRules.daysToPay(5, 100, 100_000, 14, 14));
    }
}
