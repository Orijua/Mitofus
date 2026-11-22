package com.orijua.mitofus.feature.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoinPurseTest {
    @Test
    void oneGoldIsAHundredIronAndTenThousandCopper() {
        assertEquals(10_000L, CoinPurse.COPPER_PER_GOLD);
        assertEquals(100L, CoinPurse.copperOf(1));
        assertEquals(10_000L, CoinPurse.copperOf(100));
    }

    @Test
    void changeComesBackInTheFewestCoins() {
        assertArrayEquals(new long[]{0, 0, 0}, CoinPurse.coins(0));
        assertArrayEquals(new long[]{0, 0, 99}, CoinPurse.coins(99));
        assertArrayEquals(new long[]{0, 1, 0}, CoinPurse.coins(100));
        assertArrayEquals(new long[]{1, 0, 0}, CoinPurse.coins(10_000));
        assertArrayEquals(new long[]{3, 11, 5}, CoinPurse.coins(3 * 10_000 + 11 * 100 + 5));
        assertArrayEquals(new long[]{0, 0, 0}, CoinPurse.coins(-7), "a negative amount is no change");
    }

    @Test
    void breakingAGoldGivesBackTheRestAsIron() {
        // Paying 30 iron with one gold coin: 70 iron of change.
        long[] change = CoinPurse.coins(CoinPurse.COPPER_PER_GOLD - CoinPurse.copperOf(30));
        assertArrayEquals(new long[]{0, 70, 0}, change);
    }
}
