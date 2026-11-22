package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.view.ViewRules;
import com.orijua.mitofus.feature.view.Observation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.UUID;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** View: observing someone (sneaking while looking) for ten seconds gives a point, once per target and day. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class ViewTests {
    private ViewTests() {
    }

    /** Observes {@code target} for {@code ticks} ticks; returns how many points that gave. */
    private static int observeFor(ServerPlayer player, Villager target, int ticks) {
        return observeFor(player, target, ticks, player.level().getGameTime());
    }

    /** As {@link #observeFor(ServerPlayer, Villager, int)} at a chosen game time (does not touch the world's clock). */
    private static int observeFor(ServerPlayer player, Villager target, int ticks, long gameTime) {
        int gained = 0;
        for (int i = 0; i < ticks; i++) {
            if (Observation.observe(player, target, gameTime)) {
                gained++;
            }
        }
        return gained;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void observingTeachesOncePerTargetAndDay(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Observation.forget(player);
        Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 1));

        helper.assertTrue(observeFor(player, first, ViewRules.OBSERVE_TICKS - 1) == 0, "not before ten seconds");
        Observation.stop(player);
        helper.assertTrue(observeFor(player, first, ViewRules.OBSERVE_TICKS - 1) == 0, "stopping starts it again");
        helper.assertTrue(observeFor(player, first, 1) == 1 && Observation.view(player) == 1, "a point after ten steady seconds");
        helper.assertTrue(observeFor(player, first, ViewRules.OBSERVE_TICKS * 2) == 0, "the same villager teaches once a day");
        helper.assertTrue(observeFor(player, second, ViewRules.OBSERVE_TICKS) == 1 && Observation.view(player) == 2, "another one does");

        // The next day the same villager teaches again; the one learned from today stays blocked until then.
        long t = 5 * ViewRules.DAY_TICKS + 100;
        Observation.forget(player);
        helper.assertTrue(observeFor(player, first, ViewRules.OBSERVE_TICKS, t) == 1, "wins at day t");
        Observation.stop(player);
        helper.assertTrue(observeFor(player, first, ViewRules.OBSERVE_TICKS, t) == 0, "not twice the same day");
        Observation.stop(player);
        helper.assertTrue(observeFor(player, first, ViewRules.OBSERVE_TICKS, t + ViewRules.DAY_TICKS) == 1, "wins again a day later");
        Observation.stop(player);
        helper.assertTrue(observeFor(player, second, ViewRules.OBSERVE_TICKS, t + ViewRules.DAY_TICKS) == 1,
                "a target learned yesterday doesn't block another today");
        Observation.stop(player);

        Observation.forget(player);
        player.setData(ModAttachments.VIEW, ViewRules.MEDIUM - 1);
        helper.assertTrue(observeFor(player, first, ViewRules.OBSERVE_TICKS) == 1, "crossing a threshold works too");
        helper.assertTrue(ViewRules.detail(Observation.view(player)) == ViewRules.Detail.MEDIUM, "now medium detail");
        first.discard();
        second.discard();
        helper.succeed();
    }

    /** Losing the bar (looking away, logging out) doesn't give the day back: only the saved state counts. */
    @GameTest(template = TestSupport.EMPTY)
    public static void lookingAwayDoesNotResetTheDay(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Observation.forget(player);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.assertTrue(observeFor(player, villager, ViewRules.OBSERVE_TICKS) == 1, "learned once");
        Observation.stop(player); // what logging out does to the bar
        helper.assertTrue(player.getData(ModAttachments.VIEW_LEARNED).has(villager.getUUID(),
                ViewRules.day(player.level().getGameTime())), "still remembered");
        helper.assertTrue(observeFor(player, villager, ViewRules.OBSERVE_TICKS) == 0, "still blocked");
        villager.discard();
        helper.succeed();
    }

    /** Ticks in a row without seeing anyone (the target is null). */
    private static void missFor(ServerPlayer player, int ticks, long gameTime) {
        for (int i = 0; i < ticks; i++) {
            Observation.observe(player, null, gameTime);
        }
    }

    /** Observes a fast villager: its previous position is set 0.3 blocks behind before every tick seen. */
    private static int observeFastFor(ServerPlayer player, Villager target, int ticks, long gameTime) {
        int gained = 0;
        for (int i = 0; i < ticks; i++) {
            target.xo = target.getX() - 0.3;
            if (Observation.observe(player, target, gameTime)) {
                gained++;
            }
        }
        return gained;
    }

    private static final long FIXED_TIME = 100;

    @GameTest(template = TestSupport.EMPTY)
    public static void graceKeepsProgress(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Observation.forget(player);
        player.setData(ModAttachments.VIEW, 0);
        int needed = ViewRules.OBSERVE_TICKS;
        Villager v = still(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(observeFor(player, v, needed - 10, FIXED_TIME) == 0, "not yet");
        missFor(player, ViewRules.LOOK_GRACE_BASE_TICKS, FIXED_TIME);
        helper.assertTrue(observeFor(player, v, 9, FIXED_TIME) == 0, "the grace adds no study time");
        helper.assertTrue(observeFor(player, v, 1, FIXED_TIME) == 1, "progress kept through the grace");
        v.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void gracePassedResets(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Observation.forget(player);
        player.setData(ModAttachments.VIEW, 0);
        int needed = ViewRules.OBSERVE_TICKS;
        Villager v = still(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(observeFor(player, v, needed - 1, FIXED_TIME) == 0, "not yet");
        missFor(player, ViewRules.LOOK_GRACE_BASE_TICKS + 1, FIXED_TIME);
        helper.assertTrue(observeFor(player, v, needed - 1, FIXED_TIME) == 0, "past the grace it starts again");
        helper.assertTrue(observeFor(player, v, 1, FIXED_TIME) == 1, "and a full run still works");
        v.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anotherTargetDuringGraceResets(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Observation.forget(player);
        player.setData(ModAttachments.VIEW, 0);
        int needed = ViewRules.OBSERVE_TICKS;
        Villager first = still(helper, new BlockPos(1, 1, 1));
        Villager second = still(helper, new BlockPos(2, 1, 1));
        helper.assertTrue(observeFor(player, first, 150, FIXED_TIME) == 0, "first studied");
        missFor(player, 1, FIXED_TIME);
        helper.assertTrue(observeFor(player, second, 1, FIXED_TIME) == 0, "second starts");
        helper.assertTrue(observeFor(player, first, needed - 1, FIXED_TIME) == 0, "the first one starts again from nothing");
        helper.assertTrue(observeFor(player, first, 1, FIXED_TIME) == 1, "the full run on the first one does give the point");
        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void stopResetsAtOnce(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Observation.forget(player);
        player.setData(ModAttachments.VIEW, 0);
        int needed = ViewRules.OBSERVE_TICKS;
        Villager v = still(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(observeFor(player, v, needed - 10, FIXED_TIME) == 0, "not yet");
        missFor(player, 1, FIXED_TIME);
        Observation.stop(player);
        helper.assertTrue(observeFor(player, v, 10, FIXED_TIME) == 0, "stop erased the progress despite the grace");
        v.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void fastTargetGetsMoreGrace(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Observation.forget(player);
        player.setData(ModAttachments.VIEW, 0);
        int needed = ViewRules.OBSERVE_TICKS;
        Villager v = still(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(observeFastFor(player, v, needed - 10, FIXED_TIME) == 0, "not yet");
        int grace = ViewRules.lookGraceTicks(0.3);
        helper.assertTrue(grace > ViewRules.LOOK_GRACE_BASE_TICKS, "a fast one gets more grace than a still one");
        missFor(player, grace, FIXED_TIME);
        helper.assertTrue(observeFastFor(player, v, 9, FIXED_TIME) == 0, "no study time from the grace");
        helper.assertTrue(observeFastFor(player, v, 1, FIXED_TIME) == 1, "progress kept through the longer grace");

        // Past the grace the progress is lost: a full run is needed again.
        Observation.forget(player);
        player.setData(ModAttachments.VIEW, 0); // the point just gained would shorten the run
        helper.assertTrue(observeFastFor(player, v, needed - 10, FIXED_TIME) == 0, "second run: not yet");
        missFor(player, grace + 1, FIXED_TIME);
        helper.assertTrue(observeFastFor(player, v, 9, FIXED_TIME) == 0, "past the grace: nothing");
        helper.assertTrue(observeFastFor(player, v, needed - 1 - 9, FIXED_TIME) == 0, "needs the whole run again");
        helper.assertTrue(observeFastFor(player, v, 1, FIXED_TIME) == 1, "the full run gives the point");
        v.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void fastTargetGetsMoreAimMargin(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        sneakAhead(player);
        Villager v = still(helper, new BlockPos(1, 1, 2));
        v.xo = v.getX();
        v.zo = v.getZ();
        helper.assertTrue(Observation.target(player) == null, "a still one off the line isn't seen" + why(player, v));
        double stillMargin = Observation.aimMargin(v);
        v.xo = v.getX() - 0.3;
        helper.assertTrue(Observation.aimMargin(v) > stillMargin, "a fast one has more margin");
        helper.assertTrue(Observation.target(player) == v, "a fast one off the line is seen" + why(player, v));
        v.discard();
        helper.succeed();
    }

    private static Villager still(GameTestHelper helper, BlockPos at) {
        Villager villager = helper.spawn(EntityType.VILLAGER, at);
        villager.setNoAi(true);
        return villager;
    }

    /** Sneaks the fake player and aims it level towards +Z (setShiftKeyDown alone may not stick on a FakePlayer). */
    private static void sneakAhead(ServerPlayer player) {
        player.setShiftKeyDown(true);
        player.setPose(Pose.CROUCHING);
        player.setXRot(0.0f);
        player.setYRot(0.0f);
        player.setYHeadRot(0.0f);
    }

    /** Debug text for a failed targeting assertion: sneaking state, eye and villager positions. */
    private static String why(ServerPlayer player, Villager villager) {
        return " [shift=" + player.isShiftKeyDown() + " spectator=" + player.isSpectator() + " eye=" + player.getEyePosition()
                + " view=" + player.getViewVector(1.0f) + " villager=" + villager.position() + " alive=" + villager.isAlive()
                + " los=" + player.hasLineOfSight(villager) + " lookedAt=" + Observation.lookedAt(player) + "]";
    }

    /**
     * The fake player stands at (0.5, 1, 0.5) looking towards +Z. Everything that must be seen stays inside the 3x3x3 template:
     * outside it the game test ring of barriers blocks the line of sight (that is what failed in CI).
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void targetNeedsSneakingReachAndSight(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Villager near = still(helper, new BlockPos(0, 1, 2));
        helper.assertTrue(Observation.target(player) == null, "not sneaking: nobody");
        sneakAhead(player);
        helper.assertTrue(Observation.target(player) == near, "sneaking at a villager 2 blocks away" + why(player, near));

        near.kill();
        helper.assertTrue(Observation.target(player) == null, "a dead one isn't observed");
        near.discard();

        // The reach can't be tested inside 3x3x3 (the barrier ring cuts the sight first): see ViewRulesTest.reachIsInclusive.

        Villager hidden = still(helper, new BlockPos(0, 1, 2));
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.STONE);
        helper.assertTrue(Observation.target(player) == null, "a wall in between");
        hidden.discard();
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.AIR);
        helper.succeed();
    }

    /** Only living creatures that are not players and are alive can be observed (a FakePlayer isn't added to the level). */
    @GameTest(template = TestSupport.EMPTY)
    public static void onlyLivingNonPlayersAreObservable(GameTestHelper helper) {
        Villager villager = still(helper, new BlockPos(0, 1, 2));
        ServerPlayer other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "mitofus-other"));
        helper.assertTrue(Observation.observable(villager), "a villager is observable");
        helper.assertTrue(!Observation.observable(other), "players aren't observable");
        villager.kill();
        helper.assertTrue(!Observation.observable(villager), "a dead one isn't observable");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void nearestCandidateWins(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Villager near = still(helper, new BlockPos(0, 1, 1));
        Villager far = still(helper, new BlockPos(0, 1, 2));
        sneakAhead(player);
        helper.assertTrue(Observation.target(player) == near, "the nearer one in line wins" + why(player, near));
        near.discard();
        far.discard();
        helper.succeed();
    }

    /** Case M1: a fast mob next to the eye (only its widened box contains it) must not steal the target. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aCloseFastMobBesideDoesNotStealTheTarget(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Villager ahead = still(helper, new BlockPos(0, 1, 2));
        Villager beside = still(helper, new BlockPos(1, 1, 0));
        sneakAhead(player);
        beside.xo = beside.getX() - 0.3;
        helper.assertTrue(Observation.target(player) == ahead, "the one in front wins" + why(player, ahead)
                + " beside=" + beside.position());
        ahead.discard();
        beside.discard();
        helper.succeed();
    }

    /** Case M2: a nearer candidate without line of sight must not hide a visible one. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aNearerCandidateWithoutSightDoesNotHideAVisibleOne(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Villager a = still(helper, new BlockPos(1, 1, 2));
        Villager b = still(helper, new BlockPos(0, 1, 2));
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
        sneakAhead(player);
        a.xo = a.getX() - 0.3;
        helper.assertTrue(!player.hasLineOfSight(a) && player.hasLineOfSight(b),
                "geometry: A hidden, B visible" + why(player, a) + " losB=" + player.hasLineOfSight(b));
        helper.assertTrue(Observation.target(player) == b, "the visible one is chosen" + why(player, b));
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        a.discard();
        b.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void intermittentMissesKeepProgress(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Observation.forget(player);
        player.setData(ModAttachments.VIEW, 0);
        int needed = ViewRules.OBSERVE_TICKS;
        int grace = ViewRules.LOOK_GRACE_BASE_TICKS;
        Villager v = still(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(observeFor(player, v, needed - grace, FIXED_TIME) == 0, "not yet");
        missFor(player, 6, FIXED_TIME);
        helper.assertTrue(observeFor(player, v, 1, FIXED_TIME) == 0, "seen again");
        missFor(player, 6, FIXED_TIME);
        helper.assertTrue(observeFor(player, v, 8, FIXED_TIME) == 0, "still not enough");
        helper.assertTrue(observeFor(player, v, 1, FIXED_TIME) == 1, "seeing resets the misses: 12 in total didn't erase it");
        v.discard();
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void spectatorsDoNotObserve(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Villager villager = still(helper, new BlockPos(0, 1, 2));
        sneakAhead(player);
        helper.assertTrue(Observation.target(player) == villager, "a survival player would observe" + why(player, villager));
        player.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(Observation.target(player) == null, "a spectator doesn't");
        player.setGameMode(GameType.SURVIVAL);
        villager.discard();
        helper.succeed();
    }
}
