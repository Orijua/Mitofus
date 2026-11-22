package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.sancho.NunoBetrayal;
import com.orijua.mitofus.feature.sancho.Mesnadero;
import com.orijua.mitofus.feature.sancho.Mesnada;
import com.orijua.mitofus.feature.sancho.CondeNuno;
import com.orijua.mitofus.feature.sancho.AppraiserEyeItem;
import com.orijua.mitofus.feature.sancho.LegendTitle;
import com.orijua.mitofus.feature.sancho.OrbeAurifero;
import com.orijua.mitofus.feature.sancho.SanchoStory;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.world.TorreDelOro;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.orijua.mitofus.feature.quest.QuestDeadlines;
import java.util.List;

/** Sancho's story: Conde Nuno's request, appraising the book, his anger and reading the book. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class SanchoStoryTests {
    private SanchoStoryTests() {
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void nunoOnlyConfidesInSomeoneSmithTrusts(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        NunoBetrayal.talk(player);
        helper.assertFalse(QuestService.get(player).isDiscovered(SanchoStory.SECRET_REQUEST), "no time for a nobody");
        TestSupport.completed(player, "quality_test");
        NunoBetrayal.talk(player);
        helper.assertTrue(QuestService.get(player).isDiscovered(SanchoStory.SECRET_REQUEST), "after Smith's chain he tells of it");
        helper.succeed();
    }

    /** Bringing him the book unappraised completes the request: Nuno keeps it and pays. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theBookUnopenedFulfilsTheRequest(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        CondeNuno nuno = helper.spawn(ModEntities.CONDE_NUNO.get(), 1, 1, 2);
        TestSupport.completed(player, "quality_test");
        NunoBetrayal.talk(player);
        helper.assertTrue(QuestService.accept(player, SanchoStory.SECRET_REQUEST, nuno) == QuestService.Result.SUCCESS, "accepted at Nuno");
        player.getInventory().add(new ItemStack(ModItems.UNAPPRAISED_BOOK.get()));
        helper.assertTrue(QuestService.deliver(player, SanchoStory.SECRET_REQUEST, nuno) == QuestService.Result.SUCCESS, "delivered");
        helper.assertTrue(QuestService.claim(player, SanchoStory.SECRET_REQUEST, nuno) == QuestService.Result.SUCCESS, "claimed");
        helper.assertTrue(player.getInventory().countItem(ModItems.UNAPPRAISED_BOOK.get()) == 0, "Nuno has the book");
        helper.assertTrue(player.getInventory().countItem(ModItems.GOLD_COIN.get()) == 16, "and paid for it");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "and not in vanilla emeralds");
        helper.assertFalse(SanchoStory.isNunosEnemy(player), "no anger");
        helper.succeed();
    }

    /** Appraising the book turns it into Sancho's rare book, and Nuno comes with his knights: the SS quest starts. */
    @GameTest(template = TestSupport.EMPTY)
    public static void appraisingTheBookBringsNunosAnger(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.completed(player, "quality_test");
        NunoBetrayal.talk(player);
        QuestService.accept(player, SanchoStory.SECRET_REQUEST, helper.spawn(ModEntities.CONDE_NUNO.get(), 1, 1, 2));
        helper.assertFalse(AppraiserEyeItem.appraise(player), "nothing to appraise yet");
        player.getInventory().add(new ItemStack(ModItems.UNAPPRAISED_BOOK.get()));
        helper.assertTrue(AppraiserEyeItem.appraise(player), "appraised");

        helper.assertTrue(player.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 1, "it is Sancho's rare book");
        helper.assertTrue(player.getInventory().countItem(ModItems.UNAPPRAISED_BOOK.get()) == 0, "no old book left");
        helper.assertTrue(SanchoStory.isNunosEnemy(player), "Nuno is after the player");
        helper.assertFalse(QuestService.get(player).isActive(SanchoStory.SECRET_REQUEST), "the secret request is over");
        helper.assertTrue(QuestService.get(player).isActive(SanchoStory.ANGER), "Conde Nuno's Anger has started");
        helper.assertTrue(QuestService.get(player).isDiscovered(SanchoStory.LEGACY), "and Sancho's legacy is revealed");

        List<Mesnada> summoned = retinueAround(helper, player);
        long earls = summoned.stream().filter(CondeNuno.class::isInstance).count();
        long knights = summoned.stream().filter(Mesnadero.class::isInstance).count();
        helper.assertTrue(earls == 1 && knights == NunoBetrayal.KNIGHTS, "Nuno and his knights came: " + earls + " + " + knights);
        CondeNuno earl = (CondeNuno) summoned.stream().filter(CondeNuno.class::isInstance).findFirst().orElseThrow();
        helper.assertFalse(earl.isInvulnerableTo(player.damageSources().playerAttack(player)), "the thief can fight him");
        helper.assertTrue(earl.isInvulnerableTo(player.damageSources().generic()), "nothing else hurts him");
        summoned.forEach(Mesnada::discard);
        helper.succeed();
    }

    /** whoever dies at Nuno's hands with the book loses it to him. */
    @GameTest(template = TestSupport.EMPTY)
    public static void dyingWhileNunoHuntsYouLosesTheBook(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.UNAPPRAISED_BOOK.get()));
        AppraiserEyeItem.appraise(player);
        player.getInventory().add(new ItemStack(Items.COAL, 7));
        retinueAround(helper, player).forEach(Mesnada::discard);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
        // Mitofus is where the test plays: the rest of the things are kept and handed to the new player; only the book is gone.
        ServerPlayer fresh = TestSupport.fakePlayer(helper);
        fresh.restoreFrom(player, false);
        helper.assertTrue(fresh.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 0, "Nuno took it");
        helper.assertTrue(fresh.getInventory().countItem(Items.COAL) == 7, "everything else came back");
        helper.succeed();
    }

    /** Failing "Conde Nuno's Anger" costs 2 levels, never below level 1: by dying while he hunts the player, or by time. */
    @GameTest(template = TestSupport.EMPTY)
    public static void failingNunosAngerCostsTwoLevels(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, ProgressionService.get(player).withProgress(10, 0, 0));
        player.getInventory().add(new ItemStack(ModItems.UNAPPRAISED_BOOK.get()));
        AppraiserEyeItem.appraise(player);
        retinueAround(helper, player).forEach(Mesnada::discard);
        helper.assertTrue(QuestService.get(player).isActive(SanchoStory.ANGER), "the quest is active");
        helper.assertTrue(QuestService.get(player).deadline(SanchoStory.ANGER).isPresent(), "and it has a deadline");

        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(player, player.damageSources().generic()));
        helper.assertTrue(QuestService.get(player).isFailed(SanchoStory.ANGER), "dying while he hunts the player fails the quest");
        helper.assertTrue(ProgressionService.get(player).level() == 8, "two levels lost: " + ProgressionService.get(player).level());

        // At level 1 the penalty is not negative: the floor holds.
        ServerPlayer rookie = TestSupport.fakePlayer(helper);
        ProgressionService.set(rookie, ProgressionService.get(rookie).withProgress(1, 0, 0));
        rookie.getInventory().add(new ItemStack(ModItems.UNAPPRAISED_BOOK.get()));
        AppraiserEyeItem.appraise(rookie);
        retinueAround(helper, rookie).forEach(Mesnada::discard);
        QuestDeadlines.fail(rookie, SanchoStory.ANGER);
        helper.assertTrue(ProgressionService.get(rookie).level() == 1, "level 1 stays level 1");
        helper.succeed();
    }

    /** Killing Nuno ends his anger: the SS quest can be claimed and the title "Noble Scourge" is earned. */
    @GameTest(template = TestSupport.EMPTY)
    public static void killingNunoEndsHisAnger(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.UNAPPRAISED_BOOK.get()));
        AppraiserEyeItem.appraise(player);
        retinueAround(helper, player).forEach(Mesnada::discard);
        TestSupport.kill(helper, player, ModEntities.CONDE_NUNO.get());
        helper.assertFalse(SanchoStory.isNunosEnemy(player), "he is dead");
        helper.assertTrue(player.getData(ModAttachments.PLAYER_IDENTITY).hasTitle(SanchoStory.NOBLE_SCOURGE), "Noble Scourge");
        helper.assertTrue(QuestService.claim(player, SanchoStory.ANGER) == QuestService.Result.SUCCESS, "the SS quest is done");
        helper.succeed();
    }

    /**
     * Reading the book: a warrior becomes Sancho's successor, back to level 1 with no stat points
     * spent, no skills of the old class and 20 points to spend; smithing stays, and the blueprint and title come with it.
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void readingTheBookMakesSanchosSuccessor(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.chooseClass(player, 0, Mitofus.id("swordsman"));
        SkillService.learn(player, Mitofus.id("rending_slash"));
        ProgressionService.set(player, new PlayerStats(12, 40, 3, 9, 4, 2, 1, 5, 700));
        helper.assertFalse(OrbeAurifero.read(player), "no book, nothing to read");
        player.getInventory().add(new ItemStack(ModItems.ORBE_AURIFERO.get()));
        helper.assertTrue(OrbeAurifero.read(player), "read");

        PlayerStats stats = ProgressionService.get(player);
        helper.assertTrue(stats.level() == 1 && stats.xp() == 0, "back to level 1: " + stats);
        helper.assertTrue(stats.freePoints() == SanchoStory.BONUS_POINTS, "20 points to spend: " + stats.freePoints());
        helper.assertTrue(stats.strength() == 0 && stats.dexterity() == 0, "spent points are gone: " + stats);
        helper.assertTrue(stats.smithingXp() == 700, "smithing stays");
        helper.assertTrue(ClassService.currentJob(player).filter(SanchoStory.SUCCESSOR_JOB::equals).isPresent(), "Sancho's successor job");
        helper.assertFalse(SkillService.get(player).knows(Mitofus.id("rending_slash")), "old skills are forgotten");
        helper.assertTrue(SkillService.get(player).knows(Mitofus.id("iron_guard")), "the successor's skills are learned");
        helper.assertTrue(player.getInventory().countItem(ModItems.GUADALETE_BLUEPRINT.get()) == 1, "Guadalete's blueprint");
        helper.assertTrue(LegendTitle.has(player), "One Who Became a Legend");
        helper.assertTrue(QuestService.get(player).isCompleted(SanchoStory.LEGACY), "Sancho's legacy is done");
        helper.assertTrue(player.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 0, "the book is spent");
        helper.succeed();
    }

    /** "One Who Became a Legend": a killing blow leaves the player at 1 health, once every five minutes. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theLegendTitleHoldsOffDeath(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.ORBE_AURIFERO.get()));
        OrbeAurifero.read(player);
        LivingDeathEvent first = NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
        helper.assertTrue(first.isCanceled() && player.getHealth() == 1.0f, "saved at 1 health");
        LivingDeathEvent second = NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
        helper.assertFalse(second.isCanceled(), "not twice in a row");
        LegendTitle.readyNow(player);
        player.setHealth(player.getMaxHealth());
        LivingDeathEvent third = NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
        helper.assertTrue(third.isCanceled() && player.getHealth() == 1.0f, "saved again once the cooldown is over");
        helper.succeed();
    }

    /** Only Nuno takes the book, and only from someone he hunts: dying with an unappraised-by-the-eye book keeps it. */
    @GameTest(template = TestSupport.EMPTY)
    public static void dyingWithoutNunoKeepsTheBook(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.ORBE_AURIFERO.get()));
        helper.assertFalse(SanchoStory.isNunosEnemy(player), "Nuno is not after this player");
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
        // Dying in Mitofus puts the inventory aside; the new player gets it back whole.
        ServerPlayer fresh = TestSupport.fakePlayer(helper);
        fresh.restoreFrom(player, false);
        helper.assertTrue(fresh.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 1, "nobody took the book");
        helper.succeed();
    }

    /** A second book changes nothing once the legacy is done: it isn't spent, and stats and skills stay. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aSecondBookIsRefusedOnceTheLegacyIsDone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.ORBE_AURIFERO.get()));
        helper.assertTrue(OrbeAurifero.read(player), "the first book is read");
        PlayerStats grown = ProgressionService.get(player).withProgress(12, 40, 3);
        ProgressionService.set(player, grown);
        SkillService.learn(player, Mitofus.id("iron_guard"));
        var skills = SkillService.get(player);

        player.getInventory().add(new ItemStack(ModItems.ORBE_AURIFERO.get()));
        helper.assertFalse(OrbeAurifero.read(player), "the second book is refused");
        helper.assertTrue(player.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 1, "and kept");
        helper.assertTrue(ProgressionService.get(player).equals(grown), "stats are untouched");
        helper.assertTrue(SkillService.get(player).equals(skills), "skills are untouched");
        helper.succeed();
    }

    /** Nuno's map comes in the first talk where the world has a cave to show; the secret is told the first time anyway. */
    @GameTest(template = TestSupport.EMPTY)
    public static void nunoGivesTheMapOnceThereIsACave(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.completed(player, "quality_test");
        long seed = helper.getLevel().getSeed();
        NunoBetrayal.talk(player);
        helper.assertTrue(QuestService.get(player).isDiscovered(SanchoStory.SECRET_REQUEST), "the secret is told");
        helper.assertTrue(player.getInventory().countItem(Items.FILLED_MAP) == 0, "no map without the cave dimension");

        MitofusWorld.standIn(helper.getLevel());
        boolean hadCave = WorldSites.siteFor(seed, WorldSites.ALTAMIRA).isPresent();
        if (!hadCave) {
            WorldSites.remember(seed, WorldSites.ALTAMIRA, new ChunkPos(40, 0));
        }
        try {
            NunoBetrayal.talk(player);
            helper.assertTrue(player.getInventory().countItem(Items.FILLED_MAP) == 1, "the map comes once there is a cave");
            NunoBetrayal.talk(player);
            helper.assertTrue(player.getInventory().countItem(Items.FILLED_MAP) == 1, "and only once");
        } finally {
            MitofusWorld.restoreStandIn();
            if (!hadCave) {
                WorldSites.forget(seed, WorldSites.ALTAMIRA);
            }
        }
        helper.succeed();
    }

    /** The manor: Nuno by his seat and two knights at the door, all wearing what they should. */
    @GameTest(template = TorreDelOro.MANOR)
    public static void theManorHasNunoAndHisGuard(GameTestHelper helper) {
        List<CondeNuno> earls = helper.getEntities(ModEntities.CONDE_NUNO.get());
        List<Mesnadero> knights = helper.getEntities(ModEntities.MESNADERO.get());
        helper.assertTrue(earls.size() == 1 && knights.size() == 2, "Nuno and two knights, got " + earls.size() + " + " + knights.size());
        helper.assertTrue(earls.getFirst().getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "his crown");
        helper.assertTrue(knights.stream().allMatch(knight -> knight.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD)), "armed");
        helper.assertFalse(earls.getFirst().isSummoned(), "he lives here");
        helper.succeed();
    }

    /** Those the betrayal summoned around the player (not the one a test spawned to talk to). */
    private static List<Mesnada> retinueAround(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(Mesnada.class, new AABB(player.blockPosition()).inflate(12), Mesnada::isSummoned);
    }
}
