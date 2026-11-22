package com.orijua.mitofus.feature.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCostRulesTest {
    @Test
    void cooldownIsTruncatedToSeconds() {
        assertEquals(0, SkillCostRules.cooldownSeconds(0));
        assertEquals(0, SkillCostRules.cooldownSeconds(19));
        assertEquals(1, SkillCostRules.cooldownSeconds(20));
        assertEquals(15, SkillCostRules.cooldownSeconds(300));
        assertEquals(3600, SkillCostRules.cooldownSeconds(72000));
    }

    @Test
    void manaIsTruncated() {
        assertEquals(30, SkillCostRules.manaShown(30f));
        assertEquals(0, SkillCostRules.manaShown(0f));
        assertEquals(2, SkillCostRules.manaShown(2.9f));
        assertEquals(1000, SkillCostRules.manaShown(1000f));
    }

    @Test
    void wearShownOnlyWhenPositive() {
        assertFalse(SkillCostRules.showsWear(0));
        assertTrue(SkillCostRules.showsWear(1));
        assertTrue(SkillCostRules.showsWear(4));
        assertFalse(SkillCostRules.showsWear(-1));
    }
}
