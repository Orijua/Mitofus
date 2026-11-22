package com.orijua.mitofus.feature.skill;

/**
 * Pure rules of the skill mastery (own design, see docs Fase5_decisiones_y_plan): a skill grows with successful uses,
 * 20 levels in 4 ranks of 5. Going from level n to n+1 takes {@code 100 * 1.25^(n-1)} uses.
 */
public final class MasteryRules {
    public static final int MAX_LEVEL = 20;
    public static final int LEVELS_PER_RANK = 5;
    public static final int RANKS = MAX_LEVEL / LEVELS_PER_RANK;
    private static final double FIRST_STEP = 100;
    private static final double GROWTH = 1.25;

    private MasteryRules() {
    }

    /** Uses needed to go from {@code level} to the next one. */
    public static int usesToAdvance(int level) {
        return (int) Math.round(FIRST_STEP * Math.pow(GROWTH, Math.max(1, level) - 1));
    }

    /** Total uses at which a skill reaches {@code level} (level 1 needs none). */
    public static int usesToReach(int level) {
        int total = 0;
        for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) {
            total += usesToAdvance(l);
        }
        return total;
    }

    /** The level (1 to {@link #MAX_LEVEL}) of a skill used {@code uses} times with success. */
    public static int level(int uses) {
        int level = 1;
        int remaining = Math.max(0, uses);
        while (level < MAX_LEVEL && remaining >= usesToAdvance(level)) {
            remaining -= usesToAdvance(level);
            level++;
        }
        return level;
    }

    /** The rank of a level, 0 (Beginner) to 3 (Master). */
    public static int rank(int level) {
        return (Math.min(Math.max(level, 1), MAX_LEVEL) - 1) / LEVELS_PER_RANK;
    }

    /** The level inside its rank, 1 to 5. */
    public static int levelInRank(int level) {
        return (Math.min(Math.max(level, 1), MAX_LEVEL) - 1) % LEVELS_PER_RANK + 1;
    }
}
