package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.npc.VillageChiefNpc;
import com.orijua.mitofus.feature.quest.QuestService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Quests given by NPCs ("giver"): Smith's chain and the rule that they are dealt with at the NPC, within reach. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class NpcQuestTests {
    private static final ResourceLocation FIREWOOD = Mitofus.id("smith_firewood");
    private static final ResourceLocation IRON = Mitofus.id("smith_iron");
    private static final ResourceLocation WOLFRAM_INGOTS = Mitofus.id("smith_wolfram_ingots");
    private static final ResourceLocation WOLFRAM_SWORD = Mitofus.id("smith_wolfram_sword");

    private NpcQuestTests() {
    }

    private static int stored(ServerPlayer player, ResourceLocation quest) {
        return QuestService.get(player).active().getOrDefault(quest, -1);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void npcQuestsAreOnlyDealtWithAtTheirNpc(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VillageChiefNpc chief = TestSupport.chief(helper);
        BlacksmithNpc farSmith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 14);
        helper.assertTrue(QuestService.accept(player, FIREWOOD) == QuestService.Result.TALK_TO_GIVER, "not from the Quests tab");
        helper.assertTrue(QuestService.accept(player, FIREWOOD, chief) == QuestService.Result.TALK_TO_GIVER, "not at another NPC");
        helper.assertTrue(QuestService.accept(player, FIREWOOD, farSmith) == QuestService.Result.TALK_TO_GIVER, "not from 13 blocks away");

        BlacksmithNpc smith = TestSupport.smith(helper);
        helper.assertTrue(QuestService.accept(player, FIREWOOD, smith) == QuestService.Result.SUCCESS, "at Smith");
        player.getInventory().add(new ItemStack(Items.OAK_LOG, 10));
        helper.assertTrue(QuestService.deliver(player, FIREWOOD, null) == QuestService.Result.TALK_TO_GIVER, "delivering needs Smith too");
        helper.assertTrue(QuestService.claim(player, FIREWOOD, null) == QuestService.Result.NOT_DONE, "it isn't done yet, checked before the giver");
        smith.discard();
        helper.assertTrue(QuestService.deliver(player, FIREWOOD, smith) == QuestService.Result.TALK_TO_GIVER, "not to a Smith who is gone");
        helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 10, "refused deliveries take nothing");
        helper.succeed();
    }

    /** Partial deliveries add up, take only what is missing, and accept any log. */
    @GameTest(template = TestSupport.EMPTY)
    public static void firewoodIsDeliveredInParts(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = TestSupport.smith(helper);
        QuestService.accept(player, FIREWOOD, smith);
        helper.assertTrue(QuestService.deliver(player, FIREWOOD, smith) == QuestService.Result.NOTHING_TO_DELIVER, "empty-handed");

        player.getInventory().add(new ItemStack(Items.OAK_LOG, 40));
        player.getInventory().add(new ItemStack(Items.COBBLESTONE, 5));
        helper.assertTrue(QuestService.deliver(player, FIREWOOD, smith) == QuestService.Result.SUCCESS, "first part");
        helper.assertTrue(stored(player, FIREWOOD) == 40 && player.getInventory().countItem(Items.OAK_LOG) == 0, "40 logs handed over");
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 5, "other items are left alone");
        helper.assertTrue(QuestService.claim(player, FIREWOOD, smith) == QuestService.Result.NOT_DONE, "40 of 64");

        player.getInventory().add(new ItemStack(Items.BIRCH_LOG, 30));
        QuestService.deliver(player, FIREWOOD, smith);
        helper.assertTrue(stored(player, FIREWOOD) == 64, "any log counts: " + stored(player, FIREWOOD));
        helper.assertTrue(player.getInventory().countItem(Items.BIRCH_LOG) == 6, "only the 24 missing were taken");
        helper.assertTrue(QuestService.claim(player, FIREWOOD, smith) == QuestService.Result.SUCCESS, "done");
        helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 4, "Smith's reward");
        helper.succeed();
    }

    /** A finished quest is only claimed at its giver: nowhere else and from nobody else. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aDoneQuestIsOnlyClaimedAtItsGiver(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = TestSupport.smith(helper);
        VillageChiefNpc chief = TestSupport.chief(helper);
        QuestService.accept(player, FIREWOOD, smith);
        player.getInventory().add(new ItemStack(Items.OAK_LOG, 64));
        helper.assertTrue(QuestService.deliver(player, FIREWOOD, smith) == QuestService.Result.SUCCESS, "all the logs are delivered");
        helper.assertTrue(QuestService.claim(player, FIREWOOD, null) == QuestService.Result.TALK_TO_GIVER, "not from the Quests tab");
        helper.assertTrue(QuestService.claim(player, FIREWOOD, chief) == QuestService.Result.TALK_TO_GIVER, "not at another NPC");
        helper.assertTrue(QuestService.claim(player, FIREWOOD, smith) == QuestService.Result.SUCCESS, "at Smith it is");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void ironComesRawOrAsOre(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = TestSupport.smith(helper);
        helper.assertTrue(QuestService.accept(player, IRON, smith) == QuestService.Result.NOT_AVAILABLE, "firewood first");
        TestSupport.completed(player, "smith_firewood");
        QuestService.accept(player, IRON, smith);
        player.getInventory().add(new ItemStack(Items.RAW_IRON, 20));
        player.getInventory().add(new ItemStack(Items.IRON_ORE, 8));
        player.getInventory().add(new ItemStack(Items.DEEPSLATE_IRON_ORE, 4));
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
        QuestService.deliver(player, IRON, smith);
        helper.assertTrue(stored(player, IRON) == 32, "raw iron and both ores count: " + stored(player, IRON));
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 5, "ingots are not what he asked for");
        helper.succeed();
    }

    /** The wolfram steps: hand over 8 ingots, then forge a Wolfram sword (other forges don't count). */
    @GameTest(template = TestSupport.EMPTY)
    public static void wolframIngotsThenTheWolframSword(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = TestSupport.smith(helper);
        TestSupport.completed(player, "smith_firewood", "smith_iron");
        QuestService.accept(player, WOLFRAM_INGOTS, smith);
        player.getInventory().add(new ItemStack(ModItems.WOLFRAM_INGOT.get(), 8));
        QuestService.deliver(player, WOLFRAM_INGOTS, smith);
        helper.assertTrue(QuestService.claim(player, WOLFRAM_INGOTS, smith) == QuestService.Result.SUCCESS, "8 wolfram ingots");

        helper.assertTrue(QuestService.accept(player, WOLFRAM_SWORD, smith) == QuestService.Result.SUCCESS, "the sword quest opens");
        TestSupport.forge(player, ItemRating.EPIC); // an iron sword
        helper.assertTrue(QuestService.claim(player, WOLFRAM_SWORD, smith) == QuestService.Result.NOT_DONE, "only a Wolfram sword counts");
        TestSupport.forge(player, ModItems.WOLFRAM_SWORD.get(), ItemRating.NORMAL);
        helper.assertTrue(QuestService.claim(player, WOLFRAM_SWORD, smith) == QuestService.Result.SUCCESS, "forging it completes the quest");
        helper.assertTrue(player.getInventory().countItem(ModItems.WOLFRAM_INGOT.get()) == 4, "Smith gives wolfram back to keep forging");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void smithsTradeButtonOpensTheShop(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = TestSupport.smith(helper);
        smith.startTrading(player);
        helper.assertTrue(smith.isTrading() && smith.getTradingPlayer() == player, "trading with the player");
        helper.succeed();
    }
}
