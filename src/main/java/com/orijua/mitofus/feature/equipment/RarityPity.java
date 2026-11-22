package com.orijua.mitofus.feature.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.core.config.RarityBalance;

/**
 * The anti-bad-luck system (own design), with no game objects: how many forges or finds in a row have given less than
 * Epic, per way, and the virtual skill that earns. Saved with the player and kept on death.
 *
 * @param forge forges in a row without an Epic or better
 * @param loot  finds (chests, elites, bosses) in a row without an Epic or better
 */
public record RarityPity(int forge, int loot) {
    public static final RarityPity EMPTY = new RarityPity(0, 0);

    public static final Codec<RarityPity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("forge", 0).forGetter(RarityPity::forge),
            Codec.INT.optionalFieldOf("loot", 0).forGetter(RarityPity::loot)
    ).apply(instance, RarityPity::new));

    /** Which way a rating came from. */
    public enum Way { FORGE, LOOT }

    public int misses(Way way) {
        return way == Way.FORGE ? forge : loot;
    }

    public RarityPity after(Way way, ItemRating rating, RarityBalance balance) {
        int next = next(misses(way), rating, balance);
        return way == Way.FORGE ? new RarityPity(next, loot) : new RarityPity(forge, next);
    }

    /** The bonus skill for the next roll after {@code misses} in a row: 0 until {@code pityStart}, then {@code pitySkillPerRoll} more each time, up to the cap. */
    public static double bonus(RarityBalance balance, int misses) {
        if (!balance.pityEnabled() || misses < balance.pityStart()) {
            return 0.0;
        }
        return Math.min(balance.pityCap(), (misses - balance.pityStart() + 1L) * balance.pitySkillPerRoll());
    }

    /** The counter after a roll: an Epic or better resets it; anything else adds one (a system that is off counts nothing). */
    public static int next(int misses, ItemRating rating, RarityBalance balance) {
        if (!balance.pityEnabled() || rating.ordinal() >= ItemRating.EPIC.ordinal()) {
            return 0;
        }
        return misses == Integer.MAX_VALUE ? misses : misses + 1;
    }
}
