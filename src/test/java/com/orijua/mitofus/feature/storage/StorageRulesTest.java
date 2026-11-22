package com.orijua.mitofus.feature.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageRulesTest {
    @Test
    void openingCostsThirtyIronAndGivesTwoRows() {
        assertEquals(30L, StorageRules.openCost());
        assertEquals(2, StorageRules.FIRST_ROWS);
        assertEquals(18, StorageRules.FIRST_ROWS * StorageRules.SLOTS_PER_ROW);
    }

    @Test
    void eachRowCostsMoreThanTheLast() {
        assertEquals(50L, StorageRules.nextRowCost(2));
        assertEquals(100L, StorageRules.nextRowCost(3));
        assertEquals(200L, StorageRules.nextRowCost(4));
        assertEquals(400L, StorageRules.nextRowCost(5));
    }

    @Test
    void theLastRowIsTheSixth() {
        assertEquals(-1L, StorageRules.nextRowCost(StorageRules.MAX_ROWS));
        assertEquals(-1L, StorageRules.nextRowCost(0));
    }

    @Test
    void theWholeStorageCost() {
        assertEquals(30L, StorageRules.totalCost(2));
        assertEquals(780L, StorageRules.totalCost(6));
    }
}
