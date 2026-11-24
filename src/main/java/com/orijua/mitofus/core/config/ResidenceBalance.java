package com.orijua.mitofus.core.config;

/**
 * The residence (own design): how long a player must wait before changing it. A game day is 20 real minutes.
 *
 * @param cooldownMinutes real minutes between two changes of village (20 = one game day; 0 = no wait)
 */
public record ResidenceBalance(int cooldownMinutes) {
    public static final int MAX_MINUTES = 10_080;

    public static final ResidenceBalance DEFAULT = new ResidenceBalance(20);

    public ResidenceBalance {
        if (cooldownMinutes < 0 || cooldownMinutes > MAX_MINUTES) {
            throw new IllegalArgumentException("cooldownMinutes must be between 0 and " + MAX_MINUTES + ": " + cooldownMinutes);
        }
    }

    public long cooldownTicks() {
        return cooldownMinutes * 1200L;
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static volatile ResidenceBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static ResidenceBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.residenceBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(ResidenceBalance balance, Runnable action) {
        ResidenceBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
