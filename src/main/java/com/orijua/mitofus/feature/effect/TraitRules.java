package com.orijua.mitofus.feature.effect;

import java.util.List;

/** Pure rules for adding up passive bonuses (no Minecraft objects, so they are unit-tested). */
public final class TraitRules {
    private TraitRules() {
    }

    /** Total of the unconditional bonuses of {@code type} for a player of {@code level}. */
    public static double sum(List<Bonus> bonuses, BonusType type, int level) {
        double total = 0;
        for (Bonus bonus : bonuses) {
            if (bonus.type() == type && bonus.withItem().isEmpty() && !bonus.atNight()) {
                total += bonus.valueAt(level);
            }
        }
        return total;
    }

    /**
     * Total of the {@link BonusType#DAMAGE} bonuses that apply right now.
     *
     * @param holdsRequiredItem whether the main hand holds an item of a bonus's {@code with_item} tag (checked by the caller per bonus)
     */
    public static double damage(List<Bonus> bonuses, int level, java.util.function.Predicate<Bonus> holdsRequiredItem, boolean night) {
        double total = 0;
        for (Bonus bonus : bonuses) {
            if (bonus.type() != BonusType.DAMAGE) {
                continue;
            }
            if (bonus.atNight() && !night) {
                continue;
            }
            if (bonus.withItem().isPresent() && !holdsRequiredItem.test(bonus)) {
                continue;
            }
            total += bonus.valueAt(level);
        }
        return total;
    }
}
