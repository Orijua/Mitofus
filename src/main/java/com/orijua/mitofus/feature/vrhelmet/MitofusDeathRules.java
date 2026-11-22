package com.orijua.mitofus.feature.vrhelmet;

/**
 * The rules of dying in Mitofus, with no game objects (so they can be unit-tested). The clock is the game time of the
 * level, in ticks: it only advances while the server runs, so a window or a lock does not elapse while the server is closed.
 */
public final class MitofusDeathRules {
    private MitofusDeathRules() {
    }

    /** What a death in Mitofus is: the first of a pair (nothing else happens) or the second (the VR helmet disconnects). */
    public enum Outcome { FIRST, SECOND }

    /**
     * Whether a death at {@code now} is the second within the window after {@code lastDeathAt} (0 = none yet). It is
     * the first if the window is 0 (never disconnects), there was no previous death, the clock went backwards, or the
     * previous death is exactly the window or more ago.
     */
    public static Outcome classify(long lastDeathAt, long now, long windowTicks) {
        if (windowTicks <= 0 || lastDeathAt <= 0 || now < lastDeathAt) {
            return Outcome.FIRST;
        }
        // now >= lastDeathAt > 0, so the difference cannot overflow.
        return now - lastDeathAt >= windowTicks ? Outcome.FIRST : Outcome.SECOND;
    }

    /**
     * The counter after a death. A first death remembers when it was (at least 1, because 0 means "none yet"); a second
     * one asks for the disconnection and starts counting again from zero. The reconnection lock ({@code unlockAt}) is
     * not touched by deaths: it is only set when the disconnection really happens ({@link #lockUntil}).
     */
    public static MitofusDeaths after(MitofusDeaths before, Outcome outcome, long now) {
        return switch (outcome) {
            case FIRST -> new MitofusDeaths(Math.max(now, 1L), false, before.unlockAt());
            case SECOND -> new MitofusDeaths(0L, true, before.unlockAt());
        };
    }

    /**
     * The game time at which the lock after a double-death disconnection ends: {@code now + lockTicks}, or 0 (no lock)
     * if the lock is 0 or less. Saturates instead of overflowing.
     */
    public static long lockUntil(long now, long lockTicks) {
        if (lockTicks <= 0) {
            return 0L;
        }
        long until = now + lockTicks;
        return until < now ? Long.MAX_VALUE : Math.max(until, 1L);
    }

    /**
     * The ticks still to wait before connecting. Three cases: 0 if there is no lock ({@code unlockAt <= 0}) or it has
     * ended ({@code now >= unlockAt}: exactly at the edge it is already unlocked); 0 if the configured lock is 0 or less
     * (no lock configured); otherwise the remaining time cut down to the configured lock ({@code lockTicksMax}). So
     * lowering the configured lock shortens an active lock to the new maximum, and a clock that went backwards (e.g. a
     * restored world) never locks anyone out for longer than the lock itself. The stored {@code unlockAt} is not
     * rewritten: if the operator raises the config again, the remaining time goes back to the original one (never more
     * than what was set).
     */
    public static long lockRemaining(long unlockAt, long now, long lockTicksMax) {
        if (unlockAt <= 0 || now >= unlockAt || lockTicksMax <= 0) {
            return 0L;
        }
        return Math.min(unlockAt - now, lockTicksMax);
    }

    /**
     * {@code ticks} as mm:ss, rounding up to whole seconds (so it never shows 00:00 while still locked). Same text as
     * {@link VrHelmetText#clock}, which it delegates to.
     */
    public static String formatRemaining(long ticks) {
        if (ticks <= 0) {
            return VrHelmetText.clock(0);
        }
        long seconds = ticks / 20L + (ticks % 20L > 0 ? 1L : 0L);
        return VrHelmetText.clock((int) Math.min(seconds, Integer.MAX_VALUE));
    }

    /** Whether the PvP drop happens: {@code roll} is a random number in [0, 1); a chance of 0 never drops, 1 always does. */
    public static boolean pvpDrops(double chance, double roll) {
        return chance > 0.0 && roll < chance;
    }

    /**
     * Which of the filled equipment slots (their indices) drop: {@code count} of them, chosen at random, or all if there
     * are fewer. {@code random} gives each pick as a number in [0, bound).
     */
    public static java.util.List<Integer> pickPvpDrops(java.util.List<Integer> filled, int count, java.util.function.IntUnaryOperator random) {
        java.util.List<Integer> pool = new java.util.ArrayList<>(filled);
        java.util.List<Integer> picked = new java.util.ArrayList<>();
        while (picked.size() < count && !pool.isEmpty()) {
            picked.add(pool.remove(random.applyAsInt(pool.size())));
        }
        return picked;
    }
}
