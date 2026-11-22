package com.orijua.mitofus.feature.vrhelmet;

import com.orijua.mitofus.core.config.DeathBalance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeathBalanceTest {
    @Test
    void theDefaultIsTenMinutesAndTenOfLock() {
        assertEquals(10, DeathBalance.DEFAULT.doubleDeathWindowMinutes());
        assertEquals(10, DeathBalance.DEFAULT.reconnectLockMinutes());
        assertEquals(DeathBalance.DEFAULT, DeathBalance.current());
    }

    @Test
    void theWindowIsBetweenZeroAndADay() {
        assertThrows(IllegalArgumentException.class, () -> new DeathBalance(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new DeathBalance(1441, 10));
        assertEquals(0, new DeathBalance(0, 10).doubleDeathWindowMinutes());
        assertEquals(10, new DeathBalance(10, 10).doubleDeathWindowMinutes());
        assertEquals(1440, new DeathBalance(1440, 10).doubleDeathWindowMinutes());
    }

    @Test
    void theLockIsBetweenZeroAndADay() {
        assertThrows(IllegalArgumentException.class, () -> new DeathBalance(10, -1));
        assertThrows(IllegalArgumentException.class, () -> new DeathBalance(10, 1441));
        assertEquals(0, new DeathBalance(10, 0).reconnectLockMinutes());
        assertEquals(1440, new DeathBalance(10, 1440).reconnectLockMinutes());
    }

    @Test
    void theWindowInTicks() {
        assertEquals(12_000L, new DeathBalance(10, 10).windowTicks());
        assertEquals(0L, new DeathBalance(0, 10).windowTicks());
        assertEquals(1_728_000L, new DeathBalance(1440, 10).windowTicks());
    }

    @Test
    void theLockInTicks() {
        assertEquals(12_000L, new DeathBalance(10, 10).lockTicks());
        assertEquals(0L, new DeathBalance(10, 0).lockTicks());
        assertEquals(1_728_000L, new DeathBalance(10, 1440).lockTicks());
        assertEquals(1_200L, new DeathBalance(10, 1).lockTicks());
    }

    @Test
    void anOverrideLastsOnlyWhileItRuns() {
        DeathBalance custom = new DeathBalance(3, 7);
        DeathBalance.withOverride(custom, () -> assertEquals(custom, DeathBalance.current()));
        assertEquals(DeathBalance.DEFAULT, DeathBalance.current());
    }

    @Test
    void thePvpDropIsFivePercentOnePieceAndStaysInRange() {
        assertEquals(0.05, DeathBalance.DEFAULT.pvpDropChance());
        assertEquals(1, DeathBalance.DEFAULT.pvpDropCount());
        assertThrows(IllegalArgumentException.class, () -> new DeathBalance(10, 10, 1.1, 1));
        assertThrows(IllegalArgumentException.class, () -> new DeathBalance(10, 10, -0.1, 1));
        assertThrows(IllegalArgumentException.class, () -> new DeathBalance(10, 10, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new DeathBalance(10, 10, 0.05, 6));
        assertEquals(0L, new DeathBalance(10, 10, 0.0, 0).pvpDropCount());
    }
}
