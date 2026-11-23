package com.orijua.mitofus.feature.talent;

import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TalentRulesTest {
    private static final ResourceLocation SWORD = id("swordsman");
    private static final ResourceLocation MAGE = id("mage");
    private static final ResourceLocation ROOT = id("root");
    private static final ResourceLocation A = id("a");
    private static final ResourceLocation B = id("b");
    private static final ResourceLocation TOP = id("top");
    private static final ResourceLocation SPELL = id("spell");

    private static final Map<ResourceLocation, TalentNode> TREE = Map.of(
            ROOT, node(SWORD, 0, List.of(), ClassGrade.APPRENTICE),
            A, node(SWORD, 1, List.of(ROOT), ClassGrade.APPRENTICE),
            B, node(SWORD, 2, List.of(A), ClassGrade.APPRENTICE),
            TOP, node(SWORD, 3, List.of(A, B), ClassGrade.ADEPT),
            SPELL, node(MAGE, 0, List.of(), ClassGrade.APPRENTICE));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private record Node(ResourceLocation playerClass, int cost, List<ResourceLocation> requires, ClassGrade minGrade) implements TalentNode {
    }

    private static TalentNode node(ResourceLocation owner, int cost, List<ResourceLocation> requires, ClassGrade grade) {
        return new Node(owner, cost, requires, grade);
    }

    private static ClassSlot slot(int level, ClassGrade grade, ResourceLocation... bought) {
        return new ClassSlot(0, SWORD, level, 0, grade, List.of(bought));
    }

    @Test
    void oneClassLevelGivesOnePoint() {
        assertEquals(1, TalentRules.pointsEarned(1));
        assertEquals(250, TalentRules.pointsEarned(250));
        assertEquals(0, TalentRules.pointsEarned(0));
    }

    @Test
    void theRootIsAlwaysOwnedAndCostsNothing() {
        ClassSlot fresh = slot(1, ClassGrade.APPRENTICE);
        assertEquals(java.util.Set.of(ROOT), TalentRules.owned(TREE, fresh));
        assertEquals(0, TalentRules.pointsSpent(TREE, fresh));
        assertEquals(1, TalentRules.pointsAvailable(TREE, fresh));
    }

    @Test
    void buyingNeedsThePreviousNodesPointsAndGrade() {
        assertEquals(TalentRules.Buy.OK, TalentRules.check(TREE, slot(1, ClassGrade.APPRENTICE), A));
        assertEquals(TalentRules.Buy.LOCKED, TalentRules.check(TREE, slot(10, ClassGrade.APPRENTICE), B));
        assertEquals(TalentRules.Buy.POINTS, TalentRules.check(TREE, slot(1, ClassGrade.APPRENTICE, A), B));
        assertEquals(TalentRules.Buy.OK, TalentRules.check(TREE, slot(3, ClassGrade.APPRENTICE, A), B));
        assertEquals(TalentRules.Buy.GRADE, TalentRules.check(TREE, slot(50, ClassGrade.APPRENTICE, A, B), TOP));
        assertEquals(TalentRules.Buy.OK, TalentRules.check(TREE, slot(50, ClassGrade.ADEPT, A, B), TOP));
        assertEquals(TalentRules.Buy.LOCKED, TalentRules.check(TREE, slot(50, ClassGrade.ADEPT, A), TOP), "every requirement is needed");
    }

    @Test
    void ownedRootsAndNodesOfOtherClassesAreRefused() {
        assertEquals(TalentRules.Buy.OWNED, TalentRules.check(TREE, slot(5, ClassGrade.APPRENTICE), ROOT));
        assertEquals(TalentRules.Buy.OWNED, TalentRules.check(TREE, slot(5, ClassGrade.APPRENTICE, A), A));
        assertEquals(TalentRules.Buy.WRONG_CLASS, TalentRules.check(TREE, slot(5, ClassGrade.APPRENTICE), SPELL));
        assertEquals(TalentRules.Buy.UNKNOWN, TalentRules.check(TREE, slot(5, ClassGrade.APPRENTICE), id("nope")));
    }

    @Test
    void pointsAreSpentOnlyOnNodesThatStillExist() {
        ClassSlot slot = slot(10, ClassGrade.APPRENTICE, A, id("removed_by_a_datapack"), SPELL);
        assertEquals(1, TalentRules.pointsSpent(TREE, slot), "unknown and foreign nodes cost nothing");
        assertEquals(9, TalentRules.pointsAvailable(TREE, slot));
    }

    @Test
    void pointsNeverGoNegativeWhenACostRises() {
        assertEquals(0, TalentRules.pointsAvailable(TREE, slot(2, ClassGrade.ADEPT, A, B, TOP)));
    }

    @Test
    void buyingAddsTheNodeToTheSlot() {
        ClassSlot bought = TalentRules.buy(slot(3, ClassGrade.APPRENTICE), A);
        assertEquals(List.of(A), bought.talents());
        assertTrue(TalentRules.owned(TREE, bought).contains(A));
    }
}
