package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetRules;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetState;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import com.orijua.mitofus.feature.progression.ProgressionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The VR helmet tiers: the tier of the block a connection starts from sets the session and recovery times, and the diamond
 * one grants the "rested" bonus. As in {@link VrHelmetTests}, the test overworld plays Mitofus, so the connection is
 * checked through the saved state and the attachments.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class VrHelmetTierTests {
    private static final BlockPos VR_HELMET = new BlockPos(0, 1, 1);

    private VrHelmetTierTests() {
    }


    private static GlobalPos at(GameTestHelper helper) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(VR_HELMET));
    }

    /** Runs {@code body} in Mitofus, with the factory numbers of every balance. */
    private static void inMitofus(GameTestHelper helper, Runnable body) {
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)),
                () -> TestSupport.withAllDefaults(body));
    }

    private static void inRealWorld(GameTestHelper helper, Runnable body) {
        VrHelmetTests.outsideMitofus(helper,
                () -> TestSupport.withAllDefaults(body));
    }

    /**
     * The energy is saved as a float and rewritten every second, so a sum of seconds is exact only up to the float's
     * rounding (below 2e-6 after 60 writes, so 1e-5 is plenty and still tells the tiers apart by far).
     */
    private static boolean nearEnergy(double a, double b) {
        return nearEnergy(a, b, 1e-5);
    }

    private static boolean nearEnergy(double a, double b, double tolerance) {
        return Math.abs(a - b) < tolerance;
    }

    private static VrHelmetTier tier(ServerPlayer player) {
        return player.getData(ModAttachments.VR_HELMET_TIER.get());
    }

    private static int rested(ServerPlayer player) {
        return FatigueService.restedSeconds(player);
    }

    private static int cooldown(ServerPlayer player) {
        return player.getData(ModAttachments.RESTED_COOLDOWN.get());
    }

    /** A connection from a block of {@code tier} with {@code energy}; returns the player. */
    private static ServerPlayer connected(GameTestHelper helper, VrHelmetTier tier, double energy) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, VR_HELMET, tier);
        FatigueService.setEnergy(player, energy);
        helper.assertTrue(VrHelmetService.connect(player, at(helper)), "it connects");
        return player;
    }

    // ---- The tier of the connection ----

    @GameTest(template = TestSupport.EMPTY)
    public static void aPlayerWithoutConnectionsIsBasic(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(tier(player) == VrHelmetTier.BASIC, "the default tier is the basic one");
        helper.assertTrue(rested(player) == 0, "and nobody starts rested");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPositionWithoutVrHelmetCountsAsBasic(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.VR_HELMET_TIER.get(), VrHelmetTier.ADVANCED);
        TestSupport.removeBody(helper, VR_HELMET);
        inMitofus(helper, () -> {
            helper.assertTrue(VrHelmetService.connect(player, at(helper)), "it connects");
            helper.assertTrue(tier(player) == VrHelmetTier.BASIC, "no body there: the basic tier");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aFailedTripLeavesTheTierAlone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.VR_HELMET_TIER.get(), VrHelmetTier.ADVANCED);
        TestSupport.lieBody(helper, VR_HELMET, VrHelmetTier.DIAMOND);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            // No Mitofus landing known: the connection is refused before anything is written.
            long seed = helper.getLevel().getSeed();
            var landingBefore = com.orijua.mitofus.feature.world.WorldSites.landingFor(seed);
            com.orijua.mitofus.feature.world.WorldSites.forgetLanding(seed);
            try {
                helper.assertFalse(VrHelmetService.connect(player, at(helper)), "nowhere to land");
                helper.assertTrue(tier(player) == VrHelmetTier.ADVANCED, "the tier is as it was");
                helper.assertTrue(rested(player) == 0, "and nothing is rested");
            } finally {
                landingBefore.ifPresent(pos -> com.orijua.mitofus.feature.world.WorldSites.rememberLanding(seed, pos));
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void breakingAnAdvancedVrHelmetWhileConnectingCancels(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, VR_HELMET, VrHelmetTier.ADVANCED);
        inMitofus(helper, () -> {
            VrHelmetService.start(player, at(helper));
            helper.assertTrue(VrHelmetService.isConnecting(player), "lying down starts the wait");
            TestSupport.removeBody(helper, VR_HELMET);
            VrHelmetService.tick(player);
            helper.assertFalse(VrHelmetService.isConnecting(player), "the vrhelmet gone cancels");
            helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and nothing connected");
            helper.assertTrue(tier(player) == VrHelmetTier.BASIC, "the tier did not change");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void everyBlockConnectsWithItsTierAfterTheWait(GameTestHelper helper) {
        inMitofus(helper, () -> {
            for (VrHelmetTier tier : VrHelmetTier.values()) {
                ServerPlayer player = TestSupport.fakePlayer(helper);
                TestSupport.lieBody(helper, VR_HELMET, tier);
                VrHelmetService.start(player, at(helper));
                VrHelmetTests.tickMany(player, VrHelmetRules.CONNECT_TICKS);
                helper.assertTrue(VrHelmetService.state(player).isConnected(), "the " + tier + " vrhelmet connects after the wait");
                helper.assertTrue(tier(player) == tier, "the " + tier + " vrhelmet sets its tier: " + tier(player));
            }
        });
        helper.succeed();
    }

    // ---- Session and recovery ----

    /**
     * Energy lost by a player who plays for 60 seconds: the first second is normal play (no earlier position to compare),
     * the other 59 are standing still (rest, 0.8 of the drain).
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void sixtySecondsOfPlayCostLessWithABetterVrHelmet(GameTestHelper helper) {
        inMitofus(helper, () -> {
            double[] sessionMinutes = {40.0, 55.0, 75.0};
            double[] lost = new double[VrHelmetTier.values().length];
            for (VrHelmetTier tier : VrHelmetTier.values()) {
                double perSecond = 1.0 / (sessionMinutes[tier.ordinal()] * 60.0);
                ServerPlayer player = TestSupport.fakePlayer(helper);
                player.setData(ModAttachments.VR_HELMET_TIER.get(), tier);
                FatigueService.setEnergy(player, 1.0);
                FatigueService.tickSecond(player);
                helper.assertTrue(nearEnergy(1.0 - FatigueService.energy(player), perSecond),
                        tier + ": the first second costs 1/(" + sessionMinutes[tier.ordinal()] + "*60) = " + perSecond + " but cost " + (1.0 - FatigueService.energy(player)));
                for (int i = 1; i < 60; i++) {
                    FatigueService.tickSecond(player);
                }
                lost[tier.ordinal()] = 1.0 - FatigueService.energy(player);
                double expected = (1.0 + 59 * 0.8) * perSecond;
                helper.assertTrue(nearEnergy(lost[tier.ordinal()], expected),
                        tier + ": 60 seconds cost " + expected + " (1 normal + 59 resting) but cost " + lost[tier.ordinal()]);
            }
            helper.assertTrue(lost[0] > lost[1] && lost[1] > lost[2], "a better vrhelmet loses less in the same time: " + java.util.Arrays.toString(lost));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void afterDisconnectingTheTierStaysAndRecoversInEightMinutes(GameTestHelper helper) {
        ServerPlayer[] holder = new ServerPlayer[1];
        inMitofus(helper, () -> {
            holder[0] = connected(helper, VrHelmetTier.ADVANCED, 0.0);
            helper.assertTrue(VrHelmetService.disconnect(holder[0], VrHelmetService.Reason.MANUAL), "it disconnects");
            helper.assertTrue(tier(holder[0]) == VrHelmetTier.ADVANCED, "the tier stays after disconnecting");
        });
        ServerPlayer player = holder[0];
        FatigueService.setEnergy(player, 0.0);
        // The recovery by the second must not read the wall clock: a clock that fails when asked proves it.
        FatigueService.setClock(() -> {
            throw new IllegalStateException("tickSecond must not read the wall clock");
        });
        try {
            inRealWorld(helper, () -> {
                for (int i = 0; i < 4 * 60; i++) {
                    FatigueService.tickSecond(player);
                }
                helper.assertTrue(nearEnergy(FatigueService.energy(player), 0.5), "four minutes refill half of eight: " + FatigueService.energy(player));
                for (int i = 0; i < 4 * 60; i++) {
                    FatigueService.tickSecond(player);
                }
                helper.assertTrue(nearEnergy(FatigueService.energy(player), 1.0, 5e-5), "eight minutes refill it: " + FatigueService.energy(player));
            });
        } finally {
            FatigueService.restoreClock();
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void awayFromTheGameTheRealWorldUsesTheLastTier(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.VR_HELMET_TIER.get(), VrHelmetTier.ADVANCED);
        long[] now = {1_000_000L};
        FatigueService.setClock(() -> now[0]);
        try {
            inRealWorld(helper, () -> {
                FatigueService.setEnergy(player, 0.2);
                FatigueService.onLogout(player);
                now[0] += 4 * 60 * 1000L;
                FatigueService.onLogin(player);
                helper.assertTrue(nearEnergy(FatigueService.energy(player), 0.7), "four minutes at the advanced rate refill half: " + FatigueService.energy(player));
            });
        } finally {
            FatigueService.restoreClock();
        }
        helper.succeed();
    }

    // ---- Rested ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theDiamondVrHelmetRestsOnlyWithEnoughEnergy(GameTestHelper helper) {
        inMitofus(helper, () -> {
            helper.assertTrue(rested(connected(helper, VrHelmetTier.DIAMOND, 0.95)) == 600, "0.95 energy: ten minutes");
            helper.assertTrue(rested(connected(helper, VrHelmetTier.DIAMOND, 0.85)) == 0, "0.85 energy: none");
            helper.assertTrue(rested(connected(helper, VrHelmetTier.DIAMOND, 1.0)) == 600, "full energy: ten minutes");
            helper.assertTrue(rested(connected(helper, VrHelmetTier.ADVANCED, 1.0)) == 0, "advanced: never");
            helper.assertTrue(rested(connected(helper, VrHelmetTier.BASIC, 1.0)) == 0, "basic: never");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void restedCountsDownInMitofusAndNotInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.RESTED_SECONDS.get(), 5);
        inRealWorld(helper, () -> {
            FatigueService.tickSecond(player);
            helper.assertTrue(rested(player) == 5, "it does not run in the real world");
        });
        inMitofus(helper, () -> {
            FatigueService.tickSecond(player);
            helper.assertTrue(rested(player) == 4, "one second in Mitofus: " + rested(player));
            for (int i = 0; i < 10; i++) {
                FatigueService.tickSecond(player);
            }
            helper.assertTrue(rested(player) == 0, "it ends at zero, never below: " + rested(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void restedCountsDownEvenWithTheFatigueOff(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.RESTED_SECONDS.get(), 5);
        FatigueBalance off = FatigueBalance.DEFAULT.withEnabled(false);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> FatigueBalance.withOverride(off, () -> {
            FatigueService.tickSecond(player);
            helper.assertTrue(rested(player) == 4, "the bonus runs out whatever the fatigue does: " + rested(player));
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void disconnectingEndsTheRested(GameTestHelper helper) {
        inMitofus(helper, () -> {
            ServerPlayer player = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            helper.assertTrue(rested(player) == 600, "rested after connecting");
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.MANUAL), "it disconnects");
            helper.assertTrue(rested(player) == 0, "disconnecting ends it");
            helper.assertTrue(tier(player) == VrHelmetTier.DIAMOND, "the tier is kept");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingInMitofusEndsTheRestedForTheNewBody(GameTestHelper helper) {
        inMitofus(helper, () -> {
            ServerPlayer old = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            VrHelmetService.died(old);
            helper.assertTrue(rested(old) == 0, "the old one is no longer rested");
            old.setData(ModAttachments.RESTED_SECONDS.get(), 300);
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            fresh.restoreFrom(old, false);
            helper.assertTrue(rested(fresh) == 0, "the rested bonus is not carried over a death");
            helper.assertTrue(tier(fresh) == VrHelmetTier.DIAMOND, "but the tier is");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void connectingAgainDoesNotAddRestedSeconds(GameTestHelper helper) {
        inMitofus(helper, () -> {
            ServerPlayer player = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            player.setData(ModAttachments.RESTED_SECONDS.get(), 300);
            player.setData(ModAttachments.RESTED_COOLDOWN.get(), 0);
            FatigueService.onConnected(player);
            helper.assertTrue(rested(player) == 600, "it is set, not added: " + rested(player));
            player.setData(ModAttachments.RESTED_SECONDS.get(), 300);
            player.setData(ModAttachments.RESTED_COOLDOWN.get(), 100);
            FatigueService.onConnected(player);
            helper.assertTrue(rested(player) == 0 && cooldown(player) == 100, "with a wait running it grants nothing: " + rested(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void restedSurvivesLeavingTheGameInsideMitofus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        long[] now = {1_000_000L};
        FatigueService.setClock(() -> now[0]);
        try {
            inMitofus(helper, () -> {
                player.setData(ModAttachments.RESTED_SECONDS.get(), 300);
                FatigueService.onLogout(player);
                now[0] += 60 * 60 * 1000L;
                FatigueService.onLogin(player);
                helper.assertTrue(rested(player) == 300, "quitting inside Mitofus keeps the rested seconds: " + rested(player));
            });
        } finally {
            FatigueService.restoreClock();
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void turningUpOutsideStillConnectedEndsTheRested(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        GlobalPos vrhelmet = at(helper);
        GlobalPos exit = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 0)));
        player.setData(ModAttachments.RESTED_SECONDS.get(), 300);
        inRealWorld(helper, () -> {
            player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY.connected(vrhelmet, exit));
            VrHelmetService.reconcile(player);
            helper.assertTrue(rested(player) == 0, "connected but outside Mitofus: no longer rested");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void withoutRestedTheLevelXpIsAsAwarded(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, () -> {
            int xp = ProgressionService.get(player).xp();
            ProgressionService.awardXp(player, 10);
            helper.assertTrue(ProgressionService.get(player).xp() == xp + 10, "not rested: 10");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void restedRaisesLevelXpByThePercentAndRounds(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        // The test states the percent it expects (10), it does not trust the default.
        VrHelmetBalance tenPercent = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 30);
        inMitofus(helper, () -> VrHelmetBalance.withOverride(tenPercent, () -> {
            player.setData(ModAttachments.RESTED_SECONDS.get(), 60);
            int xp = ProgressionService.get(player).xp();
            ProgressionService.awardXp(player, 10);
            helper.assertTrue(ProgressionService.get(player).xp() == xp + 11, "rested: 10 becomes 11 (+10 %)");
            xp = ProgressionService.get(player).xp();
            ProgressionService.awardXp(player, 15);
            helper.assertTrue(ProgressionService.get(player).xp() == xp + 17, "rested: 15 becomes 17 (16.5 rounds up)");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void restedXpFollowsTheConfiguredPercentAndSkipsSmithingXp(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetBalance half = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 50, 0.9, 30);
        inMitofus(helper, () -> VrHelmetBalance.withOverride(half, () -> {
            player.setData(ModAttachments.RESTED_SECONDS.get(), 60);
            int xp = ProgressionService.get(player).xp();
            ProgressionService.awardXp(player, 10);
            helper.assertTrue(ProgressionService.get(player).xp() == xp + 15, "rested at +50 %: 10 becomes 15");
            int smithing = ProgressionService.get(player).smithingXp();
            ProgressionService.addSmithingXp(player, 10);
            helper.assertTrue(ProgressionService.get(player).smithingXp() == smithing + 10, "smithing XP is the same rested or not");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingInTheRealWorldKeepsTheRestedSeconds(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.RESTED_SECONDS.get(), 300);
        inRealWorld(helper, () -> {
            VrHelmetService.died(player);
            helper.assertTrue(rested(player) == 300, "only a death in Mitofus ends the rested: " + rested(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void zeroRestedMinutesGrantsNothingEvenToTheDiamondVrHelmet(GameTestHelper helper) {
        VrHelmetBalance none = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 0, 10, 0.9, 30);
        inMitofus(helper, () -> VrHelmetBalance.withOverride(none, () ->
                helper.assertTrue(rested(connected(helper, VrHelmetTier.DIAMOND, 1.0)) == 0, "restedMinutes 0: no rested")));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theFatigueOffStillGrantsTheRestedOnConnecting(GameTestHelper helper) {
        FatigueBalance off = FatigueBalance.DEFAULT.withEnabled(false);
        inMitofus(helper, () -> FatigueBalance.withOverride(off, () -> {
            ServerPlayer player = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            helper.assertTrue(rested(player) == 600, "fatigue.enabled=false does not stop the bonus: " + rested(player));
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void resettingEverythingClearsTheTierAndTheRested(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.VR_HELMET_TIER.get(), VrHelmetTier.DIAMOND);
        player.setData(ModAttachments.RESTED_SECONDS.get(), 300);
        ModAttachments.resetAll(player);
        helper.assertTrue(tier(player) == VrHelmetTier.BASIC && rested(player) == 0, "back to basic and not rested");
        helper.succeed();
    }

    // ---- The wait for the next rested bonus ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theDiamondVrHelmetGrantsTheRestedAndOpensTheWait(GameTestHelper helper) {
        inMitofus(helper, () -> {
            ServerPlayer player = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            helper.assertTrue(rested(player) == 600, "rested 600: " + rested(player));
            helper.assertTrue(cooldown(player) == 1800, "wait 1800: " + cooldown(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void disconnectingAndConnectingAgainDoesNotRestartTheRested(GameTestHelper helper) {
        inMitofus(helper, () -> {
            ServerPlayer player = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.MANUAL), "it disconnects");
            helper.assertTrue(cooldown(player) == 1800, "the wait survives disconnecting: " + cooldown(player));
            FatigueService.setEnergy(player, 1.0);
            TestSupport.lieBody(helper, VR_HELMET, VrHelmetTier.DIAMOND);
            helper.assertTrue(VrHelmetService.connect(player, at(helper)), "it connects again");
            helper.assertTrue(rested(player) == 0, "no new rested: " + rested(player));
            helper.assertTrue(cooldown(player) == 1800, "the wait goes on: " + cooldown(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theWaitCountsDownInMitofusOnlyAndNotWhenDead(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.RESTED_COOLDOWN.get(), 100);
        inRealWorld(helper, () -> {
            FatigueService.tickSecond(player);
            helper.assertTrue(cooldown(player) == 100, "it does not run in the real world: " + cooldown(player));
        });
        inMitofus(helper, () -> {
            FatigueService.tickSecond(player);
            helper.assertTrue(cooldown(player) == 99, "one second in Mitofus: " + cooldown(player));
            player.setHealth(0.0f);
            FatigueService.tickSecond(player);
            helper.assertTrue(cooldown(player) == 99, "dead: it does not run: " + cooldown(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void whenTheWaitEndsConnectingGrantsTheRestedAgain(GameTestHelper helper) {
        inMitofus(helper, () -> {
            ServerPlayer player = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            player.setData(ModAttachments.RESTED_COOLDOWN.get(), 1);
            FatigueService.tickSecond(player);
            helper.assertTrue(cooldown(player) == 0, "the wait ended: " + cooldown(player));
            FatigueService.tickSecond(player);
            helper.assertTrue(cooldown(player) == 0, "and never goes below 0: " + cooldown(player));
            FatigueService.onConnected(player);
            helper.assertTrue(rested(player) == 600 && cooldown(player) == 1800, "rested again: " + rested(player) + ", " + cooldown(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aWaitOfZeroMinutesAlwaysGrantsTheRested(GameTestHelper helper) {
        VrHelmetBalance noWait = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 0);
        inMitofus(helper, () -> VrHelmetBalance.withOverride(noWait, () -> {
            ServerPlayer player = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            helper.assertTrue(rested(player) == 600 && cooldown(player) == 0, "no wait is opened: " + cooldown(player));
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.MANUAL), "it disconnects");
            FatigueService.setEnergy(player, 1.0);
            TestSupport.lieBody(helper, VR_HELMET, VrHelmetTier.DIAMOND);
            helper.assertTrue(VrHelmetService.connect(player, at(helper)), "it connects again");
            helper.assertTrue(rested(player) == 600, "rested again at once: " + rested(player));
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void connectingWithLowEnergyOpensNoWait(GameTestHelper helper) {
        inMitofus(helper, () -> {
            ServerPlayer player = connected(helper, VrHelmetTier.DIAMOND, 0.85);
            helper.assertTrue(rested(player) == 0 && cooldown(player) == 0, "nothing granted, no wait: " + cooldown(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingInMitofusKeepsTheWait(GameTestHelper helper) {
        inMitofus(helper, () -> {
            ServerPlayer old = connected(helper, VrHelmetTier.DIAMOND, 1.0);
            VrHelmetService.died(old);
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            fresh.restoreFrom(old, false);
            helper.assertTrue(cooldown(fresh) == 1800, "the wait is carried over a death: " + cooldown(fresh));
            helper.assertTrue(rested(fresh) == 0, "the bonus is not");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void leavingTheGameInsideMitofusKeepsTheWait(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        long[] now = {1_000_000L};
        FatigueService.setClock(() -> now[0]);
        try {
            inMitofus(helper, () -> {
                player.setData(ModAttachments.RESTED_COOLDOWN.get(), 900);
                FatigueService.onLogout(player);
                now[0] += 60 * 60 * 1000L;
                FatigueService.onLogin(player);
                helper.assertTrue(cooldown(player) == 900, "the wait is kept: " + cooldown(player));
            });
        } finally {
            FatigueService.restoreClock();
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void turningUpOutsideStillConnectedKeepsTheWait(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        GlobalPos vrhelmet = at(helper);
        GlobalPos exit = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 0)));
        player.setData(ModAttachments.RESTED_COOLDOWN.get(), 900);
        inRealWorld(helper, () -> {
            player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY.connected(vrhelmet, exit));
            VrHelmetService.reconcile(player);
            helper.assertTrue(cooldown(player) == 900, "reconciling keeps the wait: " + cooldown(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void loweringTheConfiguredWaitShortensARunningOne(GameTestHelper helper) {
        VrHelmetBalance fiveMinutes = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 5);
        VrHelmetBalance noWait = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 0);
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.RESTED_COOLDOWN.get(), 1800);
        inMitofus(helper, () -> {
            VrHelmetBalance.withOverride(fiveMinutes, () -> {
                FatigueService.tickSecond(player);
                helper.assertTrue(cooldown(player) == 299, "clamped to 300 and one second gone: " + cooldown(player));
            });
            VrHelmetBalance.withOverride(noWait, () -> {
                FatigueService.tickSecond(player);
                helper.assertTrue(cooldown(player) == 0, "0 minutes frees it: " + cooldown(player));
            });
        });
        helper.succeed();
    }

    /** The wait runs while the player is in Mitofus whatever the game mode: spectating or creative does not freeze it. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theWaitCountsDownInSpectatorAndCreativeToo(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.RESTED_COOLDOWN.get(), 100);
        inMitofus(helper, () -> {
            try {
                player.setGameMode(GameType.SPECTATOR);
                FatigueService.tickSecond(player);
                helper.assertTrue(cooldown(player) == 99, "spectator: one second gone: " + cooldown(player));
                player.setGameMode(GameType.CREATIVE);
                FatigueService.tickSecond(player);
                helper.assertTrue(cooldown(player) == 98, "creative: one more second gone: " + cooldown(player));
            } finally {
                player.setGameMode(GameType.SURVIVAL);
            }
        });
        helper.succeed();
    }

    /** Raising the configured wait never lengthens a wait already running; lowering it later cuts it and it goes on counting. */
    @GameTest(template = TestSupport.EMPTY)
    public static void raisingTheWaitDoesNotLengthenAStoredOneAndCuttingItKeepsCounting(GameTestHelper helper) {
        VrHelmetBalance anHour = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 60);
        VrHelmetBalance fiveMinutes = new VrHelmetBalance(55, 8, 75, 6, 3000, 15000, 10, 10, 0.9, 5);
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.RESTED_COOLDOWN.get(), 1800);
        inMitofus(helper, () -> {
            VrHelmetBalance.withOverride(anHour, () -> {
                FatigueService.tickSecond(player);
                helper.assertTrue(cooldown(player) == 1799, "60 minutes do not stretch the stored 1800: " + cooldown(player));
            });
            VrHelmetBalance.withOverride(fiveMinutes, () -> {
                FatigueService.tickSecond(player);
                helper.assertTrue(cooldown(player) == 299, "cut to 300 and one second gone: " + cooldown(player));
                FatigueService.tickSecond(player);
                helper.assertTrue(cooldown(player) == 298, "and it goes on counting: " + cooldown(player));
            });
        });
        helper.succeed();
    }

    /**
     * resetAll uses removeData on the synced wait: NeoForge 21.1 syncs a removal to the owner too ("if there is currently
     * no attachment, the removal is synced"), so the Status tab does not keep showing a wait that is gone.
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void resettingEverythingTellsTheClientTheWaitIsGone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        java.util.List<net.neoforged.neoforge.network.payload.SyncAttachmentsPayload> sent = new java.util.ArrayList<>();
        new net.minecraft.server.network.ServerGamePacketListenerImpl(player.server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND), player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof net.neoforged.neoforge.network.payload.SyncAttachmentsPayload payload) {
                    sent.add(payload);
                }
            }
        };
        player.setData(ModAttachments.RESTED_COOLDOWN.get(), 900);
        sent.clear();
        ModAttachments.resetAll(player);
        boolean removalSynced = sent.stream().anyMatch(payload -> payload.types().contains(ModAttachments.RESTED_COOLDOWN.get())
                && payload.syncPayload().length > 0 && payload.syncPayload()[0] == 0);
        helper.assertTrue(removalSynced, "the removal of the wait reached the owner: " + sent.size() + " sync packets");
        helper.assertTrue(cooldown(player) == 0, "and the server reads the default again: " + cooldown(player));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void resettingEverythingClearsTheWait(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.RESTED_COOLDOWN.get(), 900);
        ModAttachments.resetAll(player);
        helper.assertTrue(cooldown(player) == 0, "resetAll clears the wait: " + cooldown(player));
        helper.succeed();
    }
}
