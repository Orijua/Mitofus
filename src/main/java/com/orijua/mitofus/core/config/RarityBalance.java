package com.orijua.mitofus.core.config;

/**
 * The tunable numbers of the rating of forged and found equipment, apart from {@link Balance}: the base weight of each
 * rating (in {@code ItemRating} order), how much skill doubles the odds of each step up, and the anti-bad-luck system
 * (own design): after {@code pityStart} forges (or finds) in a row without an Epic or better, each next one gets
 * {@code pitySkillPerRoll} more virtual skill, up to {@code pityCap}; an Epic or better resets it.
 */
public record RarityBalance(double normal, double rare, double epic, double unique, double legendary, double ancestral,
                            double skillScale, boolean pityEnabled, int pityStart, double pitySkillPerRoll, double pityCap) {
    public static final double MAX_WEIGHT = 1000.0;

    /** Normal 86 %, Rare 10.5 %, Epic 3 %, Unique 0.45 %, Legendary 0.045 %, Ancestral 0.005 %; 60 skill points double each step. */
    public static final RarityBalance DEFAULT = new RarityBalance(86.0, 10.5, 3.0, 0.45, 0.045, 0.005, 60.0, true, 30, 1.0, 20.0);

    public RarityBalance {
        double total = 0;
        for (double weight : new double[]{normal, rare, epic, unique, legendary, ancestral}) {
            check("a weight", weight, 0.0, MAX_WEIGHT);
            total += weight;
        }
        if (!(total > 0)) {
            throw new IllegalArgumentException("the weights must add up to more than 0");
        }
        check("skillScale", skillScale, 1.0, 1000.0);
        check("pityStart", pityStart, 0.0, 10_000.0);
        check("pitySkillPerRoll", pitySkillPerRoll, 0.0, 1000.0);
        check("pityCap", pityCap, 0.0, 10_000.0);
    }

    private static void check(String name, double value, double min, double max) {
        // NaN fails both comparisons, so it is rejected too.
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ": " + value);
        }
    }

    /** The base weights in {@code ItemRating} order. */
    public double[] weights() {
        return new double[]{normal, rare, epic, unique, legendary, ancestral};
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static RarityBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static RarityBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.rarityBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(RarityBalance balance, Runnable action) {
        RarityBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
