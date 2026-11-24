package com.orijua.mitofus.core.config;

import com.orijua.mitofus.feature.equipment.ItemRating;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The numbers of the level a rated piece needs, apart from {@link Balance}. Rules take it as a parameter so they stay
 * pure; the game passes {@link #current()} (the server config), tests pass {@link #DEFAULT}. Own design.
 *
 * @param materialLevel      level of each material (vanilla tier name in lower case, or the path of an armor material)
 * @param otherMaterialLevel level of any material that is not in the map
 * @param ratingExtra        levels a rating adds on top of the material's
 * @param itemLevel          level of single objects by item id; it replaces the one of the material (the rating still adds its extra)
 */
public record EquipLevelBalance(
        Map<String, Integer> materialLevel,
        int otherMaterialLevel,
        Map<ItemRating, Integer> ratingExtra,
        Map<ResourceLocation, Integer> itemLevel) {

    /** Highest level a single number can have; the sum of a material and a rating must fit the requirement codec (1000). */
    public static final int MAX_LEVEL = 500;

    public static final EquipLevelBalance DEFAULT = new EquipLevelBalance(
            Map.of("wood", 1, "gold", 1, "leather", 1, "stone", 3, "chainmail", 3, "iron", 5, "diamond", 15, "netherite", 25),
            5,
            Map.of(ItemRating.NORMAL, 0, ItemRating.RARE, 2, ItemRating.EPIC, 5, ItemRating.UNIQUE, 10,
                    ItemRating.LEGENDARY, 20, ItemRating.ANCESTRAL, 40),
            Map.of(ResourceLocation.fromNamespaceAndPath("mitofus", "wolfram_sword"), 8,
                    ResourceLocation.fromNamespaceAndPath("mitofus", "wolfram_smithing_hammer"), 8,
                    ResourceLocation.fromNamespaceAndPath("mitofus", "mithril_smithing_hammer"), 20));

    public EquipLevelBalance {
        materialLevel = Map.copyOf(materialLevel);
        ratingExtra = Map.copyOf(ratingExtra);
        itemLevel = Map.copyOf(itemLevel);
        checkLevel(otherMaterialLevel, 1);
        materialLevel.values().forEach(level -> checkLevel(level, 1));
        ratingExtra.values().forEach(level -> checkLevel(level, 0));
        itemLevel.values().forEach(level -> checkLevel(level, 1));
    }

    private static void checkLevel(int level, int min) {
        if (level < min || level > MAX_LEVEL) {
            throw new IllegalArgumentException("a level must be in [" + min + ", " + MAX_LEVEL + "]: " + level);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static volatile EquipLevelBalance override;

    /** The active server config, or the defaults before a world is loaded (e.g. on the title screen). */
    public static EquipLevelBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.equipLevelBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(EquipLevelBalance balance, Runnable action) {
        EquipLevelBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
