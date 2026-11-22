package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.equipment.option.FixedOption;
import com.orijua.mitofus.feature.equipment.option.ItemOptionDefinition;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.smithing.ForgingExtras;
import com.orijua.mitofus.feature.smithing.ForgingRules;
import com.orijua.mitofus.feature.smithing.SmithingHammerItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.orijua.mitofus.core.config.RarityBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Server-side changes to equipment: what forging and enhancing write onto an item. */
public final class EquipmentService {
    public enum EnhanceResult { SUCCESS, FAILURE, NOT_EQUIPMENT, WRONG_MATERIAL, MAXED, NO_CHANCE }

    private EquipmentService() {
    }

    /** Rolls rating and options for a freshly forged item and stamps its requirements (automatic forging). */
    public static void stampForged(ServerPlayer smith, ItemStack stack, Optional<EquipRequirements> requirements) {
        stampForged(smith, stack, requirements, 0, 0);
    }

    /**
     * As above, for a manual forge: {@code skillBonus} shifts the rating roll (negative when botched) and
     * {@code extraOptionChance} may add one option beyond what the rating allows.
     */
    public static void stampForged(ServerPlayer smith, ItemStack stack, Optional<EquipRequirements> requirements,
                                   double skillBonus, double extraOptionChance) {
        stampForged(smith, stack, requirements, ForgingExtras.NONE, skillBonus, extraOptionChance);
    }

    /**
     * As above, for a recipe with {@link ForgingExtras}: the rating stays within its range and, when it has
     * fixed options, the item gets exactly those (the ones its rating reaches) instead of random ones.
     */
    public static void stampForged(ServerPlayer smith, ItemStack stack, Optional<EquipRequirements> requirements,
                                   ForgingExtras extras, double skillBonus, double extraOptionChance) {
        stampForged(smith, stack, requirements, extras, skillBonus, extraOptionChance, 0);
    }

    /**
     * As above, with a chance of a great success (the rating goes one step up, within the recipe's range). Returns
     * whether it happened.
     */
    public static boolean stampForged(ServerPlayer smith, ItemStack stack, Optional<EquipRequirements> requirements,
                                      ForgingExtras extras, double skillBonus, double extraOptionChance, double greatSuccessChance) {
        PlayerStats stats = ProgressionService.effectiveStats(smith);
        return stampForged(smith.level(), stack, requirements, extras, stats.dexterity(), ProgressionService.effectiveSmithingLevel(smith),
                skillBonus, extraOptionChance, greatSuccessChance, smith.getRandom(), smith);
    }

    /** As above, for a smith who isn't a player (an apprentice): their dexterity and smithing level are given. */
    public static void stampForged(Level level, ItemStack stack, Optional<EquipRequirements> requirements, ForgingExtras extras,
                                   int dexterity, int smithingLevel, double skillBonus, double extraOptionChance, RandomSource random) {
        stampForged(level, stack, requirements, extras, dexterity, smithingLevel, skillBonus, extraOptionChance, 0, random, null);
    }

    private static boolean stampForged(Level level, ItemStack stack, Optional<EquipRequirements> requirements, ForgingExtras extras,
                                       int dexterity, int smithingLevel, double skillBonus, double extraOptionChance,
                                       double greatSuccessChance, RandomSource random, ServerPlayer pityOwner) {
        // A player who forges has the anti-bad-luck bonus (RarityPity): virtual skill after a long run without an Epic.
        RarityBalance rarity = RarityBalance.current();
        double pity = pityOwner == null ? 0.0 : RarityPity.bonus(rarity, pityOwner.getData(ModAttachments.RARITY_PITY.get()).misses(RarityPity.Way.FORGE));
        ItemRating rolled = RatingRoller.roll(dexterity, smithingLevel, skillBonus, pity, extras.minRating(), extras.maxRating(), random.nextDouble());
        // Only rolled when there is a chance, so smiths without one keep the same random sequence.
        boolean great = greatSuccessChance > 0 && rolled != extras.maxRating() && random.nextDouble() < greatSuccessChance;
        ItemRating rating = great ? ForgingRules.raise(rolled, extras.maxRating()) : rolled;
        if (pityOwner != null) {
            pityOwner.setData(ModAttachments.RARITY_PITY.get(),
                    pityOwner.getData(ModAttachments.RARITY_PITY.get()).after(RarityPity.Way.FORGE, rating, rarity));
        }
        requirements.ifPresent(r -> stack.set(ModDataComponents.REQUIREMENTS, r));
        if (!extras.fixedOptions().isEmpty()) {
            stack.set(ModDataComponents.ITEM_RATING, rating);
            stack.set(ModDataComponents.ITEM_OPTIONS, FixedOption.forRating(extras.fixedOptions(), rating));
            refreshName(stack);
            return great;
        }
        int extraOptions = random.nextDouble() < extraOptionChance ? 1 : 0;
        applyRating(level, stack, rating, extraOptions, random);
        return great;
    }

    /**
     * Gives equipment found as loot (chests, elites, bosses) a rating and options, as if it had been forged.
     * Leaves anything that isn't equipment, or already has a rating, untouched.
     */
    public static void rateLoot(Level level, ItemStack stack, int luck, ItemRating minimum, RandomSource random) {
        rateLoot(level, stack, luck, minimum, random, null);
    }

    /** As above, for a player who found it (the chest they opened, the creature they killed): they have the anti-bad-luck bonus (RarityPity). */
    public static void rateLoot(Level level, ItemStack stack, int luck, ItemRating minimum, RandomSource random, ServerPlayer finder) {
        if (!isEquipment(stack) || stack.has(ModDataComponents.ITEM_RATING)) {
            return;
        }
        RarityBalance rarity = RarityBalance.current();
        double pity = finder == null ? 0.0 : RarityPity.bonus(rarity, finder.getData(ModAttachments.RARITY_PITY.get()).misses(RarityPity.Way.LOOT));
        ItemRating rating = RatingRoller.rollLoot(luck, Math.round(pity), minimum, random.nextDouble());
        if (finder != null) {
            finder.setData(ModAttachments.RARITY_PITY.get(),
                    finder.getData(ModAttachments.RARITY_PITY.get()).after(RarityPity.Way.LOOT, rating, rarity));
        }
        applyRating(level, stack, rating, 0, random);
        // Loot comes unappraised: its data stay hidden until the Appraiser Eye reveals them.
        AppraisalService.markUnappraised(stack, random);
    }

    /**
     * Rates a piece as if forged at exactly {@code rating} (no extra options): rating, rolled options and name. With the same
     * seed in {@code random} it gives the same options for as long as the datapack does not change (the terminal's daily
     * offer relies on that).
     */
    public static void rateAs(Level level, ItemStack stack, ItemRating rating, RandomSource random) {
        applyRating(Definitions.all(level, ModDatapackRegistries.ITEM_OPTION), stack, rating, 0, random);
    }

    /** As above, with the item_option pool already read ({@link #itemOptions}): lets a caller rating many pieces read it once. */
    public static void rateAs(ItemStack stack, ItemRating rating, RandomSource random,
                              List<Definitions.Entry<ItemOptionDefinition>> all) {
        applyRating(all, stack, rating, 0, random);
    }

    /** The item_option pool, in the order {@link #rateAs} rolls from. */
    public static List<Definitions.Entry<ItemOptionDefinition>> itemOptions(Level level) {
        return Definitions.all(level, ModDatapackRegistries.ITEM_OPTION);
    }

    private static void applyRating(Level level, ItemStack stack, ItemRating rating, int extraOptions, RandomSource random) {
        applyRating(Definitions.all(level, ModDatapackRegistries.ITEM_OPTION), stack, rating, extraOptions, random);
    }

    /**
     * Stamps {@code rating}, rolls the options it allows (plus {@code extraOptions}) from the item_option pool,
     * and renames the item.
     */
    private static void applyRating(List<Definitions.Entry<ItemOptionDefinition>> all, ItemStack stack, ItemRating rating,
                                    int extraOptions, RandomSource random) {
        stack.set(ModDataComponents.ITEM_RATING, rating);
        List<Definitions.Entry<ItemOptionDefinition>> pool = all.stream()
                .filter(e -> e.value().weight() > 0 && e.value().canRollOn(stack))
                .toList();
        int count = EquipmentRules.optionCount(rating) + extraOptions;
        List<RolledOption> options = EquipmentRules.pickWeighted(pool, e -> e.value().weight(), count, random::nextDouble)
                .stream()
                .map(e -> new RolledOption(e.id(), EquipmentRules.rollValue(e.value().min(), e.value().max(), rating, random.nextDouble())))
                .toList();
        if (!options.isEmpty()) {
            stack.set(ModDataComponents.ITEM_OPTIONS, options);
        }
        refreshName(stack);
    }

    /**
     * Writes the display name as "<name> +N" in the rating color. It goes into vanilla's item_name
     * component, so it shows everywhere (tooltip, hotbar, inventory); a name set on a vanilla anvil still wins.
     */
    public static void refreshName(ItemStack stack) {
        ItemRating rating = stack.get(ModDataComponents.ITEM_RATING);
        int enhancement = stack.getOrDefault(ModDataComponents.ENHANCEMENT, 0);
        if (rating == null && enhancement == 0) {
            // An unrated piece that falls back to +0 loses the "+N" it was named with.
            if (stack.has(DataComponents.ITEM_NAME)) {
                stack.set(DataComponents.ITEM_NAME, Component.translatable(stack.getItem().getDescriptionId()));
            }
            return;
        }
        MutableComponent name = Component.translatable(stack.getItem().getDescriptionId());
        if (enhancement > 0) {
            name.append(" +" + enhancement);
        }
        // A hidden rating shows as Normal: the name must not give the rating away.
        int color = AppraisalService.visibleRating(stack).map(ItemRating::color).orElse(ItemRating.NORMAL.color());
        stack.set(DataComponents.ITEM_NAME, rating != null ? name.withColor(color) : name);
    }

    /**
     * The name of the piece without the "+N", for messages: a custom name (vanilla anvil) wins, otherwise the item name in the
     * visible rating color (a hidden rating shows as Normal, so a message does not give it away).
     */
    public static Component baseName(ItemStack stack) {
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            return custom;
        }
        MutableComponent name = Component.translatable(stack.getItem().getDescriptionId());
        if (stack.has(ModDataComponents.ITEM_RATING)) {
            name.withColor(AppraisalService.visibleRating(stack).map(ItemRating::color).orElse(ItemRating.NORMAL.color()));
        }
        return name;
    }

    /** Anything that can come rated: a single damageable item (weapons and armor). */
    public static boolean isEquipment(ItemStack stack) {
        return stack.getCount() == 1 && stack.isDamageableItem();
    }

    /**
     * What kind of piece this is for enhancing: weapons (attack damage), smithing hammers (enhanced as weapons, no attack)
     * and armor worn by players; empty for anything else.
     */
    public static Optional<EnhanceRules.PieceType> pieceType(ItemStack stack) {
        if (!isEquipment(stack)) {
            return Optional.empty();
        }
        if (stack.getItem() instanceof SmithingHammerItem || EquipmentBonuses.isWeapon(stack)) {
            return Optional.of(EnhanceRules.PieceType.WEAPON);
        }
        return EquipmentBonuses.isArmor(stack) ? Optional.of(EnhanceRules.PieceType.ARMOR) : Optional.empty();
    }

    /** Weapons, smithing hammers and armor can be enhanced (not animal armor): the bonus per level is attack damage, forging bonus or armor. */
    public static boolean canEnhance(ItemStack stack) {
        return pieceType(stack).isPresent();
    }

    /** Whether the piece can be enhanced and this material works on its kind. */
    public static boolean canEnhance(ItemStack stack, EnhanceMaterial material) {
        return pieceType(stack).filter(type -> EnhanceRules.accepts(material.target(), type)).isPresent();
    }

    /** The outcome of one enhancement attempt: the result and the level before and after. */
    public record EnhanceOutcome(EnhanceResult result, int before, int after) {
    }

    public static EnhanceResult enhance(ServerPlayer smith, ItemStack equipment) {
        return enhance(smith, equipment, smith.getRandom().nextDouble());
    }

    /**
     * For tests, like {@code ApprenticeSmith.forgeOne(double)}: the roll is given, and it succeeds when {@code roll < chance}.
     * Uses the normal stone of the piece's kind.
     */
    public static EnhanceResult enhance(ServerPlayer smith, ItemStack equipment, double roll) {
        Optional<EnhanceRules.PieceType> type = pieceType(equipment);
        if (type.isEmpty()) {
            return EnhanceResult.NOT_EQUIPMENT;
        }
        return enhanceWith(smith, equipment, EnhanceMaterial.normalStoneFor(type.get()), roll, 0.0).result();
    }

    public static EnhanceOutcome enhanceWith(ServerPlayer smith, ItemStack equipment, EnhanceMaterial material) {
        return enhanceWith(smith, equipment, material, smith.getRandom().nextDouble(), smith.getRandom().nextDouble());
    }

    /**
     * Enhances {@code equipment} with {@code material}. For tests the rolls are given: it succeeds when {@code roll < chance}, and
     * {@code gainRoll} picks how many levels an ancient scroll adds. The server uses the real rating (also if unappraised) for the chance.
     */
    public static EnhanceOutcome enhanceWith(ServerPlayer smith, ItemStack equipment, EnhanceMaterial material, double roll, double gainRoll) {
        int level = equipment.getOrDefault(ModDataComponents.ENHANCEMENT, 0);
        if (pieceType(equipment).isEmpty()) {
            return new EnhanceOutcome(EnhanceResult.NOT_EQUIPMENT, level, level);
        }
        if (!canEnhance(equipment, material)) {
            return new EnhanceOutcome(EnhanceResult.WRONG_MATERIAL, level, level);
        }
        if (level >= EquipmentRules.MAX_ENHANCEMENT) {
            return new EnhanceOutcome(EnhanceResult.MAXED, level, level);
        }
        EnhanceBalance enhance = EnhanceBalance.current();
        ItemRating rating = equipment.get(ModDataComponents.ITEM_RATING);
        double chance = EnhanceRules.chance(Balance.current(), enhance, material.kind(), level,
                ProgressionService.effectiveStats(smith).dexterity(), rating);
        if (!EnhanceRules.hasChance(chance)) {
            smith.displayClientMessage(Component.translatable("message.mitofus.enhance_no_chance"), true);
            return new EnhanceOutcome(EnhanceResult.NO_CHANCE, level, level);
        }
        boolean success = roll < chance;
        boolean ancient = material.kind() == EnhanceMaterial.Kind.ANCIENT_SCROLL;
        int after = success
                ? EnhanceRules.levelAfterSuccess(level, ancient ? EnhanceRules.ancientGain(enhance, gainRoll) : 1)
                : EnhanceRules.levelAfterFailure(level, EnhanceRules.levelsLost(enhance, material.kind()));
        if (after > 0) {
            equipment.set(ModDataComponents.ENHANCEMENT, after);
        } else {
            equipment.remove(ModDataComponents.ENHANCEMENT);
        }
        refreshName(equipment);
        Component message;
        Component name = baseName(equipment);
        if (success) {
            message = ancient && after - level > 1
                    ? Component.translatable("message.mitofus.enhance_success_ancient", name, level, after)
                    : Component.translatable("message.mitofus.enhance_success", name, after);
        } else if (level - after > 0) {
            message = Component.translatable(EnhanceRules.levelsKey("message.mitofus.enhance_failure_lost", level - after),
                    name, level - after, after);
        } else {
            message = Component.translatable("message.mitofus.enhance_failure", name, after);
        }
        smith.displayClientMessage(message, true);
        smith.level().playSound(null, smith.blockPosition(), success ? SoundEvents.SMITHING_TABLE_USE : SoundEvents.ANVIL_DESTROY,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        return new EnhanceOutcome(success ? EnhanceResult.SUCCESS : EnhanceResult.FAILURE, level, after);
    }
}
