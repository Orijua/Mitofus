package com.orijua.mitofus.core.config;

import com.orijua.mitofus.feature.equipment.ItemRating;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The tunable numbers of what a failed enhancement costs, of the enhancement bonus and of the new enhancement materials.
 * Rules take it as a parameter so they stay pure; the game passes {@link #current()} (the server config), tests pass
 * {@link #DEFAULT}. The levels lost by failing (3 with a normal stone, 1 with a blessed one, none with a blessed scroll)
 * and the compounding bonus (5% per level, 7% from +6) are; the chance factors by rank and the ancient scroll
 * numbers are the mod's own design.
 *
 * @param normalStoneFailLoss         levels lost when an enhancement with a normal stone fails
 * @param blessedStoneFailLoss        levels lost when it fails with a blessed stone
 * @param blessedScrollFailLoss       levels lost when it fails with the blessed scroll
 * @param bonusPerLevel               fraction of the item's base value each level below {@code bonusHighFromLevel} adds (compounding)
 * @param bonusPerLevelHigh           the same for each level from {@code bonusHighFromLevel} on
 * @param bonusHighFromLevel          first level that uses {@code bonusPerLevelHigh}
 * @param chanceFactors               multiplier of the success chance by rank (stones and blessed scroll); all six ranks present
 * @param ancientMinGain              fewest levels an ancient scroll adds
 * @param ancientMaxGain              most levels an ancient scroll adds
 * @param boneLordAncientScrollChance chance the Bone Lord drops an ancient scroll when a player kills it
 */
public record EnhanceBalance(int normalStoneFailLoss, int blessedStoneFailLoss, int blessedScrollFailLoss,
                             double bonusPerLevel, double bonusPerLevelHigh, int bonusHighFromLevel,
                             Map<ItemRating, Double> chanceFactors, int ancientMinGain, int ancientMaxGain,
                             double boneLordAncientScrollChance) {
    /** Local copy of the highest enhancement level (this package must not depend on feature/). */
    public static final int MAX_LEVELS = 10;

    public static final EnhanceBalance DEFAULT = new EnhanceBalance(3, 1, 0, 0.05, 0.07, 6,
            Map.of(ItemRating.NORMAL, 1.0, ItemRating.RARE, 0.95, ItemRating.EPIC, 0.85,
                    ItemRating.UNIQUE, 0.7, ItemRating.LEGENDARY, 0.5, ItemRating.ANCESTRAL, 0.1),
            1, 3, 0.05);

    public EnhanceBalance {
        checkLoss("normalStoneFailLoss", normalStoneFailLoss);
        checkLoss("blessedStoneFailLoss", blessedStoneFailLoss);
        checkLoss("blessedScrollFailLoss", blessedScrollFailLoss);
        checkFraction("bonusPerLevel", bonusPerLevel);
        checkFraction("bonusPerLevelHigh", bonusPerLevelHigh);
        if (bonusHighFromLevel < 1 || bonusHighFromLevel > MAX_LEVELS) {
            throw new IllegalArgumentException("bonusHighFromLevel must be between 1 and " + MAX_LEVELS + ": " + bonusHighFromLevel);
        }
        chanceFactors = Map.copyOf(chanceFactors);
        for (ItemRating rating : ItemRating.values()) {
            Double factor = chanceFactors.get(rating);
            if (factor == null) {
                throw new IllegalArgumentException("chanceFactors is missing the rank " + rating);
            }
            checkFraction("chanceFactor " + rating, factor);
        }
        if (ancientMinGain < 1 || ancientMinGain > MAX_LEVELS) {
            throw new IllegalArgumentException("ancientMinGain must be between 1 and " + MAX_LEVELS + ": " + ancientMinGain);
        }
        if (ancientMaxGain < 1 || ancientMaxGain > MAX_LEVELS) {
            throw new IllegalArgumentException("ancientMaxGain must be between 1 and " + MAX_LEVELS + ": " + ancientMaxGain);
        }
        if (ancientMinGain > ancientMaxGain) {
            throw new IllegalArgumentException("ancientMinGain must not be above ancientMaxGain: " + ancientMinGain + " > " + ancientMaxGain);
        }
        checkFraction("boneLordAncientScrollChance", boneLordAncientScrollChance);
    }

    private static void checkLoss(String name, int value) {
        if (value < 0 || value > MAX_LEVELS) {
            throw new IllegalArgumentException(name + " must be between 0 and " + MAX_LEVELS + ": " + value);
        }
    }

    private static void checkFraction(String name, double value) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1: " + value);
        }
    }

    /** The success-chance factor of a rank; 1.0 when there is no rank. */
    public double chanceFactor(@Nullable ItemRating rating) {
        return rating == null ? 1.0 : chanceFactors.get(rating);
    }

    /** A copy with a different chance factor for one rank, for tests. */
    public EnhanceBalance withChanceFactor(ItemRating rating, double factor) {
        Map<ItemRating, Double> copy = new EnumMap<>(ItemRating.class);
        copy.putAll(chanceFactors);
        copy.put(rating, factor);
        return new EnhanceBalance(normalStoneFailLoss, blessedStoneFailLoss, blessedScrollFailLoss, bonusPerLevel, bonusPerLevelHigh,
                bonusHighFromLevel, copy, ancientMinGain, ancientMaxGain, boneLordAncientScrollChance);
    }

    /** A copy with a different {@link #boneLordAncientScrollChance}, e.g. 1.0 so a test always gets the drop. */
    public EnhanceBalance withBoneLordAncientScrollChance(double chance) {
        return new EnhanceBalance(normalStoneFailLoss, blessedStoneFailLoss, blessedScrollFailLoss, bonusPerLevel, bonusPerLevelHigh,
                bonusHighFromLevel, chanceFactors, ancientMinGain, ancientMaxGain, chance);
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static volatile EnhanceBalance override;

    /** The active server config, or the defaults before a world is loaded (e.g. on the title screen). */
    public static EnhanceBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.enhanceBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(EnhanceBalance balance, Runnable action) {
        EnhanceBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
