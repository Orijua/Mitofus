package com.orijua.mitofus.feature.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The numbers of the account terminal, pure: counting gold coins in a saved inventory (a list of item tags, as
 * {@code Inventory.save} writes it) and adding to it when buying. Nothing here takes gold out: they
 * are only for spending in Mitofus.
 */
public final class TerminalRules {
    /** The id of the gold coin as it appears in a saved inventory. */
    public static final String GOLD_ID = "mitofus:gold_coin";
    /** The id the money had before the gold coin: old saves still hold it, and the registry maps it to the coin on loading. */
    public static final String LEGACY_GOLD_ID = "mitofus:mitofus_emerald";

    private TerminalRules() {
    }

    /** Whether an item id is the gold coin, under its current id or the one of saves before it. */
    public static boolean isGoldId(String id) {
        return GOLD_ID.equals(id) || LEGACY_GOLD_ID.equals(id);
    }

    private static int countOf(CompoundTag entry) {
        return entry.contains("count", Tag.TAG_ANY_NUMERIC) ? entry.getInt("count") : 1;
    }

    /** How many gold coins the saved inventory holds (an entry without count counts 1). */
    public static int countGold(ListTag inventory) {
        long total = 0L;
        for (int i = 0; i < inventory.size(); i++) {
            CompoundTag entry = inventory.getCompound(i);
            if (isGoldId(entry.getString("id"))) {
                total += countOf(entry);
            }
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    // ---- Buying: writing into a saved inventory ----

    /** The main inventory slots (0 to 35); armor (100-103) and offhand (150) are never written by a purchase. */
    public static final int MAIN_SLOTS = 36;

    private static int slotOf(CompoundTag entry) {
        return entry.getByte("Slot") & 255;
    }

    private static boolean[] occupied(ListTag inventory) {
        boolean[] used = new boolean[MAIN_SLOTS];
        for (int i = 0; i < inventory.size(); i++) {
            int slot = slotOf(inventory.getCompound(i));
            if (slot < MAIN_SLOTS) {
                used[slot] = true;
            }
        }
        return used;
    }

    /** The first free slot among 0 to 35, if any. */
    public static java.util.OptionalInt firstFreeSlot(ListTag inventory) {
        boolean[] used = occupied(inventory);
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            if (!used[slot]) {
                return java.util.OptionalInt.of(slot);
            }
        }
        return java.util.OptionalInt.empty();
    }

    /** How many of the slots 0 to 35 are free. */
    public static int freeSlots(ListTag inventory) {
        boolean[] used = occupied(inventory);
        int free = 0;
        for (boolean slot : used) {
            if (!slot) {
                free++;
            }
        }
        return free;
    }

    /**
     * A copy of the inventory with {@code item} (an item tag as {@code ItemStack.save} writes it) put at {@code slot}.
     *
     * @throws IllegalArgumentException if the slot is outside 0 to 35 or already taken
     */
    public static ListTag withEntryAt(ListTag inventory, int slot, CompoundTag item) {
        if (slot < 0 || slot >= MAIN_SLOTS || occupied(inventory)[slot]) {
            throw new IllegalArgumentException("slot " + slot + " is not a free main slot");
        }
        ListTag result = new ListTag();
        for (int i = 0; i < inventory.size(); i++) {
            result.add(inventory.getCompound(i).copy());
        }
        CompoundTag entry = item.copy();
        entry.putByte("Slot", (byte) slot);
        result.add(entry);
        return result;
    }
}
