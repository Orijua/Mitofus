package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetRules;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetState;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.orijua.mitofus.gametest.VrHelmetTests.at;
import static com.orijua.mitofus.gametest.VrHelmetTests.inMitofus;
import static com.orijua.mitofus.gametest.VrHelmetTests.otherLevel;
import static com.orijua.mitofus.gametest.VrHelmetTests.outsideMitofus;
import static com.orijua.mitofus.gametest.VrHelmetTests.tickMany;

/**
 * The VR helmet's "Connecting…" wait: how long it takes, what cancels it (moving, damage, dying, losing the helmet,
 * changing dimension) and what a stale or repeated connection does. Shared helpers live in {@link VrHelmetTests}.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class VrHelmetWaitTests {
    private VrHelmetWaitTests() {
    }

    /** The wait takes {@link VrHelmetRules#CONNECT_TICKS}, and only then does the player connect. */
    @GameTest(template = TestSupport.EMPTY)
    public static void connectingTakesThreeSecondsThenConnects(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, new BlockPos(0, 1, 1), VrHelmetTier.BASIC);
        BlockPos covadonga = helper.absolutePos(new BlockPos(1, 1, 1));
        inMitofus(helper, covadonga, () -> {
            VrHelmetService.start(player, at(helper, new BlockPos(0, 1, 1)));
            helper.assertTrue(VrHelmetService.isConnecting(player), "lying down starts the wait");
            tickMany(player, VrHelmetRules.CONNECT_TICKS - 1);
            helper.assertTrue(VrHelmetService.isConnecting(player), "still waiting one tick before the end");
            helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "not connected yet");
            VrHelmetService.tick(player);
            helper.assertFalse(VrHelmetService.isConnecting(player), "the wait is over");
            helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "and the player is connected");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void losingTheVrHelmetOrBeingOutOfReachCancelsTheConnection(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos vrhelmet = new BlockPos(0, 1, 1);
        TestSupport.lieBody(helper, vrhelmet, VrHelmetTier.BASIC);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            player.setPos(helper.absoluteVec(new Vec3(0.5, 1, 0.5)));
            VrHelmetService.start(player, at(helper, vrhelmet));
            TestSupport.removeBody(helper, vrhelmet);
            VrHelmetService.tick(player);
            helper.assertFalse(VrHelmetService.isConnecting(player), "the vrhelmet gone cancels");
            helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and nothing connected");

            // Standing still but already out of reach (a VR helmet farther than MAX_DISTANCE): cancelled too.
            BlockPos far = new BlockPos(0, 1, 6);
            TestSupport.lieBody(helper, far, VrHelmetTier.BASIC);
            player.setPos(helper.absoluteVec(new Vec3(0.5, 1, 0.5)));
            helper.assertFalse(VrHelmetRules.inReach(player.distanceToSqr(helper.absolutePos(far).getCenter())),
                    "the vrhelmet is out of reach");
            VrHelmetService.start(player, at(helper, far));
            VrHelmetService.tick(player);
            helper.assertFalse(VrHelmetService.isConnecting(player), "out of reach without moving cancels");
            helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and nothing connected");
        });
        helper.succeed();
    }

    private static final Vec3 SPOT = new Vec3(0.5, 1, 0.5);

    /** The wait starts with the player at {@link #SPOT} beside a VR helmet at (0, 1, 1). */
    private static GlobalPos startWaiting(GameTestHelper helper, ServerPlayer player) {
        BlockPos vrhelmet = new BlockPos(0, 1, 1);
        TestSupport.lieBody(helper, vrhelmet, VrHelmetTier.BASIC);
        player.setPos(helper.absoluteVec(SPOT));
        GlobalPos global = at(helper, vrhelmet);
        VrHelmetService.start(player, global);
        return global;
    }

    private static void shiftTo(GameTestHelper helper, ServerPlayer player, double dx, double dy, double dz) {
        player.setPos(helper.absoluteVec(SPOT.add(dx, dy, dz)));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void movingDuringTheWaitCancelsTheConnection(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            double[][] shifts = {{0.6, 0, 0}, {0, 0.6, 0}, {0, -0.6, 0}, {0, 0, 0.6}, {0.4, 0.4, 0}, {0.3, 0.3, 0.3}};
            try {
                for (double[] shift : shifts) {
                    startWaiting(helper, player);
                    shiftTo(helper, player, shift[0], shift[1], shift[2]);
                    VrHelmetService.tick(player);
                    helper.assertFalse(VrHelmetService.isConnecting(player), "moving cancels (" + shift[0] + ", " + shift[1] + ", " + shift[2] + ")");
                    helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and nothing connected");
                    tickMany(player, VrHelmetRules.CONNECT_TICKS + 5);
                    helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "it never connects afterwards");
                }
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void turningAndSmallShiftsDoNotCancelTheConnection(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                startWaiting(helper, player);
                player.setYRot(123f);
                player.setXRot(-40f);
                player.setYHeadRot(77f);
                shiftTo(helper, player, 0.4, 0, 0);
                tickMany(player, VrHelmetRules.CONNECT_TICKS - 1);
                helper.assertTrue(VrHelmetService.isConnecting(player), "turning and a small shift keep waiting");
                VrHelmetService.tick(player);
                helper.assertFalse(VrHelmetService.isConnecting(player), "the wait is over");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "and it connects");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSmallDiagonalShiftDoesNotCancelTheConnection(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                startWaiting(helper, player);
                shiftTo(helper, player, 0.3, 0.3, 0);
                tickMany(player, VrHelmetRules.CONNECT_TICKS - 1);
                helper.assertTrue(VrHelmetService.isConnecting(player), "a small diagonal shift keeps waiting");
                VrHelmetService.tick(player);
                helper.assertFalse(VrHelmetService.isConnecting(player), "the wait is over");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "and it connects");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void creepingAwayIsMeasuredFromTheStart(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                startWaiting(helper, player);
                shiftTo(helper, player, 0.3, 0, 0);
                VrHelmetService.tick(player);
                helper.assertTrue(VrHelmetService.isConnecting(player), "0.3 from the start: still waiting");
                shiftTo(helper, player, 0.6, 0, 0);
                VrHelmetService.tick(player);
                helper.assertFalse(VrHelmetService.isConnecting(player), "0.6 from the start (0.3 more): cancelled");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and nothing connected");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void movingOnTheLastTickCancelsTheConnection(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                startWaiting(helper, player);
                tickMany(player, VrHelmetRules.CONNECT_TICKS - 1);
                helper.assertTrue(VrHelmetService.isConnecting(player), "one tick before the end");
                shiftTo(helper, player, 0.6, 0, 0);
                VrHelmetService.tick(player);
                helper.assertFalse(VrHelmetService.isConnecting(player), "moving on the last tick cancels");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and it does not connect");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void lyingDownAgainAfterMovingWorks(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                GlobalPos vrhelmet = startWaiting(helper, player);
                shiftTo(helper, player, 0.6, 0, 0);
                VrHelmetService.tick(player);
                helper.assertFalse(VrHelmetService.isConnecting(player), "moving cancels");
                VrHelmetService.start(player, vrhelmet);
                tickMany(player, VrHelmetRules.CONNECT_TICKS);
                helper.assertFalse(VrHelmetService.isConnecting(player), "the new wait is over");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "and it connects from the new spot");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void cancellingForgetsTheConnectionAndCanStartAgain(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, new BlockPos(0, 1, 1), VrHelmetTier.BASIC);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 1));
            VrHelmetService.start(player, vrhelmet);
            VrHelmetService.cancel(player, false);
            helper.assertFalse(VrHelmetService.isConnecting(player), "damage or logging out cancels");
            tickMany(player, VrHelmetRules.CONNECT_TICKS + 5);
            helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "a cancelled connection never completes");
            VrHelmetService.start(player, vrhelmet);
            helper.assertTrue(VrHelmetService.isConnecting(player), "and lying down again works");
            VrHelmetService.cancel(player, false);
        });
        helper.succeed();
    }

    // ---- Hardening: stale connections, double clicks, fall damage, death and login ----

    @GameTest(template = TestSupport.EMPTY)
    public static void takingDamageCancelsTheWait(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, new BlockPos(0, 1, 1), VrHelmetTier.BASIC);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                VrHelmetService.start(player, at(helper, new BlockPos(0, 1, 1)));
                TestSupport.hurt(player, 1f);
                helper.assertFalse(VrHelmetService.isConnecting(player), "damage wakes you up");
                tickMany(player, VrHelmetRules.CONNECT_TICKS + 5);
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and nothing connects");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void dyingWhileConnectingDoesNotConnectOnRespawn(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, new BlockPos(0, 1, 1), VrHelmetTier.BASIC);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 1));
                VrHelmetService.start(player, vrhelmet);
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
                helper.assertFalse(VrHelmetService.isConnecting(player), "dying ends the wait");
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
                tickMany(player, VrHelmetRules.CONNECT_TICKS + 5);
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "the new body does not connect by itself");

                VrHelmetService.start(player, vrhelmet);
                player.setHealth(0.0f);
                try {
                    VrHelmetService.tick(player);
                } finally {
                    player.setHealth(player.getMaxHealth());
                }
                helper.assertFalse(VrHelmetService.isConnecting(player), "a dead player's wait is cancelled by the tick");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void lyingDownTwiceStartsOnce(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ServerLevel other = otherLevel(helper);
        if (other == null) {
            helper.fail("BLOCKED: no second level to play Mitofus");
            return;
        }
        BlockPos vrhelmet = new BlockPos(0, 1, 1);
        TestSupport.lieBody(helper, vrhelmet, VrHelmetTier.BASIC);
        // Another level plays Mitofus while the clicks happen; the test level takes over for the arrival (a fake player cannot cross dimensions).
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                MitofusWorld.standIn(other);
                helper.assertTrue(VrHelmetService.begin(player, helper.absolutePos(vrhelmet)) == VrHelmetService.Begin.STARTED, "the first click starts");
                tickMany(player, 10);
                helper.assertTrue(VrHelmetService.begin(player, helper.absolutePos(vrhelmet)) == VrHelmetService.Begin.ALREADY_CONNECTING, "the second does not");
                helper.assertTrue(VrHelmetService.isConnecting(player), "still connecting");
                MitofusWorld.standIn(helper.getLevel());
                tickMany(player, VrHelmetRules.CONNECT_TICKS - 1 - 10);
                helper.assertTrue(VrHelmetService.isConnecting(player) && VrHelmetService.state(player).vrhelmet().isEmpty(),
                        "one tick before CONNECT_TICKS counted from the first click: still waiting");
                tickMany(player, 1);
                helper.assertFalse(VrHelmetService.isConnecting(player), "the count was not restarted: done CONNECT_TICKS after the first click");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isPresent(), "and the player is connected");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    /** A respawn alone (no death before it) also drops a pending connection: the new entity has the same UUID. */
    @GameTest(template = TestSupport.EMPTY)
    public static void respawnAloneDropsAPendingConnection(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, new BlockPos(0, 1, 1), VrHelmetTier.BASIC);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                VrHelmetService.start(player, at(helper, new BlockPos(0, 1, 1)));
                helper.assertTrue(VrHelmetService.isConnecting(player), "the wait is running");
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, false));
                helper.assertFalse(VrHelmetService.isConnecting(player), "the respawn drops it");
                tickMany(player, VrHelmetRules.CONNECT_TICKS + 5);
                helper.assertFalse(VrHelmetService.isConnecting(player), "and it does not come back");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "nothing connects");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    /** A VR helmet in another dimension than the player's cannot be waited on. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aVrHelmetInAnotherDimensionCancelsTheWait(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        outsideMitofus(helper, () -> {
            try {
                GlobalPos elsewhere = GlobalPos.of(helper.getLevel().dimension() == Level.END ? Level.NETHER : Level.END, helper.absolutePos(new BlockPos(0, 1, 1)));
                VrHelmetService.start(player, elsewhere);
                helper.assertTrue(VrHelmetService.isConnecting(player), "the wait starts");
                VrHelmetService.tick(player);
                helper.assertFalse(VrHelmetService.isConnecting(player), "the vrhelmet is in another dimension: cancelled");
                helper.assertTrue(VrHelmetService.state(player).vrhelmet().isEmpty(), "and nothing connected");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void changingDimensionCancelsThePendingConnection(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.lieBody(helper, new BlockPos(0, 1, 1), VrHelmetTier.BASIC);
        outsideMitofus(helper, () -> {
            try {
                VrHelmetService.start(player, at(helper, new BlockPos(0, 1, 1)));
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
                helper.assertFalse(VrHelmetService.isConnecting(player), "changing dimension forgets the wait");
            } finally {
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void lyingDownWhileConnectedIsRefused(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ServerLevel other = otherLevel(helper);
        if (other == null) {
            helper.fail("BLOCKED: no second level to play Mitofus");
            return;
        }
        try {
            MitofusWorld.standIn(other);
            player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY.connected(at(helper, new BlockPos(0, 1, 0)), at(helper, new BlockPos(1, 1, 0))));
            helper.assertTrue(VrHelmetService.begin(player, helper.absolutePos(new BlockPos(0, 1, 1))) == VrHelmetService.Begin.ALREADY_CONNECTED,
                    "already connected: refused");
            helper.assertFalse(VrHelmetService.isConnecting(player), "and no wait starts");
        } finally {
            MitofusWorld.restoreStandIn();
            VrHelmetService.cancel(player, false);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void connectingTwiceKeepsTheFirstExit(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            GlobalPos vrhelmet = at(helper, new BlockPos(0, 1, 0));
            helper.assertTrue(VrHelmetService.connect(player, vrhelmet), "it connects");
            VrHelmetState first = VrHelmetService.state(player);
            BlockPos moved = helper.absolutePos(new BlockPos(3, 1, 3));
            player.setPos(moved.getX() + 0.5, moved.getY(), moved.getZ() + 0.5);
            helper.assertFalse(VrHelmetService.connect(player, at(helper, new BlockPos(2, 1, 2))), "a second connection is refused");
            helper.assertTrue(VrHelmetService.state(player).equals(first), "the first exit and vrhelmet stay");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void arrivingResetsTheFall(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            player.fallDistance = 10f;
            helper.assertTrue(VrHelmetService.connect(player, at(helper, new BlockPos(0, 1, 0))), "it connects");
            helper.assertTrue(player.fallDistance == 0f, "no fall damage on arrival");
            player.fallDistance = 10f;
            helper.assertTrue(VrHelmetService.disconnect(player, VrHelmetService.Reason.MANUAL), "it disconnects");
            helper.assertTrue(player.fallDistance == 0f, "nor on leaving");
        });
        helper.succeed();
    }
}
