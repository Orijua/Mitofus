package com.orijua.mitofus.core.config;

/**
 * The tunable numbers of casting skills, apart from {@link Balance}.
 *
 * @param globalCooldownTicks ticks after casting an active skill before the player can cast any other one (20 = 1 second); 0 = no pause
 */
public record SkillBalance(int globalCooldownTicks) {
    /** The longest pause between two casts. */
    public static final int MAX_TICKS = 100;
    /** Half a second: enough to stop every skill going off in the same moment, short enough not to feel slow. */
    public static final int DEFAULT_TICKS = 10;

    public static final SkillBalance DEFAULT = new SkillBalance(DEFAULT_TICKS);

    public SkillBalance {
        if (globalCooldownTicks < 0 || globalCooldownTicks > MAX_TICKS) {
            throw new IllegalArgumentException("globalCooldownTicks must be between 0 and " + MAX_TICKS + ": " + globalCooldownTicks);
        }
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static volatile SkillBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static SkillBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.skillBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(SkillBalance balance, Runnable action) {
        SkillBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
