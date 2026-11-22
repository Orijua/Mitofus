package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.WeightBalance;

/** The numbers of weight, pure. Grams everywhere. The excess slows by half its percentage and raises weakness. */
public final class WeightRules {
    public static final int WEAPON_GRAMS = 1500;
    public static final int TOOL_GRAMS = 2000;
    public static final int HELMET_GRAMS = 2000;
    public static final int CHESTPLATE_GRAMS = 6000;
    public static final int LEGGINGS_GRAMS = 4000;
    public static final int BOOTS_GRAMS = 2000;
    public static final int INGOT_GRAMS = 100;
    public static final int NUGGET_GRAMS = 10;
    public static final int ORE_GRAMS = 500;

    private WeightRules() {
    }

    /** Grams as kilograms with at most one decimal and a dot ("1.5", "2", "0.1"), the same in every language. */
    public static String formatKg(long grams) {
        long tenths = Math.round(grams / 100.0);
        return tenths % 10 == 0 ? Long.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
    }

    /** What a player can carry before slowing, in grams. */
    public static long limitGrams(WeightBalance balance, int stamina) {
        return Math.round((balance.baseLimitKg() + balance.kgPerStamina() * Math.max(0, stamina)) * 1000.0);
    }

    /** How far over the limit as a fraction of it (0.5 = 50 % over); 0 when under. */
    public static double excess(long weightGrams, long limitGrams) {
        if (limitGrams <= 0L || weightGrams <= limitGrams) {
            return 0.0;
        }
        return (weightGrams - limitGrams) / (double) limitGrams;
    }

    /** The share of the speed lost: half the excess, never more than all of it (200 % over = 100 % slower). */
    public static double slowdown(double excess) {
        return Math.min(1.0, Math.max(0.0, excess) / 2.0);
    }

    /** The chance per second of being weakened: grows with the excess, 1 at 100 % over. */
    public static double weaknessChance(double excess) {
        return Math.min(1.0, Math.max(0.0, excess));
    }
}
