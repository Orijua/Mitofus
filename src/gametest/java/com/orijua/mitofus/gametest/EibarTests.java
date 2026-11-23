package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.sancho.SanchoStory;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.quest.QuestDeadlines;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.smithing.ItemForgedEvent;
import com.orijua.mitofus.feature.smithing.SmithingAnvilMenu;
import com.orijua.mitofus.feature.eibar.ContestRules;
import com.orijua.mitofus.feature.eibar.ContestState;
import com.orijua.mitofus.feature.eibar.MenciaNpc;
import com.orijua.mitofus.feature.eibar.VelascoContest;
import com.orijua.mitofus.feature.eibar.VelascoNpc;
import com.orijua.mitofus.feature.eibar.LevanteThug;
import com.orijua.mitofus.feature.eibar.EibarStory;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Velasco's story in Eibar: only Sancho's successor gets through to him. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class EibarTests {
    private static final ResourceLocation BLACKSMITH_RAGE = Mitofus.id("blacksmith_rage");

    private EibarTests() {
    }

    /** Velasco sends away anyone who isn't Sancho's successor; the successor is heard, and the milestone counts it. */
    @GameTest(template = TestSupport.EMPTY)
    public static void velascoOnlyListensToSanchosSuccessor(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        var velasco = helper.spawn(ModEntities.VELASCO.get(), 1, 1, 2);
        velasco.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(GoalTracker.milestones(player, EibarStory.MET_VELASCO) == 0, "a stranger is sent away");
        GoalTracker.milestone(player, SanchoStory.READ);
        helper.assertTrue(EibarStory.isSanchosSuccessor(player), "reading the book makes the successor");
        velasco.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(GoalTracker.milestones(player, EibarStory.MET_VELASCO) == 1, "the successor is heard");
        helper.succeed();
    }

    /** Completing "Sancho's Legacy" without the book (as /mitofus give quest does) also makes the successor. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theCompletedLegacyAlsoMakesTheSuccessor(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.assertFalse(EibarStory.isSanchosSuccessor(player), "not yet");
        TestSupport.completed(player, SanchoStory.LEGACY.getPath());
        helper.assertTrue(EibarStory.isSanchosSuccessor(player), "the legacy is enough");
        helper.succeed();
    }

    /** Smith gives Sancho's successor the map to Eibar once, and nothing to anyone else. */
    @GameTest(template = TestSupport.EMPTY)
    public static void smithPointsTheSuccessorToEibar(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        long seed = helper.getLevel().getSeed();
        MitofusWorld.standIn(helper.getLevel()); // the map is of the Mitofus dimension, which the test server doesn't have
        boolean hadEibar = WorldSites.siteFor(seed, WorldSites.EIBAR).isPresent();
        if (!hadEibar) {
            WorldSites.remember(seed, WorldSites.EIBAR, new ChunkPos(40, 0));
        }
        try {
            EibarStory.talkToSmith(player);
            helper.assertTrue(player.getInventory().countItem(Items.FILLED_MAP) == 0, "no map before the legacy");
            GoalTracker.milestone(player, SanchoStory.READ);
            EibarStory.talkToSmith(player);
            EibarStory.talkToSmith(player);
            helper.assertTrue(player.getInventory().countItem(Items.FILLED_MAP) == 1, "one map to Eibar");
        } finally {
            MitofusWorld.restoreStandIn();
            if (!hadEibar) {
                WorldSites.forget(seed, WorldSites.EIBAR);
            }
        }
        helper.succeed();
    }

    /** Sancho's successor who has met Velasco, with Velasco next to them: "The Blacksmith's Rage" is discovered. */
    private static VelascoNpc metVelasco(GameTestHelper helper, ServerPlayer player) {
        VelascoNpc velasco = helper.spawn(ModEntities.VELASCO.get(), 1, 1, 2);
        GoalTracker.milestone(player, SanchoStory.READ);
        velasco.interact(player, InteractionHand.MAIN_HAND);
        return velasco;
    }

    /** The thugs around Velasco: some stand outside the small test area. */
    private static List<LevanteThug> thugs(GameTestHelper helper, VelascoNpc velasco) {
        return helper.getLevel().getEntitiesOfClass(LevanteThug.class, velasco.getBoundingBox().inflate(16), LevanteThug::isAlive);
    }

    /**
     * The whole quest: accepting it creates the skill and brings 5 thugs who only the player can hurt;
     * it can't be abandoned; the last thug drops the contract, and tearing it up lets Velasco's quest be claimed.
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void theBlacksmithsRageFromStartToEnd(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VelascoNpc velasco = metVelasco(helper, player);
        helper.assertTrue(QuestService.get(player).isDiscovered(EibarStory.VELASCOS_RAGE), "meeting Velasco reveals his quest");
        helper.assertTrue(QuestService.accept(player, EibarStory.VELASCOS_RAGE, velasco) == QuestService.Result.SUCCESS, "accepted at Velasco");
        helper.assertTrue(SkillService.get(player).knows(BLACKSMITH_RAGE), "accepting creates the Blacksmith's Rage");
        helper.assertTrue(QuestService.get(player).deadline(EibarStory.VELASCOS_RAGE).isPresent(), "the clock runs");
        helper.assertTrue(QuestService.abandon(player, EibarStory.VELASCOS_RAGE) == QuestService.Result.CANNOT_ABANDON, "no way out");

        List<LevanteThug> thugs = thugs(helper, velasco);
        helper.assertTrue(thugs.size() == EibarStory.THUGS, "5 thugs in the smithy, got " + thugs.size());
        ServerPlayer stranger = TestSupport.fakePlayer(helper);
        LevanteThug first = thugs.getFirst();
        first.hurt(stranger.damageSources().playerAttack(stranger), 10.0f);
        helper.assertTrue(first.getHealth() == first.getMaxHealth(), "nobody else can hurt them");
        for (LevanteThug thug : thugs) {
            thug.hurt(player.damageSources().playerAttack(player), 1000.0f);
        }
        List<ItemEntity> contracts = helper.getLevel().getEntitiesOfClass(ItemEntity.class, velasco.getBoundingBox().inflate(16),
                item -> item.getItem().is(ModItems.TRANSFER_CONTRACT.get()));
        helper.assertTrue(contracts.size() == 1, "the last one drops the contract, got " + contracts.size());
        contracts.getFirst().discard();

        helper.assertTrue(QuestService.claim(player, EibarStory.VELASCOS_RAGE, velasco) == QuestService.Result.NOT_DONE, "not yet");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TRANSFER_CONTRACT.get()));
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "torn up");
        helper.assertTrue(QuestService.claim(player, EibarStory.VELASCOS_RAGE, velasco) == QuestService.Result.SUCCESS, "Velasco's smithy is saved");
        helper.succeed();
    }

    /** With a day left a warning comes once; when the time runs out the quest fails and Velasco is lost for that player. */
    @GameTest(template = TestSupport.EMPTY)
    public static void whenTheTimeRunsOutVelascoIsLost(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VelascoNpc velasco = metVelasco(helper, player);
        QuestService.accept(player, EibarStory.VELASCOS_RAGE, velasco);
        long now = QuestDeadlines.now(player);
        player.setData(ModAttachments.PLAYER_QUESTS, QuestService.get(player).withDeadline(EibarStory.VELASCOS_RAGE, now + 100));
        QuestDeadlines.check(player);
        helper.assertTrue(QuestService.get(player).warned().contains(EibarStory.VELASCOS_RAGE), "warned on the last day");
        helper.assertTrue(QuestService.get(player).isActive(EibarStory.VELASCOS_RAGE), "still running");

        player.setData(ModAttachments.PLAYER_QUESTS, QuestService.get(player).withDeadline(EibarStory.VELASCOS_RAGE, now - 1));
        QuestDeadlines.check(player);
        helper.assertTrue(QuestService.get(player).isFailed(EibarStory.VELASCOS_RAGE), "failed");
        helper.assertTrue(EibarStory.hasLostVelasco(player) && !EibarStory.heardByVelasco(player), "Velasco no longer speaks to them");
        helper.assertFalse(QuestService.canAccept(player, EibarStory.VELASCOS_RAGE,
                Definitions.get(helper.getLevel(), ModDatapackRegistries.QUEST, EibarStory.VELASCOS_RAGE).orElseThrow()), "never again");
        helper.assertTrue(velasco.isAlive(), "a test server isn't a single-player world: Velasco lives on for the others");
        thugs(helper, velasco).forEach(LevanteThug::discard);
        helper.succeed();
    }

    /**
     * Sancho's successor who saved Velasco's smithy, with the contest accepted at Velasco: Mencia comes (next to Velasco, as a
     * test has no Eibar) and the player stands by her, on the "stage".
     */
    private static MenciaNpc contestAccepted(GameTestHelper helper, ServerPlayer player) {
        VelascoNpc velasco = metVelasco(helper, player);
        TestSupport.completed(player, EibarStory.VELASCOS_RAGE.getPath());
        helper.assertTrue(QuestService.accept(player, VelascoContest.VELASCOS_CONTEST, velasco) == QuestService.Result.SUCCESS, "accepted at Velasco");
        List<MenciaNpc> found = helper.getLevel().getEntitiesOfClass(MenciaNpc.class, velasco.getBoundingBox().inflate(8), MenciaNpc::isAlive);
        helper.assertTrue(found.size() == 1, "Mencia comes, got " + found.size());
        MenciaNpc mencia = found.getFirst();
        player.moveTo(mencia.getX() + 1, mencia.getY(), mencia.getZ());
        return mencia;
    }

    private static void forgeByHand(ServerPlayer player, ItemRating rating, double precision) {
        NeoForge.EVENT_BUS.post(new ItemForgedEvent(player, new ItemStack(ModItems.WOLFRAM_SWORD.get()), rating, precision));
    }

    /**
     * The whole contest: Velasco gives the materials, an automatic forge doesn't count, a precise Epic beats her roll, and
     * claiming at Velasco makes the player his disciple (the title, with its smithing XP bonus).
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void theContestFromStartToEnd(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        MenciaNpc mencia = contestAccepted(helper, player);
        VelascoNpc velasco = helper.getLevel().getEntitiesOfClass(VelascoNpc.class, mencia.getBoundingBox().inflate(8)).getFirst();
        helper.assertTrue(VelascoContest.canStart(player), "ready to start");
        helper.assertTrue(VelascoContest.start(player, 250), "started");
        helper.assertFalse(VelascoContest.start(player, 250), "not twice");
        helper.assertTrue(player.getInventory().countItem(ModItems.WOLFRAM_INGOT.get()) == 2, "Velasco's wolfram ingots");

        NeoForge.EVENT_BUS.post(new ItemForgedEvent(player, new ItemStack(ModItems.WOLFRAM_SWORD.get()), ItemRating.UNIQUE));
        helper.assertTrue(VelascoContest.state(player).running(), "an automatic forge doesn't count");
        forgeByHand(player, ItemRating.EPIC, 0.87);
        helper.assertFalse(VelascoContest.state(player).running(), "the piece is judged");
        helper.assertTrue(GoalTracker.milestones(player, VelascoContest.CONTEST_WON) == 1, "287 beats 250");

        helper.assertTrue(QuestService.claim(player, VelascoContest.VELASCOS_CONTEST, velasco) == QuestService.Result.SUCCESS, "claimed at Velasco");
        helper.assertTrue(player.getData(ModAttachments.PLAYER_IDENTITY).hasTitle(VelascoContest.VELASCOS_DISCIPLE), "Velasco's disciple");
        int before = ProgressionService.get(player).smithingXp();
        ProgressionService.addSmithingXp(player, 100);
        helper.assertTrue(ProgressionService.get(player).smithingXp() - before == 110, "+10 % smithing XP");
        helper.succeed();
    }

    /** On the stage during the contest the anvil refuses the automatic forge; the manual one still starts. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theContestAnvilOnlyForgesByHand(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        contestAccepted(helper, player);
        SmithingAnvilMenu menu = new SmithingAnvilMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), player.blockPosition()));
        menu.getSlot(SmithingAnvilMenu.TOP_SLOT).set(new ItemStack(ModItems.WOLFRAM_INGOT.get(), 2));
        menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).set(new ItemStack(Items.STICK));
        helper.assertTrue(menu.canForge(), "the Wolfram sword is previewed");
        VelascoContest.start(player, 150);
        helper.assertTrue(VelascoContest.forcesManual(player), "on the stage, in the contest");
        helper.assertFalse(menu.clickMenuButton(player, SmithingAnvilMenu.AUTO_FORGE_BUTTON), "no automatic forge in the contest");
        helper.assertFalse(menu.autoForging(), "nothing started");
        helper.assertTrue(menu.clickMenuButton(player, SmithingAnvilMenu.MANUAL_FORGE_BUTTON), "by hand it is");
        helper.succeed();
    }

    /** A piece forged away from the stage is not the contest's. */
    @GameTest(template = TestSupport.EMPTY)
    public static void onlyAPieceForgedOnTheStageCounts(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        MenciaNpc mencia = contestAccepted(helper, player);
        VelascoContest.start(player, 150);
        player.moveTo(mencia.getX() + 20, mencia.getY(), mencia.getZ());
        forgeByHand(player, ItemRating.EPIC, 1);
        helper.assertTrue(VelascoContest.state(player).running(), "still running");
        helper.succeed();
    }

    /** With rematches (a shared world's default), losing allows another try only the next day. */
    @GameTest(template = TestSupport.EMPTY)
    public static void losingGivesARematchTheNextDay(GameTestHelper helper) {
        Balance.withOverride(Balance.DEFAULT.withContestLoss(Balance.ContestLoss.REMATCH), () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            contestAccepted(helper, player);
            VelascoContest.start(player, 300);
            forgeByHand(player, ItemRating.RARE, 0.5);
            helper.assertTrue(QuestService.get(player).isActive(VelascoContest.VELASCOS_CONTEST), "the quest goes on");
            helper.assertFalse(VelascoContest.canStart(player), "not again today");
            ContestState lost = VelascoContest.state(player);
            player.setData(ModAttachments.VELASCO_CONTEST, lost.lost(lost.lostDay() - 1));
            helper.assertTrue(VelascoContest.canStart(player), "tomorrow, a rematch");
        });
        helper.succeed();
    }

    /** Failing for good (a single-player world's default): the Levante keeps the smithy and Velasco is lost. */
    @GameTest(template = TestSupport.EMPTY)
    public static void losingForGoodFailsTheQuest(GameTestHelper helper) {
        Balance.withOverride(Balance.DEFAULT.withContestLoss(Balance.ContestLoss.FAIL), () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            contestAccepted(helper, player);
            VelascoContest.start(player, 300);
            forgeByHand(player, ItemRating.EPIC, 1);
            helper.assertTrue(QuestService.get(player).isFailed(VelascoContest.VELASCOS_CONTEST), "a tie goes to Mencia: failed");
            helper.assertTrue(EibarStory.hasLostVelasco(player) && !EibarStory.heardByVelasco(player), "Velasco no longer teaches them");
        });
        helper.succeed();
    }

    /** No piece when the time is up is a loss. */
    @GameTest(template = TestSupport.EMPTY)
    public static void whenTheContestTimeRunsOutItIsLost(GameTestHelper helper) {
        Balance.withOverride(Balance.DEFAULT.withContestLoss(Balance.ContestLoss.REMATCH), () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            contestAccepted(helper, player);
            VelascoContest.start(player, 150);
            VelascoContest.tick(player);
            helper.assertTrue(VelascoContest.state(player).running(), "time left");
            long late = helper.getLevel().getGameTime() - ContestRules.timeLimitTicks() - 1;
            player.setData(ModAttachments.VELASCO_CONTEST, VelascoContest.state(player).start(late, 150));
            VelascoContest.tick(player);
            helper.assertFalse(VelascoContest.state(player).running(), "time's up");
            helper.assertTrue(VelascoContest.state(player).lostDay() >= 0, "lost");
        });
        helper.succeed();
    }

    /** Velasco dead for the whole world: a grave sign where he stood. */
    @GameTest(template = TestSupport.EMPTY)
    public static void velascoIsBuriedWhereHeStood(GameTestHelper helper) {
        VelascoNpc velasco = helper.spawn(ModEntities.VELASCO.get(), 1, 1, 2);
        BlockPos at = velasco.blockPosition();
        EibarStory.bury(velasco);
        helper.assertTrue(velasco.isRemoved(), "Velasco is gone");
        helper.assertTrue(helper.getLevel().getBlockState(at).is(Blocks.OAK_SIGN), "a grave sign");
        helper.succeed();
    }
}
