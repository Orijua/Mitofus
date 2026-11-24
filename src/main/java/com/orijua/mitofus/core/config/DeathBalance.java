package com.orijua.mitofus.core.config;

/**
 * The tunable numbers of dying in Mitofus, apart from {@link Balance}. The mod's own design.
 *
 * @param doubleDeathWindowMinutes in-game minutes (game time: it stops while the server is closed) within which a second
 *                                 death in Mitofus also disconnects the player; 0 = never (so there is no lock either)
 * @param reconnectLockMinutes     in-game minutes after that disconnection during which the player cannot connect to
 *                                 Mitofus again; 0 = no lock
 * @param pvpDropChance            chance (0 to 1) that dying in Mitofus to another player, with PvP on, drops equipped pieces
 *                                 for whoever picks them up; 0 = never
 * @param pvpDropCount             how many equipped pieces (main hand and armor) can drop in that case
 */
public record DeathBalance(int doubleDeathWindowMinutes, int reconnectLockMinutes, double pvpDropChance, int pvpDropCount) {
    /** The most pieces that can drop: the main hand and the four armor pieces. */
    public static final int MAX_PVP_DROP_COUNT = 5;

    /** The most the window or the lock can be (a day). */
    public static final int MAX_WINDOW_MINUTES = 1440;

    /** Game ticks in a minute (20 ticks a second). */
    private static final long TICKS_PER_MINUTE = 60L * 20L;

    public static final DeathBalance DEFAULT = new DeathBalance(10, 10, 0.05, 1);

    /** Same as the full constructor with the default PvP drop. */
    public DeathBalance(int doubleDeathWindowMinutes, int reconnectLockMinutes) {
        this(doubleDeathWindowMinutes, reconnectLockMinutes, 0.05, 1);
    }

    public DeathBalance {
        if (!(pvpDropChance >= 0.0 && pvpDropChance <= 1.0)) {
            throw new IllegalArgumentException("pvpDropChance must be between 0 and 1: " + pvpDropChance);
        }
        if (pvpDropCount < 0 || pvpDropCount > MAX_PVP_DROP_COUNT) {
            throw new IllegalArgumentException("pvpDropCount must be between 0 and " + MAX_PVP_DROP_COUNT + ": " + pvpDropCount);
        }
        if (doubleDeathWindowMinutes < 0 || doubleDeathWindowMinutes > MAX_WINDOW_MINUTES) {
            throw new IllegalArgumentException(
                    "doubleDeathWindowMinutes must be between 0 and " + MAX_WINDOW_MINUTES + ": " + doubleDeathWindowMinutes);
        }
        if (reconnectLockMinutes < 0 || reconnectLockMinutes > MAX_WINDOW_MINUTES) {
            throw new IllegalArgumentException(
                    "reconnectLockMinutes must be between 0 and " + MAX_WINDOW_MINUTES + ": " + reconnectLockMinutes);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static volatile DeathBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static DeathBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.deathBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(DeathBalance balance, Runnable action) {
        DeathBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }

    /** The window in game ticks; 0 means a second death never disconnects. */
    public long windowTicks() {
        return doubleDeathWindowMinutes * TICKS_PER_MINUTE;
    }

    /** The reconnection lock in game ticks; 0 means no lock. */
    public long lockTicks() {
        return reconnectLockMinutes * TICKS_PER_MINUTE;
    }
}
