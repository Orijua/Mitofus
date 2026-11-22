package com.orijua.mitofus.feature.smithing;

import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.feature.equipment.EnhanceRules;
import com.orijua.mitofus.feature.equipment.ItemRating;

import java.util.Locale;

/**
 * Pure rules of the smithing hammer. A
 * hammer adds skill points to the rating roll, like a good manual forge: its material sets a base and its rating adds
 * more; its enhancement multiplies both with the same compounding factor as weapons and armor, and its "precision"
 * options are added on top and are not multiplied.
 */
public final class HammerRules {
    /** Extra skill points by the hammer's own rating, in {@link ItemRating} order. */
    private static final double[] RATING_BONUS = {0, 2, 4, 7, 11, 16};

    private HammerRules() {
    }

    public static double ratingBonus(ItemRating rating) {
        return RATING_BONUS[rating.ordinal()];
    }

    /**
     * All the skill points a hammer gives: (material + rating) times the compounding enhancement factor, plus the
     * options (not multiplied). At +0 the factor is 1, so nothing changes.
     */
    public static double skillBonus(EnhanceBalance balance, int materialBonus, ItemRating rating, int enhancement, double optionSkill) {
        return (materialBonus + ratingBonus(rating)) * EnhanceRules.bonusFactor(balance, enhancement) + optionSkill;
    }

    /** "+12" or "+12.5", always with a dot whatever the default locale. */
    public static String format(double bonus) {
        return bonus == Math.rint(bonus) ? "+" + (int) bonus : String.format(Locale.ROOT, "+%.1f", bonus);
    }

    /** Smithing experience with a hammer whose options teach {@code xpBonus} more (0.1 = +10 %). */
    public static int smithingXp(int baseXp, double xpBonus) {
        return (int) Math.round(baseXp * (1 + xpBonus));
    }
}
