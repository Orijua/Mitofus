package com.orijua.mitofus.feature.equipment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.common.Tags;

/**
 * What a stack weighs. Only smith's objects weigh (decided 2026-11-17): swords, tools, armor, ingots, nuggets and ores.
 * Blocks, food, plants and the rest of vanilla weigh 0.
 */
public final class ItemWeights {
    private ItemWeights() {
    }

    /** Grams of ONE item of the stack's kind (0 when it does not weigh). */
    public static int gramsEach(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            return switch (slot) {
                case HEAD -> WeightRules.HELMET_GRAMS;
                case CHEST -> WeightRules.CHESTPLATE_GRAMS;
                case LEGS -> WeightRules.LEGGINGS_GRAMS;
                default -> WeightRules.BOOTS_GRAMS;
            };
        }
        if (stack.getItem() instanceof SwordItem) {
            return WeightRules.WEAPON_GRAMS;
        }
        if (stack.getItem() instanceof DiggerItem) {
            return WeightRules.TOOL_GRAMS;
        }
        if (stack.is(Tags.Items.INGOTS)) {
            return WeightRules.INGOT_GRAMS;
        }
        if (stack.is(Tags.Items.NUGGETS)) {
            return WeightRules.NUGGET_GRAMS;
        }
        if (stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS)) {
            return WeightRules.ORE_GRAMS;
        }
        return 0;
    }

    public static long grams(ItemStack stack) {
        return (long) gramsEach(stack) * stack.getCount();
    }

    /** Everything a player carries: inventory, armor and offhand (chests do not count). */
    public static long carried(Inventory inventory) {
        long total = 0L;
        for (ItemStack stack : inventory.items) {
            total += grams(stack);
        }
        for (ItemStack stack : inventory.armor) {
            total += grams(stack);
        }
        for (ItemStack stack : inventory.offhand) {
            total += grams(stack);
        }
        return total;
    }
}
