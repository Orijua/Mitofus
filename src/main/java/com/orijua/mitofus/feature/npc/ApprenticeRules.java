package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.feature.equipment.ItemRating;

/**
 * Pure rules of the apprentice blacksmiths (own design : Cid trains a
 * hundred smiths into a production system). They forge alone, slowly and not as well as their master, can botch a piece,
 * get better with every piece and cost a daily wage.
 */
public final class ApprenticeRules {
    /** What a new apprentice already knows: smithing level 5. */
    public static final int STARTING_XP = 250;
    /** Rating roll penalty: a bit worse than the master's own automatic forging. */
    public static final double RATING_PENALTY = -10;
    /** Chance of botching a piece (the materials are lost): high for a beginner, never below the minimum. */
    public static final double MAX_FAIL_CHANCE = 0.30;
    public static final double MIN_FAIL_CHANCE = 0.05;
    private static final double FAIL_DROP_PER_LEVEL = 0.004;
    /** Each smithing level shaves this off his time per piece, between the slowest and fastest of {@link ApprenticeBalance}. */
    private static final int TICKS_SAVED_PER_LEVEL = 10;
    private static final int TICKS_PER_SECOND = 20;
    public static final long DAY_TICKS = 24000;
    // How many apprentices, their wage, the contract price and their speed are in the server config (ApprenticeBalance).

    private ApprenticeRules() {
    }

    /** How many apprentices a smith of this effective level may have: by default 0 below 50, then 1 at 50, 2 at 60... up to 5. */
    public static int allowedApprentices(int smithingLevel) {
        return allowedApprentices(smithingLevel, ApprenticeBalance.current());
    }

    public static int allowedApprentices(int smithingLevel, ApprenticeBalance balance) {
        if (smithingLevel < balance.firstLevel() || balance.maxApprentices() == 0) {
            return 0;
        }
        return Math.min(balance.maxApprentices(), 1 + (smithingLevel - balance.firstLevel()) / balance.levelsPerApprentice());
    }

    /** The level needed for the next apprentice once you have {@code owned}, or -1 past the maximum. */
    public static int levelForApprentice(int owned) {
        return levelForApprentice(owned, ApprenticeBalance.current());
    }

    public static int levelForApprentice(int owned, ApprenticeBalance balance) {
        return owned >= balance.maxApprentices() ? -1 : balance.firstLevel() + owned * balance.levelsPerApprentice();
    }

    /** A Craftsman's pieces come out as good as his master's automatic forging. */
    public static final double CRAFTSMAN_RATING_PENALTY = 0;
    /** How close to his master a forged Legendary must be to enlighten him. */
    public static final double ILLUMINATION_REACH = 8.0;

    /** The apprentice's working level: what his experience gives, below his master's and within his grade's*/
    public static int workingLevel(int levelFromXp, int masterLevel, NpcGrade grade) {
        return Math.max(0, Math.min(Math.min(levelFromXp, masterLevel - 1), grade.levelCap()));
    }

    /** Experience a piece teaches him: the recipe's, scaled by his grade. */
    public static int xpGained(int recipeXp, NpcGrade grade) {
        return (int) Math.round(recipeXp * grade.xpMultiplier());
    }

    /**
     * Whether a piece makes him a Craftsman. Only a Master can; then his own Unique or better piece is enough, or watching his master forge a
     * Legendary (illumination).
     */
    public static boolean earnsCraftsman(MasteryStage stage, ItemRating piece, boolean ownPiece) {
        if (stage != MasteryStage.MASTER) {
            return false;
        }
        return piece.ordinal() >= (ownPiece ? ItemRating.UNIQUE : ItemRating.LEGENDARY).ordinal();
    }

    public static double failChance(int level) {
        return Math.clamp(MAX_FAIL_CHANCE - level * FAIL_DROP_PER_LEVEL, MIN_FAIL_CHANCE, MAX_FAIL_CHANCE);
    }

    public static int ticksPerPiece(int level) {
        return ticksPerPiece(level, ApprenticeBalance.current());
    }

    /** By default a minute for a beginner, ten ticks less per level, down to 20 s. */
    public static int ticksPerPiece(int level, ApprenticeBalance balance) {
        return Math.max(balance.fastestSecondsPerPiece() * TICKS_PER_SECOND,
                balance.slowestSecondsPerPiece() * TICKS_PER_SECOND - level * TICKS_SAVED_PER_LEVEL);
    }

    /** {@link #failChance} as the percentage every view shows. */
    public static int failPercent(int level) {
        return (int) Math.round(failChance(level) * 100);
    }

    /** {@link #ticksPerPiece} in whole seconds, as every view shows it. */
    public static int secondsPerPiece(int level) {
        return ticksPerPiece(level) / TICKS_PER_SECOND;
    }

    /** Days a payment of {@code gold} buys, given the days already paid ahead. */
    public static int daysBought(int gold, int daysAlreadyPaid) {
        ApprenticeBalance balance = ApprenticeBalance.current();
        return daysBought(gold, balance.dailyWage(), balance.maxPaidDays(), daysAlreadyPaid);
    }

    /** Days a payment of {@code gold} buys at {@code wage} gold a day, up to {@code maxPaidDays} ahead. */
    public static int daysBought(int gold, int wage, int maxPaidDays, int daysAlreadyPaid) {
        return Math.max(0, Math.min(gold / wage, maxPaidDays - daysAlreadyPaid));
    }
}
