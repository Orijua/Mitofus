package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.EnhanceBalance;
import org.jetbrains.annotations.Nullable;

/** The pure rules of enhancing equipment: levels lost on failure, what an ancient scroll adds, and the success chance. */
public final class EnhanceRules {
    /** The two kinds of piece that can be enhanced. */
    public enum PieceType { WEAPON, ARMOR }

    private EnhanceRules() {
    }

    /** Levels a failed enhancement takes away with this kind of material. */
    public static int levelsLost(EnhanceBalance balance, EnhanceMaterial.Kind kind) {
        return switch (kind) {
            case STONE -> balance.normalStoneFailLoss();
            case BLESSED_STONE -> balance.blessedStoneFailLoss();
            case BLESSED_SCROLL -> balance.blessedScrollFailLoss();
            case ANCIENT_SCROLL -> 0;
        };
    }

    /** The level after a failure: never below +0. */
    public static int levelAfterFailure(int current, int lost) {
        return Math.max(0, current - lost);
    }

    /** Levels an ancient scroll adds for a roll in [0, 1): uniform between the configured minimum and maximum. */
    public static int ancientGain(EnhanceBalance balance, double roll01) {
        int min = balance.ancientMinGain();
        int max = balance.ancientMaxGain();
        int gain = min + (int) Math.floor(roll01 * (max - min + 1));
        return Math.clamp(gain, min, max);
    }

    /** The level after a success: never above the maximum. */
    public static int levelAfterSuccess(int current, int gain) {
        return Math.min(EquipmentRules.MAX_ENHANCEMENT, current + gain);
    }

    /**
     * The compounding bonus factor of a level: the product of (1 + percentage) for each level 1..level, using the high
     * percentage from {@code bonusHighFromLevel} on. Level 0 or less gives 1.0; above the maximum counts as the maximum.
     */
    public static double bonusFactor(EnhanceBalance balance, int level) {
        double factor = 1.0;
        int top = Math.min(level, EquipmentRules.MAX_ENHANCEMENT);
        for (int i = 1; i <= top; i++) {
            factor *= 1.0 + (i >= balance.bonusHighFromLevel() ? balance.bonusPerLevelHigh() : balance.bonusPerLevel());
        }
        return factor;
    }

    /** What an enhancement adds to a base value (base attack or base armor); nothing when the base is not positive. */
    public static double bonusAmount(EnhanceBalance balance, double base, int level) {
        return base > 0 ? base * (bonusFactor(balance, level) - 1.0) : 0.0;
    }

    /** The chance factor of a rank; 1.0 when there is no rank. */
    public static double chanceFactor(EnhanceBalance balance, @Nullable ItemRating rating) {
        return balance.chanceFactor(rating);
    }

    /**
     * The success chance. An ancient scroll never fails. For the rest it is the usual enhancement chance times the factor of the
     * piece's rank (applied after the minimum chance, so a factor of 0 gives 0). No rank counts as Normal.
     */
    public static double chance(Balance balance, EnhanceBalance enhance, EnhanceMaterial.Kind kind, int level, int dexterity,
                                @Nullable ItemRating rating) {
        if (kind == EnhanceMaterial.Kind.ANCIENT_SCROLL) {
            return 1.0;
        }
        return EquipmentRules.enhancementChance(balance, level, dexterity) * chanceFactor(enhance, rating);
    }

    /**
     * The single rule of "no chance": the client applies it to the visible chance and the server to the real one.
     */
    public static boolean hasChance(double chance) {
        return chance > 0;
    }

    /** The language key for a message about levels: the "_one" variant when exactly one level is meant. */
    public static String levelsKey(String base, int levels) {
        return levels == 1 ? base + "_one" : base;
    }

    /** Whether a material that works on {@code target} can be used on a piece of this type. */
    public static boolean accepts(EnhanceMaterial.Target target, PieceType piece) {
        return switch (target) {
            case ANY -> true;
            case WEAPON -> piece == PieceType.WEAPON;
            case ARMOR -> piece == PieceType.ARMOR;
        };
    }
}
