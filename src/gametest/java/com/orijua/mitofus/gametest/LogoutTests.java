package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.LogoutRules;
import com.orijua.mitofus.feature.vrhelmet.LogoutService;
import com.orijua.mitofus.feature.world.Altamira;
import com.orijua.mitofus.feature.world.AltamiraProtection;
import com.orijua.mitofus.feature.world.MitofusWorld;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@code /mitofus logout}: the countdown, the refusal after combat and every way it is cancelled. The test level
 * plays Mitofus; ticks are given by hand (a fake player does not tick). each test clears only its own player's
 * tables ({@code forget(player)}); {@code forgetAll()} is left to the one test that is about it.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class LogoutTests {
    private LogoutTests() {
    }

    private static void tickMany(ServerPlayer player, int ticks) {
        TestSupport.tickMany(player, ticks, LogoutService::tick);
    }

    private static GlobalPos vrhelmet(GameTestHelper helper) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)));
    }

    private static void damage(ServerPlayer player) {
        TestSupport.hurt(player, 1f);
    }

    /** Connected, counting down, then {@code event} happens: the countdown stops and nobody is disconnected. */
    private static void cancelledBy(GameTestHelper helper, String what, Consumer<ServerPlayer> event) {
        cancelledBy(helper, what, true, event);
    }

    /** As above; {@code bodyStays} is whether the body is still in the VR helmet afterwards (nobody was disconnected). */
    private static void cancelledBy(GameTestHelper helper, String what, boolean bodyStays, Consumer<ServerPlayer> event) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), what + ": connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, what + ": the countdown starts");
                event.accept(player);
                helper.assertFalse(LogoutService.isCountingDown(player), what + ": the countdown is over");
                tickMany(player, LogoutRules.COUNTDOWN_TICKS + 5);
                helper.assertTrue(VrHelmetService.state(player).lastSave().isEmpty(), what + ": nothing was saved");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent() == bodyStays,
                        what + ": nobody was disconnected (the body " + (bodyStays ? "is" : "is not") + " in the vrhelmet)");
            } finally {
                LogoutService.forget(player);
                VrHelmetService.cancel(player, false);
            }
        });
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void outsideMitofusThereIsNothingToLogOutFrom(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.outsideMitofus(helper, () -> {
            try {
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.NOT_IN_MITOFUS, "the real world");
                helper.assertFalse(LogoutService.isCountingDown(player), "no countdown");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    /** The window's "Disconnect" button goes through {@link LogoutService#request}: the same countdown as the command. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theWindowButtonStartsTheSameCountdown(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.request(player) == LogoutService.Start.STARTED, "the request starts the countdown");
                helper.assertTrue(LogoutService.isCountingDown(player), "and it is counting down");
                helper.assertTrue(LogoutService.request(player) == LogoutService.Start.ALREADY_RUNNING, "a second press does not restart it");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void combatInTheLastThreeSecondsRefusesTheLogout(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                long now = helper.getLevel().getServer().getTickCount();
                LogoutService.markCombat(player, now - (LogoutRules.DANGER_TICKS - 1));
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.IN_DANGER, "59 ticks ago: refused");
                helper.assertFalse(LogoutService.isCountingDown(player), "and nothing starts");
                LogoutService.markCombat(player, now - LogoutRules.DANGER_TICKS);
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "60 ticks ago: allowed");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    @GameTest(template = Altamira.HALL)
    public static void altamiraAllowsLogoutAfterTheCombatWait(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos hall = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos inside = helper.absolutePos(new BlockPos(3, 2, 3));
        var cave = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(Altamira.KEY);
        var chunk = level.getChunkAt(inside);
        var startsBefore = Map.copyOf(chunk.getAllStarts());
        var referencesBefore = Map.copyOf(chunk.getAllReferences());
        var templates = level.getStructureManager();
        var element = StructurePoolElement.single(Mitofus.id(Altamira.HALL).toString()).apply(StructureTemplatePool.Projection.RIGID);
        var piece = new PoolElementStructurePiece(templates, element, hall, 0, Rotation.NONE,
                element.getBoundingBox(templates, hall, Rotation.NONE), LiquidSettings.APPLY_WATERLOGGING);
        var player = TestSupport.fakePlayer(helper);

        // GameTest places the hall's blocks but does not register it as a generated structure.
        var references = new HashMap<>(referencesBefore);
        references.put(cave, LongOpenHashSet.of(chunk.getPos().toLong()));
        try {
            chunk.setStartForStructure(cave, new StructureStart(cave, chunk.getPos(), 0, new PiecesContainer(List.of(piece))));
            chunk.setAllReferences(references);
            VrHelmetTests.inMitofus(helper, inside, () -> {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                Vec3 position = Vec3.atBottomCenterOf(inside);
                player.setPos(position.x, position.y, position.z);
                helper.assertTrue(AltamiraProtection.inCave(level, player.blockPosition()), "inside a registered cave piece");

                long now = level.getServer().getTickCount();
                LogoutService.markCombat(player, now - LogoutRules.DANGER_TICKS + 1);
                helper.assertTrue(LogoutService.request(player) == LogoutService.Start.IN_DANGER, "combat still blocks logout");
                helper.assertFalse(LogoutService.isCountingDown(player), "combat does not start the countdown");
                LogoutService.markCombat(player, now - LogoutRules.DANGER_TICKS);
                helper.assertTrue(LogoutService.request(player) == LogoutService.Start.STARTED, "starts once the combat wait ends");

                tickMany(player, LogoutRules.COUNTDOWN_TICKS - 1);
                helper.assertTrue(LogoutService.isCountingDown(player), "still counting at tick 59");
                tickMany(player, 1);
                helper.assertFalse(LogoutService.isCountingDown(player), "the countdown is over");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "the body is back");
                helper.assertTrue(VrHelmetService.state(player).lastSave().equals(Optional.of(inside)), "the cave position is saved");
            });
        } finally {
            LogoutService.forget(player);
            VrHelmetService.cancel(player, false);
            chunk.setAllStarts(startsBefore);
            chunk.setAllReferences(referencesBefore);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void damageTakenRefusesTheLogout(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                damage(player);
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.IN_DANGER, "just hurt: refused");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void damageDealtRefusesTheLogout(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                LivingEntity zombie = EntityType.ZOMBIE.create(helper.getLevel());
                NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(zombie, new DamageContainer(player.damageSources().playerAttack(player), 1f)));
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.IN_DANGER, "just hit a mob: refused");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theCountdownEndsInTheDisconnection(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                tickMany(player, LogoutRules.COUNTDOWN_TICKS - 1);
                helper.assertTrue(LogoutService.isCountingDown(player), "still counting at tick 59");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "still connected");
                tickMany(player, 1);
                helper.assertFalse(LogoutService.isCountingDown(player), "the countdown is over");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "the body is back");
                helper.assertTrue(VrHelmetService.state(player).lastSave().isPresent(), "and the spot is saved");
            } finally {
                LogoutService.forget(player);
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void askingTwiceDoesNotRestartTheCount(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                tickMany(player, 30);
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.ALREADY_RUNNING, "the second call only warns");
                tickMany(player, LogoutRules.COUNTDOWN_TICKS - 31);
                helper.assertTrue(LogoutService.isCountingDown(player), "59 ticks from the first call: still counting");
                tickMany(player, 1);
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "60 ticks from the first call: done");
            } finally {
                LogoutService.forget(player);
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void damageTakenDuringTheCountdownCancelsIt(GameTestHelper helper) {
        cancelledBy(helper, "damage", LogoutTests::damage);
        helper.succeed();
    }

    private static void moveAndTick(ServerPlayer player, double dx, double dy, double dz) {
        player.setPos(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
        LogoutService.tick(player);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void movingDuringTheCountdownCancelsIt(GameTestHelper helper) {
        cancelledBy(helper, "move X", player -> moveAndTick(player, 0.6, 0, 0));
        cancelledBy(helper, "move Y", player -> moveAndTick(player, 0, -0.6, 0));
        cancelledBy(helper, "move +Y", player -> moveAndTick(player, 0, 0.6, 0));
        cancelledBy(helper, "move +Z", player -> moveAndTick(player, 0, 0, 0.6));
        cancelledBy(helper, "move -Z", player -> moveAndTick(player, 0, 0, -0.6));
        cancelledBy(helper, "diagonal XY", player -> moveAndTick(player, 0.4, 0.4, 0));
        cancelledBy(helper, "diagonal XYZ", player -> moveAndTick(player, 0.3, 0.3, 0.3));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void creepingAwayIsMeasuredFromTheStartInLogout(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                Vec3 start = player.position();
                player.setPos(start.add(0.3, 0, 0));
                LogoutService.tick(player);
                helper.assertTrue(LogoutService.isCountingDown(player), "0.3 from the start: still counting");
                player.setPos(start.add(0.6, 0, 0));
                LogoutService.tick(player);
                helper.assertFalse(LogoutService.isCountingDown(player), "0.6 from the start (0.3 more): cancelled");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "nobody was disconnected");
                helper.assertTrue(VrHelmetService.state(player).lastSave().isEmpty(), "and nothing was saved");
            } finally {
                LogoutService.forgetAll();
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSmallDiagonalShiftDoesNotCancelTheCountdown(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                player.setPos(player.getX() + 0.3, player.getY(), player.getZ() + 0.3);
                tickMany(player, LogoutRules.COUNTDOWN_TICKS - 1);
                helper.assertTrue(LogoutService.isCountingDown(player), "still counting at tick 59");
                tickMany(player, 1);
                helper.assertFalse(LogoutService.isCountingDown(player), "the countdown is over");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "the body is back");
                helper.assertTrue(VrHelmetService.state(player).lastSave().isPresent(), "and the spot is saved");
            } finally {
                LogoutService.forgetAll();
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void turningAndSmallShiftsDoNotCancelTheCountdown(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                player.setYRot(123f);
                player.setXRot(-40f);
                player.setYHeadRot(77f);
                player.setPos(player.getX() + 0.4, player.getY(), player.getZ());
                tickMany(player, LogoutRules.COUNTDOWN_TICKS - 1);
                helper.assertTrue(LogoutService.isCountingDown(player), "still counting at tick 59");
                tickMany(player, 1);
                helper.assertFalse(LogoutService.isCountingDown(player), "the countdown is over");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "the body is back");
                helper.assertTrue(VrHelmetService.state(player).lastSave().isPresent(), "and the spot is saved");
            } finally {
                LogoutService.forgetAll();
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void movingOnTheLastTickCancelsTheCountdown(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                tickMany(player, LogoutRules.COUNTDOWN_TICKS - 1);
                helper.assertTrue(LogoutService.isCountingDown(player), "one tick before the end");
                moveAndTick(player, 0.6, 0, 0);
                helper.assertFalse(LogoutService.isCountingDown(player), "moving on the last tick cancels");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "nobody was disconnected");
                helper.assertTrue(VrHelmetService.state(player).lastSave().isEmpty(), "and nothing was saved");
            } finally {
                LogoutService.forgetAll();
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void startingAgainAfterMovingCountsFromTheNewSpot(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                moveAndTick(player, 0.6, 0, 0);
                helper.assertFalse(LogoutService.isCountingDown(player), "moving cancels");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "it can start again");
                tickMany(player, LogoutRules.COUNTDOWN_TICKS - 1);
                helper.assertTrue(LogoutService.isCountingDown(player), "still counting from the new spot at tick 59");
                tickMany(player, 1);
                helper.assertFalse(LogoutService.isCountingDown(player), "the countdown is over");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and the body is back");
            } finally {
                LogoutService.forgetAll();
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void deathDuringTheCountdownCancelsIt(GameTestHelper helper) {
        // Dying in Mitofus no longer frees the body: the player reappears in Mitofus, still connected.
        cancelledBy(helper, "death", player -> NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic())));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void respawningDuringTheCountdownCancelsIt(GameTestHelper helper) {
        cancelledBy(helper, "respawn", player -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false)));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void changingDimensionDuringTheCountdownCancelsIt(GameTestHelper helper) {
        cancelledBy(helper, "dimension", player ->
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER)));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void forgettingThePlayerCancelsTheCountdown(GameTestHelper helper) {
        cancelledBy(helper, "forget", LogoutService::forget);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void forgettingEveryoneCancelsEveryCountdown(GameTestHelper helper) {
        ServerPlayer first = TestSupport.fakePlayer(helper);
        ServerPlayer second = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(LogoutService.start(first) == LogoutService.Start.STARTED, "first starts");
                helper.assertTrue(LogoutService.start(second) == LogoutService.Start.STARTED, "second starts");
                LogoutService.forgetAll();
                helper.assertFalse(LogoutService.isCountingDown(first) || LogoutService.isCountingDown(second), "nobody counts any more");
                tickMany(first, LogoutRules.COUNTDOWN_TICKS + 5);
                tickMany(second, LogoutRules.COUNTDOWN_TICKS + 5);
                helper.assertTrue(VrHelmetService.state(first).lastSave().isEmpty() && VrHelmetService.state(second).lastSave().isEmpty(),
                        "and nobody was disconnected");
            } finally {
                LogoutService.forget(first);
                LogoutService.forget(second);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void damageDealtDuringTheCountdownDoesNotCancelIt(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                LivingEntity zombie = EntityType.ZOMBIE.create(helper.getLevel());
                NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(zombie, new DamageContainer(player.damageSources().playerAttack(player), 1f)));
                helper.assertTrue(LogoutService.isCountingDown(player), "hitting a mob does not cancel it");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDeadPlayersCountdownIsCancelledByTheTick(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                player.setHealth(0.0f);
                try {
                    LogoutService.tick(player);
                } finally {
                    player.setHealth(player.getMaxHealth());
                }
                helper.assertFalse(LogoutService.isCountingDown(player), "the tick cancels it");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aTickOutsideMitofusCancelsTheCountdownWithoutDisconnecting(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                MitofusWorld.standIn(null); // the player is now in the real world as far as the mod can tell
                LogoutService.tick(player);
                helper.assertFalse(LogoutService.isCountingDown(player), "the tick cancels it");
                tickMany(player, LogoutRules.COUNTDOWN_TICKS + 5);
                helper.assertTrue(VrHelmetService.state(player).lastSave().isEmpty(), "nothing was saved");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "and nobody was disconnected");
            } finally {
                LogoutService.forget(player);
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void zeroDamageIsNotCombatAndDoesNotCancel(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                TestSupport.hurt(player, 0f);
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "no combat was marked: allowed");
                TestSupport.hurt(player, 0f);
                helper.assertTrue(LogoutService.isCountingDown(player), "and zero damage does not cancel the count");
                tickMany(player, 5);
                helper.assertTrue(LogoutService.isCountingDown(player), "still counting");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    /** Inside Mitofus without a VR helmet body (no exit point): the countdown still ends in a disconnection, to the respawn. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theCountdownWithoutAVrHelmetEndsInTheRespawn(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                BlockPos where = player.blockPosition();
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "no vrhelmet connected");
                helper.assertTrue(LogoutService.start(player) == LogoutService.Start.STARTED, "starts");
                tickMany(player, LogoutRules.COUNTDOWN_TICKS);
                helper.assertFalse(LogoutService.isCountingDown(player), "the countdown is over");
                helper.assertTrue(VrHelmetService.state(player).lastSave().equals(java.util.Optional.of(where)), "the spot is saved");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and there is still no body");
            } finally {
                LogoutService.forget(player);
            }
        });
        helper.succeed();
    }

    // ---- A quest with a deadline running ----

    private static final net.minecraft.resources.ResourceLocation TIMED = Mitofus.id("velascos_contest");

    private static void withDeadline(ServerPlayer player) {
        player.setData(com.orijua.mitofus.core.registry.ModAttachments.PLAYER_QUESTS,
                com.orijua.mitofus.feature.quest.PlayerQuests.EMPTY.accept(TIMED, 0).withDeadline(TIMED, 10_000_000L));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aQuestWithADeadlineBlocksTheVoluntaryLogout(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                withDeadline(player);
                helper.assertTrue(LogoutService.request(player) == LogoutService.Start.QUEST_DEADLINE, "refused while the deadline runs");
                helper.assertFalse(LogoutService.isCountingDown(player), "and nothing starts");
            } finally {
                LogoutService.forget(player);
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void forcingTheLogoutFailsTheQuestWhenTheCountdownEnds(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                withDeadline(player);
                helper.assertTrue(LogoutService.request(player, true) == LogoutService.Start.STARTED, "forced: the countdown starts");
                helper.assertTrue(com.orijua.mitofus.feature.quest.QuestService.get(player).isActive(TIMED), "the quest is still active during the countdown");
                tickMany(player, LogoutRules.COUNTDOWN_TICKS + 5);
                helper.assertTrue(com.orijua.mitofus.feature.quest.QuestService.get(player).isFailed(TIMED), "the quest failed on disconnecting");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and the player is disconnected");
            } finally {
                LogoutService.forget(player);
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aCancelledForcedLogoutDoesNotFailTheQuest(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                withDeadline(player);
                helper.assertTrue(LogoutService.request(player, true) == LogoutService.Start.STARTED, "forced: the countdown starts");
                LogoutService.cancel(player, false);
                tickMany(player, LogoutRules.COUNTDOWN_TICKS + 5);
                helper.assertTrue(com.orijua.mitofus.feature.quest.QuestService.get(player).isActive(TIMED), "the quest is still active");
            } finally {
                LogoutService.forget(player);
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }
}
