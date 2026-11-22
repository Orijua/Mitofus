package com.orijua.mitofus.feature.fatigue;

import com.orijua.mitofus.core.config.FatigueBalance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one place that states the factory numbers of {@link FatigueBalance}; the other tests read them from the balance they use. */
class FatigueBalanceTest {
    private static final FatigueBalance D = FatigueBalance.DEFAULT;

    @Test
    void theDefaultIsTheDesignedOne() {
        assertAll(
                () -> assertTrue(D.enabled()),
                () -> assertEquals(40.0, D.sessionMinutes()),
                () -> assertEquals(0.8, D.restMultiplier(), "standing still costs most of the normal drain"),
                () -> assertEquals(2.0, D.combatMultiplier()),
                () -> assertEquals(20.0, D.forgeCostSeconds()),
                () -> assertEquals(10.0, D.recoveryMinutes()),
                () -> assertEquals(0.25, D.minEnergyToConnect()),
                () -> assertEquals(5, D.graceSeconds(), "the safety protocol gives five seconds"),
                () -> assertEquals(List.of(0.15, 0.08, 0.02), D.warnAt()),
                () -> assertEquals(0.30, D.sleepRecovery()));
    }

    @Test
    void theCopiesChangeOnlyWhatTheyName() {
        FatigueBalance off = D.withEnabled(false);
        assertFalse(off.enabled());
        assertEquals(D.withEnabled(true), off.withEnabled(true));
        FatigueBalance noGrace = D.withGraceSeconds(0);
        assertEquals(0, noGrace.graceSeconds());
        assertEquals(D, noGrace.withGraceSeconds(D.graceSeconds()));
    }
}
