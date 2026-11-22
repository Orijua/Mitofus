package com.orijua.mitofus.core.config;

/**
 * What a character's storage costs, in iron. It
 * opens with 2 rows (18 slots) and grows one row at a time up to 6 (54 slots).
 *
 * @param openIron  opening the storage (it comes with 2 rows)
 * @param row3Iron  the third row
 * @param row4Iron  the fourth row
 * @param row5Iron  the fifth row
 * @param row6Iron  the sixth row
 */
public record StorageBalance(int openIron, int row3Iron, int row4Iron, int row5Iron, int row6Iron) {
    public static final int MAX_IRON = 1_000_000;

    public static final StorageBalance DEFAULT = new StorageBalance(30, 50, 100, 200, 400);

    public StorageBalance {
        check("openIron", openIron);
        check("row3Iron", row3Iron);
        check("row4Iron", row4Iron);
        check("row5Iron", row5Iron);
        check("row6Iron", row6Iron);
    }

    private static void check(String name, int value) {
        if (value < 0 || value > MAX_IRON) {
            throw new IllegalArgumentException(name + " must be between 0 and " + MAX_IRON + ": " + value);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static StorageBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static StorageBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.storageBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(StorageBalance balance, Runnable action) {
        StorageBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
