package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.WeightBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.Residence;
import com.orijua.mitofus.feature.vrhelmet.ResidenceRules;
import com.orijua.mitofus.feature.vrhelmet.ResidenceService;
import com.orijua.mitofus.feature.coach.CoachService;
import com.orijua.mitofus.feature.coach.CoachmanNpc;
import com.orijua.mitofus.feature.economy.Purse;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.equipment.WeightService;
import com.orijua.mitofus.feature.npc.VillageChiefNpc;
import com.orijua.mitofus.feature.storage.Storage;
import com.orijua.mitofus.feature.storage.StorageService;
import com.orijua.mitofus.feature.storage.StorekeeperNpc;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * The lore features: the virtual balance (the purse), the weight, the residence, the coachman and the storage. The test
 * level plays Mitofus; a fake player does not tick, so the services are called by hand.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class LoreTests {
    private LoreTests() {
    }

    // ---- The purse ----

    @GameTest(template = TestSupport.EMPTY)
    public static void payingTakesTheCoinsCarriedIntoThePurseFirst(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.GOLD_COIN.get(), 3));
        player.getInventory().add(new ItemStack(ModItems.IRON_COIN.get(), 50));
        helper.assertTrue(Wallet.balance(player) == 350, "350 iron in coins: " + Wallet.balance(player));
        helper.assertTrue(Wallet.pay(player, 150), "150 can be paid");
        helper.assertTrue(Wallet.purse(player).iron() == 200 && Wallet.balance(player) == 200, "200 are left, all in the purse: " + Wallet.purse(player));
        helper.assertTrue(player.getInventory().countItem(ModItems.GOLD_COIN.get()) == 0 && player.getInventory().countItem(ModItems.IRON_COIN.get()) == 0,
                "no coin item is left");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDiamondCoinIsWorthAHundredGold(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.DIAMOND_COIN.get(), 2));
        helper.assertTrue(Wallet.balance(player) == 20_000, "two diamond coins are 20.000 iron: " + Wallet.balance(player));
        helper.assertTrue(Wallet.absorb(player) == 20_000 && Wallet.purse(player).iron() == 20_000, "they go into the purse");
        helper.assertTrue(player.getInventory().countItem(ModItems.DIAMOND_COIN.get()) == 0, "no coin item is left");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void payingMoreThanThereIsChangesNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        Wallet.add(player, 40);
        helper.assertFalse(Wallet.pay(player, 41), "41 cannot be paid with 40");
        helper.assertTrue(Wallet.purse(player).iron() == 40, "the purse is untouched");
        helper.assertTrue(Wallet.pay(player, 40) && Wallet.purse(player).iron() == 0, "all 40 can");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void everyCopperCoinIsAbsorbed(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.COPPER_COIN.get(), 150));
        long absorbed = Wallet.absorb(player);
        helper.assertTrue(absorbed == 1 && Wallet.purse(player).iron() == 1, "150 copper make 1 iron: " + absorbed);
        helper.assertTrue(player.getInventory().countItem(ModItems.COPPER_COIN.get()) == 0, "no copper coin stays in the inventory");
        helper.assertTrue(Wallet.purse(player).copper() == 50, "the 50 left over stay in the purse: " + Wallet.purse(player));
        player.getInventory().add(new ItemStack(ModItems.COPPER_COIN.get(), 50));
        Wallet.absorb(player);
        helper.assertTrue(Wallet.purse(player).iron() == 2 && Wallet.purse(player).copper() == 0, "50 + 50 copper make another iron");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thePurseSurvivesDeath(GameTestHelper helper) {
        ServerPlayer old = TestSupport.fakePlayer(helper);
        Wallet.add(old, 1234);
        ServerPlayer reborn = TestSupport.fakePlayer(helper);
        reborn.restoreFrom(old, false);
        helper.assertTrue(Wallet.purse(reborn).iron() == 1234, "the balance is kept at death: " + Wallet.purse(reborn));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thePurseSurvivesSaveAndLoad(GameTestHelper helper) {
        ServerPlayer old = TestSupport.fakePlayer(helper);
        Wallet.add(old, 777);
        CompoundTag tag = old.saveWithoutId(new CompoundTag());
        ServerPlayer fresh = TestSupport.fakePlayer(helper);
        helper.assertTrue(Wallet.purse(fresh).equals(Purse.EMPTY), "a new player has nothing");
        fresh.load(tag);
        helper.assertTrue(Wallet.purse(fresh).iron() == 777, "after load: " + Wallet.purse(fresh));
        helper.succeed();
    }

    // ---- The weight ----

    @GameTest(template = TestSupport.EMPTY)
    public static void carryingTooMuchSlowsYouOnlyWhenWeightIsOn(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.IRON_SWORD));
        }
        var id = Mitofus.id("weight_slowdown");
        WeightBalance.withOverride(new WeightBalance(false, 1.0, 0.0), () -> {
            WeightService.update(player);
            helper.assertFalse(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(id), "off by default: no slowdown");
        });
        WeightBalance.withOverride(new WeightBalance(true, 1.0, 0.0), () -> {
            WeightService.update(player);
            helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(id), "on and overloaded: slowed down");
            player.getInventory().clearContent();
            WeightService.update(player);
            helper.assertFalse(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(id), "empty: the slowdown goes away");
        });
        helper.succeed();
    }

    // ---- The residence ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theChiefMakesYouResidentOnceAndWithACooldown(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        VillageChiefNpc chief = helper.spawn(ModEntities.VILLAGE_CHIEF.get(), 1, 1, 1);
        player.moveTo(chief.getX(), chief.getY(), chief.getZ());
        helper.assertTrue(ResidenceService.become(player, chief) == ResidenceRules.Result.OK && ResidenceService.of(player).has(), "resident");
        helper.assertTrue(ResidenceService.become(player, chief) == ResidenceRules.Result.SAME_PLACE, "the same village again is refused");
        Residence elsewhere = new Residence(Optional.of(player.blockPosition()), "eibar", player.serverLevel().getGameTime());
        player.setData(ModAttachments.RESIDENCE.get(), elsewhere);
        helper.assertTrue(ResidenceService.become(player, chief) == ResidenceRules.Result.COOLDOWN, "changing village within a day is refused");
        helper.assertTrue(ResidenceService.of(player).equals(elsewhere), "and nothing changes");
        helper.succeed();
    }

    // ---- The coachman ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theCoachmanDoesNotTakeYouWhereThereIsNoRoadOrYouAreAlready(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        CoachmanNpc coachman = helper.spawn(ModEntities.COACHMAN.get(), 1, 1, 1);
        player.moveTo(coachman.getX(), coachman.getY(), coachman.getZ());
        Wallet.add(player, 5000);
        helper.assertFalse(CoachService.travel(player, coachman, "nowhere"), "an unknown destination");
        helper.assertFalse(CoachService.travel(player, coachman, "covadonga"), "the village the coachman is in");
        helper.assertTrue(Wallet.purse(player).iron() == 5000, "nothing was charged");
        helper.succeed();
    }

    // ---- The storage ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theStorageIsNotOpenedWithoutMoney(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        StorekeeperNpc keeper = helper.spawn(ModEntities.STOREKEEPER.get(), 1, 1, 1);
        player.moveTo(keeper.getX(), keeper.getY(), keeper.getZ());
        Wallet.add(player, 29);
        helper.assertFalse(StorageService.open(player, keeper), "29 iron do not open it");
        helper.assertTrue(!StorageService.of(player).opened() && Wallet.purse(player).iron() == 29, "nothing opened, nothing charged");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void eachExtraRowCostsMoreUpToSix(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        StorekeeperNpc keeper = helper.spawn(ModEntities.STOREKEEPER.get(), 1, 1, 1);
        player.moveTo(keeper.getX(), keeper.getY(), keeper.getZ());
        helper.assertFalse(StorageService.expand(player, keeper), "no storage, no rows");
        player.setData(ModAttachments.STORAGE.get(), new Storage(2, 123456, new CompoundTag()));
        long[] costs = {50, 100, 200, 400};
        for (int i = 0; i < costs.length; i++) {
            Wallet.add(player, costs[i] - 1);
            helper.assertFalse(StorageService.expand(player, keeper), "one iron short for row " + (3 + i));
            Wallet.add(player, 1);
            helper.assertTrue(StorageService.expand(player, keeper) && StorageService.of(player).rows() == 3 + i, "row " + (3 + i) + " for " + costs[i]);
            helper.assertTrue(Wallet.purse(player).iron() == 0, "paid exactly " + costs[i]);
        }
        Wallet.add(player, 10_000);
        helper.assertFalse(StorageService.expand(player, keeper), "six rows is the most");
        helper.assertTrue(StorageService.of(player).rows() == 6 && StorageService.of(player).number() == 123456, "six rows, same number");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theStorageIsOutOfReachWhenYouWalkAway(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        StorekeeperNpc keeper = helper.spawn(ModEntities.STOREKEEPER.get(), 1, 1, 1);
        player.moveTo(keeper.getX(), keeper.getY(), keeper.getZ());
        player.setData(ModAttachments.STORAGE.get(), new Storage(2, 123456, new CompoundTag()));
        helper.assertTrue(StorageService.open(player, keeper), "opens next to the storekeeper");
        helper.assertTrue(StorageService.withinReach(keeper, player), "in reach while close");
        player.moveTo(keeper.getX() + 20, keeper.getY(), keeper.getZ());
        helper.assertFalse(StorageService.withinReach(keeper, player), "out of reach from far away");
        keeper.discard();
        player.moveTo(keeper.getX(), keeper.getY(), keeper.getZ());
        helper.assertFalse(StorageService.withinReach(keeper, player), "a gone storekeeper is out of reach too");
        helper.succeed();
    }
}
