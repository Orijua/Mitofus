package com.orijua.mitofus.feature.fusion;

import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassSlot;
import com.orijua.mitofus.feature.talent.TalentNode;
import com.orijua.mitofus.feature.talent.TalentRules;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FusionRulesTest {
    private static final ResourceLocation SWORD = id("swordsman");
    private static final ResourceLocation MAGE = id("mage");
    private static final ResourceLocation HYBRID = id("spellsword");
    private static final ResourceLocation SWORD_ROOT = id("sword_root");
    private static final ResourceLocation SWORD_A = id("sword_a");
    private static final ResourceLocation MAGE_ROOT = id("mage_root");
    private static final ResourceLocation MAGE_A = id("mage_a");
    private static final ResourceLocation TRUNK_ROOT = id("trunk_root");
    private static final ResourceLocation TRUNK_A = id("trunk_a");

    private record Node(ResourceLocation playerClass, int cost, List<ResourceLocation> requires, ClassGrade minGrade) implements TalentNode {
    }

    private static final Map<ResourceLocation, TalentNode> TREE = Map.of(
            SWORD_ROOT, new Node(SWORD, 0, List.of(), ClassGrade.APPRENTICE),
            SWORD_A, new Node(SWORD, 1, List.of(SWORD_ROOT), ClassGrade.APPRENTICE),
            MAGE_ROOT, new Node(MAGE, 0, List.of(), ClassGrade.APPRENTICE),
            MAGE_A, new Node(MAGE, 2, List.of(MAGE_ROOT), ClassGrade.APPRENTICE),
            TRUNK_ROOT, new Node(HYBRID, 0, List.of(), ClassGrade.APPRENTICE),
            TRUNK_A, new Node(HYBRID, 1, List.of(TRUNK_ROOT), ClassGrade.APPRENTICE));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static ClassSlot slot(int index, ResourceLocation classId, int level, ClassGrade grade, ResourceLocation... bought) {
        return new ClassSlot(index, classId, level, 0, grade, List.of(bought));
    }

    private static FusionRules.Parent parent(ClassSlot slot, boolean legendary) {
        return new FusionRules.Parent(Optional.of(slot), legendary, FusionRules.treeComplete(TREE, slot));
    }

    @Test
    void aFusionWithEverythingInPlaceHasNothingMissing() {
        FusionRules.Parent sword = parent(slot(0, SWORD, 30, ClassGrade.ADEPT, SWORD_A), false);
        FusionRules.Parent mage = parent(slot(1, MAGE, 20, ClassGrade.ADEPT, MAGE_A), false);
        assertTrue(FusionRules.missing(sword, mage, false, true).isEmpty());
    }

    @Test
    void anIncompleteTreeCountsButAClassWithNoTreeIsComplete() {
        assertFalse(FusionRules.treeComplete(TREE, slot(0, SWORD, 30, ClassGrade.ADEPT)));
        assertTrue(FusionRules.treeComplete(TREE, slot(0, SWORD, 30, ClassGrade.ADEPT, SWORD_A)));
        assertTrue(FusionRules.treeComplete(TREE, slot(0, id("archer"), 1, ClassGrade.APPRENTICE)));
    }

    @Test
    void eachMissingThingIsReported() {
        FusionRules.Parent sword = parent(slot(0, SWORD, 30, ClassGrade.ADEPT), false);
        FusionRules.Parent mage = parent(slot(1, MAGE, 20, ClassGrade.EXPERT, MAGE_A), true);
        assertEquals(EnumSet.of(FusionRules.Missing.TREE, FusionRules.Missing.RANK, FusionRules.Missing.GRADE, FusionRules.Missing.COINS),
                FusionRules.missing(sword, mage, false, false));
        assertFalse(FusionRules.missing(sword, mage, true, true).contains(FusionRules.Missing.GRADE), "mixed grades can be allowed");
    }

    @Test
    void aClassThatIsNotPlacedOrAlreadyHybridBlocksTheFusion() {
        FusionRules.Parent absent = new FusionRules.Parent(Optional.empty(), false, false);
        FusionRules.Parent mage = parent(slot(1, MAGE, 20, ClassGrade.ADEPT, MAGE_A), false);
        assertEquals(EnumSet.of(FusionRules.Missing.CLASS), FusionRules.missing(absent, mage, false, true));
        ClassSlot hybrid = FusionRules.fuse(slot(0, SWORD, 5, ClassGrade.APPRENTICE), slot(1, MAGE, 5, ClassGrade.APPRENTICE), HYBRID, 0);
        assertEquals(EnumSet.of(FusionRules.Missing.CLASS), FusionRules.missing(parent(hybrid, false), mage, false, true));
    }

    @Test
    void theHybridKeepsTheBestOfBothAndRemembersItsParents() {
        ClassSlot sword = new ClassSlot(2, SWORD, 120, 40, ClassGrade.ADEPT, List.of(SWORD_A));
        ClassSlot mage = new ClassSlot(0, MAGE, 150, 10, ClassGrade.ADEPT, List.of(MAGE_A, SWORD_A));
        ClassSlot hybrid = FusionRules.fuse(sword, mage, HYBRID, 3);
        assertEquals(0, hybrid.slot(), "the lower slot keeps the hybrid");
        assertEquals(HYBRID, hybrid.classId());
        assertEquals(150, hybrid.classLevel(), "the higher level");
        assertEquals(10, hybrid.classXp(), "with the XP of that class");
        assertEquals(List.of(SWORD_A, MAGE_A), hybrid.talents(), "every node, once");
        assertEquals(List.of(SWORD, MAGE), hybrid.origin());
        assertEquals(3, hybrid.paidPoints());
        assertTrue(hybrid.isHybrid());
    }

    @Test
    void theHybridTakesTheHigherGradeWhenMixedGradesAreAllowed() {
        ClassSlot hybrid = FusionRules.fuse(slot(0, SWORD, 120, ClassGrade.ADEPT), slot(1, MAGE, 210, ClassGrade.EXPERT), HYBRID, 0);
        assertEquals(ClassGrade.EXPERT, hybrid.grade());
    }

    @Test
    void theMergedTreeOwnsBothParentsAndTheTrunkAndKeepsPointsPaid() {
        ClassSlot hybrid = FusionRules.fuse(slot(0, SWORD, 10, ClassGrade.ADEPT, SWORD_A), slot(1, MAGE, 10, ClassGrade.ADEPT, MAGE_A), HYBRID, 3);
        assertEquals(java.util.Set.of(SWORD_ROOT, SWORD_A, MAGE_ROOT, MAGE_A, TRUNK_ROOT), TalentRules.owned(TREE, hybrid));
        assertEquals(3, TalentRules.pointsSpent(TREE, hybrid));
        assertEquals(10, TalentRules.pointsAvailable(TREE, hybrid), "level 10 gives 10 points on top of what was already paid");
        assertEquals(TalentRules.Buy.OK, TalentRules.check(TREE, hybrid, TRUNK_A));
        assertEquals(TalentRules.Buy.OWNED, TalentRules.check(TREE, hybrid, SWORD_A));
        assertEquals(TalentRules.Buy.WRONG_CLASS, TalentRules.check(TREE, slot(0, SWORD, 10, ClassGrade.ADEPT), TRUNK_A));
    }
}
