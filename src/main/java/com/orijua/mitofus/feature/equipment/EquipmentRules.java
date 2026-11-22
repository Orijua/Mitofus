package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.Balance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.ToIntFunction;

/** Pure equipment formulas (project design): option rolls and enhancement odds. */
public final class EquipmentRules {
    public static final int MAX_ENHANCEMENT = 10;

    private EquipmentRules() {
    }

    /** A bonus for a tooltip: at most 2 decimals, no trailing zeros, always with a dot (1.5, 2, 0.7). */
    public static String formatBonus(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /**
     * A fraction as a percentage text, with the "%" sign: "60%", "4.0%", "0.10%". More decimals for small values so
     * the rare tiers don't all read 0%; a positive fraction that would round to 0.00% reads "<0.01%".
     */
    public static String formatPercent(double fraction) {
        double percent = fraction * 100;
        String format = percent >= 10 ? "%.0f%%" : percent >= 1 ? "%.1f%%" : "%.2f%%";
        String text = String.format(java.util.Locale.ROOT, format, percent);
        if (fraction > 0 && text.equals("0.00%")) {
            return "<0.01%";
        }
        return text;
    }

    /** Normal 0, Rare/Epic 1, Unique 2, Legendary/Ancestral 3. */
    public static int optionCount(ItemRating rating) {
        return switch (rating) {
            case NORMAL -> 0;
            case RARE, EPIC -> 1;
            case UNIQUE -> 2;
            case LEGENDARY, ANCESTRAL -> 3;
        };
    }

    /** Better ratings roll closer to the top of the option's range. */
    public static float rollValue(float min, float max, ItemRating rating, double random01) {
        double floor = rating.ordinal() / (double) (ItemRating.values().length + 1);
        double t = floor + (1 - floor) * random01;
        return (float) (min + (max - min) * t);
    }

    /** Weighted pick of {@code count} distinct entries. */
    public static <T> List<T> pickWeighted(List<T> pool, ToIntFunction<T> weight, int count, DoubleSupplier random01) {
        List<T> remaining = new ArrayList<>(pool);
        List<T> picked = new ArrayList<>();
        while (picked.size() < count && !remaining.isEmpty()) {
            int total = remaining.stream().mapToInt(weight).sum();
            double target = random01.getAsDouble() * total;
            for (int i = 0; i < remaining.size(); i++) {
                target -= weight.applyAsInt(remaining.get(i));
                if (target < 0 || i == remaining.size() - 1) {
                    picked.add(remaining.remove(i));
                    break;
                }
            }
        }
        return picked;
    }

    /** Drops with each level already reached, rises a little with DEX; never above the base chance. */
    public static double enhancementChance(Balance balance, int currentLevel, int dexterity) {
        double chance = balance.enhanceBaseChance() - balance.enhanceChanceLossPerLevel() * currentLevel
                + balance.enhanceChancePerDexterity() * dexterity;
        return Math.clamp(chance, balance.enhanceMinChance(), Math.max(balance.enhanceMinChance(), balance.enhanceBaseChance()));
    }
}
