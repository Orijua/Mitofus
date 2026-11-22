package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.EquipLevelBalance;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The level a rated piece needs, pure: the material's level plus the extra of its rating, never below what a recipe stored. */
public final class EquipLevelRules {
    private EquipLevelRules() {
    }

    /** A level of a single object, as the config writes it: {@code ns:path=N}. */
    public record ItemLevel(ResourceLocation id, int level) {
        public String asString() {
            return id + "=" + level;
        }
    }

    /** Reads {@code ns:path=N}; empty if the id is not a valid item id (a tag is not) or N is not a whole number from 1 to the maximum. */
    public static Optional<ItemLevel> parseItemLevel(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int equals = text.lastIndexOf('=');
        if (equals < 1) {
            return Optional.empty();
        }
        String idText = text.substring(0, equals).trim();
        if (idText.startsWith("#")) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null || id.getPath().isEmpty()) {
            return Optional.empty();
        }
        int level;
        try {
            level = Integer.parseInt(text.substring(equals + 1).trim());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return level >= 1 && level <= EquipLevelBalance.MAX_LEVEL ? Optional.of(new ItemLevel(id, level)) : Optional.empty();
    }

    /** The levels of a config list: the invalid lines are skipped and, if an id repeats, the first one wins. */
    public static Map<ResourceLocation, Integer> itemLevels(List<? extends String> lines) {
        Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
        for (String line : lines) {
            parseItemLevel(line).ifPresent(entry -> levels.putIfAbsent(entry.id(), entry.level()));
        }
        return levels;
    }

    /**
     * The level of the material of a piece: the one of this very item if the balance gives it (it replaces the material's),
     * else the one of the material key, else the level of any other material.
     */
    public static int materialLevel(ResourceLocation itemId, String materialKey, EquipLevelBalance balance) {
        Integer own = balance.itemLevel().get(itemId);
        if (own != null) {
            return own;
        }
        return materialKey == null ? balance.otherMaterialLevel()
                : balance.materialLevel().getOrDefault(materialKey, balance.otherMaterialLevel());
    }

    /** The level a rating asks for: the one of its material plus the extra of the rating. */
    public static int derivedLevel(int materialLevel, ItemRating rating, EquipLevelBalance balance) {
        return materialLevel + balance.ratingExtra().getOrDefault(rating, 0);
    }

    /** The levels of a piece: what a recipe stored (0 if none), what its material and rating justify, and the one that counts. */
    public record Levels(int stored, int derived, int effective) {
        public static Levels of(int stored, int derived) {
            return new Levels(stored, derived, effectiveLevel(stored, derived));
        }
    }

    /** The effective levels of a piece that can come out of any rating from {@code min} to {@code max} (the lowest and the highest). */
    public record Range(int low, int high) {
    }

    public static Range effectiveRange(int materialLevel, ItemRating min, ItemRating max, int stored, EquipLevelBalance balance) {
        return new Range(Levels.of(stored, derivedLevel(materialLevel, min, balance)).effective(),
                Levels.of(stored, derivedLevel(materialLevel, max, balance)).effective());
    }

    /** The level that counts: the larger of the one a recipe stored ({@code stored}, 0 if none) and the derived one. */
    public static int effectiveLevel(int stored, int derived) {
        return Math.max(stored, derived);
    }
}
