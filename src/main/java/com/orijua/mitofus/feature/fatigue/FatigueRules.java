package com.orijua.mitofus.feature.fatigue;

import com.orijua.mitofus.core.config.RuleBalance;
import com.orijua.mitofus.core.config.FatigueBalance;

import java.util.OptionalDouble;

/**
 * The rules of the fatigue as pure functions: no Minecraft, no clock. Energy goes from 1 (rested) to 0 (spent).
 * The game feeds them the {@link FatigueBalance} and what the player is doing; the VR helmet tiers feed them
 * {@link SessionModifiers} ({@link SessionModifiers#NEUTRAL} for the basic VR helmet).
 */
public final class FatigueRules {
    /** Never divide by less than this many seconds, however low the configuration is set. */
    private static final double MIN_SECONDS = 1.0;

    // Fixed numbers, not configuration (like the logout's): the drain counts as combat for 10 seconds after damage given or
    // taken, a player who moved less than half a block in the last second is resting, and a forced disconnection is followed
    // by 10 seconds of protection when the player connects again.
    public static final int COMBAT_WINDOW_TICKS = 200;
    public static final int PROTECTION_TICKS = 200;
    public static final double REST_MAX_MOVE_SQUARED = 0.25;
    /** Breaking a block counts as normal play (not rest) for this long, even standing still: mining is work. */
    public static final int WORK_WINDOW_TICKS = 60;

    private FatigueRules() {
    }

    /** What the player is doing, as far as the drain is concerned. */
    public enum Activity { REST, NORMAL, COMBAT }

    /**
     * How a VR helmet changes the session. {@code durationMul} stretches the session (2 = twice as long), {@code recoveryMul}
     * how fast the real world refills it, {@code restedSeconds} the seconds of "rested" bonus a connection grants (0 = none).
     */
    public record SessionModifiers(double durationMul, double recoveryMul, int restedSeconds) {
        public static final SessionModifiers NEUTRAL = new SessionModifiers(1.0, 1.0, 0);
    }

    /** Whether damage was dealt or taken within the combat window. A tick in the future counts as combat. */
    public static boolean inCombat(long nowTick, java.util.OptionalLong lastCombatTick) {
        return lastCombatTick.isPresent() && nowTick - lastCombatTick.getAsLong() < RuleBalance.current().combatWindowTicks();
    }

    /**
     * What the player is doing: fighting, or working at an anvil or a smelter, costs as combat (forging is hard physical work);
     * standing still is rest unless they broke a block within the work window (mining is work); anything else is normal.
     * A tick in the future counts as recent.
     */
    public static Activity activity(boolean inCombat, boolean atWorkstation, long nowTick, java.util.OptionalLong lastBreakTick,
                                    boolean measured, double distanceSquared) {
        if (inCombat || atWorkstation) {
            return Activity.COMBAT;
        }
        boolean mining = lastBreakTick.isPresent() && nowTick - lastBreakTick.getAsLong() < RuleBalance.current().workWindowTicks();
        return measured && isResting(distanceSquared) && !mining ? Activity.REST : Activity.NORMAL;
    }

    /** Whether a player who moved {@code distanceSquared} blocks (squared) in the last second counts as resting. */
    public static boolean isResting(double distanceSquared) {
        return distanceSquared < RuleBalance.current().restMaxMove() * RuleBalance.current().restMaxMove();
    }

    /** Whether a held player has moved too far from where the hold began: horizontally, or upwards (a jump). Falling is fine. */
    public static boolean driftedTooFar(double dx, double dy, double dz, double leeway) {
        return dx * dx + dz * dz > leeway * leeway || dy > leeway;
    }

    /**
     * Whether the safety protocol holds the player still: during the grace period (graceLeft >= 0) in Mitofus, for a living
     * survival or adventure player. The protection after reconnecting does NOT restrain.
     */
    public static boolean isRestrained(int graceLeft, boolean inMitofus, boolean survivalOrAdventure, boolean alive) {
        return graceLeft >= 0 && inMitofus && survivalOrAdventure && alive;
    }

    /**
     * Whether the client closes the screen it has open (like pressing Escape) while the player is held still: only when they are
     * restrained and the screen is one of those that close. System screens (chat, pause, death, connecting, options...) are
     * never touched. Without a screen there is nothing to close.
     */
    public static boolean shouldCloseScreen(boolean restrained, boolean screenOpen, boolean protectedScreen) {
        return restrained && screenOpen && !protectedScreen;
    }

    /** Root package of the mod's classes; its own screens are recognised by it. */
    public static final String MOD_PACKAGE = "com.orijua.mitofus.";

    /**
     * Whether a screen is left open while the player is held still: only containers (the inventory included) and the mod's
     * own screens (class name inside {@link #MOD_PACKAGE}) close; anything else (chat, pause, system) is protected.
     * A missing or empty name is protected.
     */
    public static boolean isProtectedScreen(boolean isContainerScreen, String className) {
        if (isContainerScreen) {
            return false;
        }
        return className == null || !className.startsWith(MOD_PACKAGE);
    }

    public static double clamp(double energy) {
        return Double.isNaN(energy) ? 0.0 : Math.max(0.0, Math.min(1.0, energy));
    }

    private static double activityMultiplier(FatigueBalance b, Activity activity) {
        return switch (activity) {
            case REST -> b.restMultiplier();
            case NORMAL -> 1.0;
            case COMBAT -> b.combatMultiplier();
        };
    }

    /** Energy lost per real second while in Mitofus; 0 when the fatigue is off. */
    public static double drainPerSecond(FatigueBalance b, Activity activity, SessionModifiers m) {
        if (!b.enabled()) {
            return 0.0;
        }
        double seconds = Math.max(b.sessionMinutes() * 60.0, MIN_SECONDS);
        return activityMultiplier(b, activity) / (seconds * Math.max(m.durationMul(), 1e-6));
    }

    /** Energy gained per real second while resting in the real world. */
    public static double recoverPerSecond(FatigueBalance b, SessionModifiers m) {
        double seconds = Math.max(b.recoveryMinutes() * 60.0, MIN_SECONDS);
        return Math.max(m.recoveryMul(), 0.0) / seconds;
    }

    /** The energy after {@code seconds} of {@code activity} in Mitofus. */
    public static double afterDrain(FatigueBalance b, double energy, Activity activity, SessionModifiers m, double seconds) {
        return clamp(energy - drainPerSecond(b, activity, m) * seconds);
    }

    /** The energy after {@code seconds} of resting in the real world. */
    public static double afterRecovery(FatigueBalance b, double energy, SessionModifiers m, double seconds) {
        return clamp(energy + recoverPerSecond(b, m) * seconds);
    }

    /** The energy after forging one item: a fixed number of seconds of normal play. */
    public static double afterForge(FatigueBalance b, double energy, SessionModifiers m) {
        return clamp(energy - drainPerSecond(b, Activity.NORMAL, m) * b.forgeCostSeconds());
    }

    /**
     * The most urgent warning threshold the energy crossed going down from {@code before} to {@code after}, if any:
     * a threshold {@code t} is crossed when {@code before > t >= after}. Each one therefore fires once, without any
     * flag to save, and a big jump (a forge) reports only the lowest one it passed.
     */
    public static OptionalDouble crossedWarning(double before, double after, java.util.List<Double> warnAt) {
        double lowest = Double.NaN;
        for (double t : warnAt) {
            // The energy is saved as a float, so the thresholds are compared as floats: 0.35 is the same number on both sides.
            float threshold = (float) t;
            if ((float) before > threshold && (float) after <= threshold && (Double.isNaN(lowest) || t < lowest)) {
                lowest = t;
            }
        }
        return Double.isNaN(lowest) ? OptionalDouble.empty() : OptionalDouble.of(lowest);
    }

    /** How worrying the energy is, for the colour of the bars (HUD and terminal). */
    public enum Level { NORMAL, LOW, CRITICAL }

    /**
     * The level of an energy against the warning thresholds, as {@link FatigueBalance#warnAt()} gives them (descending:
     * the first is "low", the second "critical"; 0.25 and 0.10 when missing). A threshold counts when the energy is at or below it.
     * Energy and thresholds are compared as floats, like the saved energy; a NaN energy counts as empty (CRITICAL).
     */
    public static Level level(double energy, java.util.List<Double> warnAt) {
        float e = (float) clamp(energy);
        float low = (float) (warnAt.isEmpty() ? 0.25 : warnAt.get(0));
        float critical = (float) (warnAt.size() < 2 ? 0.10 : warnAt.get(1));
        return e <= critical ? Level.CRITICAL : e <= low ? Level.LOW : Level.NORMAL;
    }

    /** The energy as the player reads it, 0 to 100; every screen and message uses it. */
    public static int percent(double energy) {
        return (int) Math.round(clamp(energy) * 100);
    }

    /**
     * The level of the energy as the player reads it: it is computed on the shown percentage, so the same number always
     * has the same color (the color follows the number, not the exact energy). Only paints: warnings and connecting
     * use the exact energy.
     */
    public static Level shownLevel(double energy, java.util.List<Double> warnAt) {
        return level(percent(energy) / 100.0, warnAt);
    }

    /** Whether the VR helmet lets the player connect (compared as floats, like the saved energy). With the fatigue off, always. */
    public static boolean canConnect(FatigueBalance b, double energy) {
        return !b.enabled() || (float) energy >= (float) b.minEnergyToConnect();
    }

    /** Whole minutes of play left at this activity, rounded up; empty when nothing drains (off, or a zero multiplier). */
    public static OptionalDouble minutesLeft(FatigueBalance b, double energy, Activity activity, SessionModifiers m) {
        double drain = drainPerSecond(b, activity, m);
        if (drain <= 0.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.ceil(clamp(energy) / drain / 60.0));
    }
}
