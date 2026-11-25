package com.orijua.mitofus.feature.effect.status;

import com.orijua.mitofus.feature.effect.TraitRules;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusRulesTest {
    private static StatusRules.Limits status(int immunity) {
        return new StatusRules.Limits(100, 300, 1, 3, 0.5, immunity);
    }

    @Test
    void chanceStaysBetweenZeroAndTheMaximum() {
        assertEquals(0.5, StatusRules.chance(2.0, status(0)));
        assertEquals(0.25, StatusRules.chance(0.25, status(0)));
        assertEquals(0.0, StatusRules.chance(-1, status(0)));
    }

    @Test
    void durationAndIntensityUseTheDefaultsAndNeverPassTheMaximums() {
        assertEquals(100, StatusRules.duration(Optional.empty(), status(0)));
        assertEquals(200, StatusRules.duration(Optional.of(200), status(0)));
        assertEquals(300, StatusRules.duration(Optional.of(9000), status(0)));
        assertEquals(1, StatusRules.intensity(Optional.empty(), status(0)));
        assertEquals(2, StatusRules.intensity(Optional.of(2), status(0)));
        assertEquals(3, StatusRules.intensity(Optional.of(10), status(0)));
    }

    @Test
    void theImmunityWindowStartsWhenTheStatusEnds() {
        var shock = status(100);
        long until = StatusRules.immuneUntil(1000, 20, shock);
        assertEquals(1120, until);
        assertTrue(StatusRules.isImmune(1119, until));
        assertFalse(StatusRules.isImmune(1120, until));
        assertEquals(0, StatusRules.immuneUntil(1000, 20, status(0)), "no window, no immunity");
    }

    @Test
    void aValueUnderItsGlobalCapIsKeptAndOneOverItIsCut() {
        assertEquals(0.3, TraitRules.capped(0.3, Optional.of(0.5)));
        assertEquals(0.5, TraitRules.capped(0.9, Optional.of(0.5)));
        assertEquals(0.9, TraitRules.capped(0.9, Optional.empty()), "no cap in data, no cut");
    }

    @Test
    void aStrongerStatusIsKeptAndAtTheSameStrengthTheLongerOne() {
        assertTrue(StatusRules.keepsExisting(2, 40, 0, 300), "Slowness III is not cancelled by Slowness I");
        assertFalse(StatusRules.keepsExisting(0, 300, 2, 40), "a stronger one replaces a weaker one");
        assertTrue(StatusRules.keepsExisting(1, 200, 1, 100), "same strength, the longer lasts");
        assertFalse(StatusRules.keepsExisting(1, 100, 1, 200), "same strength, a longer one renews it");
        assertFalse(StatusRules.keepsExisting(1, 100, 1, 100), "equal ones renew");
    }
}
