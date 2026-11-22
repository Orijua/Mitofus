package com.orijua.mitofus.feature.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasteryRulesTest {
    @Test
    void theStepsGrowByAQuarter() {
        assertEquals(100, MasteryRules.usesToAdvance(1));
        assertEquals(125, MasteryRules.usesToAdvance(2));
        assertEquals(244, MasteryRules.usesToAdvance(5));
        assertTrue(MasteryRules.usesToAdvance(20) > 6000, "the last step is long");
    }

    @Test
    void levelFollowsTheTotalUses() {
        assertEquals(1, MasteryRules.level(0));
        assertEquals(1, MasteryRules.level(99));
        assertEquals(2, MasteryRules.level(100));
        assertEquals(2, MasteryRules.level(224));
        assertEquals(3, MasteryRules.level(225));
        assertEquals(1, MasteryRules.level(-5), "never below the first level");
    }

    @Test
    void levelNeverPassesTheMaximumAndMatchesUsesToReach() {
        assertEquals(MasteryRules.MAX_LEVEL, MasteryRules.level(Integer.MAX_VALUE));
        for (int level = 1; level <= MasteryRules.MAX_LEVEL; level++) {
            assertEquals(level, MasteryRules.level(MasteryRules.usesToReach(level)), "reaching level " + level);
            if (level > 1) {
                assertEquals(level - 1, MasteryRules.level(MasteryRules.usesToReach(level) - 1), "one use short of " + level);
            }
        }
    }

    @Test
    void fourRanksOfFiveLevels() {
        assertEquals(0, MasteryRules.rank(1));
        assertEquals(1, MasteryRules.levelInRank(1));
        assertEquals(0, MasteryRules.rank(5));
        assertEquals(5, MasteryRules.levelInRank(5));
        assertEquals(1, MasteryRules.rank(6));
        assertEquals(1, MasteryRules.levelInRank(6));
        assertEquals(3, MasteryRules.rank(20));
        assertEquals(5, MasteryRules.levelInRank(20));
    }
}
