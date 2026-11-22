package com.orijua.mitofus.core.config;

/**
 * The weight a smith carries. Own design: only smith's objects weigh, the limit grows with Stamina, and the whole system is off by default.
 *
 * @param enabled        whether weight does anything at all (also whether the tooltips show it)
 * @param baseLimitKg    kilograms everyone can carry with no Stamina
 * @param kgPerStamina   kilograms each point of Stamina adds to the limit
 */
public record WeightBalance(boolean enabled, double baseLimitKg, double kgPerStamina) {
    public static final double MAX_KG = 100_000.0;

    public static final WeightBalance DEFAULT = new WeightBalance(false, 40.0, 2.0);

    public WeightBalance {
        if (!(baseLimitKg >= 1.0 && baseLimitKg <= MAX_KG)) {
            throw new IllegalArgumentException("baseLimitKg must be between 1 and " + MAX_KG + ": " + baseLimitKg);
        }
        if (!(kgPerStamina >= 0.0 && kgPerStamina <= MAX_KG)) {
            throw new IllegalArgumentException("kgPerStamina must be between 0 and " + MAX_KG + ": " + kgPerStamina);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static WeightBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static WeightBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.weightBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(WeightBalance balance, Runnable action) {
        WeightBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
