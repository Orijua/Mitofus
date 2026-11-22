package com.orijua.mitofus.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PassiveXpBalanceTest {
    @Test
    void theDefaultIsTenAndThirty() {
        assertEquals(10, PassiveXpBalance.DEFAULT.fullXpUntilLevel());
        assertEquals(30, PassiveXpBalance.DEFAULT.oneXpFromLevel());
        assertEquals(PassiveXpBalance.DEFAULT, PassiveXpBalance.current());
    }

    @Test
    void rejectsLevelsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new PassiveXpBalance(-1, 30));
        assertThrows(IllegalArgumentException.class, () -> new PassiveXpBalance(10_001, 30));
        assertThrows(IllegalArgumentException.class, () -> new PassiveXpBalance(10, 0));
        assertThrows(IllegalArgumentException.class, () -> new PassiveXpBalance(10, 10_001));
        assertEquals(0, new PassiveXpBalance(0, 1).fullXpUntilLevel());
        assertEquals(10_000, new PassiveXpBalance(10_000, 10_000).oneXpFromLevel());
    }

    @Test
    void firstReducedLevelIsTheOneAfterFullXp() {
        assertEquals(11, new PassiveXpBalance(10, 30).firstReducedLevel());
        assertEquals(1, new PassiveXpBalance(0, 5).firstReducedLevel());
        assertEquals(10, new PassiveXpBalance(10, 10).firstReducedLevel());
        assertEquals(10, new PassiveXpBalance(20, 10).firstReducedLevel());
        assertEquals(10_000, new PassiveXpBalance(10_000, 10_000).firstReducedLevel());
        assertEquals(10_000, new PassiveXpBalance(9_999, 10_000).firstReducedLevel());
    }

    @Test
    void anOverrideIsRestoredAfterAnException() {
        PassiveXpBalance custom = new PassiveXpBalance(0, 5);
        assertThrows(IllegalStateException.class, () -> PassiveXpBalance.withOverride(custom, () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(PassiveXpBalance.DEFAULT, PassiveXpBalance.current());
    }

    @Test
    void validLimits() {
        assertEquals(1, new PassiveXpBalance(10_000, 1).oneXpFromLevel());
        assertEquals(10, new PassiveXpBalance(10, 10).fullXpUntilLevel());
    }

    @Test
    void anOverrideLastsOnlyWhileTheActionRuns() {
        PassiveXpBalance custom = new PassiveXpBalance(0, 5);
        PassiveXpBalance.withOverride(custom, () -> assertEquals(custom, PassiveXpBalance.current()));
        assertEquals(PassiveXpBalance.DEFAULT, PassiveXpBalance.current());
    }
}
