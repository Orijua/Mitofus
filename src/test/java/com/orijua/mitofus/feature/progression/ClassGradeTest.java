package com.orijua.mitofus.feature.progression;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassGradeTest {
    @Test
    void gradesGoUpEveryHundredLevels() {
        assertEquals(100, ClassGrade.APPRENTICE.levelToRankUp());
        assertEquals(200, ClassGrade.ADEPT.levelToRankUp());
        assertEquals(400, ClassGrade.MASTER.levelToRankUp());
    }

    @Test
    void canRankUpOnlyAtTheLevelAndWhenThereIsANextGrade() {
        assertFalse(ClassGrade.APPRENTICE.canRankUp(99));
        assertTrue(ClassGrade.APPRENTICE.canRankUp(100));
        assertTrue(ClassGrade.APPRENTICE.canRankUp(250));
        assertFalse(ClassGrade.ADEPT.canRankUp(150));
        assertFalse(ClassGrade.GRAND_MASTER.canRankUp(100000), "there is nothing above Grand Master");
    }

    @Test
    void gradesFollowInOrder() {
        assertEquals(Optional.of(ClassGrade.ADEPT), ClassGrade.APPRENTICE.next());
        assertEquals(Optional.of(ClassGrade.GRAND_MASTER), ClassGrade.MASTER.next());
        assertEquals(Optional.empty(), ClassGrade.GRAND_MASTER.next());
        assertEquals("grand_master", ClassGrade.GRAND_MASTER.getSerializedName());
    }
}
