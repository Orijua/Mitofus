package com.orijua.mitofus.feature.equipment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pure game rule (no Minecraft state): what material and quality an elite's drop has, from the elite's level.
 * The weights are the mod's own design, approved by the user on 2026-11-17 to be tested in play. Each material has a weight at five
 * anchor levels; between two anchors the weights are interpolated, so there are no jumps when levelling.
 */
public final class EliteDropRules {
    /** Materials of the gear an elite can drop, from worst to best. Wood and leather share a weight, and so do stone and chainmail. */
    public enum Material {
        WOOD_LEATHER, STONE_CHAIN, IRON, GOLD, DIAMOND, NETHERITE;

        /** The material of a vanilla gear piece from the path of its id ({@code iron_sword}), empty if it is none of them. */
        public static Optional<Material> ofItemPath(String path) {
            if (path.startsWith("wooden_") || path.startsWith("leather_")) {
                return Optional.of(WOOD_LEATHER);
            }
            if (path.startsWith("stone_") || path.startsWith("chainmail_")) {
                return Optional.of(STONE_CHAIN);
            }
            if (path.startsWith("iron_")) {
                return Optional.of(IRON);
            }
            if (path.startsWith("golden_")) {
                return Optional.of(GOLD);
            }
            if (path.startsWith("diamond_")) {
                return Optional.of(DIAMOND);
            }
            if (path.startsWith("netherite_")) {
                return Optional.of(NETHERITE);
            }
            return Optional.empty();
        }
    }

    /** The elite levels the weight rows below are written for (the middle of the brackets 1-9, 10-19, 20-34, 35-49 and 50+). */
    private static final int[] ANCHOR_LEVELS = {5, 15, 27, 42, 55};
    /** Weights out of 100 per anchor level, in {@link Material} order. Each column adds up to 100. */
    private static final double[][] WEIGHTS = {
            // 5     15    27    42    55
            {59.5, 30.0, 10.0, 3.0, 1.0},   // WOOD_LEATHER
            {30.0, 35.0, 20.0, 7.0, 2.0},   // STONE_CHAIN
            {9.0, 28.0, 40.0, 25.0, 10.0},  // IRON
            {1.0, 5.0, 12.0, 10.0, 5.0},    // GOLD
            {0.5, 2.0, 15.0, 40.0, 40.0},   // DIAMOND
            {0.0, 0.0, 3.0, 15.0, 42.0}};   // NETHERITE

    /** From this elite level the drop is Rare at least; from {@link #EPIC_FROM} it is Epic at least. */
    public static final int RARE_FROM = 35;
    public static final int EPIC_FROM = 50;
    /** The loot luck is the elite's level, up to this much (the rating odds double every 40 points, see {@link RatingRoller}). */
    public static final int MAX_LUCK = 100;

    private EliteDropRules() {
    }

    /** The weight of {@code material} for an elite of {@code level}, interpolated between the anchor levels (flat outside them). */
    public static double weight(Material material, int level) {
        double[] row = WEIGHTS[material.ordinal()];
        if (level <= ANCHOR_LEVELS[0]) {
            return row[0];
        }
        for (int i = 1; i < ANCHOR_LEVELS.length; i++) {
            if (level <= ANCHOR_LEVELS[i]) {
                double t = (double) (level - ANCHOR_LEVELS[i - 1]) / (ANCHOR_LEVELS[i] - ANCHOR_LEVELS[i - 1]);
                return row[i - 1] + (row[i] - row[i - 1]) * t;
            }
        }
        return row[row.length - 1];
    }

    /** All the weights for an elite of {@code level}. */
    public static Map<Material, Double> weights(int level) {
        Map<Material, Double> weights = new EnumMap<>(Material.class);
        for (Material material : Material.values()) {
            weights.put(material, weight(material, level));
        }
        return weights;
    }

    /**
     * Picks a material among {@code available} by the weights of {@code level}. A material with weight 0 never comes out
     * unless it is all there is.
     *
     * @param random01 a uniform random number in [0, 1)
     */
    public static Optional<Material> pickMaterial(int level, java.util.Set<Material> available, double random01) {
        double total = 0;
        for (Material material : available) {
            total += weight(material, level);
        }
        if (available.isEmpty()) {
            return Optional.empty();
        }
        if (total <= 0) {
            return available.stream().min(java.util.Comparator.naturalOrder());
        }
        double target = random01 * total;
        Material last = null;
        for (Material material : Material.values()) {
            double weight = available.contains(material) ? weight(material, level) : 0;
            if (weight <= 0) {
                continue;
            }
            last = material;
            if (target < weight) {
                return Optional.of(material);
            }
            target -= weight;
        }
        return Optional.ofNullable(last);
    }

    /** The lowest rating an elite of {@code level} can drop: the higher the level, the harder it is to get something bad. */
    public static ItemRating minimumRating(int level) {
        if (level >= EPIC_FROM) {
            return ItemRating.EPIC;
        }
        return level >= RARE_FROM ? ItemRating.RARE : ItemRating.NORMAL;
    }

    /**
     * The chance that an elite of {@code level} drops a piece: it starts at {@code baseChance} at level 1 and falls with
     * every level, without a level cap, tending to {@code floorChance} but never going below it. The stronger the elite,
     * the rarer the piece (the user's decision of 2026-11-17). The floor is never above the base chance, so a base of 0 stays 0.
     *
     * @param decay what is left of the excess over the floor gets multiplied by for each level above 1 (0 to 1)
     */
    public static double chance(double baseChance, double floorChance, double decay, int level) {
        double floor = Math.min(floorChance, baseChance);
        return floor + (baseChance - floor) * Math.pow(decay, Math.max(0, level - 1));
    }

    /** The loot luck for the rating roll of an elite of {@code level}. */
    public static int luck(int level) {
        return Math.max(0, Math.min(MAX_LUCK, level));
    }
}
