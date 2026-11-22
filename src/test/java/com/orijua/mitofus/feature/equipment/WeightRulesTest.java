package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.core.config.WeightBalance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeightRulesTest {
    private static final WeightBalance B = WeightBalance.DEFAULT;

    @Test
    void theSystemIsOffByDefault() {
        assertFalse(WeightBalance.DEFAULT.enabled());
    }

    @Test
    void theLimitGrowsWithStamina() {
        assertEquals(40_000L, WeightRules.limitGrams(B, 0));
        assertEquals(60_000L, WeightRules.limitGrams(B, 10));
        assertEquals(40_000L, WeightRules.limitGrams(B, -3), "a negative stat counts as none");
    }

    @Test
    void underTheLimitThereIsNoExcess() {
        assertEquals(0.0, WeightRules.excess(40_000L, 40_000L));
        assertEquals(0.0, WeightRules.excess(10L, 40_000L));
        assertEquals(0.0, WeightRules.excess(10L, 0L));
    }

    @Test
    void theExcessSlowsByHalfItsPercentage() {
        assertEquals(0.5, WeightRules.excess(60_000L, 40_000L));
        assertEquals(0.25, WeightRules.slowdown(0.5), 1e-9);
        assertEquals(0.5, WeightRules.slowdown(1.0), 1e-9, "100 % over slows 50 %");
        assertEquals(1.0, WeightRules.slowdown(2.0), 1e-9, "200 % over stops you");
        assertEquals(1.0, WeightRules.slowdown(9.0), "never more than all of it");
        assertEquals(0.0, WeightRules.slowdown(-1.0));
    }

    @Test
    void weaknessGetsLikelierWithTheExcess() {
        assertEquals(0.0, WeightRules.weaknessChance(0.0));
        assertEquals(0.3, WeightRules.weaknessChance(0.3), 1e-9);
        assertEquals(1.0, WeightRules.weaknessChance(5.0));
    }

    @Test
    void kilogramsAreShownWithOneDecimalAndADot() {
        assertEquals("1.5", WeightRules.formatKg(1500));
        assertEquals("2", WeightRules.formatKg(2000));
        assertEquals("0.1", WeightRules.formatKg(100));
        assertEquals("0", WeightRules.formatKg(10));
    }

    @Test
    void theBalanceRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new WeightBalance(true, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new WeightBalance(true, 10.0, -1.0));
        assertThrows(IllegalArgumentException.class, () -> new WeightBalance(true, Double.NaN, 1.0));
    }
}
