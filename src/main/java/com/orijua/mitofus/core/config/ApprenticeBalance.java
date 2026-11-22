package com.orijua.mitofus.core.config;

/**
 * The tunable numbers of the apprentice blacksmiths, apart from {@link Balance}. The mod's own design (inspired by the
 * production system.
 *
 * @param firstLevel             effective smithing level that allows the first apprentice
 * @param levelsPerApprentice    smithing levels for each apprentice after the first
 * @param maxApprentices         most apprentices a smith may have
 * @param dailyWage              gold each apprentice costs per Minecraft day
 * @param maxPaidDays            days of wage that can be paid ahead
 * @param contractPrice          gold Smith asks for each contract after the first, which he gives
 * @param slowestSecondsPerPiece seconds a beginner takes per piece
 * @param fastestSecondsPerPiece seconds the most skilled apprentice takes per piece
 */
public record ApprenticeBalance(int firstLevel, int levelsPerApprentice, int maxApprentices, int dailyWage, int maxPaidDays,
                                int contractPrice, int slowestSecondsPerPiece, int fastestSecondsPerPiece) {
    public static final int MAX_LEVEL = 1000;
    public static final int MAX_APPRENTICES = 50;
    public static final int MAX_GOLD = 100_000;
    public static final int MAX_DAYS = 365;
    public static final int MAX_SECONDS = 3600;

    public static final ApprenticeBalance DEFAULT = new ApprenticeBalance(50, 10, 5, 1, 14, 21, 60, 20);

    public ApprenticeBalance {
        check("firstLevel", firstLevel, 0, MAX_LEVEL);
        check("levelsPerApprentice", levelsPerApprentice, 1, MAX_LEVEL);
        check("maxApprentices", maxApprentices, 0, MAX_APPRENTICES);
        check("dailyWage", dailyWage, 1, MAX_GOLD);
        check("maxPaidDays", maxPaidDays, 1, MAX_DAYS);
        check("contractPrice", contractPrice, 1, MAX_GOLD);
        check("slowestSecondsPerPiece", slowestSecondsPerPiece, 1, MAX_SECONDS);
        check("fastestSecondsPerPiece", fastestSecondsPerPiece, 1, MAX_SECONDS);
        // The two speeds are a range: if they come swapped, they swap back.
        if (fastestSecondsPerPiece > slowestSecondsPerPiece) {
            int slowest = fastestSecondsPerPiece;
            fastestSecondsPerPiece = slowestSecondsPerPiece;
            slowestSecondsPerPiece = slowest;
        }
    }

    private static void check(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ": " + value);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static ApprenticeBalance override;

    /** The active server config (synced to clients), or the defaults before a world is loaded. */
    public static ApprenticeBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.apprenticeBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(ApprenticeBalance balance, Runnable action) {
        ApprenticeBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
