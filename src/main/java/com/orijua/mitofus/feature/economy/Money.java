package com.orijua.mitofus.feature.economy;

/**
 * How the economy rounds money: the one place for it, so the Trade tab and the terminal never disagree by one iron coin.
 * Money always rounds down (the player never gets a unit the site did not pay). Pure, no Minecraft.
 */
public final class Money {
    /** Absorbs floating point noise before rounding down, so 94.99999999999999 counts as 95. */
    private static final double EPSILON = 1e-9;

    private Money() {
    }

    /**
     * An amount of coins for the player to read: digits in groups of three with a dot ({@code 6.400}),
     * the same in every language and on every JVM (it never reads the default locale). A leading minus is kept.
     */
    public static String format(long amount) {
        String digits = Long.toString(amount);
        boolean negative = digits.startsWith("-");
        if (negative) {
            digits = digits.substring(1);
        }
        StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        if (negative) {
            out.append('-');
        }
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append('.');
            }
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    /**
     * Every gold price: rounded down (like all money here), never below 1. Zero, negative and NaN amounts give 1; a huge
     * amount saturates at {@link Long#MAX_VALUE}.
     */
    public static long gold(double amount) {
        if (Double.isNaN(amount)) {
            return 1L;
        }
        return Math.max(1L, floor(amount));
    }

    /** 1 gold = 100 iron. Iron is the unit prices and balances are kept in. */
    public static final int IRON_PER_GOLD = 100;
    /** 1 iron = 100 copper: this is the mod's own design. Copper is only a reward, cashed in as iron. */
    public static final int COPPER_PER_IRON = 100;
    /** 1 diamond = 100 gold: the top coin of the mod's four tiers (copper, iron, gold, diamond), each worth 100 of the one below. */
    public static final int GOLD_PER_DIAMOND = 100;
    /** Iron (the unit prices are kept in) in one diamond coin. */
    public static final long IRON_PER_DIAMOND = (long) IRON_PER_GOLD * GOLD_PER_DIAMOND;

    /** The whole diamond coins of an amount of iron. */
    public static long diamondPart(long iron) {
        return iron / IRON_PER_DIAMOND;
    }

    /** The gold coins left over after the whole diamond coins. */
    public static long goldRest(long iron) {
        return goldPart(iron) % GOLD_PER_DIAMOND;
    }

    /**
     * A price in iron for an amount in gold: rounded down, never below 1 iron. Zero, negative and NaN amounts give 1;
     * a huge amount saturates at {@link Long#MAX_VALUE}.
     */
    public static long iron(double goldAmount) {
        if (Double.isNaN(goldAmount)) {
            return 1L;
        }
        double iron = goldAmount * IRON_PER_GOLD;
        if (iron >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, floor(iron));
    }

    /** The whole gold coins of an amount of iron. */
    public static long goldPart(long iron) {
        return iron / IRON_PER_GOLD;
    }

    /** The iron left over after the whole gold coins. */
    public static long ironPart(long iron) {
        return iron % IRON_PER_GOLD;
    }

    /**
     * An amount of copper split into the fewest coins: [diamond, gold, iron, copper] (each worth 100 of the one below).
     */
    public static long[] coinsOfCopper(long copper) {
        long left = Math.max(0L, copper);
        long copperPart = left % COPPER_PER_IRON;
        long iron = left / COPPER_PER_IRON;
        return new long[]{diamondPart(iron), goldRest(iron), ironPart(iron), copperPart};
    }

    /** Iron paid for copper, rounded down (the copper left over is lost). */
    public static long ironFromCopper(long copper) {
        return Math.max(0L, copper) / COPPER_PER_IRON;
    }

    /** A price computed with doubles (shares, saturation), rounded down to whole units. */
    public static long floor(double value) {
        return (long) Math.floor(value + EPSILON);
    }
}
