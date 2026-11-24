package com.orijua.mitofus.feature.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Stateless helpers to look for, take and store items in plain {@link Container}s (barrels, chests). */
final class ContainerItems {
    private ContainerItems() {}

    /** What is short of {@code count} of {@code ingredient} in the containers (one of its items, with the count missing), or empty. */
    static ItemStack missing(List<Container> containers, Ingredient ingredient, int count) {
        int found = 0;
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (ingredient.test(stack)) {
                    found += stack.getCount();
                }
            }
        }
        if (found >= count) {
            return ItemStack.EMPTY;
        }
        ItemStack[] options = ingredient.getItems();
        return options.length == 0 ? new ItemStack(Items.BARRIER, count - found) : options[0].copyWithCount(count - found);
    }

    /** Takes {@code count} of {@code ingredient} from the containers; returns what was taken. */
    static List<ItemStack> take(List<Container> containers, Ingredient ingredient, int count) {
        List<ItemStack> taken = new ArrayList<>();
        int missing = count;
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize() && missing > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (ingredient.test(stack)) {
                    ItemStack removed = container.removeItem(slot, Math.min(missing, stack.getCount()));
                    missing -= removed.getCount();
                    taken.add(removed);
                }
            }
            container.setChanged();
        }
        return taken;
    }

    static boolean hasRoom(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static void put(Container container, ItemStack piece) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, piece);
                container.setChanged();
                return;
            }
        }
    }
}
