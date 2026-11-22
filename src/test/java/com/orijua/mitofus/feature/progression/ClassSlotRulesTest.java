package com.orijua.mitofus.feature.progression;

import com.orijua.mitofus.core.config.Balance;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassSlotRulesTest {
    private static final ResourceLocation SWORD = ResourceLocation.parse("mitofus:swordsman");
    private static final ResourceLocation MAGE = ResourceLocation.parse("mitofus:mage");
    private static final ResourceLocation SMITH = ResourceLocation.parse("mitofus:blacksmith");

    @Test
    void slotsOpenAtLevels1And20And50() {
        assertEquals(1, ClassSlotRules.unlockedSlots(1));
        assertEquals(1, ClassSlotRules.unlockedSlots(19));
        assertEquals(2, ClassSlotRules.unlockedSlots(20));
        assertEquals(2, ClassSlotRules.unlockedSlots(49));
        assertEquals(3, ClassSlotRules.unlockedSlots(50));
        assertEquals(3, ClassSlotRules.unlockedSlots(500));
    }

    @Test
    void aClassGoesInAnOpenFreeSlot() {
        assertTrue(ClassSlotRules.canChoose(PlayerClasses.DEFAULT, 0, SWORD, 1));
        PlayerClasses one = PlayerClasses.DEFAULT.withSlot(ClassSlot.fresh(0, SWORD));
        assertTrue(ClassSlotRules.canChoose(one, 1, MAGE, 20));
    }

    @Test
    void closedOccupiedRepeatedAndOutOfRangeSlotsAreRefused() {
        PlayerClasses one = PlayerClasses.DEFAULT.withSlot(ClassSlot.fresh(0, SWORD));
        assertEquals(ClassSlotRules.Choice.LOCKED, ClassSlotRules.check(one, 1, MAGE, 19), "slot 2 needs level 20");
        assertEquals(ClassSlotRules.Choice.LOCKED, ClassSlotRules.check(one, 2, MAGE, 49));
        assertEquals(ClassSlotRules.Choice.OCCUPIED, ClassSlotRules.check(one, 0, MAGE, 60), "what is chosen is not changed");
        assertEquals(ClassSlotRules.Choice.DUPLICATE, ClassSlotRules.check(one, 1, SWORD, 20), "the same class twice");
        assertEquals(ClassSlotRules.Choice.BAD_SLOT, ClassSlotRules.check(one, -1, MAGE, 60));
        assertEquals(ClassSlotRules.Choice.BAD_SLOT, ClassSlotRules.check(one, 3, MAGE, 60));
        assertFalse(ClassSlotRules.canChoose(one, 3, MAGE, 60));
    }

    @Test
    void firstFreeSlotIsTheLowestOpenOne() {
        PlayerClasses one = PlayerClasses.DEFAULT.withSlot(ClassSlot.fresh(0, SWORD));
        assertEquals(0, ClassSlotRules.firstFreeSlot(PlayerClasses.DEFAULT, 1));
        assertEquals(-1, ClassSlotRules.firstFreeSlot(one, 19));
        assertEquals(1, ClassSlotRules.firstFreeSlot(one, 20));
    }

    @Test
    void theJobIsChosenOnce() {
        assertTrue(ClassSlotRules.canChooseJob(PlayerClasses.DEFAULT));
        assertFalse(ClassSlotRules.canChooseJob(PlayerClasses.DEFAULT.withJob(SMITH)));
    }

    @Test
    void gradeNeedsHundredLevelsPerStep() {
        assertEquals(ClassGrade.APPRENTICE, ClassSlotRules.eligibleGrade(1));
        assertEquals(ClassGrade.APPRENTICE, ClassSlotRules.eligibleGrade(99));
        assertEquals(ClassGrade.ADEPT, ClassSlotRules.eligibleGrade(100));
        assertEquals(ClassGrade.MASTER, ClassSlotRules.eligibleGrade(399));
        assertEquals(ClassGrade.GRAND_MASTER, ClassSlotRules.eligibleGrade(450));
    }

    @Test
    void advancingNeedsTheLevelAndGoesOneGradeAtATime() {
        assertEquals(ClassSlotRules.Advance.LEVEL_TOO_LOW, ClassSlotRules.advance(at(99, ClassGrade.APPRENTICE), List.of(), Map.of()));
        assertTrue(ClassSlotRules.canAdvance(at(100, ClassGrade.APPRENTICE), List.of(), Map.of()), "100 allows Adept");
        // 450 is enough for Grand Master, but an Apprentice only gets to Adept: no skipping.
        assertTrue(ClassSlotRules.canAdvance(at(450, ClassGrade.APPRENTICE), List.of(), Map.of()));
        assertEquals(ClassSlotRules.Advance.LEVEL_TOO_LOW, ClassSlotRules.advance(at(150, ClassGrade.ADEPT), List.of(), Map.of()));
        assertTrue(ClassSlotRules.canAdvance(at(450, ClassGrade.MASTER), List.of(), Map.of()));
        assertEquals(ClassSlotRules.Advance.MAX_GRADE, ClassSlotRules.advance(at(450, ClassGrade.GRAND_MASTER), List.of(), Map.of()));
    }

    @Test
    void aJobBelowTheRequiredLevelBlocksOnlyThatGrade() {
        List<GradeRequirement> requirements = List.of(new GradeRequirement(ClassGrade.MASTER, SMITH, 10));
        assertTrue(ClassSlotRules.canAdvance(at(300, ClassGrade.ADEPT), requirements, Map.of()), "the requirement is for Master, not Expert");
        ClassSlot expert = at(300, ClassGrade.EXPERT);
        assertEquals(ClassSlotRules.Advance.JOB_TOO_LOW, ClassSlotRules.advance(expert, requirements, Map.of()), "no job is level 0");
        assertEquals(ClassSlotRules.Advance.JOB_TOO_LOW, ClassSlotRules.advance(expert, requirements, Map.of(SMITH, 9)));
        assertTrue(ClassSlotRules.canAdvance(expert, requirements, Map.of(SMITH, 10)));
    }

    @Test
    void classXpUsesTheSameCurveAsThePlayer() {
        Balance balance = Balance.DEFAULT;
        int needed = ProgressionRules.xpToNextLevel(balance, 1);
        ClassSlot half = ClassSlotRules.addClassXp(balance, ClassSlot.fresh(0, SWORD), needed - 1);
        assertEquals(1, half.classLevel());
        assertEquals(needed - 1, half.classXp());
        ClassSlot up = ClassSlotRules.addClassXp(balance, half, 1);
        assertEquals(2, up.classLevel());
        assertEquals(0, up.classXp());
        assertEquals(SWORD, up.classId());
        assertEquals(ClassGrade.APPRENTICE, up.grade());
    }

    private static ClassSlot at(int level, ClassGrade grade) {
        return new ClassSlot(0, SWORD, level, 0, grade);
    }
}
