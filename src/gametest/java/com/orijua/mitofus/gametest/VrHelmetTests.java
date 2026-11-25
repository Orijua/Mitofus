package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetRules;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetState;
import com.orijua.mitofus.feature.vrhelmet.LevelProbe;
import com.orijua.mitofus.feature.vrhelmet.SafeSpot;
import com.orijua.mitofus.feature.vrhelmet.StarterVrHelmet;
import com.orijua.mitofus.feature.vrhelmet.WorldSeparation;
import com.orijua.mitofus.feature.character.CharacterSnapshot;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.Characters;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.sancho.OrbeAurifero;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The VR helmet: lying down, the "Connecting…" wait and its cancellations, where a connection lands and where a
 * disconnection leaves you. The test server has no Mitofus dimension, so its overworld plays it ({@link MitofusWorld#standIn});
 * a fake player ignores teleport packets, so what moves is checked through the saved state, not the player's position,
 * and a real dimension crossing cannot be tried with one (its connection has no channel): that is left to a manual test.
 * The tests that clear the global connection tables ({@code forgetAllConnections}) are only safe because their bodies
 * are synchronous: no other test can run in the middle of one.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class VrHelmetTests {
    private VrHelmetTests() {
    }

    /** Runs {@code body} with the test level playing Mitofus and a saved Covadonga landing, then puts everything back. */
    static void inMitofus(GameTestHelper helper, BlockPos landing, Runnable body) {
        ServerLevel level = helper.getLevel();
        long seed = level.getSeed();
        Optional<BlockPos> landingBefore = WorldSites.landingFor(seed);
        try {
            MitofusWorld.standIn(level);
            WorldSites.rememberLanding(seed, landing);
            body.run();
        } finally {
            MitofusWorld.restoreStandIn();
            landingBefore.ifPresentOrElse(pos -> WorldSites.rememberLanding(seed, pos), () -> WorldSites.forgetLanding(seed));
        }
    }

    /** Runs {@code body} with no level playing Mitofus (whatever another test left), and puts the test default back (the overworld plays Mitofus) afterwards. */
    static void outsideMitofus(GameTestHelper helper, Runnable body) {
        try {
            MitofusWorld.standIn(null);
            helper.assertFalse(MitofusWorld.is(helper.getLevel()), "the test level is the real world here");
            body.run();
        } finally {
            MitofusWorld.restoreStandIn();
        }
    }

    static GlobalPos at(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    /** A level other than the test's, to play Mitofus while the player stays "in the real world". Null if there is none. */
    static ServerLevel otherLevel(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether != null && nether != helper.getLevel()) {
            return nether;
        }
        for (ServerLevel candidate : helper.getLevel().getServer().getAllLevels()) {
            if (candidate != helper.getLevel()) {
                return candidate;
            }
        }
        return null;
    }

    static void tickMany(ServerPlayer player, int ticks) {
        TestSupport.tickMany(player, ticks, VrHelmetService::tick);
    }

    /** Arriving but not being able to swap characters is not a connection: the player goes back and nothing changes. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aConnectionWhoseSwapFailsGoesBack(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.REAL, CharacterSnapshot.empty()));
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        inMitofus(helper, covadonga, () -> {
            VrHelmetState before = VrHelmetService.state(player);
            Characters.applierForTests((p, tag) -> {
                throw new IllegalStateException("test: the character cannot be applied");
            });
            try {
                helper.assertFalse(VrHelmetService.connect(player, at(helper, new BlockPos(0, 1, 0))), "the connection is undone");
            } finally {
                Characters.applierForTests(null);
            }
            helper.assertTrue(VrHelmetService.state(player).equals(before), "the vrhelmet state is as before");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3, "they keep their things");
            helper.assertTrue(Characters.vault(player).live().equals(Optional.of(WorldSide.REAL)), "still carrying the real character");
        });
        helper.succeed();
    }

    /** With the real character unreadable a voluntary disconnection is refused (a forced one, by fatigue, goes through). */
    @GameTest(template = TestSupport.EMPTY)
    public static void aDisconnectionWithAnUnreadableRealCharacterIsRefused(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        inMitofus(helper, covadonga, () -> {
            helper.assertTrue(VrHelmetService.connect(player, at(helper, new BlockPos(0, 1, 0))), "it connects");
            VrHelmetState connected = VrHelmetService.state(player);
            player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.MITOFUS, new CompoundTag()));
            for (VrHelmetService.Reason reason : new VrHelmetService.Reason[] {VrHelmetService.Reason.MANUAL, VrHelmetService.Reason.LOGOUT}) {
                helper.assertFalse(VrHelmetService.disconnect(player, reason), "refused (" + reason + ")");
                helper.assertTrue(VrHelmetService.state(player).equals(connected), "the state is intact (" + reason + ")");
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void firstConnectionLandsInCovadongaAndLeavesTheBodyInTheVrHelmet(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        inMitofus(helper, covadonga, () -> {
            helper.assertTrue(VrHelmetService.state(player).equals(VrHelmetState.EMPTY), "a new player has no vrhelmet state");
            VrHelmetRules.Spawn spawn = VrHelmetService.spawnFor(player, helper.getLevel()).orElseThrow();
            helper.assertTrue(spawn.source() == VrHelmetRules.Source.COVADONGA && spawn.pos().equals(covadonga), "the first time: Covadonga's plaza");

            GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
            GlobalPos lyingAt = GlobalPos.of(helper.getLevel().dimension(), player.blockPosition());
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "it connects");
            VrHelmetState state = VrHelmetService.state(player);
            helper.assertTrue(state.vrhelmet().equals(Optional.of(vrhelmet)), "the body stays in the vrhelmet");
            helper.assertTrue(state.exit().equals(Optional.of(lyingAt)), "and the spot to step out to is where they lay down");
            helper.assertTrue(state.lastSave().isEmpty(), "nothing saved yet");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void starterVrHelmetIsGivenOnlyOnce(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertTrue(StarterVrHelmet.giveIfFirstTime(player), "the first time it is given");
        helper.assertTrue(player.getInventory().countItem(ModItems.HELMET.get()) == 1, "one vrhelmet in the inventory");
        helper.assertFalse(StarterVrHelmet.giveIfFirstTime(player), "never again");
        helper.assertTrue(player.getInventory().countItem(ModItems.HELMET.get()) == 1, "still one vrhelmet");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void starterVrHelmetFlagSurvivesDeath(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        StarterVrHelmet.giveIfFirstTime(player);
        VrHelmetService.died(player);
        helper.assertTrue(player.getData(ModAttachments.STARTER_VR_HELMET_GIVEN.get()), "dying does not clear the flag");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void disconnectingSavesTheSpotAndTheNextConnectionStartsThere(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        inMitofus(helper, covadonga, () -> {
            GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
            BlockPos lyingAt = player.blockPosition();
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "it connects");
            BlockPos exit = VrHelmetService.state(player).exit().orElseThrow().pos();
            helper.assertTrue(exit.equals(lyingAt), "they will step out where they had lain down");

            BlockPos wandered = helper.absolutePos(new BlockPos(2, 1, 2));
            player.setPos(wandered.getX() + 0.5, wandered.getY(), wandered.getZ() + 0.5);
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.MANUAL), "in Mitofus: it disconnects");

            VrHelmetState state = VrHelmetService.state(player);
            helper.assertTrue(state.vrhelmet().isEmpty() && state.exit().isEmpty(), "the body is back, no vrhelmet in use");
            helper.assertTrue(state.lastSave().equals(Optional.of(wandered)), "the spot where they left is saved");

            VrHelmetRules.Spawn next = VrHelmetService.spawnFor(player, helper.getLevel()).orElseThrow();
            helper.assertTrue(next.source() == VrHelmetRules.Source.LAST_SAVE && next.pos().equals(wandered),
                    "the next time they appear where they left, not in Covadonga");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void disconnectingWhenNotInMitofusDoesNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        outsideMitofus(helper, () -> {
            helper.assertFalse(VrHelmetService.disconnect(player, VrHelmetService.Reason.DEBUG), "the real world: nothing to disconnect from");
            helper.assertTrue(VrHelmetService.state(player).equals(VrHelmetState.EMPTY), "and nothing is saved");
        });
        helper.succeed();
    }

    /** Without an exit point (they came in some other way) the player goes to the respawn point and the spot is still saved. */
    @GameTest(template = TestSupport.EMPTY)
    public static void disconnectingWithoutAnExitPointFallsBackToTheRespawn(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            helper.assertTrue(VrHelmetService.state(player).equals(VrHelmetState.EMPTY), "no vrhelmet, no exit point");
            BlockPos where = player.blockPosition();
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.DEBUG), "in Mitofus: it disconnects");
            helper.assertTrue(VrHelmetService.state(player).lastSave().equals(Optional.of(where)), "the spot is saved");
            helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and there is no body");
        });
        helper.succeed();
    }

    /** Own design: the Nether and the End are the real world too, but the VR helmet only connects from the overworld. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theVrHelmetOnlyConnectsFromTheOverworld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the test server has a Nether");
        ServerPlayer inNether = TestSupport.fakePlayerIn(nether);
        BlockPos spot = inNether.blockPosition();
        outsideMitofus(helper, () -> {
            helper.assertTrue(VrHelmetService.begin(inNether, spot) == VrHelmetService.Begin.NOT_OVERWORLD, "lying down in the Nether does nothing");
            helper.assertFalse(VrHelmetService.isConnecting(inNether), "and no connection starts");
            ServerLevel end = server.getLevel(Level.END);
            if (end != null) {
                ServerPlayer inEnd = TestSupport.fakePlayerIn(end);
                helper.assertTrue(VrHelmetService.begin(inEnd, inEnd.blockPosition()) == VrHelmetService.Begin.NOT_OVERWORLD, "nor in the End");
                helper.assertFalse(VrHelmetService.isConnecting(inEnd), "no connection there either");
            }
            ServerPlayer inOverworld = TestSupport.fakePlayer(helper);
            helper.assertTrue(VrHelmetService.begin(inOverworld, helper.absolutePos(new BlockPos(0, 1, 0))) != VrHelmetService.Begin.NOT_OVERWORLD,
                    "the overworld is still allowed");
            VrHelmetService.cancel(inOverworld, false);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void withoutMitofusOrLandingThereIsNowhereToConnect(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        outsideMitofus(helper, () -> {
            helper.assertTrue(MitofusWorld.of(helper.getLevel().getServer()).isEmpty(), "an old world has no Mitofus dimension");
            helper.assertTrue(VrHelmetService.begin(player, helper.absolutePos(new BlockPos(0, 1, 0))) == VrHelmetService.Begin.NO_MITOFUS,
                    "so lying down has nowhere to go");
        });

        ServerLevel level = helper.getLevel();
        long seed = level.getSeed();
        Optional<BlockPos> landingBefore = WorldSites.landingFor(seed);
        try {
            MitofusWorld.standIn(level);
            WorldSites.forgetLanding(seed);
            helper.assertTrue(VrHelmetService.begin(player, helper.absolutePos(new BlockPos(0, 1, 0))) == VrHelmetService.Begin.IN_MITOFUS,
                    "already in Mitofus: lying down in a vrhelmet does nothing");
            helper.assertTrue(VrHelmetService.spawnFor(player, level).isEmpty(), "no saved spot and no Covadonga: nowhere to appear");
            helper.assertFalse(VrHelmetService.connect(player, at(helper, new BlockPos(0, 1, 0))), "so it does not connect");
            helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and the body is not left in the vrhelmet");
        } finally {
            MitofusWorld.restoreStandIn();
            landingBefore.ifPresent(pos -> WorldSites.rememberLanding(seed, pos));
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingInMitofusKeepsTheBodyInTheVrHelmetAndTheLastSave(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
        BlockPos saved = helper.absolutePos(new BlockPos(2, 1, 2));
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "it connects");
            player.setPos(saved.getX() + 0.5, saved.getY(), saved.getZ() + 0.5);
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.MANUAL), "it disconnects");
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "and connects again");
            BlockPos deathSpot = helper.absolutePos(new BlockPos(4, 1, 4));
            player.setPos(deathSpot.getX() + 0.5, deathSpot.getY(), deathSpot.getZ() + 0.5);

            NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
            VrHelmetState state = VrHelmetService.state(player);
            helper.assertTrue(state.vrhelmet().equals(Optional.of(vrhelmet)) && state.exit().isPresent(), "the body stays in the vrhelmet: the player reappears in Mitofus");
            helper.assertTrue(state.lastSave().equals(Optional.of(saved)), "the last save is kept, not the place of death");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingOutsideMitofusLeavesTheStateAlone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        outsideMitofus(helper, () -> {
            VrHelmetState connected = VrHelmetState.EMPTY.connected(at(helper, new BlockPos(0, 1, 0)), at(helper, new BlockPos(1, 1, 0)));
            player.setData(ModAttachments.VR_HELMET.get(), connected);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
            helper.assertTrue(VrHelmetService.state(player).equals(connected), "dying in the real world does not touch the state");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDeathTheLegendTitlePreventsDoesNotFreeTheBody(GameTestHelper helper) {
        GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
        ServerPlayer legend = TestSupport.fakePlayer(helper);
        legend.getInventory().add(new ItemStack(ModItems.ORBE_AURIFERO.get()));
        OrbeAurifero.read(legend);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            helper.assertTrue(VrHelmetService.connect(legend, vrhelmet), "the legend connects");
            VrHelmetState before = VrHelmetService.state(legend);
            LivingDeathEvent death = NeoForge.EVENT_BUS.post(new LivingDeathEvent(legend, legend.damageSources().generic()));
            helper.assertTrue(death.isCanceled(), "the legend is saved");
            helper.assertTrue(VrHelmetService.state(legend).equals(before), "so the body stays in the vrhelmet");
        });
        helper.succeed();
    }

    // ---- Separate worlds: the real world and Mitofus ----

    private static boolean travelCancelled(Entity entity, ResourceKey<Level> to) {
        return NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(entity, to)).isCanceled();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPlayerCannotCrossBetweenTheRealWorldAndMitofusOnItsOwn(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        outsideMitofus(helper, () -> {
            helper.assertTrue(travelCancelled(player, MitofusWorld.LEVEL), "real world to Mitofus: cancelled");
            helper.assertFalse(travelCancelled(player, Level.NETHER), "real world to the Nether: not touched");
        });
        try {
            MitofusWorld.standIn(helper.getLevel());
            helper.assertTrue(travelCancelled(player, Level.NETHER), "Mitofus to the real world: cancelled");
            helper.assertFalse(travelCancelled(player, MitofusWorld.LEVEL), "Mitofus to Mitofus: not touched");
        } finally {
            MitofusWorld.restoreStandIn();
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void otherEntitiesCannotCrossEitherAndTheVrHelmetFlagLetsThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity zombie = EntityType.ZOMBIE.create(level);
        Entity item = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.STICK));
        try {
            MitofusWorld.standIn(level);
            helper.assertTrue(travelCancelled(zombie, Level.NETHER), "a zombie from Mitofus to the Nether: cancelled");
            helper.assertTrue(travelCancelled(item, Level.NETHER), "an item too");
            WorldSeparation.crossing(() -> {
                helper.assertFalse(travelCancelled(zombie, Level.NETHER), "inside crossing(...): allowed");
                helper.assertFalse(travelCancelled(item, Level.NETHER), "also for an item");
            });
            helper.assertTrue(travelCancelled(zombie, Level.NETHER), "and cancelled again afterwards");
        } finally {
            MitofusWorld.restoreStandIn();
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theCrossingFlagIsOnlyOnInsideAndAlwaysCleared(GameTestHelper helper) {
        helper.assertFalse(WorldSeparation.crossingAllowed(), "off by default");
        WorldSeparation.crossing(() -> helper.assertTrue(WorldSeparation.crossingAllowed(), "on inside"));
        helper.assertFalse(WorldSeparation.crossingAllowed(), "off after");
        boolean thrown = false;
        try {
            WorldSeparation.crossing(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            thrown = true;
        }
        helper.assertTrue(thrown, "the exception comes out");
        helper.assertFalse(WorldSeparation.crossingAllowed(), "off even if the body throws");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void mitofusIsAgreesForLevelsAndKeys(GameTestHelper helper) {
        try {
            for (ServerLevel candidate : helper.getLevel().getServer().getAllLevels()) {
                MitofusWorld.standIn(null);
                helper.assertTrue(MitofusWorld.is(candidate) == MitofusWorld.is(candidate.dimension()), "without a stand-in: " + candidate.dimension().location());
                MitofusWorld.standIn(helper.getLevel());
                helper.assertTrue(MitofusWorld.is(candidate) == MitofusWorld.is(candidate.dimension()), "with a stand-in: " + candidate.dimension().location());
            }
            helper.assertTrue(MitofusWorld.is(MitofusWorld.LEVEL), "the real key is Mitofus");
            helper.assertTrue(MitofusWorld.is(helper.getLevel().dimension()), "and so is the stand-in's key");
            MitofusWorld.standIn(null);
            helper.assertFalse(MitofusWorld.is(helper.getLevel().dimension()), "not without the stand-in");
        } finally {
            MitofusWorld.restoreStandIn();
        }
        helper.succeed();
    }

    // ---- Deaths only count in Mitofus ----

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingInTheRealWorldCostsNoXpButInMitofusItDoes(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Balance.withOverride(Balance.DEFAULT.withDeathXpLoss(0.5), () -> {
            outsideMitofus(helper, () -> {
                ProgressionService.set(player, PlayerStats.DEFAULT.withProgress(12, 50, 0));
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
                helper.assertTrue(ProgressionService.get(player).xp() == 50, "the real world: XP untouched");
            });
            inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
                ProgressionService.set(player, PlayerStats.DEFAULT.withProgress(12, 50, 0));
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
                helper.assertTrue(ProgressionService.get(player).xp() == 25, "Mitofus: half of 50 XP lost, now " + ProgressionService.get(player).xp());
            });
        });
        helper.succeed();
    }

    // ---- Free spot (LevelProbe) ----

    /**
     * Lava and fire have no collision, so they are never a floor for that reason alone (there is no separate branch for
     * them in {@link LevelProbe#isSafeFloor}); magma has collision, so it is the one block its own check rejects.
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void theLevelProbeTellsAirStoneAndFluidsApart(GameTestHelper helper) {
        LevelProbe probe = new LevelProbe(helper.getLevel());
        BlockPos at = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(at);
        try {
            helper.setBlock(at, Blocks.AIR);
            helper.setBlock(new BlockPos(1, 0, 1), Blocks.AIR);
            helper.assertTrue(probe.isOpen(pos) && !probe.isSafeFloor(pos), "air: open, not a floor");
            helper.setBlock(at, Blocks.STONE);
            helper.assertTrue(!probe.isOpen(pos) && probe.isSafeFloor(pos), "stone: not open, a floor");
            helper.setBlock(at, Blocks.WATER);
            helper.assertTrue(!probe.isOpen(pos) && !probe.isSafeFloor(pos), "water: neither");
            helper.setBlock(at, Blocks.LAVA);
            helper.assertTrue(!probe.isOpen(pos) && !probe.isSafeFloor(pos), "lava: neither (no collision, and a fluid)");
            helper.setBlock(at, Blocks.MAGMA_BLOCK);
            helper.assertTrue(!probe.isOpen(pos) && !probe.isSafeFloor(pos), "magma has collision but is not a safe floor (its own check)");
            helper.setBlock(new BlockPos(1, 0, 1), Blocks.NETHERRACK); // fire needs something to burn on (the box floor is air)
            helper.setBlock(at, Blocks.FIRE);
            helper.assertTrue(!probe.isOpen(pos) && !probe.isSafeFloor(pos), "fire: neither");
        } finally {
            helper.setBlock(at, Blocks.AIR);
            helper.setBlock(new BlockPos(1, 0, 1), Blocks.AIR);
        }
        helper.succeed();
    }

    /** Blocks with no collision that hurt, trap or move you are not free to stand in. */
    @GameTest(template = TestSupport.EMPTY)
    public static void harmfulBlocksWithoutCollisionAreNotOpen(GameTestHelper helper) {
        LevelProbe probe = new LevelProbe(helper.getLevel());
        BlockPos at = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(at);
        try {
            helper.setBlock(new BlockPos(1, 0, 1), Blocks.NETHERRACK);
            helper.setBlock(at, Blocks.TORCH);
            helper.assertTrue(probe.isOpen(pos), "control: a torch has no collision and is harmless: open");
            for (net.minecraft.world.level.block.Block block : new net.minecraft.world.level.block.Block[]{
                    Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.COBWEB, Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW,
                    Blocks.NETHER_PORTAL, Blocks.END_PORTAL}) {
                helper.setBlock(new BlockPos(1, 0, 1), block == Blocks.SOUL_FIRE ? Blocks.SOUL_SOIL : Blocks.NETHERRACK);
                helper.setBlock(at, block);
                helper.assertFalse(probe.isOpen(pos), block + " is not open");
                helper.assertFalse(probe.isSafeFloor(pos), block + " is not a floor either");
            }
        } finally {
            helper.setBlock(at, Blocks.AIR);
            helper.setBlock(new BlockPos(1, 0, 1), Blocks.AIR);
        }
        helper.succeed();
    }

    /** A saved spot whose feet are inside stone resolves to a free spot nearby, valid for the probe. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aLastSaveInsideStoneResolvesToAFreeSpot(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        LevelProbe probe = new LevelProbe(helper.getLevel());
        BlockPos[] stone = {new BlockPos(1, 0, 1), new BlockPos(1, 1, 1), new BlockPos(1, 2, 1)};
        BlockPos wanted = helper.absolutePos(new BlockPos(1, 1, 1));
        inMitofus(helper, helper.absolutePos(new BlockPos(2, 1, 2)), () -> {
            try {
                for (BlockPos block : stone) {
                    helper.setBlock(block, Blocks.STONE);
                }
                player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY.disconnected(wanted));
                VrHelmetRules.Spawn spawn = VrHelmetService.spawnFor(player, helper.getLevel()).orElseThrow();
                helper.assertTrue(spawn.source() == VrHelmetRules.Source.LAST_SAVE && spawn.pos().equals(wanted), "the last save is the wanted spot");
                helper.assertFalse(probe.isOpen(wanted), "whose feet are inside stone");
                BlockPos free = VrHelmetService.freeSpot(helper.getLevel(), spawn.pos(), "test", player);
                helper.assertFalse(free.equals(wanted), "so it is not used as it is: " + free);
                helper.assertTrue(probe.isOpen(free) && probe.isOpen(free.above()) && probe.isSafeFloor(free.below()),
                        "the spot found fits a player: " + free);
            } finally {
                for (BlockPos block : stone) {
                    helper.setBlock(block, Blocks.AIR);
                }
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aValidOriginIsKeptByTheSearch(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(SafeSpot.find(new LevelProbe(helper.getLevel()), origin).equals(Optional.of(origin)),
                "feet and head free over stone: the origin itself");
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theVrHelmetStateIsSavedWithThePlayer(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetState state = VrHelmetState.EMPTY.disconnected(helper.absolutePos(new BlockPos(2, 1, 2)))
                .connected(at(helper, new BlockPos(0, 1, 0)), at(helper, new BlockPos(1, 1, 0)));
        player.setData(ModAttachments.VR_HELMET.get(), state);
        CompoundTag tag = player.saveWithoutId(new CompoundTag());
        ServerPlayer other = TestSupport.fakePlayer(helper);
        other.load(tag);
        helper.assertTrue(VrHelmetService.state(other).equals(state), "the state is read back from the saved player");
        helper.succeed();
    }

    /** Calls {@code reconcile} directly (what the login event does), not the event. */
    @GameTest(template = TestSupport.EMPTY)
    public static void loggingInReconcilesTheState(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos saved = helper.absolutePos(new BlockPos(2, 1, 2));
        GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
        GlobalPos exit = at(helper, new BlockPos(1, 1, 0));

        outsideMitofus(helper, () -> {
            player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY.disconnected(saved).connected(vrhelmet, exit));
            VrHelmetService.reconcile(player);
            VrHelmetState outside = VrHelmetService.state(player);
            helper.assertTrue(outside.vrhelmet().isEmpty() && outside.exit().isEmpty() && outside.lastSave().equals(Optional.of(saved)),
                    "connected but outside Mitofus: the body is released, the last save kept");
        });

        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            VrHelmetState disconnected = VrHelmetState.EMPTY.disconnected(saved);
            player.setData(ModAttachments.VR_HELMET.get(), disconnected);
            VrHelmetService.reconcile(player);
            helper.assertTrue(VrHelmetService.state(player).equals(disconnected), "inside and disconnected: nothing changes");

            VrHelmetState connected = disconnected.connected(vrhelmet, exit);
            player.setData(ModAttachments.VR_HELMET.get(), connected);
            VrHelmetService.reconcile(player);
            helper.assertTrue(VrHelmetService.state(player).equals(connected), "inside and connected: nothing changes");
        });
        helper.succeed();
    }

    /** Calls {@code forgetAllConnections} directly (what the server stopping/starting events do), not the events. */
    @GameTest(template = TestSupport.EMPTY)
    public static void stoppingTheServerForgetsEveryConnection(GameTestHelper helper) {
        ServerPlayer first = TestSupport.fakePlayer(helper);
        ServerPlayer second = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, new BlockPos(0, 1, 1), VrHelmetTier.BASIC);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                VrHelmetService.start(first, at(helper, new BlockPos(0, 1, 1)));
                VrHelmetService.start(second, at(helper, new BlockPos(0, 1, 1)));
                VrHelmetService.forgetAllConnections();
                helper.assertFalse(VrHelmetService.isConnecting(first) || VrHelmetService.isConnecting(second), "nobody is connecting any more");
                tickMany(first, VrHelmetRules.CONNECT_TICKS + 5);
                tickMany(second, VrHelmetRules.CONNECT_TICKS + 5);
                helper.assertTrue(VrHelmetService.state(first).vrhelmet().isEmpty() && VrHelmetService.state(second).vrhelmet().isEmpty(),
                        "and nobody connects");
            } finally {
                VrHelmetService.forgetAllConnections();
            }
        });
        helper.succeed();
    }

    // ---- Where a disconnection leads ----

    private static ServerLevel levelOtherThanTheOverworld(GameTestHelper helper) {
        for (ServerLevel candidate : helper.getLevel().getServer().getAllLevels()) {
            if (candidate != helper.getLevel().getServer().overworld()) {
                return candidate;
            }
        }
        return null;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDisconnectionWithoutAnExitGoesToTheRespawnOrTheSpawn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos respawn = helper.absolutePos(new BlockPos(2, 1, 2));
        ResourceKey<Level> nowhere = ResourceKey.create(Registries.DIMENSION, Mitofus.id("nowhere"));
        outsideMitofus(helper, () -> {
            VrHelmetService.Destination bed = VrHelmetService.destination(server, Optional.empty(), Level.OVERWORLD, respawn);
            helper.assertTrue(bed.level() == overworld && bed.pos().equals(respawn), "a bed in the overworld: there");
            VrHelmetService.Destination noBed = VrHelmetService.destination(server, Optional.empty(), Level.OVERWORLD, null);
            helper.assertTrue(noBed.level() == overworld && noBed.pos().equals(overworld.getSharedSpawnPos()), "no bed: the world's spawn");
            VrHelmetService.Destination gone = VrHelmetService.destination(server, Optional.empty(), nowhere, respawn);
            helper.assertTrue(gone.level() == overworld && gone.pos().equals(overworld.getSharedSpawnPos()),
                    "a respawn dimension that does not exist: the overworld's spawn, not the bed position");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDisconnectionWithAnExitGoesBesideTheVrHelmet(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos exitPos = helper.absolutePos(new BlockPos(1, 1, 0));
        BlockPos respawn = helper.absolutePos(new BlockPos(2, 1, 2));
        ResourceKey<Level> nowhere = ResourceKey.create(Registries.DIMENSION, Mitofus.id("nowhere"));
        outsideMitofus(helper, () -> {
            VrHelmetService.Destination there = VrHelmetService.destination(server,
                    Optional.of(GlobalPos.of(Level.OVERWORLD, exitPos)), Level.OVERWORLD, respawn);
            helper.assertTrue(there.level() == overworld && there.pos().equals(exitPos), "the exit point, not the respawn");
            VrHelmetService.Destination missing = VrHelmetService.destination(server,
                    Optional.of(GlobalPos.of(nowhere, exitPos)), Level.OVERWORLD, respawn);
            helper.assertTrue(missing.level() == overworld && missing.pos().equals(respawn),
                    "an exit in a dimension that no longer exists: falls back to the respawn");
        });
        helper.succeed();
    }

    /** A bed slept in Mitofus must not send a disconnection from Mitofus to Mitofus. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aRespawnInMitofusLeadsToTheRealWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        ServerLevel notOverworld = levelOtherThanTheOverworld(helper);
        if (notOverworld == null) {
            helper.fail("BLOCKED: no level besides the overworld to play Mitofus");
            return;
        }
        BlockPos bed = helper.absolutePos(new BlockPos(2, 1, 2));
        try {
            MitofusWorld.standIn(notOverworld);
            VrHelmetService.Destination destination = VrHelmetService.destination(server, Optional.empty(), notOverworld.dimension(), bed);
            helper.assertFalse(MitofusWorld.is(destination.level()), "never Mitofus");
            helper.assertTrue(destination.level() == overworld, "the overworld");
            helper.assertTrue(destination.pos().equals(overworld.getSharedSpawnPos()), "at its spawn, not at the bed of Mitofus");
        } finally {
            MitofusWorld.restoreStandIn();
        }
        helper.succeed();
    }

    // ---- Beds in Mitofus and trips that do not happen ----

    private static boolean spawnSetCancelled(GameTestHelper helper, ServerPlayer player, ResourceKey<Level> dimension) {
        return NeoForge.EVENT_BUS.post(new PlayerSetSpawnEvent(player, dimension, helper.absolutePos(new BlockPos(1, 1, 1)), false)).isCanceled();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBedInMitofusDoesNotSetTheRespawn(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ResourceKey<Level> real = helper.getLevel().dimension() == Level.OVERWORLD ? Level.NETHER : Level.OVERWORLD;
        try {
            MitofusWorld.standIn(null);
            helper.assertTrue(spawnSetCancelled(helper, player, MitofusWorld.LEVEL), "the real Mitofus key: cancelled");
            helper.assertFalse(spawnSetCancelled(helper, player, Level.OVERWORLD), "the overworld: not touched");
            MitofusWorld.standIn(helper.getLevel());
            helper.assertTrue(spawnSetCancelled(helper, player, helper.getLevel().dimension()), "the stand-in's key: cancelled");
            helper.assertFalse(spawnSetCancelled(helper, player, real), "another real dimension: not touched");
        } finally {
            MitofusWorld.restoreStandIn();
        }
        helper.succeed();
    }

    /** Another mod cancels the trip (simulated): the player stays in the real world and so must the saved state. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aConnectionWhoseTripIsCancelledIsUndone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ServerLevel other = otherLevel(helper);
        if (other == null) {
            helper.fail("BLOCKED: no second level to play Mitofus");
            return;
        }
        Consumer<EntityTravelToDimensionEvent> canceller = event -> event.setCanceled(true);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                MitofusWorld.standIn(other);
                NeoForge.EVENT_BUS.addListener(canceller);
                VrHelmetState before = VrHelmetService.state(player);
                helper.assertFalse(VrHelmetService.connect(player, at(helper, new BlockPos(0, 1, 0))), "the trip did not happen: not connected");
                helper.assertTrue(VrHelmetService.state(player).equals(before), "and the saved state is back as it was");
                helper.assertFalse(VrHelmetService.state(player).isConnected(), "not left connected in the real world");
            } finally {
                NeoForge.EVENT_BUS.unregister(canceller);
                MitofusWorld.standIn(helper.getLevel());
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void nestedCrossingsKeepTheOuterFlagOn(GameTestHelper helper) {
        WorldSeparation.crossing(() -> {
            WorldSeparation.crossing(() -> helper.assertTrue(WorldSeparation.crossingAllowed(), "on in the inner one"));
            helper.assertTrue(WorldSeparation.crossingAllowed(), "the inner one does not turn the outer flag off");
        });
        helper.assertFalse(WorldSeparation.crossingAllowed(), "off after the outer one");
        helper.succeed();
    }
}
