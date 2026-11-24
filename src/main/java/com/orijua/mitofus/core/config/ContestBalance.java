package com.orijua.mitofus.core.config;

/**
 * The tunable numbers of the production contest against Mencia in Eibar (quest «Velasco's contest»), apart from
 * {@link Balance}, where its loss rule is. The mod's own design.
 *
 * @param timeLimitMinutes minutes of game time to forge the one piece by hand (whole minutes: the messages say minutes)
 * @param rivalMinScore    fewest points Mencia can score
 * @param rivalMaxScore    most points Mencia can score
 */
public record ContestBalance(int timeLimitMinutes, int rivalMinScore, int rivalMaxScore) {
    public static final int MAX_MINUTES = 60;
    /** A Ancestral piece forged perfectly scores 5 * 100 + 100. */
    public static final int MAX_SCORE = 600;

    public static final ContestBalance DEFAULT = new ContestBalance(5, 150, 300);

    public ContestBalance {
        if (timeLimitMinutes < 1 || timeLimitMinutes > MAX_MINUTES) {
            throw new IllegalArgumentException("timeLimitMinutes must be between 1 and " + MAX_MINUTES + ": " + timeLimitMinutes);
        }
        if (rivalMinScore < 0 || rivalMinScore > MAX_SCORE || rivalMaxScore < 0 || rivalMaxScore > MAX_SCORE) {
            throw new IllegalArgumentException("Mencia's scores must be between 0 and " + MAX_SCORE);
        }
        // A range: if the two come swapped, they swap back.
        if (rivalMinScore > rivalMaxScore) {
            int max = rivalMinScore;
            rivalMinScore = rivalMaxScore;
            rivalMaxScore = max;
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static volatile ContestBalance override;

    /** The active server config (synced to clients), or the defaults before a world is loaded. */
    public static ContestBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.contestBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(ContestBalance balance, Runnable action) {
        ContestBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
