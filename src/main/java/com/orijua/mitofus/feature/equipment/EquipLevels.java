package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.EquipLevelBalance;
import com.orijua.mitofus.core.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * The bridge between a stack and {@link EquipLevelRules}: the requirements a piece really has. Computed on every read and
 * never stored, so an old piece gets its level too. It touches no client class, so the tooltip and the server agree.
 */
public final class EquipLevels {
    private EquipLevels() {
    }

    /** The level its material and rating ask for; 0 for a piece with no rating. */
    public static int derivedLevel(ItemStack stack) {
        return derivedLevel(stack, EquipLevelBalance.current());
    }

    public static int derivedLevel(ItemStack stack, EquipLevelBalance balance) {
        ItemRating rating = stack.get(ModDataComponents.ITEM_RATING);
        return rating == null ? 0 : levelFor(stack.getItem(), rating, 0, balance).derived();
    }

    /** The level of the material of an item (its own level if the balance gives one). */
    public static int materialLevel(Item item, EquipLevelBalance balance) {
        return EquipLevelRules.materialLevel(BuiltInRegistries.ITEM.getKey(item),
                EquipMaterial.key(new ItemStack(item)).orElse(null), balance);
    }

    /** The one source of the levels of a rated piece of {@code item} whose recipe stored {@code storedLevel} (0 if none). */
    public static EquipLevelRules.Levels levelFor(Item item, ItemRating rating, int storedLevel, EquipLevelBalance balance) {
        return EquipLevelRules.Levels.of(storedLevel, EquipLevelRules.derivedLevel(materialLevel(item, balance), rating, balance));
    }

    /** The levels a piece of {@code item} can need, from its lowest to its highest possible rating, with a recipe's stored level. */
    public static EquipLevelRules.Range levelRange(Item item, ItemRating min, ItemRating max, int storedLevel, EquipLevelBalance balance) {
        return EquipLevelRules.effectiveRange(materialLevel(item, balance), min, max, storedLevel, balance);
    }

    /** Stored, derived and effective levels of a stack together (all 0 for a piece with neither rating nor stored level). */
    public static EquipLevelRules.Levels levels(ItemStack stack, EquipLevelBalance balance) {
        ItemRating rating = stack.get(ModDataComponents.ITEM_RATING);
        int stored = storedLevel(stack);
        return rating == null ? EquipLevelRules.Levels.of(stored, 0) : levelFor(stack.getItem(), rating, stored, balance);
    }

    /** The level a recipe stored in the piece, 0 if it has none. */
    public static int storedLevel(ItemStack stack) {
        EquipRequirements stored = stack.get(ModDataComponents.REQUIREMENTS);
        return stored == null ? 0 : stored.level();
    }

    /** The level that counts: the larger of the stored one and the derived one (0 for a piece with neither). */
    public static int effectiveLevel(ItemStack stack, EquipLevelBalance balance) {
        return levels(stack, balance).effective();
    }

    /** The level a Normal piece of {@code item} asks for: what an unappraised piece of it asks for, whatever it really is. */
    public static int normalLevel(Item item, EquipLevelBalance balance) {
        return levelFor(item, ItemRating.NORMAL, 0, balance).effective();
    }

    /**
     * The requirements to equip the piece, as they count: an unappraised piece asks for the level of a Normal piece of its
     * material and no stats; any other, its true requirements ({@link #trueRequirements}).
     */
    public static Optional<EquipRequirements> requirements(ItemStack stack) {
        return requirements(stack, EquipLevelBalance.current());
    }

    public static Optional<EquipRequirements> requirements(ItemStack stack, EquipLevelBalance balance) {
        if (AppraisalService.isUnappraised(stack)) {
            return Optional.of(new EquipRequirements(normalLevel(stack.getItem(), balance), Map.of()));
        }
        return trueRequirements(stack, balance);
    }

    /**
     * The requirements the piece really has, appraised or not: the stored ones with the level raised to the derived one if it
     * is higher. A rated piece with none stored asks for the level alone; an unrated one keeps what it has (its requirements
     * or nothing).
     */
    public static Optional<EquipRequirements> trueRequirements(ItemStack stack) {
        return trueRequirements(stack, EquipLevelBalance.current());
    }

    public static Optional<EquipRequirements> trueRequirements(ItemStack stack, EquipLevelBalance balance) {
        EquipRequirements stored = stack.get(ModDataComponents.REQUIREMENTS);
        if (stack.get(ModDataComponents.ITEM_RATING) == null) {
            return Optional.ofNullable(stored);
        }
        int level = effectiveLevel(stack, balance);
        if (stored != null && stored.level() == level) {
            return Optional.of(stored);
        }
        return Optional.of(new EquipRequirements(level, stored == null ? Map.of() : stored.stats()));
    }
}
