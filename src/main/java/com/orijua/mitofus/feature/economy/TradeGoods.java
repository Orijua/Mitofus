package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.config.EquipLevelBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.EquipLevelRules;
import com.orijua.mitofus.feature.equipment.EquipLevels;
import com.orijua.mitofus.feature.equipment.EquipMaterial;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The bridge between a stack and {@link TradeRules}: what a piece is worth and why it may not be traded. It touches no
 * client class, so the server and the Trade tab read the same numbers. {@code today} is a game day ({@link TradeRules#day}).
 */
public final class TradeGoods {
    private TradeGoods() {
    }

    /** Why this stack cannot be traded, if it cannot. Only a loaned-for-a-quest mark counts as a quest item. */
    public static Optional<TradeRules.Refusal> refusal(ItemStack stack) {
        return TradeRules.refusal(stack.getCount(), stack.isDamageableItem(),
                stack.get(ModDataComponents.ITEM_RATING) != null, stack.get(ModDataComponents.LOANED_FOR) != null);
    }

    /** The saturation key of a rated stack: call it only when {@link #refusal} is empty. */
    public static TradeKey key(ItemStack stack) {
        ItemRating real = Objects.requireNonNull(stack.get(ModDataComponents.ITEM_RATING), "the stack has no rating");
        // An unappraised piece is sold as a Normal one, so its key (its saturation) is Normal's.
        ItemRating rating = AppraisalService.isUnappraised(stack) ? ItemRating.NORMAL : real;
        return new TradeKey(TradeCategory.classify(stack::is), rating);
    }

    /**
     * The material multiplier: the one the config gives to this very item (it replaces the material's), else the vanilla tier
     * of a tool or the material of an armor, else the config's other material.
     */
    public static double material(ItemStack stack, TradeBalance balance) {
        Double own = balance.itemMultiplier().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (own != null) {
            return own;
        }
        return EquipMaterial.key(stack).map(name -> balance.materialMultiplier().getOrDefault(name, balance.otherMaterial()))
                .orElse(balance.otherMaterial());
    }

    /** The price factor of the durability the stack has left (1 for a piece without durability). */
    public static double durability(ItemStack stack, TradeBalance balance) {
        return TradeRules.durabilityFactor(stack.getDamageValue(), stack.getMaxDamage(), balance.minDurabilityFactor());
    }

    /** What a buyer sees and a sale pays: the reference of a piece as it is known (see {@link #reference(ItemStack, TradeBalance, EquipLevelBalance)}). */
    public static long reference(ItemStack stack, TradeBalance balance) {
        return reference(stack, balance, EquipLevelBalance.current());
    }

    /**
     * The reference price as the buyer sees it: an unappraised piece is worth a Normal one of its material and level, with no
     * options and no impossible-recipe cut (enhancement and durability still count); any other, its true reference.
     */
    public static long reference(ItemStack stack, TradeBalance balance, EquipLevelBalance levels) {
        if (!AppraisalService.isUnappraised(stack)) {
            return trueReference(stack, balance, levels);
        }
        return TradeRules.reference(ItemRating.NORMAL, material(stack, balance), EquipLevels.normalLevel(stack.getItem(), levels), 0,
                stack.getOrDefault(ModDataComponents.ENHANCEMENT, 0), false, durability(stack, balance), balance);
    }

    /** What the piece is really worth, whether it is appraised or not (the price of the terminal's offer and the value datum). */
    public static long trueReference(ItemStack stack, TradeBalance balance) {
        return trueReference(stack, balance, EquipLevelBalance.current());
    }

    /**
     * The reference price: the rating, the material, the effective required level (stored or derived, the larger), the
     * options, the enhancement and the durability; a recipe level far above the derived one cuts it (see {@link TradeRules#impossible}).
     */
    public static long trueReference(ItemStack stack, TradeBalance balance, EquipLevelBalance levels) {
        ItemRating rating = Objects.requireNonNull(stack.get(ModDataComponents.ITEM_RATING), "the stack has no rating");
        EquipLevelRules.Levels level = EquipLevels.levels(stack, levels);
        return TradeRules.reference(rating, material(stack, balance), level.effective(),
                stack.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of()).size(),
                stack.getOrDefault(ModDataComponents.ENHANCEMENT, 0), TradeRules.impossible(level.stored(), level.derived(), balance),
                durability(stack, balance), balance);
    }

    /** The true reference before rounding, in gold (the price of the terminal offer is made from it). */
    public static double trueReferenceValue(ItemStack stack, TradeBalance balance) {
        return trueReferenceValue(stack, balance, EquipLevelBalance.current());
    }

    /** See {@link #trueReferenceValue(ItemStack, TradeBalance)}; with the level balance given. */
    public static double trueReferenceValue(ItemStack stack, TradeBalance balance, EquipLevelBalance levels) {
        ItemRating rating = Objects.requireNonNull(stack.get(ModDataComponents.ITEM_RATING), "the stack has no rating");
        EquipLevelRules.Levels level = EquipLevels.levels(stack, levels);
        return TradeRules.referenceValue(rating, material(stack, balance), level.effective(),
                stack.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of()).size(),
                stack.getOrDefault(ModDataComponents.ENHANCEMENT, 0), TradeRules.impossible(level.stored(), level.derived(), balance),
                durability(stack, balance), balance);
    }

    /** What the stack pays today in iron, with the pressure the saturation has on its key. */
    public static long price(ItemStack stack, TradeSaturation saturation, long today, TradeBalance balance) {
        int pressure = saturation.pressure(key(stack), today, balance);
        return TradeRules.sellPriceIron(reference(stack, balance), pressure, balance);
    }

    /**
     * The price of each stack of a batch sold in this order, each with the pressure the previous ones leave. The only source
     * of the price and the total, for the client and for the server. Every stack must be tradable ({@link #refusal} empty).
     */
    public static long[] salePrices(List<ItemStack> stacks, TradeSaturation saturation, long today, TradeBalance balance) {
        List<TradeRules.Piece> pieces = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            pieces.add(new TradeRules.Piece(key(stack), reference(stack, balance)));
        }
        return TradeRules.sellPricesIron(pieces, saturation, today, balance);
    }

    /** The slots of the main inventory (hotbar included) with a piece that can be sold, ascending. */
    public static List<Integer> sellableSlots(Inventory inventory) {
        return TradeRules.sellableSlots(Inventory.INVENTORY_SIZE, slot -> inventory.getItem(slot).isEmpty(),
                slot -> refusal(inventory.getItem(slot)));
    }
}
