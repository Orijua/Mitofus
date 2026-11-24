package com.orijua.mitofus.feature.vrhelmet;

import com.mojang.serialization.JsonOps;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.feature.fatigue.FatigueRules;
import com.orijua.mitofus.feature.fatigue.FatigueRules.SessionModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VrHelmetRulesTest {
    private static final BlockPos COVADONGA = new BlockPos(10, 70, 20);
    private static final BlockPos SAVED = new BlockPos(-300, 80, 512);

    @Test
    void theFirstConnectionLandsInCovadonga() {
        VrHelmetRules.Spawn spawn = VrHelmetRules.spawn(Optional.empty(), Optional.of(COVADONGA)).orElseThrow();
        assertEquals(COVADONGA, spawn.pos());
        assertEquals(VrHelmetRules.Source.COVADONGA, spawn.source());
    }

    @Test
    void laterConnectionsStartWhereYouLastDisconnected() {
        VrHelmetRules.Spawn spawn = VrHelmetRules.spawn(Optional.of(SAVED), Optional.of(COVADONGA)).orElseThrow();
        assertEquals(SAVED, spawn.pos());
        assertEquals(VrHelmetRules.Source.LAST_SAVE, spawn.source());
    }

    @Test
    void aSavedSpotWorksEvenWithoutCovadonga() {
        assertEquals(VrHelmetRules.Source.LAST_SAVE, VrHelmetRules.spawn(Optional.of(SAVED), Optional.empty()).orElseThrow().source());
    }

    @Test
    void withNeitherThereIsNowhereToAppear() {
        assertTrue(VrHelmetRules.spawn(Optional.empty(), Optional.empty()).isEmpty());
    }

    @Test
    void inReachStopsAtMaxDistance() {
        assertTrue(VrHelmetRules.inReach(VrHelmetRules.MAX_DISTANCE * VrHelmetRules.MAX_DISTANCE));
        assertFalse(VrHelmetRules.inReach(VrHelmetRules.MAX_DISTANCE * VrHelmetRules.MAX_DISTANCE + 0.01));
    }

    @Test
    void stayedStillAllowsTheToleranceAndNothingMore() {
        assertTrue(VrHelmetRules.stayedStill(0.0));
        double limit = VrHelmetRules.STILL_TOLERANCE * VrHelmetRules.STILL_TOLERANCE;
        assertTrue(VrHelmetRules.stayedStill(limit));
        assertFalse(VrHelmetRules.stayedStill(Math.nextUp(limit)));
        assertFalse(VrHelmetRules.stayedStill(0.36));
        assertFalse(VrHelmetRules.stayedStill(Double.NaN));
        assertFalse(VrHelmetRules.stayedStill(Double.POSITIVE_INFINITY));
    }

    @Test
    void stillToleranceIsSmallerThanTheReach() {
        assertTrue(VrHelmetRules.STILL_TOLERANCE < VrHelmetRules.MAX_DISTANCE);
    }

    @Test
    void stateRoundTripsThroughItsCodec() {
        GlobalPos vrhelmet = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 2));
        GlobalPos exit = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 3));
        VrHelmetState connected = VrHelmetState.EMPTY.connected(vrhelmet, exit);
        assertEquals(connected, roundTrip(connected));
        assertEquals(connected.disconnected(SAVED), roundTrip(connected.disconnected(SAVED)));
        assertEquals(VrHelmetState.EMPTY, roundTrip(VrHelmetState.EMPTY));
    }

    @Test
    void disconnectingSavesTheSpotAndBringsTheBodyBack() {
        GlobalPos vrhelmet = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 2));
        GlobalPos exit = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 3));
        VrHelmetState back = VrHelmetState.EMPTY.connected(vrhelmet, exit).disconnected(SAVED);
        assertEquals(Optional.of(SAVED), back.lastSave());
        assertTrue(back.vrhelmet().isEmpty() && back.exit().isEmpty(), "the body is back in the real world");
    }

    @Test
    void connectingKeepsTheLastSave() {
        VrHelmetState state = VrHelmetState.EMPTY.disconnected(SAVED)
                .connected(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO.above()));
        assertEquals(Optional.of(SAVED), state.lastSave());
    }

    @Test
    void withoutBodyKeepsTheLastSave() {
        GlobalPos vrhelmet = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        GlobalPos exit = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO.above());
        VrHelmetState freed = VrHelmetState.EMPTY.disconnected(SAVED).connected(vrhelmet, exit).withoutBody();
        assertTrue(freed.vrhelmet().isEmpty() && freed.exit().isEmpty());
        assertEquals(Optional.of(SAVED), freed.lastSave());
        assertEquals(VrHelmetState.EMPTY, VrHelmetState.EMPTY.connected(vrhelmet, exit).withoutBody());
    }

    @Test
    void withoutBodyRoundTripsThroughItsCodec() {
        VrHelmetState freed = VrHelmetState.EMPTY.disconnected(SAVED)
                .connected(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO.above()))
                .withoutBody();
        assertEquals(freed, roundTrip(freed));
    }

    @Test
    void isConnectedFollowsTheVrHelmet() {
        GlobalPos spot = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        assertFalse(VrHelmetState.EMPTY.isConnected());
        assertFalse(VrHelmetState.EMPTY.disconnected(SAVED).isConnected());
        assertTrue(VrHelmetState.EMPTY.connected(spot, spot).isConnected());
    }

    @Test
    void coherenceCoversTheFourCases() {
        GlobalPos spot = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        VrHelmetState connected = VrHelmetState.EMPTY.connected(spot, spot);
        assertEquals(VrHelmetRules.Coherence.OK, VrHelmetRules.coherence(connected, true));
        assertEquals(VrHelmetRules.Coherence.CONNECTED_OUTSIDE, VrHelmetRules.coherence(connected, false));
        assertEquals(VrHelmetRules.Coherence.DISCONNECTED_INSIDE, VrHelmetRules.coherence(VrHelmetState.EMPTY, true));
        assertEquals(VrHelmetRules.Coherence.OK, VrHelmetRules.coherence(VrHelmetState.EMPTY, false));
    }

    @Test
    void theClientScreenFollowsTheServerWait() {
        try {
            ClientVrHelmet.connecting(0, 1_000);
            assertEquals(-1.0f, ClientVrHelmet.fraction(1_000), "nothing connecting");
            ClientVrHelmet.connecting(-5, 1_000);
            assertEquals(-1.0f, ClientVrHelmet.fraction(1_000), "negative ticks mean nothing connecting");
            long duration = VrHelmetRules.CONNECT_TICKS * 50L;
            ClientVrHelmet.connecting(VrHelmetRules.CONNECT_TICKS, 10_000);
            assertEquals(0.0f, ClientVrHelmet.fraction(9_000), 1e-6, "before the start: 0");
            assertEquals(0.0f, ClientVrHelmet.fraction(10_000), 1e-6);
            assertEquals(0.5f, ClientVrHelmet.fraction(10_000 + duration / 2), 1e-3);
            assertEquals(1.0f, ClientVrHelmet.fraction(10_000 + duration + 100), 1e-6);
            assertEquals(1.0f, ClientVrHelmet.fraction(10_000 + duration + 1_000), 1e-6, "last moment the screen stays at 1");
            assertEquals(-1.0f, ClientVrHelmet.fraction(10_000 + duration + 1_001), "a lost packet must not leave the screen up");
        } finally {
            ClientVrHelmet.connecting(0, 0);
        }
    }

    @Test
    void onlyTheTwoCrossingsWithoutPermissionAreBlocked() {
        assertFalse(VrHelmetRules.crossingBlocked(false, false, false), "real to real");
        assertFalse(VrHelmetRules.crossingBlocked(true, true, false), "Mitofus to Mitofus");
        assertTrue(VrHelmetRules.crossingBlocked(false, true, false), "real to Mitofus");
        assertTrue(VrHelmetRules.crossingBlocked(true, false, false), "Mitofus to real");
        assertFalse(VrHelmetRules.crossingBlocked(false, false, true));
        assertFalse(VrHelmetRules.crossingBlocked(true, true, true));
        assertFalse(VrHelmetRules.crossingBlocked(false, true, true), "allowed: the vrhelmet");
        assertFalse(VrHelmetRules.crossingBlocked(true, false, true), "allowed: the vrhelmet");
    }

    private static VrHelmetState roundTrip(VrHelmetState state) {
        var json = VrHelmetState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        return VrHelmetState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static final FatigueBalance F = FatigueBalance.DEFAULT;
    private static final VrHelmetBalance C = VrHelmetBalance.DEFAULT;

    private static double sessionMinutes(VrHelmetTier tier) {
        SessionModifiers m = VrHelmetRules.modifiers(tier, F, C);
        return 1.0 / FatigueRules.drainPerSecond(F, FatigueRules.Activity.NORMAL, m) / 60.0;
    }

    private static double recoveryMinutes(VrHelmetTier tier) {
        SessionModifiers m = VrHelmetRules.modifiers(tier, F, C);
        return 1.0 / FatigueRules.recoverPerSecond(F, m) / 60.0;
    }

    @Test
    void theBasicVrHelmetIsExactlyNeutral() {
        assertEquals(SessionModifiers.NEUTRAL, VrHelmetRules.modifiers(VrHelmetTier.BASIC, F, C));
        VrHelmetBalance odd = new VrHelmetBalance(30, 5, 60, 3, 100, 200, 5, 20, 0.5, 30);
        assertEquals(SessionModifiers.NEUTRAL, VrHelmetRules.modifiers(VrHelmetTier.BASIC, F, odd));
    }

    @Test
    void sessionsLast40And55And75Minutes() {
        assertEquals(40.0, sessionMinutes(VrHelmetTier.BASIC), 1e-9);
        assertEquals(55.0, sessionMinutes(VrHelmetTier.ADVANCED), 1e-9);
        assertEquals(75.0, sessionMinutes(VrHelmetTier.DIAMOND), 1e-9);
    }

    @Test
    void recoveriesTake10And8And6Minutes() {
        assertEquals(10.0, recoveryMinutes(VrHelmetTier.BASIC), 1e-9);
        assertEquals(8.0, recoveryMinutes(VrHelmetTier.ADVANCED), 1e-9);
        assertEquals(6.0, recoveryMinutes(VrHelmetTier.DIAMOND), 1e-9);
    }

    @Test
    void onlyTheDiamondCarriesRestedSeconds() {
        assertEquals(0, VrHelmetRules.modifiers(VrHelmetTier.BASIC, F, C).restedSeconds());
        assertEquals(0, VrHelmetRules.modifiers(VrHelmetTier.ADVANCED, F, C).restedSeconds());
        assertEquals(600, VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C).restedSeconds());
    }

    @Test
    void restedNeedsTheMinimumEnergyComparedAsFloats() {
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C);
        assertEquals(600, VrHelmetRules.restedOnConnect(diamond, 0.95, C));
        assertEquals(600, VrHelmetRules.restedOnConnect(diamond, 0.90, C));
        assertEquals(600, VrHelmetRules.restedOnConnect(diamond, (double) 0.90f, C));
        assertEquals(600, VrHelmetRules.restedOnConnect(diamond, 1.0, C));
        assertEquals(0, VrHelmetRules.restedOnConnect(diamond, 0.8999, C));
        assertEquals(0, VrHelmetRules.restedOnConnect(diamond, 0.85, C));
    }

    @Test
    void otherTiersAndAZeroDurationGiveNoRested() {
        assertEquals(0, VrHelmetRules.restedOnConnect(VrHelmetRules.modifiers(VrHelmetTier.ADVANCED, F, C), 1.0, C));
        assertEquals(0, VrHelmetRules.restedOnConnect(VrHelmetRules.modifiers(VrHelmetTier.BASIC, F, C), 1.0, C));
        VrHelmetBalance off = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 0, 10, 0.90, 30);
        assertEquals(0, VrHelmetRules.restedOnConnect(VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, off), 1.0, off));
    }

    @Test
    void connectingGrantsTheRestedAndOpensTheWait() {
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C);
        assertEquals(new VrHelmetRules.RestedGrant(600, 1800), VrHelmetRules.onConnect(diamond, 1.0, 0, C));
        assertEquals(new VrHelmetRules.RestedGrant(600, 1800), VrHelmetRules.onConnect(diamond, 0.9, -5, C), "a negative wait is none");
    }

    @Test
    void whileTheWaitRunsNothingIsGrantedAndTheWaitIsNotRestarted() {
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C);
        assertEquals(new VrHelmetRules.RestedGrant(0, 1234), VrHelmetRules.onConnect(diamond, 1.0, 1234, C));
        assertEquals(new VrHelmetRules.RestedGrant(0, 1), VrHelmetRules.onConnect(diamond, 1.0, 1, C));
    }

    @Test
    void aConnectionThatGrantsNothingOpensNoWait() {
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C);
        assertEquals(new VrHelmetRules.RestedGrant(0, 0), VrHelmetRules.onConnect(diamond, 0.85, 0, C), "low energy");
        assertEquals(new VrHelmetRules.RestedGrant(0, 0),
                VrHelmetRules.onConnect(VrHelmetRules.modifiers(VrHelmetTier.ADVANCED, F, C), 1.0, 0, C), "advanced");
        VrHelmetBalance off = withRested(0, 0.9);
        assertEquals(new VrHelmetRules.RestedGrant(0, 0), VrHelmetRules.onConnect(VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, off), 1.0, 0, off),
                "restedMinutes 0");
    }

    @Test
    void aWaitOfZeroMinutesAlwaysGrants() {
        VrHelmetBalance noWait = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.90, 0);
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, noWait);
        assertEquals(new VrHelmetRules.RestedGrant(600, 0), VrHelmetRules.onConnect(diamond, 1.0, 0, noWait));
        assertEquals(new VrHelmetRules.RestedGrant(600, 0), VrHelmetRules.onConnect(diamond, 1.0, VrHelmetRules.cooldownLeft(1800, noWait), noWait));
    }

    @Test
    void theWaitLeftIsClampedToTheConfiguredWait() {
        VrHelmetBalance fiveMinutes = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.90, 5);
        VrHelmetBalance noWait = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.90, 0);
        assertEquals(300, VrHelmetRules.cooldownLeft(1800, fiveMinutes));
        assertEquals(120, VrHelmetRules.cooldownLeft(120, fiveMinutes));
        assertEquals(0, VrHelmetRules.cooldownLeft(-7, C));
        assertEquals(0, VrHelmetRules.cooldownLeft(1800, noWait));
        assertEquals(1800, VrHelmetRules.cooldownLeft(1800, C));
    }

    @Test
    void theWaitCountsDownToZeroAndStops() {
        assertEquals(1799, VrHelmetRules.tickDown(1800));
        assertEquals(0, VrHelmetRules.tickDown(1));
        assertEquals(0, VrHelmetRules.tickDown(0));
        assertEquals(0, VrHelmetRules.tickDown(-3));
    }

    @Test
    void theWaitRulesSurviveTheExtremesOfAnInt() {
        assertEquals(1800, VrHelmetRules.cooldownLeft(Integer.MAX_VALUE, C), "a huge saved wait is cut to the configured one");
        assertEquals(0, VrHelmetRules.cooldownLeft(Integer.MIN_VALUE, C), "a hugely negative one is none");
        assertEquals(Integer.MAX_VALUE - 1, VrHelmetRules.tickDown(Integer.MAX_VALUE));
        assertEquals(0, VrHelmetRules.tickDown(Integer.MIN_VALUE), "no wrap-around to a huge wait");
        VrHelmetBalance longest = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.90, VrHelmetBalance.MAX_RESTED_COOLDOWN_MINUTES);
        assertEquals(86_400, VrHelmetRules.cooldownLeft(Integer.MAX_VALUE, longest), "the longest wait still fits an int");
    }

    @Test
    void aRunningWaitIsKeptWhateverTheConnectionWouldHaveGranted() {
        VrHelmetBalance noRested = withRested(0, 0.9);
        assertEquals(new VrHelmetRules.RestedGrant(0, 500),
                VrHelmetRules.onConnect(VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, noRested), 1.0, 500, noRested), "rested minutes 0");
        assertEquals(new VrHelmetRules.RestedGrant(0, 500),
                VrHelmetRules.onConnect(VrHelmetRules.modifiers(VrHelmetTier.ADVANCED, F, C), 1.0, 500, C), "advanced tier");
        assertEquals(new VrHelmetRules.RestedGrant(0, 500),
                VrHelmetRules.onConnect(VrHelmetRules.modifiers(VrHelmetTier.BASIC, F, C), 0.1, 500, C), "basic tier, low energy");
    }

    @Test
    void anEnergyThatIsNotANumberGrantsNothingAndOpensNoWait() {
        assertEquals(new VrHelmetRules.RestedGrant(0, 0),
                VrHelmetRules.onConnect(VrHelmetRules.modifiers(VrHelmetTier.ADVANCED, F, C), Double.NaN, 0, C), "advanced");
        assertEquals(new VrHelmetRules.RestedGrant(0, 0),
                VrHelmetRules.onConnect(VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C), Double.NaN, 0, C), "diamond");
        assertEquals(new VrHelmetRules.RestedGrant(0, 700),
                VrHelmetRules.onConnect(VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C), Double.NaN, 700, C), "a running wait still goes on");
    }

    private static VrHelmetBalance withRested(int restedMinutes, double minEnergy) {
        return new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, restedMinutes, 10, minEnergy, 30);
    }

    @Test
    void theMultipliersAreExactlyTheRatiosOfTheDesign() {
        SessionModifiers advanced = VrHelmetRules.modifiers(VrHelmetTier.ADVANCED, F, C);
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C);
        assertAll(
                () -> assertEquals(55.0 / 40.0, advanced.durationMul()),
                () -> assertEquals(10.0 / 8.0, advanced.recoveryMul()),
                () -> assertEquals(75.0 / 40.0, diamond.durationMul()),
                () -> assertEquals(10.0 / 6.0, diamond.recoveryMul()));
    }

    @Test
    void theTiersKeepTheirMinutesWhateverTheFatigueBalanceIs() {
        FatigueBalance other = new FatigueBalance(true, 20.0, 0.8, 2.0, 20.0, 5.0, 0.25, 15, java.util.List.of(0.15, 0.08, 0.02), 0.30, 1.0);
        for (VrHelmetTier tier : new VrHelmetTier[]{VrHelmetTier.ADVANCED, VrHelmetTier.DIAMOND}) {
            SessionModifiers m = VrHelmetRules.modifiers(tier, other, C);
            double session = 1.0 / FatigueRules.drainPerSecond(other, FatigueRules.Activity.NORMAL, m) / 60.0;
            double recovery = 1.0 / FatigueRules.recoverPerSecond(other, m) / 60.0;
            assertAll(tier.toString(),
                    () -> assertEquals(C.sessionMinutes(tier, other), session, 1e-9, "session"),
                    () -> assertEquals(C.recoveryMinutes(tier, other), recovery, 1e-9, "recovery"));
        }
        assertEquals(55.0, 1.0 / FatigueRules.drainPerSecond(other, FatigueRules.Activity.NORMAL, VrHelmetRules.modifiers(VrHelmetTier.ADVANCED, other, C)) / 60.0, 1e-9);
        assertEquals(8.0, 1.0 / FatigueRules.recoverPerSecond(other, VrHelmetRules.modifiers(VrHelmetTier.ADVANCED, other, C)) / 60.0, 1e-9);
    }

    @Test
    void restedJustBelowTheMinimumIsStillTheMinimumAsAFloat() {
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C);
        assertEquals(600, VrHelmetRules.restedOnConnect(diamond, 0.9 - 1e-9, C), "the same float as 0.9");
        assertEquals(0, VrHelmetRules.restedOnConnect(diamond, 0.9 - 1e-6, C), "a visible difference as a float");
    }

    @Test
    void restedMinEnergyAtTheEdges() {
        VrHelmetBalance zero = withRested(10, 0.0);
        SessionModifiers diamondZero = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, zero);
        VrHelmetBalance one = withRested(10, 1.0);
        SessionModifiers diamondOne = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, one);
        assertAll(
                () -> assertEquals(600, VrHelmetRules.restedOnConnect(diamondZero, 0.0, zero), "minimum 0, energy 0"),
                () -> assertEquals(600, VrHelmetRules.restedOnConnect(diamondZero, 0.5, zero), "minimum 0, energy .5"),
                () -> assertEquals(600, VrHelmetRules.restedOnConnect(diamondOne, 1.0, one), "minimum 1, energy 1"),
                () -> assertEquals(0, VrHelmetRules.restedOnConnect(diamondOne, 0.999, one), "minimum 1, energy .999"));
    }

    @Test
    void aNonsenseEnergyGivesNothing() {
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, C);
        VrHelmetBalance zero = withRested(10, 0.0);
        assertAll(
                () -> assertEquals(0, VrHelmetRules.restedOnConnect(diamond, Double.NaN, C), "NaN"),
                () -> assertEquals(0, VrHelmetRules.restedOnConnect(diamond, -0.5, C), "negative"),
                () -> assertEquals(0, VrHelmetRules.restedOnConnect(diamond, Double.NaN, zero), "NaN, minimum 0"),
                () -> assertEquals(0, VrHelmetRules.restedOnConnect(diamond, -0.5, zero), "negative, minimum 0"));
    }

    @Test
    void theLongestRestedIsADayOfSeconds() {
        VrHelmetBalance longest = withRested(1440, 0.9);
        SessionModifiers diamond = VrHelmetRules.modifiers(VrHelmetTier.DIAMOND, F, longest);
        assertEquals(86_400, diamond.restedSeconds());
        assertEquals(86_400, VrHelmetRules.restedOnConnect(diamond, 1.0, longest));
    }

    @Test
    void noRestedSecondsInTheModifiersMeansNoneEvenAtFullEnergy() {
        SessionModifiers none = new SessionModifiers(1.4, 1.2, 0);
        assertEquals(0, VrHelmetRules.restedOnConnect(none, 1.0, C));
    }

    @Test
    void afterDyingTheResidenceWinsOverTheLastSaveAndCovadonga() {
        VrHelmetRules.Spawn spawn = VrHelmetRules.deathSpawn(Optional.of(SAVED.above(5)), Optional.of(SAVED), Optional.of(COVADONGA)).orElseThrow();
        assertEquals(VrHelmetRules.Source.RESIDENCE, spawn.source());
        assertEquals(SAVED.above(5), spawn.pos());
    }

    @Test
    void withoutAResidenceDyingReappearsAsAConnectionDoes() {
        assertEquals(VrHelmetRules.spawn(Optional.of(SAVED), Optional.of(COVADONGA)),
                VrHelmetRules.deathSpawn(Optional.empty(), Optional.of(SAVED), Optional.of(COVADONGA)));
        assertEquals(VrHelmetRules.spawn(Optional.empty(), Optional.empty()),
                VrHelmetRules.deathSpawn(Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
