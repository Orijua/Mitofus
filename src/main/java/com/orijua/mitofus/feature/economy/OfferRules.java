package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.feature.equipment.ItemRating;
import net.minecraft.util.Mth;

/**
 * The daily offer of the terminal, pure: how many pieces it has, which rating each position has, the seed of a position
 * and the price. The offer is a function of (day, rating, position) only, so every player sees the same one and nothing
 * is saved. The prices and the seed are the mod's own design.
 */
public final class OfferRules {
    private OfferRules() {
    }

    /** Pieces in the offer: {@code perRank} for every rating from normal up to {@code max}. */
    public static int size(int perRank, ItemRating max) {
        return perRank * (max.ordinal() + 1);
    }

    /**
     * The rating of a position (0-based): the first {@code perRank} are normal, the next ones rare, and so on.
     *
     * @throws IllegalArgumentException if {@code perRank} is below 1 or the position is outside {@code 0} to
     *                                  {@code size(perRank, ANCESTRAL) - 1}
     */
    public static ItemRating ratingAt(int position, int perRank) {
        if (perRank < 1 || position < 0 || position / perRank >= ItemRating.values().length) {
            throw new IllegalArgumentException("position " + position + " is outside the offer of " + perRank + " per rank");
        }
        return ItemRating.values()[position / perRank];
    }

    /** The seed of one piece: mixed from the day, the rating and the position, so no two pieces share a sequence. */
    public static long seed(long day, ItemRating rating, int position) {
        int key = rating.ordinal() * 128 + position;
        int lo = Mth.murmurHash3Mixer((int) day);
        int hi = Mth.murmurHash3Mixer((int) (day >>> 32) ^ 0x5BD1E995);
        int a = Mth.murmurHash3Mixer(lo ^ Mth.murmurHash3Mixer(key));
        int b = Mth.murmurHash3Mixer(hi + Mth.murmurHash3Mixer(key ^ 0x9E3779B9) + a);
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    /**
     * What a piece costs in iron: {@code max(1, referenceGold)} x {@code multiplier} x {@code ironPerGold}, rounded down, at least 1,
     * saturating at {@link Long#MAX_VALUE}. The reference never counts as less than 1 gold, so the offer never sells a piece
     * below what the Trade tab would pay for it (no arbitrage). This is the one place that turns the reference into iron for buying.
     */
    public static long price(double referenceGold, double multiplier, int ironPerGold) {
        double raw = Math.max(1.0, referenceGold) * multiplier * ironPerGold;
        if (raw >= 9.2e18) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, Money.floor(raw));
    }
}
