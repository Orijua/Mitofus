package com.orijua.mitofus.core.config;

import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;

/**
 * The tunable numbers of the VR helmet tiers, apart from {@link Balance}. The mod's own design. The basic
 * VR helmet has no numbers here: it uses the {@link FatigueBalance} as is.
 *
 * @param advancedSessionMinutes  real minutes of normal play from full energy to empty with the advanced VR helmet
 * @param advancedRecoveryMinutes real minutes to refill from empty in the real world after an advanced session
 * @param diamondSessionMinutes   the same for the diamond VR helmet
 * @param diamondRecoveryMinutes  the same for the diamond VR helmet
 * @param advancedHelmetPrice      price of the advanced helmet in iron (1 gold = 100 iron)
 * @param diamondHelmetPrice       price of the diamond helmet in iron
 * @param restedMinutes           minutes of "rested" bonus a diamond connection grants (0 turns it off)
 * @param restedXpPercent         extra level XP (percent) while rested
 * @param restedMinEnergy         the energy (0 to 1) needed when connecting to get the rested bonus
 * @param restedCooldownMinutes   minutes spent inside Mitofus after a rested bonus is granted before connecting grants another (0 = no wait)
 * @param unbindCostPercent       percent of the price of a VR helmet's tier that unbinding a bound VR helmet costs at the terminal (0 = free)
 * @param basicReferencePrice     price of the basic helmet in iron
 */
public record VrHelmetBalance(
        double advancedSessionMinutes,
        double advancedRecoveryMinutes,
        double diamondSessionMinutes,
        double diamondRecoveryMinutes,
        long advancedHelmetPrice,
        long diamondHelmetPrice,
        int restedMinutes,
        int restedXpPercent,
        double restedMinEnergy,
        int restedCooldownMinutes,
        int unbindCostPercent,
        long basicReferencePrice) {

    public static final double MAX_SESSION_MINUTES = 1440.0;
    public static final double MIN_RECOVERY_MINUTES = 0.1;
    public static final long MAX_PRICE = 1_000_000_000L;
    public static final int MAX_RESTED_MINUTES = 1440;
    public static final int MAX_RESTED_XP_PERCENT = 1000;
    public static final int MAX_RESTED_COOLDOWN_MINUTES = 1440;

    public static final int MAX_UNBIND_COST_PERCENT = 1000;

    public static final VrHelmetBalance DEFAULT = new VrHelmetBalance(55.0, 8.0, 75.0, 6.0, 1000L, 5000L, 10, 10, 0.90, 30, 200, 350L);

    /** Same as the full constructor with the default unbinding cost. */
    public VrHelmetBalance(double advancedSessionMinutes, double advancedRecoveryMinutes, double diamondSessionMinutes,
                          double diamondRecoveryMinutes, long advancedHelmetPrice, long diamondHelmetPrice, int restedMinutes,
                          int restedXpPercent, double restedMinEnergy, int restedCooldownMinutes) {
        this(advancedSessionMinutes, advancedRecoveryMinutes, diamondSessionMinutes, diamondRecoveryMinutes, advancedHelmetPrice,
                diamondHelmetPrice, restedMinutes, restedXpPercent, restedMinEnergy, restedCooldownMinutes, 200, 350L);
    }

    public VrHelmetBalance {
        checkRange("advancedSessionMinutes", advancedSessionMinutes, 1.0, MAX_SESSION_MINUTES);
        checkRange("diamondSessionMinutes", diamondSessionMinutes, 1.0, MAX_SESSION_MINUTES);
        checkRange("advancedRecoveryMinutes", advancedRecoveryMinutes, MIN_RECOVERY_MINUTES, MAX_SESSION_MINUTES);
        checkRange("diamondRecoveryMinutes", diamondRecoveryMinutes, MIN_RECOVERY_MINUTES, MAX_SESSION_MINUTES);
        checkRange("advancedHelmetPrice", advancedHelmetPrice, 1.0, MAX_PRICE);
        checkRange("diamondHelmetPrice", diamondHelmetPrice, 1.0, MAX_PRICE);
        checkRange("restedMinutes", restedMinutes, 0.0, MAX_RESTED_MINUTES);
        checkRange("restedXpPercent", restedXpPercent, 0.0, MAX_RESTED_XP_PERCENT);
        checkRange("restedMinEnergy", restedMinEnergy, 0.0, 1.0);
        checkRange("restedCooldownMinutes", restedCooldownMinutes, 0.0, MAX_RESTED_COOLDOWN_MINUTES);
        checkRange("unbindCostPercent", unbindCostPercent, 0.0, MAX_UNBIND_COST_PERCENT);
        checkRange("basicReferencePrice", basicReferencePrice, 0.0, MAX_PRICE);
    }

    private static void checkRange(String name, double value, double min, double max) {
        // NaN fails both comparisons, so it is rejected too.
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ": " + value);
        }
    }

    /** Real minutes of normal play from full to empty with this tier; the basic one is the fatigue's. */
    public double sessionMinutes(VrHelmetTier tier, FatigueBalance fatigue) {
        return switch (tier) {
            case BASIC -> fatigue.sessionMinutes();
            case ADVANCED -> advancedSessionMinutes;
            case DIAMOND -> diamondSessionMinutes;
        };
    }

    /** Real minutes to refill from empty after a session with this tier; the basic one is the fatigue's. */
    public double recoveryMinutes(VrHelmetTier tier, FatigueBalance fatigue) {
        return switch (tier) {
            case BASIC -> fatigue.recoveryMinutes();
            case ADVANCED -> advancedRecoveryMinutes;
            case DIAMOND -> diamondRecoveryMinutes;
        };
    }

    /** Iron the helmet of this tier counts as worth (the basic one uses the reference price). */
    public long helmetPrice(VrHelmetTier tier) {
        return switch (tier) {
            case BASIC -> basicReferencePrice;
            case ADVANCED -> advancedHelmetPrice;
            case DIAMOND -> diamondHelmetPrice;
        };
    }

    /** Iron it costs to unbind a bound helmet of {@code tier}: {@code unbindCostPercent} of its tier's price. */
    public long unbindPrice(VrHelmetTier tier) {
        long price = helmetPrice(tier);
        return (price * unbindCostPercent + 99) / 100;
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static VrHelmetBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static VrHelmetBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.vrHelmetBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(VrHelmetBalance balance, Runnable action) {
        VrHelmetBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
