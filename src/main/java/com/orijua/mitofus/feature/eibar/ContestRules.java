package com.orijua.mitofus.feature.eibar;

import com.orijua.mitofus.core.config.ContestBalance;
import com.orijua.mitofus.core.config.Balance.ContestLoss;
import com.orijua.mitofus.feature.equipment.ItemRating;

/**
 * Pure rules of the item production contest against Mencia in Eibar's square. How it is scored is our own design:
 * one piece forged by hand within the time limit, worth {@link #POINTS_PER_RATING} per rating step above Normal plus
 * the minigame's precision in percent, against Mencia's roll (by default 150-300, between a fair Rare and a perfect
 * Epic). The time and her range are in the server config ({@link ContestBalance}).
 */
public final class ContestRules {
    public static final int POINTS_PER_RATING = 100;

    private ContestRules() {
    }

    /** A piece's score: 100 per rating step above Normal plus the precision (0-1) in whole percent. Epic at 87 % is 287. */
    public static int score(ItemRating rating, double precision) {
        double p = Double.isNaN(precision) ? 0 : Math.clamp(precision, 0, 1);
        return rating.ordinal() * POINTS_PER_RATING + (int) Math.round(p * 100);
    }

    /** Mencia's score for a {@code roll} in 0-1. */
    public static int rivalScore(double roll) {
        return rivalScore(roll, ContestBalance.current());
    }

    public static int rivalScore(double roll, ContestBalance balance) {
        double r = Double.isNaN(roll) ? 0 : Math.clamp(roll, 0, 1);
        return balance.rivalMinScore() + (int) Math.round(r * (balance.rivalMaxScore() - balance.rivalMinScore()));
    }

    /** The contest's time limit in game ticks (by default five minutes). */
    public static int timeLimitTicks() {
        return timeLimitTicks(ContestBalance.current());
    }

    public static int timeLimitTicks(ContestBalance balance) {
        return balance.timeLimitMinutes() * 20 * 60;
    }

    /** The player has to beat her: a tie goes to Mencia. */
    public static boolean wins(int score, int rivalScore) {
        return score > rivalScore;
    }

    /** Whether losing closes the quest for good, or allows a rematch the next day. */
    public static boolean failsForGood(ContestLoss loss, boolean wholeWorld) {
        return switch (loss) {
            case FAIL -> true;
            case REMATCH -> false;
            case AUTO -> wholeWorld;
        };
    }

    /** Game ticks left of a contest that began at {@code startedAt}; never negative. */
    public static long ticksLeft(long startedAt, long now) {
        return Math.max(0, timeLimitTicks() - (now - startedAt));
    }

    /** A player who lost on {@code lostDay} (negative: never lost) can try again from the next day. */
    public static boolean canRematch(long lostDay, long today) {
        return lostDay < 0 || today > lostDay;
    }
}
