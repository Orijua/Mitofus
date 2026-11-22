package com.orijua.mitofus.feature.vrhelmet;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.orijua.mitofus.feature.vrhelmet.MitofusDeathRules.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MitofusDeathRulesTest {
    private static final long WINDOW = 12_000L;

    @Test
    void theFirstDeathEverIsFirst() {
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(0L, 5_000L, WINDOW));
    }

    @Test
    void justInsideTheWindowIsSecond() {
        assertEquals(Outcome.SECOND, MitofusDeathRules.classify(1_000L, 1_000L + WINDOW - 1, WINDOW));
        assertEquals(Outcome.SECOND, MitofusDeathRules.classify(1_000L, 1_000L, WINDOW));
    }

    @Test
    void exactlyTheWindowIsFirst() {
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(1_000L, 1_000L + WINDOW, WINDOW));
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(1_000L, 1_000L + WINDOW + 1, WINDOW));
    }

    @Test
    void aClockThatWentBackwardsIsFirst() {
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(5_000L, 4_999L, WINDOW));
    }

    @Test
    void aZeroWindowNeverDisconnects() {
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(1_000L, 1_000L, 0L));
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(1_000L, 1_001L, 0L));
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(1_000L, 1_001L, -5L));
    }

    @Test
    void extremesDoNotOverflow() {
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(1L, Long.MAX_VALUE, WINDOW));
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(Long.MAX_VALUE, Long.MIN_VALUE, WINDOW));
        assertEquals(Outcome.SECOND, MitofusDeathRules.classify(Long.MAX_VALUE - 1, Long.MAX_VALUE, WINDOW));
        assertEquals(Outcome.SECOND, MitofusDeathRules.classify(1L, 2L, Long.MAX_VALUE));
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(0L, Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void aFirstDeathRemembersWhenItWasAndAsksForNothing() {
        assertEquals(new MitofusDeaths(777L, false, 0L), MitofusDeathRules.after(MitofusDeaths.EMPTY, Outcome.FIRST, 777L));
    }

    @Test
    void aFirstDeathAtTickZeroStillCounts() {
        // 0 means "none yet", so the very first tick is stored as 1.
        assertEquals(new MitofusDeaths(1L, false, 0L), MitofusDeathRules.after(MitofusDeaths.EMPTY, Outcome.FIRST, 0L));
        assertEquals(Outcome.SECOND, MitofusDeathRules.classify(1L, 10L, WINDOW));
    }

    @Test
    void aSecondDeathAsksForTheDisconnectionAndStartsOver() {
        MitofusDeaths after = MitofusDeathRules.after(new MitofusDeaths(100L, false, 0L), Outcome.SECOND, 200L);
        assertEquals(new MitofusDeaths(0L, true, 0L), after);
        assertEquals(Outcome.FIRST, MitofusDeathRules.classify(after.lastDeathAt(), 201L, WINDOW));
    }

    @Test
    void deathsDoNotTouchTheLock() {
        assertEquals(500L, MitofusDeathRules.after(new MitofusDeaths(100L, false, 500L), Outcome.SECOND, 200L).unlockAt());
        assertEquals(500L, MitofusDeathRules.after(new MitofusDeaths(0L, false, 500L), Outcome.FIRST, 200L).unlockAt());
        assertEquals(500L, new MitofusDeaths(0L, true, 500L).withoutPending().unlockAt());
    }

    @Test
    void theLockStartsAtDisconnectionPlusTheLockTicks() {
        assertEquals(15_000L, MitofusDeathRules.lockUntil(3_000L, WINDOW));
        assertEquals(WINDOW, MitofusDeathRules.lockUntil(0L, WINDOW));
        assertEquals(0L, MitofusDeathRules.lockUntil(3_000L, 0L));
        assertEquals(Long.MAX_VALUE, MitofusDeathRules.lockUntil(Long.MAX_VALUE, WINDOW));
    }

    @Test
    void anActiveLockCountsDown() {
        assertEquals(WINDOW, MitofusDeathRules.lockRemaining(15_000L, 3_000L, WINDOW));
        assertEquals(1L, MitofusDeathRules.lockRemaining(15_000L, 14_999L, WINDOW));
    }

    @Test
    void justAtTheEdgeTheLockIsOver() {
        assertEquals(0L, MitofusDeathRules.lockRemaining(15_000L, 15_000L, WINDOW));
        assertEquals(0L, MitofusDeathRules.lockRemaining(15_000L, 15_001L, WINDOW));
    }

    @Test
    void noLockMeansNothingToWait() {
        assertEquals(0L, MitofusDeathRules.lockRemaining(0L, 100L, WINDOW));
        assertEquals(0L, MitofusDeathRules.lockRemaining(-5L, 100L, WINDOW));
    }

    @Test
    void aLockLongerThanTheConfiguredOneIsCutDown() {
        // 12,001 left with a lock of 12,000: cut down to the lock.
        assertEquals(WINDOW, MitofusDeathRules.lockRemaining(15_001L, 3_000L, WINDOW));
        assertEquals(WINDOW, MitofusDeathRules.lockRemaining(15_000L, 3_000L, WINDOW));
        // A lock turned off in the config frees everyone.
        assertEquals(0L, MitofusDeathRules.lockRemaining(15_000L, 3_000L, 0L));
    }

    @Test
    void aLoweredLockConfigCutsAnActiveLockDown() {
        assertEquals(6_000L, MitofusDeathRules.lockRemaining(15_000L, 3_000L, 6_000L));
        assertEquals(3_000L, MitofusDeathRules.lockRemaining(15_000L, 12_000L, 6_000L));
    }

    @Test
    void theFormatNeverOverflowsAndMatchesTheClock() {
        String huge = MitofusDeathRules.formatRemaining(Long.MAX_VALUE);
        assertEquals(false, huge.startsWith("-"));
        assertEquals(VrHelmetText.clock(65), MitofusDeathRules.formatRemaining(1_300L));
    }

    @Test
    void theRemainingTimeIsFormattedAsMinutesAndSeconds() {
        assertEquals("10:00", MitofusDeathRules.formatRemaining(12_000L));
        assertEquals("00:01", MitofusDeathRules.formatRemaining(1L));
        assertEquals("00:01", MitofusDeathRules.formatRemaining(20L));
        assertEquals("00:02", MitofusDeathRules.formatRemaining(21L));
        assertEquals("00:00", MitofusDeathRules.formatRemaining(0L));
        assertEquals("01:05", MitofusDeathRules.formatRemaining(1_300L));
    }

    @Test
    void anOldJsonWithoutTheLockReadsAsNoLock() {
        JsonObject old = new JsonObject();
        old.addProperty("last_death_at", 55L);
        old.addProperty("pending_disconnect", true);
        assertEquals(new MitofusDeaths(55L, true, 0L), MitofusDeaths.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow());
    }

    @Test
    void theCounterSurvivesAJsonRoundTrip() {
        MitofusDeaths original = new MitofusDeaths(123_456_789_012L, true, 987_654L);
        var json = MitofusDeaths.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, MitofusDeaths.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void anEmptyJsonIsTheEmptyCounter() {
        assertEquals(MitofusDeaths.EMPTY, MitofusDeaths.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
    }

    @Test
    void thePvpDropFollowsTheChance() {
        assertEquals(false, MitofusDeathRules.pvpDrops(0.0, 0.0), "0 never drops");
        assertEquals(true, MitofusDeathRules.pvpDrops(1.0, 0.999999), "1 always drops");
        assertEquals(true, MitofusDeathRules.pvpDrops(0.05, 0.049));
        assertEquals(false, MitofusDeathRules.pvpDrops(0.05, 0.05));
    }

    @Test
    void thePvpDropPicksTheCountFromTheFilledSlotsWithoutRepeating() {
        var picked = MitofusDeathRules.pickPvpDrops(java.util.List.of(0, 2, 3), 2, bound -> 0);
        assertEquals(java.util.List.of(0, 2), picked);
        assertEquals(java.util.List.of(0, 2, 3), MitofusDeathRules.pickPvpDrops(java.util.List.of(0, 2, 3), 5, bound -> bound - 1).stream().sorted().toList());
        assertEquals(java.util.List.of(), MitofusDeathRules.pickPvpDrops(java.util.List.of(), 1, bound -> 0));
        assertEquals(java.util.List.of(), MitofusDeathRules.pickPvpDrops(java.util.List.of(1), 0, bound -> 0));
    }
}
