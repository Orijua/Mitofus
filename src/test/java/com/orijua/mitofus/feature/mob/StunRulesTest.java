package com.orijua.mitofus.feature.mob;

import com.orijua.mitofus.core.config.StunBalance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StunRulesTest {
    private static final StunBalance B = StunBalance.DEFAULT;

    @Test
    void aBlowOfFortyPercentStuns() {
        assertTrue(StunRules.stuns(B, 8f, 20f));
        assertTrue(StunRules.stuns(B, 30f, 20f));
    }

    @Test
    void aSmallerBlowDoesNot() {
        assertFalse(StunRules.stuns(B, 7.9f, 20f));
        assertFalse(StunRules.stuns(B, 0f, 20f));
    }

    @Test
    void durationZeroTurnsStunOff() {
        assertFalse(StunRules.stuns(new StunBalance(0.4, 0), 100f, 20f));
    }

    @Test
    void theThresholdFollowsTheConfig() {
        StunBalance half = new StunBalance(0.5, 2);
        assertFalse(StunRules.stuns(half, 8f, 20f));
        assertTrue(StunRules.stuns(half, 10f, 20f));
    }

    @Test
    void invalidMaxHealthNeverStuns() {
        assertFalse(StunRules.stuns(B, 5f, 0f));
    }

    @Test
    void invalidBalanceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StunBalance(0.0, 2));
        assertThrows(IllegalArgumentException.class, () -> new StunBalance(1.5, 2));
        assertThrows(IllegalArgumentException.class, () -> new StunBalance(0.4, -1));
        assertThrows(IllegalArgumentException.class, () -> new StunBalance(0.4, StunBalance.MAX_SECONDS + 1));
        assertThrows(IllegalArgumentException.class, () -> new StunBalance(0.4, 2, -1));
        assertThrows(IllegalArgumentException.class, () -> new StunBalance(0.4, 2, StunBalance.MAX_IMMUNITY_SECONDS + 1));
    }

    @Test
    void immunityStartsWhenTheStunEnds() {
        StunBalance balance = new StunBalance(0.4, 2, 10);
        assertEquals(1000L + 40 + 200, StunRules.immuneUntil(balance, 1000L));
    }

    @Test
    void isImmuneUntilTheEndTick() {
        assertTrue(StunRules.isImmune(1239L, 1240L));
        assertFalse(StunRules.isImmune(1240L, 1240L));
        assertFalse(StunRules.isImmune(500L, 0L));
    }

    @Test
    void zeroImmunityEndsWithTheStun() {
        StunBalance balance = new StunBalance(0.4, 2, 0);
        assertFalse(StunRules.isImmune(1000L + 40, StunRules.immuneUntil(balance, 1000L)));
    }
}
