package com.orijua.mitofus.gametest;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.core.config.SmithShopBalance;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetState;
import com.orijua.mitofus.feature.character.CharacterSnapshot;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.view.TalentSearch;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.npc.ApprenticeRules;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.npc.SmithShop;
import com.orijua.mitofus.feature.economy.Wallet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The economy, step 1: the purse, the gold coin and the shop of Smith, which trades in it.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class EconomyTests {
    private EconomyTests() {
    }

    private static boolean refused(GameTestHelper helper, ServerPlayer player, String command, int permission) {
        try {
            helper.getLevel().getServer().getCommands().getDispatcher()
                    .execute(command, player.createCommandSourceStack().withPermission(permission));
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    private static MerchantOffer offerFor(BlacksmithNpc smith, net.minecraft.world.item.Item result) {
        return smith.getOffers().stream().filter(offer -> offer.getResult().is(result)).findFirst().orElseThrow();
    }

    private static long count(MerchantOffers offers, net.minecraft.world.item.Item result) {
        return offers.stream().filter(offer -> offer.getResult().is(result)).count();
    }

    /** Runs a command as {@code player} with the permission level, collecting what it answers; throws if refused. */
    private static List<Component> run(GameTestHelper helper, ServerPlayer player, String command, int permission) throws CommandSyntaxException {
        List<Component> answers = new ArrayList<>();
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                answers.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = player.createCommandSourceStack().withPermission(permission).withSource(sink);
        helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        return answers;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thePurseSurvivesRespawn(GameTestHelper helper) {
        ServerPlayer old = TestSupport.fakePlayer(helper);
        TestSupport.setIron(old, 700);
        ServerPlayer fresh = TestSupport.fakePlayer(helper);
        helper.assertTrue(Wallet.purse(fresh).equals(com.orijua.mitofus.feature.economy.Purse.EMPTY), "a new player has an empty purse");
        fresh.restoreFrom(old, false);
        helper.assertTrue(Wallet.purse(fresh).iron() == 700, "dying does not touch the money");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thePurseIsSavedWithThePlayer(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        TestSupport.setIron(player, 321);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        ServerPlayer other = TestSupport.fakePlayer(helper);
        other.load(saved);
        helper.assertTrue(Wallet.purse(other).iron() == 321, "the purse is read back from the saved player");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void resetAllDoesNotTouchTheVault(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        CharacterVault vault = CharacterVault.of(WorldSide.MITOFUS, CharacterSnapshot.capture(player));
        player.setData(ModAttachments.CHARACTER_VAULT.get(), vault);
        player.setData(ModAttachments.VR_HELMET.get(), VrHelmetState.EMPTY.disconnected(player.blockPosition()));

        ModAttachments.resetAll(player);

        helper.assertTrue(player.getData(ModAttachments.CHARACTER_VAULT.get()).equals(vault), "and the vault does not change");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDebugCommandsSetAndShowThePurse(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        dispatcher.execute("mitofus set iron 500", player.createCommandSourceStack().withPermission(4));
        helper.assertTrue(Wallet.purse(player).iron() == 500, "500 iron");
        helper.assertTrue(refused(helper, player, "mitofus set iron -1", 4), "a negative amount is refused");
        helper.assertTrue(Wallet.purse(player).iron() == 500, "and nothing changed");
        helper.assertTrue(refused(helper, player, "mitofus set iron 10", 0), "the command is for operators");
        helper.assertTrue(Wallet.purse(player).iron() == 500, "and nothing changed either");
        helper.assertTrue(dispatcher.execute("mitofus purse", player.createCommandSourceStack().withPermission(4)) == 1, "purse answers");
        helper.assertTrue(refused(helper, player, "mitofus purse", 0), "purse is for operators too");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void noOfferOfSmithUsesTheVanillaEmerald(GameTestHelper helper) {
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        for (MerchantOffer offer : smith.getOffers()) {
            helper.assertFalse(offer.getBaseCostA().is(Items.EMERALD), "cost A is not the vanilla emerald");
            helper.assertFalse(offer.getCostB().is(Items.EMERALD), "cost B is not the vanilla emerald");
            helper.assertFalse(offer.getResult().is(Items.EMERALD), "the result is not the vanilla emerald");
        }
        MerchantOffer mithril = offerFor(smith, ModItems.GOLD_COIN.get());
        helper.assertTrue(mithril.getBaseCostA().is(ModItems.MITHRIL_INGOT.get()) && mithril.getBaseCostA().getCount() == 4,
                "the mithril costs four ingots");
        helper.assertTrue(mithril.getResult().getCount() == 1, "and gives one gold");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void smithTakesGoldAndNotTheVanillaEmerald(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        MerchantOffer stone = offerFor(smith, ModItems.ENHANCEMENT_STONE.get());
        helper.assertTrue(stone.getItemCostA().count() == SmithShopBalance.DEFAULT.stonePrice(), "the stone costs " + SmithShopBalance.DEFAULT.stonePrice() + " gold");
        helper.assertTrue(stone.getItemCostA().test(new ItemStack(ModItems.GOLD_COIN.get())), "a gold coin is what he takes");
        helper.assertFalse(stone.getItemCostA().test(new ItemStack(Items.EMERALD)), "a vanilla emerald is not");
        MerchantOffer contract = offerFor(smith, ModItems.APPRENTICE_CONTRACT.get());
        helper.assertTrue(contract.getItemCostA().count() == ApprenticeBalance.DEFAULT.contractPrice() && ApprenticeBalance.DEFAULT.contractPrice() == 21,
                "the contract is paid in 21 gold");
        helper.assertFalse(contract.getItemCostA().test(new ItemStack(Items.EMERALD)), "and not in vanilla emeralds");
        int price = SmithShopBalance.DEFAULT.stonePrice();
        player.getInventory().add(new ItemStack(Items.EMERALD, price));
        smith.startTrading(player);
        helper.assertTrue(SmithShop.buy(player, smith, smith.getOffers().indexOf(stone), stone.getResult().copy(), price, 0) == SmithShop.Result.NOT_ENOUGH,
                price + " vanilla emeralds do not buy the stone");
        player.getInventory().add(new ItemStack(ModItems.GOLD_COIN.get(), price));
        helper.assertTrue(SmithShop.buy(player, smith, smith.getOffers().indexOf(stone), stone.getResult().copy(), price, 0) == SmithShop.Result.OK,
                price + " gold coins buy it");
        helper.assertTrue(player.getInventory().countItem(ModItems.ENHANCEMENT_STONE.get()) == 1 && player.getInventory().countItem(Items.EMERALD) == price,
                "the stone is bought and the emeralds are untouched");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theGoldCoinIsNotAVanillaEmerald(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.GOLD_COIN.get());
        helper.assertFalse(stack.is(Items.EMERALD), "it is not the vanilla emerald");
        helper.assertFalse(stack.is(Tags.Items.GEMS_EMERALD), "nor is it in the emerald gem tag");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void covadongasQuestPaysGoldAndNoVanillaEmeralds(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        net.minecraft.world.entity.Entity chief = helper.spawn(ModEntities.VILLAGE_CHIEF.get(), 1, 1, 2);
        TestSupport.completed(player, "watch_commander");
        var quest = Mitofus.id("covadonga_talents");
        helper.assertTrue(QuestService.accept(player, quest, chief) == QuestService.Result.SUCCESS, "accepted at the chief");
        for (int i = 0; i < 3; i++) {
            GoalTracker.milestone(player, TalentSearch.MILESTONE);
        }
        helper.assertTrue(QuestService.claim(player, quest, chief) == QuestService.Result.SUCCESS, "claimed");
        helper.assertTrue(player.getInventory().countItem(ModItems.GOLD_COIN.get()) == 5, "five gold");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "and no vanilla emerald");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDebugCommandPermissionIsLevelTwo(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        for (String command : new String[]{"mitofus set iron 5", "mitofus set iron 0", "mitofus purse"}) {
            helper.assertTrue(refused(helper, player, command, 1), "level 1 is refused: " + command);
            run(helper, player, command, 2);
        }
        helper.assertTrue(Wallet.purse(player).iron() == 0, "level 2 ran the last 'set iron 0' and the balance is 0");
        run(helper, player, "mitofus set iron 5", 2);
        helper.assertTrue(Wallet.purse(player).iron() == 5, "level 2 may set the balance");
        run(helper, player, "mitofus set iron 0", 2);
        helper.assertTrue(Wallet.purse(player).iron() == 0, "zero is valid");
        run(helper, player, "mitofus set iron 9223372036854775807", 2);
        helper.assertTrue(Wallet.purse(player).iron() == Long.MAX_VALUE, "the largest amount is valid");
        helper.assertTrue(refused(helper, player, "mitofus set iron 9223372036854775808", 4), "one more than the largest is not a number");
        helper.assertTrue(Wallet.purse(player).iron() == Long.MAX_VALUE, "and nothing changed");
        helper.succeed();
    }

}
