package com.orijua.mitofus.core.config;

/**
 * The coachman's tariff. Prices in iron
 * (100 iron = 1 gold). The surcharge grows with the distance of the road and never goes past {@code maxSurchargeIron}.
 *
 * @param baseIron              the base fare (500 = 5 gold)
 * @param surchargePer100Blocks   iron added for every 100 blocks of road
 * @param maxSurchargeIron      the most the surcharge can add
 * @param travelSeconds         seconds the player is blind and still on the road before arriving
 */
public record CoachBalance(int baseIron, int surchargePer100Blocks, int maxSurchargeIron, int travelSeconds) {
    public static final int MAX_IRON = 1_000_000;
    public static final int MAX_SECONDS = 60;

    public static final CoachBalance DEFAULT = new CoachBalance(500, 10, 500, 3);

    public CoachBalance {
        if (baseIron < 1 || baseIron > MAX_IRON) {
            throw new IllegalArgumentException("baseIron must be between 1 and " + MAX_IRON + ": " + baseIron);
        }
        if (surchargePer100Blocks < 0 || surchargePer100Blocks > MAX_IRON) {
            throw new IllegalArgumentException("surchargePer100Blocks must be between 0 and " + MAX_IRON + ": " + surchargePer100Blocks);
        }
        if (maxSurchargeIron < 0 || maxSurchargeIron > MAX_IRON) {
            throw new IllegalArgumentException("maxSurchargeIron must be between 0 and " + MAX_IRON + ": " + maxSurchargeIron);
        }
        if (travelSeconds < 0 || travelSeconds > MAX_SECONDS) {
            throw new IllegalArgumentException("travelSeconds must be between 0 and " + MAX_SECONDS + ": " + travelSeconds);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static volatile CoachBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static CoachBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.coachBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(CoachBalance balance, Runnable action) {
        CoachBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
