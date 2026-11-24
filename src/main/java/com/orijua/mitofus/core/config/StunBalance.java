package com.orijua.mitofus.core.config;

/**
 * The tunable numbers of stun, apart from {@link Balance}. a blow that takes 40% or more of the
 * victim's life at once stuns them. How long the stun lasts is the mod's own design.
 *
 * @param lifeFraction    fraction of the victim's maximum life one blow must take to stun (0.4 = 40%)
 * @param durationSeconds seconds the victim stays stunned; 0 = stun is off
 * @param immunitySeconds seconds after a stun ends in which the victim cannot be stunned again (so a stun cannot be chained); 0 = no immunity
 */
public record StunBalance(double lifeFraction, int durationSeconds, int immunitySeconds) {
    /** The longest a stun can last. */
    public static final int MAX_SECONDS = 60;
    /** The longest the immunity after a stun can last. */
    public static final int MAX_IMMUNITY_SECONDS = 600;
    public static final int DEFAULT_IMMUNITY_SECONDS = 10;

    public static final StunBalance DEFAULT = new StunBalance(0.4, 2);

    public StunBalance(double lifeFraction, int durationSeconds) {
        this(lifeFraction, durationSeconds, DEFAULT_IMMUNITY_SECONDS);
    }

    public StunBalance {
        if (immunitySeconds < 0 || immunitySeconds > MAX_IMMUNITY_SECONDS) {
            throw new IllegalArgumentException("immunitySeconds must be between 0 and " + MAX_IMMUNITY_SECONDS + ": " + immunitySeconds);
        }
        if (!(lifeFraction > 0.0 && lifeFraction <= 1.0)) {
            throw new IllegalArgumentException("lifeFraction must be in (0, 1]: " + lifeFraction);
        }
        if (durationSeconds < 0 || durationSeconds > MAX_SECONDS) {
            throw new IllegalArgumentException("durationSeconds must be between 0 and " + MAX_SECONDS + ": " + durationSeconds);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static volatile StunBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static StunBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.stunBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(StunBalance balance, Runnable action) {
        StunBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }

    public int durationTicks() {
        return durationSeconds * 20;
    }

    public int immunityTicks() {
        return immunitySeconds * 20;
    }
}
