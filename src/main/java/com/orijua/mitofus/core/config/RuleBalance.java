package com.orijua.mitofus.core.config;

/**
 * Small tunable numbers that used to be fixed in the code: the VR helmet and logout waits, the NPC grade chances, the
 * Bone Lord, the fatigue windows and the terminal. Apart from {@link Balance}. The mod's own design.
 * The defaults are the old fixed values.
 *
 * @param connectSeconds           seconds lying in the VR helmet before connecting
 * @param logoutSeconds            seconds of the {@code /mitofus logout} countdown
 * @param dangerSeconds            seconds after any combat during which logout is refused (0 = never refused)
 * @param maxDistance              blocks the player may get from the VR helmet while connecting
 * @param stillTolerance           blocks the player may move during a connection or a logout and still count as still
 * @param namedChance              chance (0 to 1) that an apprentice is named
 * @param semiNamedChance          chance (0 to 1) that an apprentice is semi-named; with namedChance it never passes 1
 * @param boneLordMinions          skeletons the Bone Lord summons
 * @param boneLordRegenPercent     percent of its max health the Bone Lord heals per second when left alone
 * @param boneLordOutOfCombatSeconds seconds without being hurt before the Bone Lord starts healing
 * @param combatWindowSeconds      seconds after damage given or taken that count as fighting for the fatigue
 * @param protectionSeconds        seconds of protection the fatigue gives after a forced disconnection
 * @param restMaxMove              blocks the player may move and still count as resting
 * @param workWindowSeconds        seconds after breaking a block that count as working for the fatigue
 * @param terminalRange            blocks from the terminal beyond which its screen closes
 * @param eliteDropMinChance       the chance of an elite's equipment drop that very high levels tend to (0 to 1), see {@code EliteDropRules.chance}
 * @param eliteDropDecay           what the excess of the elite drop chance over that floor gets multiplied by for each level (0.5 to 1; 1 = no decay)
 * @param smithLevel             the level of Smith, the village blacksmith (shown by Jade and the name tag; design)
 * @param chiefLevel             the same for the village chief
 * @param velascoLevel              the same for Velasco
 * @param menciaLevel         the same for Mencia
 */
public record RuleBalance(
        double connectSeconds,
        double logoutSeconds,
        double dangerSeconds,
        double maxDistance,
        double stillTolerance,
        double namedChance,
        double semiNamedChance,
        int boneLordMinions,
        double boneLordRegenPercent,
        double boneLordOutOfCombatSeconds,
        double combatWindowSeconds,
        double protectionSeconds,
        double restMaxMove,
        double workWindowSeconds,
        double terminalRange,
        double eliteDropMinChance,
        double eliteDropDecay,
        int smithLevel,
        int chiefLevel,
        int velascoLevel,
        int menciaLevel) {

    public static final double MAX_SECONDS = 600.0;
    public static final double MAX_BLOCKS = 64.0;
    public static final int MAX_MINIONS = 8;
    public static final int MAX_NPC_LEVEL = 10_000;
    /** One tick: the shortest wait of a VR helmet connection or a logout. */
    public static final double MIN_WAIT_SECONDS = 0.05;

    public static final RuleBalance DEFAULT = new RuleBalance(3.0, 3.0, 3.0, 3.0, 0.5, 0.01, 0.09, 2, 2.0, 10.0,
            10.0, 10.0, 0.5, 3.0, 8.0, 0.00005, 0.92, 15, 30, 25, 50);

    public RuleBalance {
        checkRange("connectSeconds", connectSeconds, MIN_WAIT_SECONDS, MAX_SECONDS);
        checkRange("logoutSeconds", logoutSeconds, MIN_WAIT_SECONDS, MAX_SECONDS);
        checkRange("dangerSeconds", dangerSeconds, 0.0, MAX_SECONDS);
        checkRange("maxDistance", maxDistance, 0.5, MAX_BLOCKS);
        checkRange("stillTolerance", stillTolerance, 0.0, MAX_BLOCKS);
        checkRange("namedChance", namedChance, 0.0, 1.0);
        checkRange("semiNamedChance", semiNamedChance, 0.0, 1.0);
        if (namedChance + semiNamedChance > 1.0) {
            throw new IllegalArgumentException("namedChance + semiNamedChance must not pass 1: " + (namedChance + semiNamedChance));
        }
        checkRange("boneLordMinions", boneLordMinions, 0.0, MAX_MINIONS);
        checkRange("boneLordRegenPercent", boneLordRegenPercent, 0.0, 100.0);
        checkRange("boneLordOutOfCombatSeconds", boneLordOutOfCombatSeconds, 0.0, MAX_SECONDS);
        checkRange("combatWindowSeconds", combatWindowSeconds, 0.0, MAX_SECONDS);
        checkRange("protectionSeconds", protectionSeconds, 0.0, MAX_SECONDS);
        checkRange("restMaxMove", restMaxMove, 0.0, MAX_BLOCKS);
        checkRange("workWindowSeconds", workWindowSeconds, 0.0, MAX_SECONDS);
        checkRange("terminalRange", terminalRange, 1.0, MAX_BLOCKS);
        checkRange("eliteDropMinChance", eliteDropMinChance, 0.0, 1.0);
        checkRange("eliteDropDecay", eliteDropDecay, 0.5, 1.0);
        checkRange("smithLevel", smithLevel, 1.0, MAX_NPC_LEVEL);
        checkRange("chiefLevel", chiefLevel, 1.0, MAX_NPC_LEVEL);
        checkRange("velascoLevel", velascoLevel, 1.0, MAX_NPC_LEVEL);
        checkRange("menciaLevel", menciaLevel, 1.0, MAX_NPC_LEVEL);
    }

    private static void checkRange(String name, double value, double min, double max) {
        // NaN fails both comparisons, so it is rejected too.
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ": " + value);
        }
    }

    /** A copy with a different elite drop floor and decay, e.g. both 1.0 and 1.0 so a test always gets the drop at any level. */
    public RuleBalance withEliteDrop(double minChance, double decay) {
        return new RuleBalance(connectSeconds, logoutSeconds, dangerSeconds, maxDistance, stillTolerance, namedChance,
                semiNamedChance, boneLordMinions, boneLordRegenPercent, boneLordOutOfCombatSeconds, combatWindowSeconds,
                protectionSeconds, restMaxMove, workWindowSeconds, terminalRange, minChance, decay, smithLevel, chiefLevel, velascoLevel, menciaLevel);
    }

    /** Seconds to whole ticks (20 per second). */
    public static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    public int connectTicks() {
        return Math.max(1, ticks(connectSeconds));
    }

    public int logoutTicks() {
        return Math.max(1, ticks(logoutSeconds));
    }

    public int dangerTicks() {
        return ticks(dangerSeconds);
    }

    public int boneLordOutOfCombatTicks() {
        return ticks(boneLordOutOfCombatSeconds);
    }

    public int combatWindowTicks() {
        return ticks(combatWindowSeconds);
    }

    public int protectionTicks() {
        return ticks(protectionSeconds);
    }

    public int workWindowTicks() {
        return ticks(workWindowSeconds);
    }

    /** Set only inside {@link #withOverride}; read by {@link #current()} before the config. */
    private static RuleBalance override;

    /** The active server config, or the defaults before a world is loaded. */
    public static RuleBalance current() {
        if (override != null) {
            return override;
        }
        return Config.isLoaded() ? Config.ruleBalance() : DEFAULT;
    }

    /** For game tests: {@link #current()} returns {@code balance} while {@code action} runs, then the config again. */
    public static void withOverride(RuleBalance balance, Runnable action) {
        RuleBalance previous = override;
        override = balance;
        try {
            action.run();
        } finally {
            override = previous;
        }
    }
}
