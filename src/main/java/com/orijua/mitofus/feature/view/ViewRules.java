package com.orijua.mitofus.feature.view;

/**
 * Pure rules of View. Here it
 * grows by observing: sneaking (the concentration) while looking at an NPC or a monster for {@link #OBSERVE_TICKS}
 * gives one point, once per target and Minecraft day. The more View, the more an NPC's window shows
 * ({@link Detail}). Own design: the ten seconds, the point per target and day, and the 15/40 thresholds.
 * Own design: aim margin and look grace (fast targets are easier to keep in sight; the time to observe doesn't change).
 */
public final class ViewRules {
    /** Ten seconds of steady observation. */
    public static final int OBSERVE_TICKS = 200;
    /** How far you can study someone. */
    public static final double REACH = 16.0;
    /** View needed for each level of detail. */
    public static final int MEDIUM = 15;
    public static final int HIGH = 40;
    public static final long DAY_TICKS = 24000;
    /** View that halves the time to observe; it never drops under {@link #FASTEST_OBSERVE_TICKS}. */
    public static final int HALVING_VIEW = 50;
    public static final int FASTEST_OBSERVE_TICKS = 40;

    /** Aim margin of a target standing still, in blocks added around its hitbox. */
    public static final double AIM_MARGIN_BASE = 0.25;
    /** Extra aim margin per unit of horizontal speed, in blocks per (block/tick). */
    public static final double AIM_MARGIN_PER_SPEED = 4.0;
    /** Largest aim margin, in blocks. */
    public static final double AIM_MARGIN_MAX = 1.0;
    /** Ticks of look grace for a target standing still. */
    public static final int LOOK_GRACE_BASE_TICKS = 10;
    /** Extra grace ticks per unit of horizontal speed, in ticks per (block/tick). */
    public static final int LOOK_GRACE_TICKS_PER_SPEED = 100;
    /** Largest look grace, in ticks. */
    public static final int LOOK_GRACE_MAX_TICKS = 45;

    /** How much Character Observation reveals. */
    public enum Detail { LOW, MEDIUM, HIGH }

    private ViewRules() {
    }

    public static Detail detail(int view) {
        if (view >= HIGH) {
            return Detail.HIGH;
        }
        return view >= MEDIUM ? Detail.MEDIUM : Detail.LOW;
    }

    /** The View needed for the next detail, or -1 when everything is visible. */
    public static int nextThreshold(int view) {
        return switch (detail(view)) {
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH -> -1;
        };
    }

    /**
     * Ticks of steady looking for a point, from {@code baseTicks} (ten seconds, or a sword of observation's): the more
     * View, the faster the bar fills. 0 View: the whole base; {@link #HALVING_VIEW}: half; never under
     * {@link #FASTEST_OBSERVE_TICKS}.
     */
    public static int observeTicks(int baseTicks, int view) {
        int ticks = (int) Math.round(baseTicks * HALVING_VIEW / (double) (HALVING_VIEW + Math.max(0, view)));
        return Math.max(Math.min(baseTicks, FASTEST_OBSERVE_TICKS), ticks);
    }

    private static double clean(double speed) {
        return Double.isNaN(speed) ? 0.0 : Math.max(0.0, speed);
    }

    /**
     * Blocks added around a target's hitbox when aiming, from its horizontal speed in blocks per tick. Vertical speed
     * doesn't count on purpose: flyers that climb or dive get no extra margin.
     */
    public static double aimMargin(double speed) {
        return Math.min(AIM_MARGIN_MAX, AIM_MARGIN_BASE + AIM_MARGIN_PER_SPEED * clean(speed));
    }

    /**
     * Ticks in a row the progress survives without seeing the target, from its speed in blocks per tick. Invariant:
     * {@code LOOK_GRACE_BASE_TICKS <= result <= LOOK_GRACE_MAX_TICKS}; the extra is capped as a double before rounding so
     * that infinite or huge speeds don't overflow.
     */
    public static int lookGraceTicks(double speed) {
        double extra = Math.min(LOOK_GRACE_MAX_TICKS - LOOK_GRACE_BASE_TICKS, LOOK_GRACE_TICKS_PER_SPEED * clean(speed));
        return LOOK_GRACE_BASE_TICKS + (int) Math.round(extra);
    }

    /** Whether a squared distance is within {@link #REACH} (inclusive). */
    public static boolean inReach(double distanceSqr) {
        return distanceSqr <= REACH * REACH;
    }

    /** Whether the progress is kept after {@code missed} ticks in a row without seeing the target. */
    public static boolean keepsProgress(int missed, int grace) {
        return missed <= grace;
    }

    public static long day(long gameTime) {
        return gameTime / DAY_TICKS;
    }
}
