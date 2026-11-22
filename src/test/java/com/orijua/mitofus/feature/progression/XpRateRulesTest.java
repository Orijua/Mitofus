package com.orijua.mitofus.feature.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XpRateRulesTest {
    private static XpRate ticks(XpRate rate, int seconds) {
        for (int i = 0; i < seconds; i++) {
            rate = XpRateRules.tick(rate);
        }
        return rate;
    }

    @Test
    void fiftyNineSecondsDoNotCloseTheMinuteAndTheSixtiethDoes() {
        XpRate rate = XpRateRules.record(XpRate.EMPTY, 7);
        XpRate at59 = ticks(rate, 59);
        assertTrue(at59.minutes().isEmpty());
        assertEquals(59, at59.currentSeconds());
        assertEquals(7, at59.currentXp());
        XpRate at60 = XpRateRules.tick(at59);
        assertEquals(List.of(7), at60.minutes());
        assertEquals(0, at60.currentXp());
        assertEquals(0, at60.currentSeconds());
    }

    @Test
    void theListKeepsFourteenMinutesAndDropsTheOldest() {
        XpRate rate = XpRate.EMPTY;
        for (int minute = 1; minute <= 20; minute++) {
            rate = ticks(XpRateRules.record(rate, minute), 60);
        }
        assertEquals(14, rate.minutes().size());
        assertEquals(20, rate.minutes().get(0));
        assertEquals(7, rate.minutes().get(13));
    }

    @Test
    void recordSaturatesAndIgnoresNonPositive() {
        XpRate rate = XpRateRules.record(XpRate.EMPTY, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, XpRateRules.record(rate, 100).currentXp());
        assertEquals(XpRate.EMPTY, XpRateRules.record(XpRate.EMPTY, 0));
        assertEquals(XpRate.EMPTY, XpRateRules.record(XpRate.EMPTY, -5));
    }

    @Test
    void noRateBeforeAMinuteIsMeasured() {
        XpRate rate = ticks(XpRateRules.record(XpRate.EMPTY, 10), 59);
        assertEquals(OptionalLong.empty(), XpRateRules.xpPerHour(rate));
        assertEquals(OptionalLong.of(600), XpRateRules.xpPerHour(XpRateRules.tick(rate)));
    }

    @Test
    void theXpOfFifteenMinutesAgoNoLongerCounts() {
        XpRate rate = XpRateRules.record(XpRate.EMPTY, 1000);
        rate = ticks(rate, 60 * 15);
        // 15 minutes later the 1000 XP is out of the window: 14 full minutes plus the one in progress.
        assertEquals(0, XpRateRules.totalXp(rate));
        assertEquals(OptionalLong.of(0), XpRateRules.xpPerHour(rate));
    }

    @Test
    void stillCountsAtFourteenMinutesFiftyNineSeconds() {
        XpRate rate = ticks(XpRateRules.record(XpRate.EMPTY, 1000), 899);
        assertEquals(1000, XpRateRules.totalXp(rate));
    }

    @Test
    void xpPerHourWithAMixedSample() {
        XpRate rate = new XpRate(List.of(10, 0, 5), 7, 30);
        assertEquals(OptionalLong.of(377), XpRateRules.xpPerHour(rate));
    }

    @Test
    void zeroXpOverFiveMinutesIsZero() {
        assertEquals(OptionalLong.of(0), XpRateRules.xpPerHour(ticks(XpRate.EMPTY, 300)));
    }

    @Test
    void etaNeedsAPaceAndSomethingMissing() {
        assertEquals(OptionalLong.empty(), XpRateRules.etaMinutes(30, 0));
        assertEquals(OptionalLong.empty(), XpRateRules.etaMinutes(0, 600));
        assertEquals(OptionalLong.empty(), XpRateRules.etaMinutes(-1, 600));
        assertEquals(OptionalLong.empty(), XpRateRules.etaMinutes(Integer.MAX_VALUE, 1));
    }

    @Test
    void etaRoundsUpWithAMinimumOfOne() {
        assertEquals(OptionalLong.of(3), XpRateRules.etaMinutes(30, 600));
        assertEquals(OptionalLong.of(4), XpRateRules.etaMinutes(31, 600));
        assertEquals(OptionalLong.of(1), XpRateRules.etaMinutes(1, 6000));
    }

    @Test
    void etaIsHiddenOverNinetyNineHours() {
        assertEquals(OptionalLong.of(5940), XpRateRules.etaMinutes(5940, 60));
        assertEquals(OptionalLong.empty(), XpRateRules.etaMinutes(5941, 60));
    }

    @Test
    void theConstructorClampsANegativeMinuteToZero() {
        XpRate rate = new XpRate(List.of(-1, 2), 0, 0);
        assertEquals(0, rate.minutes().get(0));
    }

    @Test
    void theConstructorKeepsTheFourteenMostRecentMinutes() {
        XpRate rate = new XpRate(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), 0, 0);
        assertEquals(14, rate.minutes().size());
        assertEquals(1, rate.minutes().get(0));
        assertEquals(14, rate.minutes().get(13));
        assertTrue(!rate.minutes().contains(15) && !rate.minutes().contains(16));
    }

    @Test
    void theConstructorBoundsTheCurrentMinute() {
        XpRate rate = new XpRate(List.of(), -5, 99);
        assertEquals(0, rate.currentXp());
        assertEquals(59, rate.currentSeconds());
    }
}
