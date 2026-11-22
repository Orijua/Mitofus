package com.orijua.mitofus.feature.economy;

import com.orijua.mitofus.core.config.TerminalBalance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalRulesTest {
    private static CompoundTag entry(String id, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", count);
        return tag;
    }

    private static ListTag list(CompoundTag... entries) {
        ListTag list = new ListTag();
        for (CompoundTag entry : entries) {
            list.add(entry);
        }
        return list;
    }

    // ---- Counting ----

    @Test
    void countsOnlyTheGoldCoinById() {
        ListTag inventory = list(entry(TerminalRules.GOLD_ID, 10), entry("minecraft:emerald", 7), entry(TerminalRules.GOLD_ID, 5));
        assertEquals(15, TerminalRules.countGold(inventory));
    }

    @Test
    void countsTheLegacyIdOfSavesBeforeTheGoldCoin() {
        ListTag inventory = list(entry(TerminalRules.LEGACY_GOLD_ID, 10), entry(TerminalRules.GOLD_ID, 5), entry("minecraft:emerald", 7));
        assertEquals(15, TerminalRules.countGold(inventory));
        assertTrue(TerminalRules.isGoldId(TerminalRules.GOLD_ID));
        assertTrue(TerminalRules.isGoldId(TerminalRules.LEGACY_GOLD_ID));
        assertFalse(TerminalRules.isGoldId("minecraft:emerald"));
        assertFalse(TerminalRules.isGoldId(""));
    }

    @Test
    void anEntryWithComponentsStillCounts() {
        CompoundTag withComponents = entry(TerminalRules.GOLD_ID, 3);
        CompoundTag components = new CompoundTag();
        components.putString("minecraft:custom_name", "x");
        withComponents.put("components", components);
        assertEquals(3, TerminalRules.countGold(list(withComponents)));
    }

    @Test
    void goldInsideAContainerDoNotCount() {
        CompoundTag shulker = entry("minecraft:shulker_box", 1);
        CompoundTag components = new CompoundTag();
        components.put("minecraft:container", list(entry(TerminalRules.GOLD_ID, 9)));
        shulker.put("components", components);
        assertEquals(0, TerminalRules.countGold(list(shulker)));
        assertEquals(2, TerminalRules.countGold(list(shulker, entry(TerminalRules.GOLD_ID, 2))));
    }

    @Test
    void anEmptyListHasNoGoldAndAnEntryWithoutCountCountsOne() {
        assertEquals(0, TerminalRules.countGold(new ListTag()));
        CompoundTag noCount = new CompoundTag();
        noCount.putString("id", TerminalRules.GOLD_ID);
        assertEquals(1, TerminalRules.countGold(list(noCount)));
    }

    // ---- Work ----

    // ---- Buying: writing into a saved inventory ----

    private static CompoundTag slotted(int slot, String id, int count) {
        CompoundTag tag = entry(id, count);
        tag.putByte("Slot", (byte) slot);
        return tag;
    }

    /** Stone in the slots 0 to 35 except the ones given. */
    private static ListTag fullExcept(int... free) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < 36; slot++) {
            boolean skip = false;
            for (int f : free) {
                skip |= f == slot;
            }
            if (!skip) {
                list.add(slotted(slot, "minecraft:stone", 64));
            }
        }
        return list;
    }

    @Test
    void anEmptyInventoryHasEverySlotFree() {
        ListTag empty = new ListTag();
        assertEquals(java.util.OptionalInt.of(0), TerminalRules.firstFreeSlot(empty));
        assertEquals(36, TerminalRules.freeSlots(empty));
    }

    @Test
    void aFullInventoryHasNoFreeSlot() {
        ListTag inv = fullExcept(0);
        inv.add(slotted(0, TerminalRules.GOLD_ID, 60));
        assertEquals(java.util.OptionalInt.empty(), TerminalRules.firstFreeSlot(inv));
        assertEquals(60, TerminalRules.countGold(inv));
    }

    @Test
    void armorAndOffhandSlotsAreNotRoom() {
        ListTag inv = fullExcept();
        inv.add(slotted(100, "minecraft:iron_helmet", 1));
        inv.add(slotted(103, "minecraft:iron_boots", 1));
        inv.add(slotted(150, "minecraft:shield", 1));
        assertEquals(0, TerminalRules.freeSlots(inv));
        assertEquals(java.util.OptionalInt.empty(), TerminalRules.firstFreeSlot(inv));
        assertThrows(IllegalArgumentException.class, () -> TerminalRules.withEntryAt(inv, 100, entry("minecraft:stone", 1)));
    }

    @Test
    void withEntryAtNeedsAFreeMainSlot() {
        ListTag inv = list(slotted(3, "minecraft:stone", 1));
        assertThrows(IllegalArgumentException.class, () -> TerminalRules.withEntryAt(inv, 3, entry("minecraft:dirt", 1)));
        assertThrows(IllegalArgumentException.class, () -> TerminalRules.withEntryAt(inv, 36, entry("minecraft:dirt", 1)));
        assertThrows(IllegalArgumentException.class, () -> TerminalRules.withEntryAt(inv, -1, entry("minecraft:dirt", 1)));
        ListTag result = TerminalRules.withEntryAt(inv, 4, entry("minecraft:dirt", 2));
        assertEquals(1, inv.size(), "the argument does not change");
        assertEquals(2, result.size());
        assertEquals(4, result.getCompound(1).getByte("Slot"));
        assertEquals("minecraft:dirt", result.getCompound(1).getString("id"));
    }

}
