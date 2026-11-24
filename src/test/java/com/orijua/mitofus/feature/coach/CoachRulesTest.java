package com.orijua.mitofus.feature.coach;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoachRulesTest {
    @Test
    void theBaseFareIsFiveGold() {
        assertEquals(500L, CoachRules.fareIron(0.0));
        assertEquals(500L, CoachRules.fareIron(99.0));
    }

    @Test
    void theSurchargeGrowsWithTheRoad() {
        assertEquals(510L, CoachRules.fareIron(100.0));
        assertEquals(590L, CoachRules.fareIron(950.0));
    }

    @Test
    void theSurchargeHasATop() {
        assertEquals(1000L, CoachRules.fareIron(1_000_000.0));
    }

    @Test
    void aNegativeDistanceCostsTheBase() {
        assertEquals(500L, CoachRules.fareIron(-5.0));
    }

    @Test
    void youCanOnlyGoToTheOtherVillage() {
        assertTrue(CoachRules.canGo("covadonga", "eibar"));
        assertTrue(CoachRules.canGo("eibar", "covadonga"));
        assertFalse(CoachRules.canGo("covadonga", "covadonga"));
        assertFalse(CoachRules.canGo("covadonga", "torre_del_oro"));
        assertFalse(CoachRules.canGo("covadonga", "nowhere"));
    }

    @Test
    void theTripTakesAFewSecondsByDefaultAndTheLimitsAreChecked() {
        org.junit.jupiter.api.Assertions.assertEquals(3, com.orijua.mitofus.core.config.CoachBalance.DEFAULT.travelSeconds());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new com.orijua.mitofus.core.config.CoachBalance(500, 10, 500, -1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new com.orijua.mitofus.core.config.CoachBalance(500, 10, 500, 61));
    }
}
