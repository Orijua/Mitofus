package com.orijua.mitofus.feature.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewRulesTest {
    @Test
    void moreViewRevealsMore() {
        assertEquals(ViewRules.Detail.LOW, ViewRules.detail(-5));
        assertEquals(ViewRules.Detail.LOW, ViewRules.detail(0));
        assertEquals(ViewRules.Detail.LOW, ViewRules.detail(14));
        assertEquals(ViewRules.Detail.MEDIUM, ViewRules.detail(15));
        assertEquals(ViewRules.Detail.MEDIUM, ViewRules.detail(39));
        assertEquals(ViewRules.Detail.HIGH, ViewRules.detail(40));
    }

    @Test
    void theNextThresholdIsWhatTheWindowAsksFor() {
        assertEquals(15, ViewRules.nextThreshold(0));
        assertEquals(15, ViewRules.nextThreshold(3));
        assertEquals(15, ViewRules.nextThreshold(14));
        assertEquals(40, ViewRules.nextThreshold(15));
        assertEquals(40, ViewRules.nextThreshold(20));
        assertEquals(40, ViewRules.nextThreshold(39));
        assertEquals(-1, ViewRules.nextThreshold(40));
    }

    @Test
    void moreViewFillsTheBarFaster() {
        assertEquals(200, ViewRules.observeTicks(200, 0), "ten seconds with none");
        assertEquals(100, ViewRules.observeTicks(200, 50), "half at 50");
        assertEquals(50, ViewRules.observeTicks(100, 50), "a sword's time shortens too");
        assertEquals(40, ViewRules.observeTicks(200, 10_000), "never under two seconds");
        assertEquals(30, ViewRules.observeTicks(30, 500), "a base already under the floor stays");
        assertEquals(200, ViewRules.observeTicks(200, -10), "negative View counts as none");
        assertEquals(0, ViewRules.observeTicks(0, 0), "a zero base stays zero");
        assertEquals(98, ViewRules.observeTicks(100, 1), "the Commander's sword at 1 View");
        assertEquals(137, ViewRules.observeTicks(140, 1), "the Ruler's sword at 1 View");
    }

    @Test
    void aimMarginGrowsWithSpeedUpToACap() {
        assertEquals(ViewRules.AIM_MARGIN_BASE, ViewRules.aimMargin(0.0), 1e-9);
        assertEquals(ViewRules.AIM_MARGIN_BASE, ViewRules.aimMargin(-1.0), 1e-9);
        assertEquals(ViewRules.AIM_MARGIN_BASE, ViewRules.aimMargin(Double.NaN), 1e-9);
        assertTrue(ViewRules.aimMargin(0.1) > ViewRules.aimMargin(0.05));
        assertTrue(ViewRules.aimMargin(0.05) > ViewRules.aimMargin(0.0));
        assertEquals(ViewRules.AIM_MARGIN_MAX, ViewRules.aimMargin(10.0), 1e-9);
        assertEquals(ViewRules.AIM_MARGIN_BASE + ViewRules.AIM_MARGIN_PER_SPEED * 0.1, ViewRules.aimMargin(0.1), 1e-9);
        double cap = (ViewRules.AIM_MARGIN_MAX - ViewRules.AIM_MARGIN_BASE) / ViewRules.AIM_MARGIN_PER_SPEED;
        assertEquals(ViewRules.AIM_MARGIN_MAX, ViewRules.aimMargin(cap), 1e-9);
        assertTrue(ViewRules.aimMargin(cap * 0.9) < ViewRules.AIM_MARGIN_MAX);
        assertEquals(ViewRules.AIM_MARGIN_MAX, ViewRules.aimMargin(Double.POSITIVE_INFINITY), 1e-9);
    }

    @Test
    void lookGraceGrowsWithSpeedUpToACap() {
        assertEquals(ViewRules.LOOK_GRACE_BASE_TICKS, ViewRules.lookGraceTicks(0.0));
        assertTrue(ViewRules.lookGraceTicks(0.3) > ViewRules.lookGraceTicks(0.05));
        assertEquals(ViewRules.LOOK_GRACE_MAX_TICKS, ViewRules.lookGraceTicks(10.0));
        assertEquals(ViewRules.lookGraceTicks(0.0), ViewRules.lookGraceTicks(-1.0));
        assertEquals(ViewRules.lookGraceTicks(0.0), ViewRules.lookGraceTicks(Double.NaN));
        assertEquals(ViewRules.LOOK_GRACE_MAX_TICKS, ViewRules.lookGraceTicks(Double.POSITIVE_INFINITY));
        assertEquals(ViewRules.LOOK_GRACE_MAX_TICKS, ViewRules.lookGraceTicks(Double.MAX_VALUE));
        assertEquals(ViewRules.LOOK_GRACE_MAX_TICKS, ViewRules.lookGraceTicks(1e12));
        double cap = (ViewRules.LOOK_GRACE_MAX_TICKS - ViewRules.LOOK_GRACE_BASE_TICKS)
                / (double) ViewRules.LOOK_GRACE_TICKS_PER_SPEED;
        assertEquals(ViewRules.LOOK_GRACE_MAX_TICKS, ViewRules.lookGraceTicks(cap));
        assertEquals(ViewRules.LOOK_GRACE_MAX_TICKS - 1, ViewRules.lookGraceTicks(cap - 0.006));
        double[] speeds = {0, 1e-3, 0.05, 0.1, 0.2, 1, 1e9, 1e20, Double.POSITIVE_INFINITY};
        for (double speed : speeds) {
            int grace = ViewRules.lookGraceTicks(speed);
            assertTrue(grace >= ViewRules.LOOK_GRACE_BASE_TICKS && grace <= ViewRules.LOOK_GRACE_MAX_TICKS,
                    "grace out of range for speed " + speed + ": " + grace);
        }
    }

    @Test
    void reachIsInclusive() {
        assertTrue(ViewRules.inReach(ViewRules.REACH * ViewRules.REACH));
        assertFalse(ViewRules.inReach(Math.nextUp(ViewRules.REACH * ViewRules.REACH)));
    }

    @Test
    void graceKeepsProgressUntilItRunsOut() {
        int g = 10;
        assertTrue(ViewRules.keepsProgress(g, g));
        assertFalse(ViewRules.keepsProgress(g + 1, g));
        assertFalse(ViewRules.keepsProgress(1, 0));
        assertTrue(ViewRules.keepsProgress(0, 0));
    }

    @Test
    void theTimeToObserveDoesNotChange() {
        assertEquals(200, ViewRules.OBSERVE_TICKS);
    }

    @Test
    void aDayIsAMinecraftDay() {
        assertEquals(0, ViewRules.day(23_999));
        assertEquals(1, ViewRules.day(24_000));
    }
}
