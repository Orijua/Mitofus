package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * The single rule for "what the player carries" when paying with it: inventory slots 0 to 35 and the offhand, not armor and not
 * the crafting grid. Smith's shop, the apprentice's wage and the gold shown on screen all use it. The terminal (work order) has
 * its own rule over the saved inventory and does not use this.
 */
public final class CarriedItems {
    private CarriedItems() {
    }

    /** How many items matching {@code filter} the inventory holds: slots 0 to 35 and the offhand. */
    public static long count(Inventory inventory, Predicate<ItemStack> filter) {
        long total = 0L;
        for (ItemStack stack : inventory.items) {
            if (filter.test(stack)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (filter.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Takes exactly {@code amount} matching items: slots 0 to 35 in order, then the offhand. Only called when there is enough. */
    public static void take(Inventory inventory, Predicate<ItemStack> filter, int amount) {
        int left = amount;
        for (ItemStack stack : inventory.items) {
            left -= takeFrom(stack, filter, left);
        }
        for (ItemStack stack : inventory.offhand) {
            left -= takeFrom(stack, filter, left);
        }
    }

    /** How many gold coins the player carries. */
    public static long gold(Inventory inventory) {
        return count(inventory, CarriedItems::isGold);
    }

    /** How many diamond coins the player carries. */
    public static long diamond(Inventory inventory) {
        return count(inventory, stack -> stack.is(ModItems.DIAMOND_COIN.get()));
    }

    /** How many iron coins the player carries. */
    public static long iron(Inventory inventory) {
        return count(inventory, stack -> stack.is(ModItems.IRON_COIN.get()));
    }

    /** How many copper coins the player carries. */
    public static long copper(Inventory inventory) {
        return count(inventory, stack -> stack.is(ModItems.COPPER_COIN.get()));
    }

    /** Everything the player carries in coins, as iron (the copper left over, less than an iron, is not counted). */
    public static long totalIron(Inventory inventory) {
        return diamond(inventory) * Money.IRON_PER_DIAMOND + gold(inventory) * Money.IRON_PER_GOLD + iron(inventory) + Money.ironFromCopper(copper(inventory));
    }

    private static boolean isGold(ItemStack stack) {
        return stack.is(ModItems.GOLD_COIN.get());
    }

    private static int takeFrom(ItemStack stack, Predicate<ItemStack> filter, int left) {
        if (left <= 0 || !filter.test(stack)) {
            return 0;
        }
        int taken = Math.min(left, stack.getCount());
        stack.shrink(taken);
        return taken;
    }
}
