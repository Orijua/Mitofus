package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.PassiveXpBalance;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionRulesTest {
    private static final Balance B = Balance.DEFAULT;
    private static final StatSource CLASS = new StatSource(Component.literal("class"));
    private static final StatSource TITLE = new StatSource(Component.literal("title"));

    @Test
    void xpCurveGrowsWithLevel() {
        assertEquals(75, ProgressionRules.xpToNextLevel(B, 1));
        assertEquals(100, ProgressionRules.xpToNextLevel(B, 2));
    }

    @Test
    void addXpResolvesSeveralLevelUps() {
        // Level 1 → 2 costs 75 XP, 2 → 3 costs 100.
        ProgressionRules.XpResult result = ProgressionRules.addXp(B, PlayerStats.DEFAULT, 180);
        assertEquals(3, result.stats().level());
        assertEquals(5, result.stats().xp());
        assertEquals(20, result.stats().freePoints());
        assertEquals(2, result.levelsGained());
    }

    @Test
    void addXpLevelsUpExactlyAtTheThreshold() {
        ProgressionRules.XpResult below = ProgressionRules.addXp(B, PlayerStats.DEFAULT, 74);
        assertEquals(1, below.stats().level());
        assertEquals(74, below.stats().xp());
        assertEquals(0, below.levelsGained());

        ProgressionRules.XpResult exact = ProgressionRules.addXp(B, PlayerStats.DEFAULT, 75);
        assertEquals(2, exact.stats().level());
        assertEquals(0, exact.stats().xp());
        assertEquals(1, exact.levelsGained());
    }

    @Test
    void negativeXpIsIgnored() {
        assertEquals(PlayerStats.DEFAULT, ProgressionRules.addXp(B, PlayerStats.DEFAULT, -50).stats());
    }

    @Test
    void allocateSpendsOnePoint() {
        PlayerStats stats = PlayerStats.DEFAULT.withProgress(2, 0, 5);
        PlayerStats spent = ProgressionRules.allocate(stats, StatType.DEXTERITY);
        assertEquals(1, spent.dexterity());
        assertEquals(4, spent.freePoints());
        assertSame(PlayerStats.DEFAULT, ProgressionRules.allocate(PlayerStats.DEFAULT, StatType.DEXTERITY), "no points, no change");
    }

    @Test
    void allocateManySpendsUpToWhatIsLeft() {
        PlayerStats stats = PlayerStats.DEFAULT.withProgress(30, 0, 25);
        PlayerStats ten = ProgressionRules.allocate(stats, StatType.STRENGTH, 10);
        assertEquals(10, ten.strength());
        assertEquals(15, ten.freePoints());
        PlayerStats hundred = ProgressionRules.allocate(ten, StatType.STRENGTH, 100);
        assertEquals(25, hundred.strength(), "only the 15 left are spent");
        assertEquals(0, hundred.freePoints());
        assertSame(stats, ProgressionRules.allocate(stats, StatType.STRENGTH, 0), "zero changes nothing");
        assertSame(stats, ProgressionRules.allocate(stats, StatType.STRENGTH, -5), "a negative amount from a bad client changes nothing");
    }

    @Test
    void refundAllReturnsAllocatedPoints() {
        PlayerStats stats = PlayerStats.DEFAULT.with(StatType.STRENGTH, 4).with(StatType.DEXTERITY, 3).withProgress(5, 0, 2);
        PlayerStats refunded = ProgressionRules.refundAll(stats);
        assertEquals(9, refunded.freePoints(), "2 free + 7 allocated");
        assertEquals(0, refunded.strength());
        assertEquals(0, refunded.dexterity());
        assertEquals(5, refunded.level());
    }

    @Test
    void effectiveAddsBonusesOnTopOfAllocatedStats() {
        PlayerStats stats = PlayerStats.DEFAULT.with(StatType.STRENGTH, 2).withProgress(4, 10, 1);
        List<StatModifier> modifiers = new ArrayList<>(StatModifier.allocated(stats));
        modifiers.addAll(StatModifier.fromBonuses(CLASS, Map.of(StatType.STRENGTH, 3)));
        modifiers.addAll(StatModifier.fromBonuses(TITLE, Map.of(StatType.DEXTERITY, 1)));
        PlayerStats total = ProgressionRules.effective(stats, modifiers);
        assertEquals(5, total.strength());
        assertEquals(1, total.dexterity());
        assertEquals(0, total.agility());
        assertEquals(4, total.level(), "level, XP and points are kept");
        assertEquals(1, total.freePoints());
    }

    @Test
    void aPenaltyNeverTakesAStatBelowZero() {
        PlayerStats stats = PlayerStats.DEFAULT.with(StatType.INTELLIGENCE, 5);
        List<StatModifier> modifiers = new ArrayList<>(StatModifier.allocated(stats));
        modifiers.addAll(StatModifier.fromBonuses(TITLE, Map.of(StatType.INTELLIGENCE, -50)));
        assertEquals(0, ProgressionRules.total(StatType.INTELLIGENCE, modifiers), "-50 on 5 points leaves 0, not -45");
        assertEquals(0, ProgressionRules.effective(stats, modifiers).intelligence());
    }

    @Test
    void percentagesApplyAfterEveryFlatBonus() {
        List<StatModifier> modifiers = List.of(
                StatModifier.multiply(TITLE, StatType.STRENGTH, 0.5), // listed first on purpose: order must not matter
                StatModifier.add(StatSource.ALLOCATED, StatType.STRENGTH, 6),
                StatModifier.add(CLASS, StatType.STRENGTH, 4));
        assertEquals(15, ProgressionRules.total(StatType.STRENGTH, modifiers), "(6 + 4) * 1.5");
    }

    @Test
    void percentagesAddUpBeforeApplying() {
        List<StatModifier> modifiers = List.of(
                StatModifier.add(StatSource.ALLOCATED, StatType.AGILITY, 10),
                StatModifier.multiply(CLASS, StatType.AGILITY, 0.1),
                StatModifier.multiply(TITLE, StatType.AGILITY, 0.2));
        assertEquals(13, ProgressionRules.total(StatType.AGILITY, modifiers), "10 * (1 + 0.1 + 0.2), not 10 * 1.1 * 1.2");
    }

    @Test
    void totalRoundsDownAndIgnoresOtherStats() {
        List<StatModifier> modifiers = List.of(
                StatModifier.add(StatSource.ALLOCATED, StatType.STAMINA, 7),
                StatModifier.multiply(CLASS, StatType.STAMINA, 0.1),
                StatModifier.add(CLASS, StatType.STRENGTH, 50));
        assertEquals(7, ProgressionRules.total(StatType.STAMINA, modifiers), "7 * 1.1 = 7.7 rounds down");
        assertEquals(0, ProgressionRules.total(StatType.INTELLIGENCE, List.of()), "no modifiers, no points");
    }

    @Test
    void killXpRewardsToughMobsAndPunishesSpawners() {
        int zombie = ProgressionRules.killXp(B, new ProgressionRules.Victim(20, 3, 2, false, false));
        int armoredBrute = ProgressionRules.killXp(B, new ProgressionRules.Victim(20, 8, 10, false, false));
        int farmedZombie = ProgressionRules.killXp(B, new ProgressionRules.Victim(20, 3, 2, true, false));
        int eliteZombie = ProgressionRules.killXp(B, new ProgressionRules.Victim(20, 3, 2, false, true));
        assertEquals(28, zombie);
        assertTrue(armoredBrute > zombie);
        assertEquals(7, farmedZombie, "a quarter");
        assertEquals(84, eliteZombie, "x3");
    }

    @Test
    void killXpIsAtLeastOne() {
        assertEquals(1, ProgressionRules.killXp(B, new ProgressionRules.Victim(0, 0, 0, true, false)));
    }

    @Test
    void dyingUnderLevelTenCostsNoXp() {
        PlayerStats stats = PlayerStats.DEFAULT.withProgress(9, 50, 0);
        assertEquals(stats, ProgressionRules.applyDeathPenalty(B, stats), "under level 10 nobody loses XP");
    }

    @Test
    void dyingFromLevelTenCostsAShareOfTheLevelXp() {
        PlayerStats after = ProgressionRules.applyDeathPenalty(B, PlayerStats.DEFAULT.withProgress(10, 100, 0));
        assertEquals(70, after.xp(), "30 % of 100 XP lost by default");
        assertEquals(10, after.level());
    }

    @Test
    void startingStatsAreTheDefaults() {
        assertEquals(6, ProgressionRules.baseStat(StatType.STRENGTH));
        assertEquals(7, ProgressionRules.baseStat(StatType.STAMINA));
        assertEquals(4, ProgressionRules.baseStat(StatType.AGILITY));
        assertEquals(5, ProgressionRules.baseStat(StatType.INTELLIGENCE));
        assertEquals(0, ProgressionRules.baseStat(StatType.DEXTERITY));
    }

    @Test
    void deathCostsPartOfTheLevelXpButNeverALevel() {
        Balance penalty = B.withDeathXpLoss(0.10);
        PlayerStats after = ProgressionRules.applyDeathPenalty(penalty, PlayerStats.DEFAULT.withProgress(12, 50, 0));
        assertEquals(45, after.xp());
        assertEquals(12, after.level());
        assertEquals(PlayerStats.DEFAULT, ProgressionRules.applyDeathPenalty(penalty, PlayerStats.DEFAULT));
    }

    @Test
    void deathAtLevelTenKeepsTheLevel() {
        PlayerStats after = ProgressionRules.applyDeathPenalty(B.withDeathXpLoss(0.10), PlayerStats.DEFAULT.withProgress(10, 50, 0));
        assertEquals(10, after.level());
        assertEquals(45, after.xp(), "same share lost as at any other level from level 10");
    }

    @Test
    void smithingLevelIsTheSquareRootOfXpOverTen() {
        assertEquals(0, ProgressionRules.smithingLevel(9));
        assertEquals(1, ProgressionRules.smithingLevel(10));
        assertEquals(3, ProgressionRules.smithingLevel(90));
        assertEquals(0, ProgressionRules.smithingLevel(-5));
    }

    @Test
    void smithingXpForALevelIsWhereThatLevelStarts() {
        assertEquals(0, ProgressionRules.smithingXpFor(0));
        for (int level : new int[]{1, 7, 50, 90}) {
            assertEquals(level, ProgressionRules.smithingLevel(ProgressionRules.smithingXpFor(level)));
            assertEquals(level - 1, ProgressionRules.smithingLevel(ProgressionRules.smithingXpFor(level) - 1), "just below it");
        }
        assertEquals(25_000, ProgressionRules.smithingXpFor(50));
    }

    @Test
    void manaScalesWithIntelligence() {
        assertEquals(20.0f, ProgressionRules.maxMana(B, 0));
        assertEquals(45.0f, ProgressionRules.maxMana(B, 10));
        assertEquals(2.0f, ProgressionRules.manaRegenPerSecond(B, 10), 1e-6f);
    }

    @Test
    void aTitleBonusRaisesSmithingXp() {
        assertEquals(110, ProgressionRules.boostedSmithingXp(100, 0.1));
        assertEquals(17, ProgressionRules.boostedSmithingXp(15, 0.1));
        assertEquals(15, ProgressionRules.boostedSmithingXp(15, 0));
        assertEquals(0, ProgressionRules.boostedSmithingXp(0, 0.1));
    }

    @Test
    void aTitleBonusSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, ProgressionRules.boostedSmithingXp(Integer.MAX_VALUE, 0.1));
        assertEquals(Integer.MAX_VALUE, ProgressionRules.boostedSmithingXp(2_000_000_000, 0.5));
        assertEquals(Integer.MAX_VALUE, ProgressionRules.boostedSmithingXp(10, Double.POSITIVE_INFINITY));
    }

    @Test
    void aBonusThatIsNotAPositiveNumberChangesNothing() {
        assertEquals(15, ProgressionRules.boostedSmithingXp(15, Double.NaN));
        assertEquals(15, ProgressionRules.boostedSmithingXp(15, -0.5));
        assertEquals(15, ProgressionRules.boostedSmithingXp(15, Double.NEGATIVE_INFINITY));
    }

    @Test
    void restedRaisesLevelXpByAPercent() {
        assertEquals(1, ProgressionRules.restedXp(1, 10));
        assertEquals(11, ProgressionRules.restedXp(10, 10));
        assertEquals(17, ProgressionRules.restedXp(15, 10));
        assertEquals(0, ProgressionRules.restedXp(0, 10));
        assertEquals(-5, ProgressionRules.restedXp(-5, 10));
        assertEquals(100, ProgressionRules.restedXp(100, 0));
        assertEquals(Integer.MAX_VALUE, ProgressionRules.restedXp(Integer.MAX_VALUE, 10));
    }

    @Test
    void restedXpEdgeCases() {
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(6, ProgressionRules.restedXp(5, 10), "5 + 10 % = 5.5 rounds up"),
                () -> assertEquals(10, ProgressionRules.restedXp(10, -5), "a negative percent changes nothing"),
                () -> assertEquals(Integer.MAX_VALUE, ProgressionRules.restedXp(Integer.MAX_VALUE - 1, 10), "capped"),
                () -> assertEquals(Integer.MAX_VALUE, ProgressionRules.restedXp(Integer.MAX_VALUE, 0), "no percent"));
    }

    @Test
    void passiveKillXpFollowsTheDefaultCurve() {
        PassiveXpBalance d = PassiveXpBalance.DEFAULT;
        int[][] expected = {{1, 10}, {10, 10}, {11, 10}, {15, 8}, {20, 6}, {25, 3}, {29, 1}, {30, 1}, {100, 1}};
        for (int[] row : expected) {
            assertEquals(row[1], ProgressionRules.passiveKillXp(d, 10, row[0]), "level " + row[0]);
        }
    }

    @Test
    void passiveKillXpOfOneIsAlwaysOneAndOfZeroStaysZero() {
        for (int level = 0; level <= 40; level++) {
            assertEquals(1, ProgressionRules.passiveKillXp(PassiveXpBalance.DEFAULT, 1, level));
            assertEquals(0, ProgressionRules.passiveKillXp(PassiveXpBalance.DEFAULT, 0, level));
        }
    }

    @Test
    void passiveKillXpWithEqualLevelsIsOneStep() {
        PassiveXpBalance step = new PassiveXpBalance(10, 10);
        assertEquals(10, ProgressionRules.passiveKillXp(step, 10, 9));
        assertEquals(1, ProgressionRules.passiveKillXp(step, 10, 10));
    }

    @Test
    void passiveKillXpWithFullXpFromLevelZero() {
        PassiveXpBalance early = new PassiveXpBalance(0, 5);
        assertEquals(10, ProgressionRules.passiveKillXp(early, 10, 0));
        assertEquals(1, ProgressionRules.passiveKillXp(early, 10, 5));
    }

    @Test
    void passiveKillXpWithCrossedLevelsIsOneStepAtOneXpFrom() {
        PassiveXpBalance crossed = new PassiveXpBalance(20, 10);
        assertEquals(10, ProgressionRules.passiveKillXp(crossed, 10, 9));
        assertEquals(1, ProgressionRules.passiveKillXp(crossed, 10, 10));
        assertEquals(1, ProgressionRules.passiveKillXp(crossed, 10, 15));
    }

    @Test
    void passiveKillXpNeverGrowsWithTheLevel() {
        for (int base : new int[]{2, 10, 1000, Integer.MAX_VALUE}) {
            int previous = Integer.MAX_VALUE;
            for (int level = 0; level <= 40; level++) {
                int xp = ProgressionRules.passiveKillXp(PassiveXpBalance.DEFAULT, base, level);
                assertTrue(xp <= previous, "base " + base + " level " + level);
                assertTrue(xp >= 1, "base " + base + " level " + level);
                if (level <= 10) {
                    assertEquals(base, xp, "base " + base + " level " + level);
                }
                previous = xp;
            }
        }
    }

    @Test
    void passiveKillXpKeepsNegativeXpAndGivesFullXpToNegativeLevels() {
        assertEquals(-5, ProgressionRules.passiveKillXp(PassiveXpBalance.DEFAULT, -5, 20));
        assertEquals(10, ProgressionRules.passiveKillXp(PassiveXpBalance.DEFAULT, 10, -3));
    }

    @Test
    void passiveKillXpMatchesFirstReducedLevel() {
        PassiveXpBalance[] configs = {PassiveXpBalance.DEFAULT, new PassiveXpBalance(0, 5), new PassiveXpBalance(10, 10),
                new PassiveXpBalance(20, 10), new PassiveXpBalance(3, 100)};
        for (PassiveXpBalance b : configs) {
            assertTrue(ProgressionRules.passiveKillXp(b, 100, b.firstReducedLevel()) < 100, b.toString());
            if (b.firstReducedLevel() > 0) {
                assertEquals(100, ProgressionRules.passiveKillXp(b, 100, b.firstReducedLevel() - 1), b.toString());
            }
        }
    }

    @Test
    void progressSaysHowMuchIsMissing() {
        PlayerStats stats = PlayerStats.DEFAULT.withProgress(1, 45, 0);
        ProgressionRules.XpProgress progress = ProgressionRules.progress(B, stats);
        assertEquals(1, progress.level());
        assertEquals(45, progress.xp());
        assertEquals(75, progress.needed());
        assertEquals(30, progress.remaining());
        assertEquals(1, ProgressionRules.progress(B, stats.withProgress(1, 74, 0)).remaining());
        assertEquals(0, ProgressionRules.progress(B, stats.withProgress(1, 80, 0)).remaining());
    }

    @Test
    void losingLevelsLowersTheLevelAndKeepsAtLeastLevelOne() {
        PlayerStats stats = PlayerStats.DEFAULT.withProgress(10, 50, 7);
        PlayerStats lost = ProgressionRules.loseLevels(B, stats, 2);
        assertEquals(8, lost.level());
        assertEquals(0, lost.xp(), "the XP of the level is lost too");
        assertEquals(Math.max(0, 7 - 2 * B.statPointsPerLevel()), lost.freePoints(), "free points of those levels go with them, never below 0");
        assertEquals(1, ProgressionRules.loseLevels(B, PlayerStats.DEFAULT.withProgress(2, 10, 0), 2).level(), "never below level 1");
        PlayerStats first = PlayerStats.DEFAULT.withProgress(1, 30, 3);
        assertEquals(first, ProgressionRules.loseLevels(B, first, 2), "at level 1 nothing changes, not even the XP");
        assertEquals(stats, ProgressionRules.loseLevels(B, stats, 0), "zero levels is nothing");
    }
}
