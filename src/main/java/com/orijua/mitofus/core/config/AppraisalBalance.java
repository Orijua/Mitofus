package com.orijua.mitofus.core.config;

/**
 * The tunable numbers of the appraisal of equipment with the Appraiser Eye. Rules take it as a parameter so they stay
 * pure; the game passes {@link #current()} (the server config), tests pass {@link #DEFAULT}. Revealing 6 to 10 data per use
 *; the cooldown of the Eye is the mod's own design.
 *
 * @param minData          fewest data one use of the Eye reveals
 * @param maxData          most data one use of the Eye reveals
 * @param eyeCooldownTicks ticks before the Eye can be used again
 */
public record AppraisalBalance(int minData, int maxData, int eyeCooldownTicks) {
    /** The most data a piece can have (and so the most one use can reveal). */
    public static final int MAX_DATA = 64;
    public static final int MAX_COOLDOWN_TICKS = 1200;

    public static final AppraisalBalance DEFAULT = new AppraisalBalance(6, 10, 20);

    public AppraisalBalance {
        if (minData < 1) {
            throw new IllegalArgumentException("minData must be at least 1: " + minData);
        }
        if (maxData < minData) {
            throw new IllegalArgumentException("maxData must not be below minData: " + maxData + " < " + minData);
        }
        if (maxData > MAX_DATA) {
            throw new IllegalArgumentException("maxData must be at most " + MAX_DATA + ": " + maxData);
        }
        if (eyeCooldownTicks < 0 || eyeCooldownTicks > MAX_COOLDOWN_TICKS) {
            throw new IllegalArgumentException("eyeCooldownTicks must be between 0 and " + MAX_COOLDOWN_TICKS + ": " + eyeCooldownTicks);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static AppraisalBalance override;

    /** The active server config, or the defaults before a world is loaded (e.g. on the title screen). */
    public static AppraisalBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.appraisalBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(AppraisalBalance balance, Runnable action) {
        AppraisalBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
