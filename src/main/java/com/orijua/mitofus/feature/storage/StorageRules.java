package com.orijua.mitofus.feature.storage;

import com.orijua.mitofus.core.config.StorageBalance;

/**
 * What a storage costs, in iron. It opens with 2 rows (18
 * slots) and grows one row at a time, each row dearer than the last, up to 6 rows (54 slots). Depositing is free.
 */
public final class StorageRules {
    public static final int SLOTS_PER_ROW = 9;
    public static final int FIRST_ROWS = 2;
    public static final int MAX_ROWS = 6;

    private StorageRules() {
    }

    /** What the next row costs, in iron, for a storage of {@code rows} rows; -1 if it already has the most. */
    public static long nextRowCost(int rows) {
        if (rows < FIRST_ROWS || rows >= MAX_ROWS) {
            return -1L;
        }
        return rowCosts(StorageBalance.current())[rows - FIRST_ROWS];
    }

    /** What opening the storage costs. */
    public static long openCost() {
        return StorageBalance.current().openIron();
    }

    private static long[] rowCosts(StorageBalance balance) {
        return new long[]{balance.row3Iron(), balance.row4Iron(), balance.row5Iron(), balance.row6Iron()};
    }

    /** What everything costs altogether to reach {@code rows} rows from nothing. */
    public static long totalCost(int rows) {
        long total = openCost();
        long[] costs = rowCosts(StorageBalance.current());
        for (int row = FIRST_ROWS; row < Math.min(rows, MAX_ROWS); row++) {
            total += costs[row - FIRST_ROWS];
        }
        return total;
    }
}
