package com.orijua.mitofus.core.config;

/**
 * How the XP of killing peaceful mobs (animals and the like) falls with the player's level. The mod's own design.
 *
 * @param fullXpUntilLevel up to this level (included) they give their normal XP
 * @param oneXpFromLevel   from this level on they give 1 XP; in between the XP falls in a straight line. If it is not
 *                         above {@code fullXpUntilLevel} the drop is a single step
 */
public record PassiveXpBalance(int fullXpUntilLevel, int oneXpFromLevel) {
    /** The most either level can be. */
    public static final int MAX_LEVEL = 10_000;

    public static final PassiveXpBalance DEFAULT = new PassiveXpBalance(10, 30);

    public PassiveXpBalance {
        if (fullXpUntilLevel < 0 || fullXpUntilLevel > MAX_LEVEL) {
            throw new IllegalArgumentException("fullXpUntilLevel must be between 0 and " + MAX_LEVEL + ": " + fullXpUntilLevel);
        }
        if (oneXpFromLevel < 1 || oneXpFromLevel > MAX_LEVEL) {
            throw new IllegalArgumentException("oneXpFromLevel must be between 1 and " + MAX_LEVEL + ": " + oneXpFromLevel);
        }
    }

    /** First player level at which a peaceful mob gives less than its normal XP (if that normal XP is above 1). */
    public int firstReducedLevel() {
        return Math.min(fullXpUntilLevel + 1, oneXpFromLevel);
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static PassiveXpBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static PassiveXpBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.passiveXpBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(PassiveXpBalance balance, Runnable action) {
        PassiveXpBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
