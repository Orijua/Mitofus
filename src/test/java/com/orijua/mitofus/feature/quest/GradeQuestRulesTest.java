package com.orijua.mitofus.feature.quest;

import com.orijua.mitofus.feature.progression.ClassGrade;
import com.orijua.mitofus.feature.progression.ClassSlot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeQuestRulesTest {
    private static final ResourceLocation FISHER = ResourceLocation.fromNamespaceAndPath("test", "fisher");
    private static final ResourceLocation SMITH = ResourceLocation.fromNamespaceAndPath("test", "smith");
    private static final GradeQuest GENERIC = new GradeQuest(ClassGrade.APPRENTICE, Optional.empty());
    private static final GradeQuest FISHER_VARIANT = new GradeQuest(ClassGrade.APPRENTICE, Optional.of(FISHER));

    private static Optional<ClassSlot> slot(int level, ClassGrade grade) {
        return Optional.of(new ClassSlot(0, ResourceLocation.fromNamespaceAndPath("test", "swordsman"), level, 0, grade));
    }

    @Test
    void opensAtTheGradeLevelWithTheFullTree() {
        assertEquals(GradeQuestRules.Check.OK, GradeQuestRules.check(GENERIC, slot(100, ClassGrade.APPRENTICE), true, Optional.empty(), false));
        assertEquals(GradeQuestRules.Check.LEVEL_TOO_LOW, GradeQuestRules.check(GENERIC, slot(99, ClassGrade.APPRENTICE), true, Optional.empty(), false));
        assertEquals(GradeQuestRules.Check.TREE_INCOMPLETE, GradeQuestRules.check(GENERIC, slot(100, ClassGrade.APPRENTICE), false, Optional.empty(), false));
    }

    @Test
    void needsTheClassAtTheQuestsGrade() {
        assertEquals(GradeQuestRules.Check.NO_CLASS, GradeQuestRules.check(GENERIC, Optional.empty(), true, Optional.empty(), false));
        assertEquals(GradeQuestRules.Check.WRONG_GRADE, GradeQuestRules.check(GENERIC, slot(300, ClassGrade.ADEPT), true, Optional.empty(), false));
    }

    @Test
    void theJobOnlyPicksTheVariantAndNeverBlocksAGrade() {
        Optional<ClassSlot> ready = slot(100, ClassGrade.APPRENTICE);
        assertEquals(GradeQuestRules.Check.OK, GradeQuestRules.check(FISHER_VARIANT, ready, true, Optional.of(FISHER), true));
        assertEquals(GradeQuestRules.Check.WRONG_JOB, GradeQuestRules.check(FISHER_VARIANT, ready, true, Optional.of(SMITH), true));
        assertEquals(GradeQuestRules.Check.WRONG_JOB, GradeQuestRules.check(GENERIC, ready, true, Optional.of(FISHER), true));
        assertEquals(GradeQuestRules.Check.OK, GradeQuestRules.check(GENERIC, ready, true, Optional.of(SMITH), false));
        assertEquals(GradeQuestRules.Check.OK, GradeQuestRules.check(GENERIC, ready, true, Optional.empty(), false));
    }

    @Test
    void onlyTheQuestsMeantForThePlayerAreListed() {
        assertTrue(GradeQuestRules.isForPlayer(GradeQuestRules.Check.OK));
        assertTrue(GradeQuestRules.isForPlayer(GradeQuestRules.Check.LEVEL_TOO_LOW));
        assertTrue(GradeQuestRules.isForPlayer(GradeQuestRules.Check.TREE_INCOMPLETE));
        assertFalse(GradeQuestRules.isForPlayer(GradeQuestRules.Check.WRONG_JOB));
        assertFalse(GradeQuestRules.isForPlayer(GradeQuestRules.Check.WRONG_GRADE));
        assertFalse(GradeQuestRules.isForPlayer(GradeQuestRules.Check.NO_CLASS));
    }

    @Test
    void theQuestOfTheLastGradeOpensAtItsEndLevel() {
        GradeQuest sovereign = new GradeQuest(ClassGrade.GRAND_MASTER, Optional.empty());
        assertEquals(GradeQuestRules.Check.OK, GradeQuestRules.check(sovereign, slot(500, ClassGrade.GRAND_MASTER), true, Optional.empty(), false));
        assertEquals(GradeQuestRules.Check.LEVEL_TOO_LOW, GradeQuestRules.check(sovereign, slot(499, ClassGrade.GRAND_MASTER), true, Optional.empty(), false));
    }
}
