package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.save.SaveMigrations;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetState;
import com.orijua.mitofus.feature.vrhelmet.LogoutService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/**
 * Logging in and out through the real events ({@code PlayerLoggedInEvent} / {@code PlayerLoggedOutEvent} posted on the
 * game bus), not by calling the services by hand: what matters here is that the listeners are registered, that they
 * run in the right order and that each one only touches its own player. Posting the login event runs every listener
 * of the mod (phone, starter VR helmet, terminal...), so each test only looks at what it is about.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class LoginEventTests {
    private LoginEventTests() {
    }

    private static void logIn(ServerPlayer player) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    }

    private static void logOut(ServerPlayer player) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
    }

    private static GlobalPos vrhelmet(GameTestHelper helper) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 1, 0)));
    }

    /** The save migrations run at the highest priority: a listener of any other priority already sees the data brought up to date. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theMigrationRunsBeforeTheOtherLoginListeners(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.SAVE_VERSION.get(), SaveMigrations.BASELINE - 1); // an older save than any step knows
        int[] seen = {-1};
        for (EventPriority priority : new EventPriority[] {EventPriority.HIGH, EventPriority.NORMAL, EventPriority.LOWEST}) {
            seen[0] = -1;
            player.setData(ModAttachments.SAVE_VERSION.get(), SaveMigrations.BASELINE - 1);
            Consumer<PlayerEvent.PlayerLoggedInEvent> spy = event -> {
                if (event.getEntity() == player) {
                    seen[0] = player.getData(ModAttachments.SAVE_VERSION.get());
                }
            };
            NeoForge.EVENT_BUS.addListener(priority, spy);
            try {
                logIn(player);
            } finally {
                NeoForge.EVENT_BUS.unregister(spy);
            }
            helper.assertTrue(seen[0] == SaveMigrations.CURRENT, "a " + priority + " listener saw save version " + seen[0]
                    + " instead of " + SaveMigrations.CURRENT);
        }
        helper.assertTrue(player.getData(ModAttachments.SAVE_VERSION.get()) == SaveMigrations.CURRENT, "stamped with the current version");
        helper.succeed();
    }

    /** Logging in outside Mitofus still "connected" (the server went down in there): the body is released and the rested bonus is gone. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aLoginOutsideMitofusReleasesTheBody(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        GlobalPos vrhelmet = vrhelmet(helper);
        player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY.connected(vrhelmet, vrhelmet));
        player.setData(ModAttachments.RESTED_SECONDS.get(), 120);
        VrHelmetTests.outsideMitofus(helper, () -> {
            logIn(player);
            helper.assertFalse(VrHelmetService.state(player).isConnected(), "the body leaves the vrhelmet");
            helper.assertTrue(player.getData(ModAttachments.RESTED_SECONDS.get()) == 0, "and the rested bonus does not outlive it");
        });
        helper.succeed();
    }

    /** Leaving the server cancels the connection and the logout countdown of that player, and of nobody else. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aLogoutClearsOnlyThatPlayersPendingThings(GameTestHelper helper) {
        ServerPlayer leaving = TestSupport.fakePlayer(helper);
        ServerPlayer staying = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                VrHelmetService.start(leaving, vrhelmet(helper));
                VrHelmetService.start(staying, vrhelmet(helper));
                helper.assertTrue(LogoutService.start(leaving) == LogoutService.Start.STARTED, "the countdown of the one who leaves");
                helper.assertTrue(LogoutService.start(staying) == LogoutService.Start.STARTED, "the countdown of the other");

                logOut(leaving);

                helper.assertFalse(VrHelmetService.isConnecting(leaving), "his connection is gone");
                helper.assertFalse(LogoutService.isCountingDown(leaving), "and his countdown");
                helper.assertTrue(VrHelmetService.isConnecting(staying), "the other one is still connecting");
                helper.assertTrue(LogoutService.isCountingDown(staying), "and still counting down");
            } finally {
                VrHelmetService.cancel(leaving, false);
                VrHelmetService.cancel(staying, false);
                LogoutService.forget(leaving);
                LogoutService.forget(staying);
            }
        });
        helper.succeed();
    }

    /** Dropping out in Mitofus and coming back: the state is the same, nothing is lost and nobody is moved. */
    @GameTest(template = TestSupport.EMPTY)
    public static void anAbruptExitInMitofusLeavesAConsistentState(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VrHelmetTests.inMitofus(helper, helper.absolutePos(new BlockPos(1, 1, 1)), () -> {
            try {
                helper.assertTrue(VrHelmetService.connect(player, vrhelmet(helper)), "connects");
                VrHelmetState connected = VrHelmetService.state(player);
                logOut(player);
                logIn(player);
                helper.assertTrue(VrHelmetService.state(player).equals(connected), "connected in Mitofus: the body stays in its vrhelmet");

                player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY);
                logOut(player);
                logIn(player);
                helper.assertTrue(VrHelmetService.state(player).equals(VrHelmetState.EMPTY), "without a body in Mitofus it is only reported, nothing changes");
            } finally {
                LogoutService.forget(player);
                VrHelmetService.cancel(player, false);
            }
        });
        helper.succeed();
    }
}
