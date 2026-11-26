package com.orijua.mitofus.feature.progression;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchSwapRulesTest {
    private static final ResourceLocation SWORD = ResourceLocation.parse("mitofus:swordsman");
    private static final ResourceLocation MAGE = ResourceLocation.parse("mitofus:mage");
    private static final ResourceLocation ARCHER = ResourceLocation.parse("mitofus:archer");
    private static final ResourceLocation SPEAR = ResourceLocation.parse("mitofus:spearman");

    private static PlayerClasses two() {
        return PlayerClasses.DEFAULT.withSlot(new ClassSlot(0, SWORD, 30, 5, ClassGrade.ADEPT, List.of(ResourceLocation.parse("mitofus:sharp_edge"))))
                .withSlot(ClassSlot.fresh(1, MAGE));
    }

    @Test
    void aBranchCanReplaceAnotherInAnOccupiedOpenSlot() {
        assertEquals(ClassSlotRules.Swap.OK, ClassSlotRules.checkSwap(two(), 0, ARCHER, 20));
    }

    @Test
    void aSwapIsRefusedForEmptyClosedRepeatedAndOutOfRangeSlots() {
        assertEquals(ClassSlotRules.Swap.EMPTY, ClassSlotRules.checkSwap(two(), 2, ARCHER, 50), "nothing to replace: choose it instead");
        assertEquals(ClassSlotRules.Swap.LOCKED, ClassSlotRules.checkSwap(two(), 1, ARCHER, 19));
        assertEquals(ClassSlotRules.Swap.SAME, ClassSlotRules.checkSwap(two(), 0, SWORD, 20));
        assertEquals(ClassSlotRules.Swap.DUPLICATE, ClassSlotRules.checkSwap(two(), 0, MAGE, 20), "no branch in two slots");
        assertEquals(ClassSlotRules.Swap.BAD_SLOT, ClassSlotRules.checkSwap(two(), 3, ARCHER, 60));
    }

    @Test
    void theOldBranchGoesDormantWithEverythingItHad() {
        PlayerClasses after = ClassSlotRules.swap(two(), 0, ARCHER);
        assertEquals(ARCHER, after.slot(0).orElseThrow().classId());
        assertEquals(1, after.slot(0).orElseThrow().classLevel(), "a new branch starts at level 1");
        ClassSlot sleeping = after.dormant(SWORD).orElseThrow();
        assertEquals(30, sleeping.classLevel());
        assertEquals(ClassGrade.ADEPT, sleeping.grade());
        assertEquals(1, sleeping.talents().size());
        assertEquals(MAGE, after.slot(1).orElseThrow().classId(), "other slots are untouched");
    }

    @Test
    void aDormantBranchComesBackExactlyAsItWas() {
        PlayerClasses away = ClassSlotRules.swap(two(), 0, ARCHER);
        PlayerClasses back = ClassSlotRules.swap(away, 0, SWORD);
        ClassSlot restored = back.slot(0).orElseThrow();
        assertEquals(new ClassSlot(0, SWORD, 30, 5, ClassGrade.ADEPT, List.of(ResourceLocation.parse("mitofus:sharp_edge"))), restored);
        assertTrue(back.dormant(SWORD).isEmpty(), "it is no longer dormant");
        assertEquals(1, back.dormant(ARCHER).orElseThrow().classLevel());
    }

    @Test
    void aDormantBranchCanGoToAnotherSlot() {
        PlayerClasses away = ClassSlotRules.swap(two(), 0, ARCHER);
        PlayerClasses moved = ClassSlotRules.swap(away, 1, SWORD);
        assertEquals(1, moved.slot(1).orElseThrow().slot());
        assertEquals(30, moved.slot(1).orElseThrow().classLevel());
    }

    @Test
    void theFirstChangeOfEachSlotIsFree() {
        PlayerClasses classes = two();
        assertEquals(0, ClassSlotRules.swapCost(classes, 0, 1));
        PlayerClasses after = ClassSlotRules.swap(classes, 0, ARCHER);
        assertEquals(1, ClassSlotRules.swapCost(after, 0, 1), "the second change of slot 1 costs");
        assertEquals(0, ClassSlotRules.swapCost(after, 1, 1), "slot 2 still has its free change");
    }

    @Test
    void theSwapStateRoundTripsThroughTheCodec() {
        PlayerClasses classes = ClassSlotRules.swap(two(), 0, ARCHER);
        var json = PlayerClasses.CODEC.encodeStart(JsonOps.INSTANCE, classes).getOrThrow();
        assertEquals(classes, PlayerClasses.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertFalse(classes.hasClass(SWORD), "a dormant branch is not a held branch");
        assertEquals(List.of(ARCHER, MAGE), classes.classIds());
    }

    @Test
    void aDormantBranchIsReplacedWhenTheSameOneGoesDormantAgain() {
        PlayerClasses a = ClassSlotRules.swap(two(), 0, ARCHER);
        PlayerClasses b = ClassSlotRules.swap(a, 0, SPEAR);
        assertEquals(1, b.dormantBranches().stream().filter(s -> s.classId().equals(ARCHER)).count());
        assertEquals(2, b.dormantBranches().size());
    }
}
