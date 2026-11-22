package com.orijua.mitofus.feature.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectRulesTest {
    @Test
    void aFullCircleIncludesWhatIsBehind() {
        double minDot = EffectRules.coneMinDot(360);
        assertTrue(EffectRules.inCone(3, -1.0, 5, minDot), "behind the caster is still inside 360 degrees");
    }

    @Test
    void theEdgeOfAThirtyDegreeCone() {
        double minDot = EffectRules.coneMinDot(30);
        assertTrue(EffectRules.inCone(3, Math.cos(Math.toRadians(14.9)), 5, minDot));
        assertFalse(EffectRules.inCone(3, Math.cos(Math.toRadians(15.1)), 5, minDot));
    }

    @Test
    void closerThanOneBlockCountsFromAnyAngle() {
        assertTrue(EffectRules.inCone(0.5, -1.0, 5, EffectRules.coneMinDot(30)));
    }

    @Test
    void theRangeIsInclusive() {
        double minDot = EffectRules.coneMinDot(140);
        assertTrue(EffectRules.inCone(5.0, 1.0, 5.0, minDot));
        assertFalse(EffectRules.inCone(5.01, 1.0, 5.0, minDot));
    }

    @Test
    void areaDamageScalesWithStats() {
        assertEquals(8.4f, EffectRules.areaDamage(6, 1.0, 0.03, 0.02, 10, 5), 1e-4);
        assertEquals(12f, EffectRules.areaDamage(6, 2.0, 0, 0, 40, 40), 1e-4, "no scaling: attack times multiplier");
        assertEquals(0f, EffectRules.areaDamage(6, 0, 0.03, 0.02, 10, 5), 1e-4);
        assertEquals(0f, EffectRules.areaDamage(0, 1.0, 0.03, 0.02, 10, 5), 1e-4, "a caster without attack does no damage");
    }

    @Test
    void projectilesSpreadSymmetrically() {
        assertEquals(0.0, EffectRules.projectileSpread(0, 1), 1e-9);
        assertEquals(-0.08, EffectRules.projectileSpread(0, 3), 1e-9);
        assertEquals(0.0, EffectRules.projectileSpread(1, 3), 1e-9);
        assertEquals(0.08, EffectRules.projectileSpread(2, 3), 1e-9);
    }
}
