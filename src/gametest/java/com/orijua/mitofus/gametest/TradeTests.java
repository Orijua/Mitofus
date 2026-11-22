package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.economy.TradeCategory;
import com.orijua.mitofus.feature.economy.TradeGoods;
import com.orijua.mitofus.feature.economy.TradeKey;
import com.orijua.mitofus.feature.economy.TradeRules;
import com.orijua.mitofus.feature.economy.TradeSaturation;
import com.orijua.mitofus.feature.economy.TradeService;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.economy.TradeService.Result;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.economy.TradeSellPayload;
import com.orijua.mitofus.feature.notification.SystemNotification;
import com.orijua.mitofus.feature.notification.SystemNotificationPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** The economy, step 2: selling equipment in the Trade tab, and its saturation. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class TradeTests {
    /** The pre-gold price scale: see {@link TradeBalances#preGold()}. */
    private static final TradeBalance B = TradeBalances.preGold();
    private static final TradeKey SWORD_EPIC = new TradeKey(TradeCategory.SWORD, ItemRating.EPIC);

    private TradeTests() {
    }

    private static ItemStack rated(Item item, ItemRating rating) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.ITEM_RATING, rating);
        return stack;
    }

    /**
     * An epic piece of +10: iron (level 5 + 5 = 10) it is worth 21 x 1,18 x 2 = 49,56 -> 49, and sells for 19, then 18, 17, 16 as the
     * pressure of its key grows (49 x 0,4 x 0,95^n), so the price falls with every sale a test makes.
     */
    private static ItemStack epic(Item item) {
        ItemStack stack = rated(item, ItemRating.EPIC);
        stack.set(ModDataComponents.ENHANCEMENT, 10);
        return stack;
    }

    private static int gold(ServerPlayer player) {
        return (int) (Wallet.balance(player) / 100);
    }

    private static int pressure(ServerPlayer player, TradeKey key, long day) {
        return TradeService.saturation(player).pressure(key, day, B);
    }

    private static void run(Runnable body) {
        TradeBalance.withOverride(B, body);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sellingAnEpicIronSwordPaysSixteenGold(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(3, epic(Items.IRON_SWORD));
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0);
            helper.assertTrue(outcome.result() == Result.SOLD && outcome.paid() / 100 == 19, "sold for 19: " + outcome);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_SWORD) == 0, "the sword is gone from the inventory");
            helper.assertTrue(gold(player) == 19, "19 gold, got " + gold(player));
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 1, "sword/epic has pressure 1");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void twoSalesOfTheSameKeyLowerThePrice(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(0, epic(Items.IRON_SWORD));
            player.getInventory().setItem(1, epic(Items.IRON_SWORD));
            player.getInventory().setItem(2, epic(Items.IRON_HELMET));
            TradeService.Outcome first = TradeService.sell(player, 0, 0);
            helper.assertTrue(first.result() == Result.SOLD && first.paid() / 100 == 19, "first sword, expected 19, got " + first);
            TradeService.Outcome second = TradeService.sell(player, 1, 0);
            helper.assertTrue(second.result() == Result.SOLD && second.paid() / 100 == 18, "the second sword pays with the pressure of the first, expected 18, got " + second);
            TradeService.Outcome helmet = TradeService.sell(player, 2, 0);
            helper.assertTrue(helmet.result() == Result.SOLD && helmet.paid() / 100 == 19, "an epic helmet is another key, expected 19, got " + helmet);
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 2, "the sword key has pressure 2");
        });
        helper.succeed();
    }

    private static void assertRefused(GameTestHelper helper, ServerPlayer player, int slot, ItemStack stack, Result expected, String what) {
        ItemStack before = stack.copy();
        player.getInventory().setItem(slot, stack);
        TradeService.Outcome outcome = TradeService.sell(player, slot, 0);
        helper.assertTrue(outcome.result() == expected, what + ": expected " + expected + ", got " + outcome);
        helper.assertTrue(outcome.paid() == 0 && gold(player) == 0, what + ": nothing is paid");
        helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY), what + ": the saturation is untouched");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(slot), before), what + ": the piece stays");
        player.getInventory().setItem(slot, ItemStack.EMPTY);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void whatCannotBeSoldStaysIntact(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack loaned = epic(Items.IRON_SWORD);
            loaned.set(ModDataComponents.LOANED_FOR, Mitofus.id("some_quest"));
            assertRefused(helper, player, 2, loaned, Result.REFUSED, "a quest loan");
            assertRefused(helper, player, 2, new ItemStack(Items.IRON_SWORD), Result.REFUSED, "no rating");
            assertRefused(helper, player, 2, rated(Items.STICK, ItemRating.EPIC), Result.REFUSED, "not damageable");
            ItemStack two = epic(Items.IRON_SWORD);
            two.setCount(2);
            assertRefused(helper, player, 2, two, Result.REFUSED, "stacked");
            assertRefused(helper, player, 2, ItemStack.EMPTY, Result.EMPTY, "an empty slot");
        });
        helper.succeed();
    }

    private static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> copy = new ArrayList<>();
        for (List<ItemStack> list : List.of(inventory.items, inventory.armor, inventory.offhand)) {
            for (ItemStack stack : list) {
                copy.add(stack.copy());
            }
        }
        return copy;
    }

    private static void assertUnchanged(GameTestHelper helper, List<ItemStack> before, List<ItemStack> after, String what) {
        helper.assertTrue(before.size() == after.size(), what + ": same number of slots");
        for (int i = 0; i < before.size(); i++) {
            helper.assertTrue(ItemStack.matches(before.get(i), after.get(i)), what + ": snapshot slot " + i + " changed from " + before.get(i) + " to " + after.get(i));
        }
    }

    private static List<ItemEntity> goldDrops(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(6),
                entity -> entity.getItem().is(ModItems.GOLD_COIN.get()));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void invalidSlotsKeepEverythingIntact(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            Inventory inventory = player.getInventory();
            inventory.setItem(0, epic(Items.IRON_SWORD));
            inventory.armor.set(0, rated(Items.IRON_BOOTS, ItemRating.EPIC));
            inventory.armor.set(1, rated(Items.IRON_LEGGINGS, ItemRating.EPIC));
            inventory.armor.set(2, rated(Items.IRON_CHESTPLATE, ItemRating.EPIC));
            inventory.armor.set(3, epic(Items.IRON_HELMET));
            inventory.offhand.set(0, epic(Items.IRON_SWORD));
            List<ItemStack> before = snapshot(inventory);
            for (int slot : new int[]{-1, 36, 37, 38, 39, 40, 41, 99, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                TradeService.Outcome outcome = TradeService.sell(player, slot, 0);
                helper.assertTrue(outcome.result() == Result.INVALID_SLOT, "slot " + slot + ": expected INVALID_SLOT, got " + outcome);
            }
            assertUnchanged(helper, before, snapshot(inventory), "invalid slots");
            helper.assertTrue(gold(player) == 0, "nothing paid");
            helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY), "the saturation is untouched");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sellingFromBoundarySlotsWorks(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(0, epic(Items.IRON_SWORD));
            player.getInventory().setItem(35, epic(Items.IRON_SWORD));
            TradeService.Outcome first = TradeService.sell(player, 0, 0);
            helper.assertTrue(first.result() == Result.SOLD && first.paid() / 100 == 19, "slot 0: expected 19, got " + first);
            TradeService.Outcome last = TradeService.sell(player, 35, 0);
            helper.assertTrue(last.result() == Result.SOLD && last.paid() / 100 == 18, "slot 35 with pressure 1: expected 18, got " + last);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void outsideMitofusChecksBeforeTheSlot(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            VrHelmetTests.outsideMitofus(helper, () -> {
                for (int slot : new int[]{99, 3}) {
                    TradeService.Outcome outcome = TradeService.sell(player, slot, 0);
                    helper.assertTrue(outcome.result() == Result.OUTSIDE_MITOFUS, "slot " + slot + " (empty or invalid): expected OUTSIDE_MITOFUS, got " + outcome);
                }
            });
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void nothingSellsOutsideMitofus(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            VrHelmetTests.outsideMitofus(helper, () -> {
                TradeService.Outcome outcome = TradeService.sell(player, 3, 0);
                helper.assertTrue(outcome.result() == Result.OUTSIDE_MITOFUS, "outside Mitofus: " + outcome);
            });
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), sword), "the sword stays");
            helper.assertTrue(gold(player) == 0 && TradeService.saturation(player).equals(TradeSaturation.EMPTY), "nothing paid or counted");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sendingTheSameSlotTwiceSellsOnce(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(3, epic(Items.IRON_SWORD));
            helper.assertTrue(TradeService.sell(player, 3, 0).result() == Result.SOLD, "the first goes through");
            helper.assertTrue(TradeService.sell(player, 3, 0).result() == Result.EMPTY, "the second finds the slot empty");
            helper.assertTrue(gold(player) == 19, "paid once");
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 1, "counted once");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aFailedPaymentGivesTheSwordBack(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0, (p, amount) -> {
                throw new IllegalStateException("the payment broke");
            });
            helper.assertTrue(outcome.result() == Result.FAILED, "failed: " + outcome);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), sword), "the sword is back in its slot");
            helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY), "the saturation is untouched");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 0 && gold(player) == 0, "nothing was paid");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aFullInventoryDropsTheRestOfTheGold(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            ItemStack legendary = rated(ModItems.DURANDARTE.get(), ItemRating.LEGENDARY);
            legendary.set(ModDataComponents.ENHANCEMENT, 5);
            player.getInventory().setItem(5, legendary);
            TradeService.Outcome outcome = TradeService.sell(player, 5, 0);
            helper.assertTrue(outcome.result() == Result.SOLD && outcome.paid() / 100 == 907, "907 for a legendary diamond piece (450 x 2 x 1,68 x 1,5 x 0,4 = 907,2): " + outcome);
            int carried = gold(player);
            helper.assertTrue(carried == 907, "the 907 are in the purse, got " + carried);
            helper.assertTrue(goldDrops(helper, player).isEmpty(), "nothing falls on the ground: the money is a balance");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thePressureRecoversWithTheDays(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            for (int i = 0; i < 3; i++) {
                player.getInventory().setItem(i, epic(Items.IRON_SWORD));
                helper.assertTrue(TradeService.sell(player, i, 0).result() == Result.SOLD, "sale " + i);
            }
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 3, "pressure 3 the same day");
            helper.assertTrue(pressure(player, SWORD_EPIC, 1) == 1, "pressure 1 a day later");
            helper.assertTrue(pressure(player, SWORD_EPIC, 5) == 0, "pressure 0 five days later");
            player.getInventory().setItem(4, epic(Items.IRON_SWORD));
            TradeService.Outcome again = TradeService.sell(player, 4, 5);
            helper.assertTrue(again.result() == Result.SOLD && again.paid() / 100 == 19, "the full price again, expected 19, got " + again);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSaturationSurvivesDeath(GameTestHelper helper) {
        run(() -> {
            ServerPlayer old = TestSupport.fakePlayer(helper);
            old.getInventory().setItem(3, epic(Items.IRON_SWORD));
            TradeService.sell(old, 3, 0);
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            helper.assertTrue(TradeService.saturation(fresh).equals(TradeSaturation.EMPTY), "a new player has none");
            fresh.restoreFrom(old, false);
            helper.assertTrue(TradeService.saturation(fresh).equals(TradeService.saturation(old)) && pressure(fresh, SWORD_EPIC, 0) == 1,
                    "dying does not touch the saturation");
            ModAttachments.resetAll(fresh);
            helper.assertTrue(TradeService.saturation(fresh).equals(TradeSaturation.EMPTY), "resetAll clears it");
        });
        helper.succeed();
    }

    private static void category(GameTestHelper helper, Item item, TradeCategory expected, double material) {
        ItemStack stack = rated(item, ItemRating.NORMAL);
        TradeCategory actual = TradeGoods.key(stack).category();
        helper.assertTrue(actual == expected, item + ": category " + expected + ", got " + actual);
        double multiplier = TradeGoods.material(stack, B);
        helper.assertTrue(multiplier == material, item + ": material x" + material + ", got " + multiplier);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void realItemsGetTheirCategoryAndMaterial(GameTestHelper helper) {
        run(() -> {
            category(helper, Items.IRON_SWORD, TradeCategory.SWORD, 1.0);
            category(helper, Items.WOODEN_SWORD, TradeCategory.SWORD, 0.5);
            category(helper, Items.GOLDEN_AXE, TradeCategory.AXE, 0.5);
            category(helper, Items.DIAMOND_PICKAXE, TradeCategory.PICKAXE, 2.0);
            category(helper, Items.STONE_SHOVEL, TradeCategory.SHOVEL, 1.0);
            category(helper, Items.BOW, TradeCategory.BOW, 1.0);
            category(helper, Items.SHIELD, TradeCategory.SHIELD, 1.0);
            category(helper, Items.LEATHER_BOOTS, TradeCategory.BOOTS, 0.5);
            category(helper, Items.CHAINMAIL_CHESTPLATE, TradeCategory.CHESTPLATE, 1.0);
            category(helper, Items.NETHERITE_LEGGINGS, TradeCategory.LEGGINGS, 3.0);
            category(helper, Items.IRON_HELMET, TradeCategory.HELMET, 1.0);
            category(helper, ModItems.WOLFRAM_SWORD.get(), TradeCategory.SWORD, 2.0);
            category(helper, ModItems.GUADALETE_SWORD.get(), TradeCategory.SWORD, 3.0);
            category(helper, Items.IRON_HOE, TradeCategory.OTHER, 1.0);
            category(helper, Items.TRIDENT, TradeCategory.OTHER, 1.0);
            category(helper, ModItems.IRON_SMITHING_HAMMER.get(), TradeCategory.OTHER, 1.0);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void enhancementRaisesTheValueOfEveryPiece(GameTestHelper helper) {
        run(() -> {
            ItemStack armor = rated(Items.IRON_CHESTPLATE, ItemRating.EPIC);
            armor.set(ModDataComponents.ENHANCEMENT, 5);
            helper.assertTrue(TradeGoods.reference(armor, B) == 37, "armor +5 counts too: 21 x 1,18 x 1,5 = 37,17, got " + TradeGoods.reference(armor, B));
            ItemStack sword = epic(Items.IRON_SWORD);
            sword.set(ModDataComponents.ENHANCEMENT, 5);
            helper.assertTrue(TradeGoods.reference(sword, B) == 37, "sword +5 is worth 37, got " + TradeGoods.reference(sword, B));
            ItemStack legendary = rated(Items.DIAMOND_SWORD, ItemRating.LEGENDARY);
            legendary.set(ModDataComponents.ENHANCEMENT, 5);
            helper.assertTrue(TradeGoods.reference(legendary, B) == 2268, "diamond legendary +5 is worth 450 x 2 x 1,68 x 1,5 = 2268");
        });
        helper.succeed();
    }

    // ---- BUG-055: the piece and the price the player saw ----

    private static TradeService.Outcome sellExpecting(ServerPlayer player, int slot, ItemStack seen, long price) {
        return TradeService.sell(player, slot, 0, new TradeService.Expected(seen, price));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDifferentPieceInTheSlotIsStale(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack seen = epic(Items.IRON_SWORD);
            ItemStack now = rated(Items.IRON_SWORD, ItemRating.LEGENDARY);
            player.getInventory().setItem(3, now.copy());
            TradeService.Outcome outcome = sellExpecting(player, 3, seen, 1960);
            helper.assertTrue(outcome.result() == Result.STALE, "expected STALE, got " + outcome);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), now), "the legendary stays");
            helper.assertTrue(gold(player) == 0, "nothing paid");
            helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY), "the saturation is untouched");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aChangedPriceIsStale(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.setData(ModAttachments.TRADE_SATURATION.get(), TradeSaturation.EMPTY.recordSale(SWORD_EPIC, 0, B));
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            TradeService.Outcome outcome = sellExpecting(player, 3, sword, 1960);
            helper.assertTrue(outcome.result() == Result.STALE, "the real price is 18, not the 19 seen: " + outcome);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), sword), "the sword stays");
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 1 && gold(player) == 0, "nothing moved");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSeenPieceAtTheSeenPriceSells(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            TradeService.Outcome outcome = sellExpecting(player, 3, sword, 1960);
            helper.assertTrue(outcome.result() == Result.SOLD && outcome.paid() / 100 == 19, "expected SOLD 19, got " + outcome);
            helper.assertTrue(gold(player) == 19, "19 gold, got " + gold(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anEmptyExpectedStackIsStale(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            TradeService.Outcome outcome = sellExpecting(player, 3, ItemStack.EMPTY, 1960);
            helper.assertTrue(outcome.result() == Result.STALE, "expected STALE, got " + outcome);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), sword), "the sword stays");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thePacketPathSellsOnceAndTheRepeatIsStale(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            for (int slot = 0; slot < 3; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            TradeSellPayload payload = new TradeSellPayload(List.of(new TradeSellPayload.Entry(3, sword.copy(), 1960)));
            TradeService.Batch first = TradeService.sell(player, payload);
            helper.assertTrue(first.sold() == 1 && first.paid() / 100 == 19 && first.skipped() == 0 && first.first() == Result.SOLD, "the first sells: " + first);
            helper.assertTrue(Wallet.purse(player).iron() == 1960 && player.getInventory().getItem(3).isEmpty(),
                    "the 19,60 go to the purse and the slot is free: " + Wallet.purse(player));
            TradeService.Batch second = TradeService.sell(player, payload);
            helper.assertTrue(second.sold() == 0 && second.skipped() == 1 && second.first() == Result.EMPTY, "the same packet again finds the slot empty, got " + second);
            helper.assertTrue(gold(player) == 19 && pressure(player, SWORD_EPIC, TradeRules.day(player.serverLevel().getGameTime())) == 1, "paid and counted once");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sendingTheSameSlotTwiceInSellMode(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            for (int slot = 0; slot < 3; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            helper.assertTrue(TradeService.sell(player, 3, 0).result() == Result.SOLD, "the first goes through");
            helper.assertTrue(player.getInventory().getItem(3).isEmpty() && Wallet.purse(player).iron() == 1960, "19,60 in the purse, slot 3 free");
            TradeService.Outcome second = TradeService.sell(player, 3, 0);
            helper.assertTrue(second.result() == Result.EMPTY, "the slot is empty now, got " + second);
            helper.assertTrue(gold(player) == 19 && pressure(player, SWORD_EPIC, 0) == 1, "19 gold and pressure 1 in total");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSellPayloadCodecRoundTrips(GameTestHelper helper) {
        ItemStack rich = rated(Items.IRON_SWORD, ItemRating.LEGENDARY);
        rich.set(ModDataComponents.ENHANCEMENT, 4);
        for (int count : new int[]{0, 1, 36}) {
            List<TradeSellPayload.Entry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                entries.add(new TradeSellPayload.Entry(i == 0 ? Integer.MAX_VALUE : i - 1, i % 2 == 0 ? rich : ItemStack.EMPTY, 171000L + i));
            }
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            TradeSellPayload.STREAM_CODEC.encode(buffer, new TradeSellPayload(entries));
            TradeSellPayload back = TradeSellPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(back.entries().size() == count, count + " entries, got " + back.entries().size());
            for (int i = 0; i < count; i++) {
                TradeSellPayload.Entry want = entries.get(i);
                TradeSellPayload.Entry got = back.entries().get(i);
                helper.assertTrue(got.slot() == want.slot() && got.expectedPrice() == want.expectedPrice() && ItemStack.matches(got.expected(), want.expected()),
                        "entry " + i + ": got " + got);
            }
        }
        // 37 entries written by hand (the encoder refuses them): the decoder refuses them too.
        RegistryFriendlyByteBuf tooMany = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        tooMany.writeVarInt(37);
        for (int i = 0; i < 37; i++) {
            tooMany.writeVarInt(0);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(tooMany, ItemStack.EMPTY);
            tooMany.writeVarLong(1L);
        }
        boolean refused = false;
        try {
            TradeSellPayload.STREAM_CODEC.decode(tooMany);
        } catch (RuntimeException e) {
            refused = true;
        }
        helper.assertTrue(refused, "a batch of 37 entries does not decode");
        helper.succeed();
    }

    // ---- BUG-056: cap, parts and minimum price ----

    private static void withBalance(TradeBalance balance, Runnable body) {
        TradeBalance.withOverride(balance, body);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSaleOverTheCapFailsBeforeTakingThePiece(GameTestHelper helper) {
        withBalance(TradeBalances.preGoldWithMaxSell(10), () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0);
            helper.assertTrue(outcome.result() == Result.FAILED, "19 over a cap of 10 fails, got " + outcome);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), sword), "the sword stays");
            helper.assertTrue(gold(player) == 0 && goldDrops(helper, player).isEmpty(), "no gold anywhere");
            helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY), "the saturation is untouched");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPartialPaymentDoesNotDuplicate(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack legendary = rated(ModItems.DURANDARTE.get(), ItemRating.LEGENDARY);
            legendary.set(ModDataComponents.ENHANCEMENT, 5);
            player.getInventory().setItem(5, legendary);
            int[] calls = {0};
            TradeService.Outcome outcome = TradeService.sell(player, 5, 0, (p, amount) -> {
                if (calls[0]++ == 1) {
                    throw new IllegalStateException("the second pile broke");
                }
                TradeService.give(p, amount);
            });
            helper.assertTrue(outcome.result() == Result.FAILED && outcome.paid() / 100 == 64, "FAILED having paid 64, got " + outcome);
            helper.assertTrue(player.getInventory().countItem(ModItems.DURANDARTE.get()) == 0, "the piece does NOT come back");
            int total = gold(player);
            List<ItemEntity> drops = goldDrops(helper, player);
            for (ItemEntity drop : drops) {
                total += drop.getItem().getCount();
                drop.discard();
            }
            helper.assertTrue(total == 64, "64 gold in total, got " + total);
            helper.assertTrue(pressure(player, new TradeKey(TradeCategory.SWORD, ItemRating.LEGENDARY), 0) == 1, "the sale is counted");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theFirstPartFailingRestoresWithoutOverwriting(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0, (p, amount) -> {
                p.getInventory().setItem(3, new ItemStack(Items.DIRT, 5));
                throw new IllegalStateException("broke after something took the slot");
            });
            helper.assertTrue(outcome.result() == Result.FAILED && outcome.paid() == 0, "failed with nothing paid: " + outcome);
            ItemStack inSlot = player.getInventory().getItem(3);
            helper.assertTrue(inSlot.is(Items.DIRT) && inSlot.getCount() == 5, "what took the slot stays: " + inSlot);
            helper.assertTrue(player.getInventory().countItem(Items.IRON_SWORD) == 1, "the sword is somewhere else in the inventory");
            helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY), "the saturation is untouched");
        });
        helper.succeed();
    }

    // ---- BUG-060: other cases ----

    @GameTest(template = TestSupport.EMPTY)
    public static void fullInventoryWithPartialGoldStack(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            player.getInventory().setItem(0, new ItemStack(ModItems.GOLD_COIN.get(), 30));
            ItemStack legendary = rated(ModItems.DURANDARTE.get(), ItemRating.LEGENDARY);
            legendary.set(ModDataComponents.ENHANCEMENT, 5);
            player.getInventory().setItem(5, legendary);
            TradeService.Outcome outcome = TradeService.sell(player, 5, 0);
            helper.assertTrue(outcome.result() == Result.SOLD && outcome.paid() / 100 == 907, "sold for 907: " + outcome);
            int carried = gold(player);
            helper.assertTrue(carried == 937, "937 in total: 907 of the sale in the purse and the 30 coins carried, got " + carried);
            helper.assertTrue(goldDrops(helper, player).isEmpty(), "nothing falls on the ground: the money is a balance");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sellUsesTheServerClock(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(3, epic(Items.IRON_SWORD));
            helper.assertTrue(TradeService.sell(player, 3).result() == Result.SOLD, "sold");
            long expected = TradeRules.day(player.serverLevel().getGameTime());
            TradeSaturation.Entry entry = TradeService.saturation(player).entries().get(SWORD_EPIC);
            helper.assertTrue(entry != null && entry.lastDay() == expected, "the stamp is the server day " + expected + ", got " + entry);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSaturationSurvivesSaveAndLoad(GameTestHelper helper) {
        run(() -> {
            ServerPlayer old = TestSupport.fakePlayer(helper);
            old.getInventory().setItem(3, epic(Items.IRON_SWORD));
            TradeService.sell(old, 3, 0);
            CompoundTag tag = old.saveWithoutId(new CompoundTag());
            ServerPlayer fresh = TestSupport.fakePlayer(helper);
            helper.assertTrue(TradeService.saturation(fresh).equals(TradeSaturation.EMPTY), "a new player has none");
            fresh.load(tag);
            helper.assertTrue(TradeService.saturation(fresh).equals(TradeService.saturation(old)) && pressure(fresh, SWORD_EPIC, 0) == 1,
                    "after load: " + TradeService.saturation(fresh));
            ServerPlayer moved = TestSupport.fakePlayer(helper);
            moved.restoreFrom(old, true);
            helper.assertTrue(TradeService.saturation(moved).equals(TradeService.saturation(old)), "a dimension change keeps it");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void ratingAndCategoryChangeTheKey(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            for (int i = 0; i < 2; i++) {
                player.getInventory().setItem(i, rated(Items.IRON_SWORD, ItemRating.RARE));
                helper.assertTrue(TradeService.sell(player, i, 0).result() == Result.SOLD, "rare sale " + i);
            }
            player.getInventory().setItem(2, epic(Items.IRON_SWORD));
            TradeService.Outcome epic = TradeService.sell(player, 2, 0);
            helper.assertTrue(epic.paid() / 100 == 19, "an epic sword has its own pressure 0: expected 19, got " + epic);
            player.getInventory().setItem(3, epic(Items.IRON_AXE));
            TradeService.Outcome axe = TradeService.sell(player, 3, 0);
            helper.assertTrue(axe.paid() / 100 == 19, "an epic axe is another key than the epic sword: expected 19, got " + axe);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void modItemsUseOtherMaterial(GameTestHelper helper) {
        // Limit: a tier of another mod or foreign armor cannot be tested without registering an item after the registry froze.
        TradeBalance other = TradeBalances.preGoldWithOtherMaterial(7.0);
        for (Item item : new Item[]{Items.BOW, Items.SHIELD, Items.TRIDENT, ModItems.IRON_SMITHING_HAMMER.get()}) {
            double material = TradeGoods.material(rated(item, ItemRating.NORMAL), other);
            helper.assertTrue(material == 7.0, item + ": expected 7.0, got " + material);
        }
        double hoe = TradeGoods.material(rated(Items.IRON_HOE, ItemRating.NORMAL), other);
        helper.assertTrue(hoe == 1.0, "an iron hoe is still iron: expected 1.0, got " + hoe);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSaleAtTheCapPaysTheFloor(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            TradeSaturation saturated = TradeSaturation.EMPTY;
            for (int i = 0; i < 40; i++) {
                saturated = saturated.recordSale(SWORD_EPIC, 0, B);
            }
            player.setData(ModAttachments.TRADE_SATURATION.get(), saturated);
            player.getInventory().setItem(0, epic(Items.IRON_SWORD));
            player.getInventory().setItem(1, epic(Items.IRON_SWORD));
            TradeService.Outcome sale = TradeService.sell(player, 0, 0);
            helper.assertTrue(sale.result() == Result.SOLD && sale.paid() / 100 == 2, "a sale at the cap: expected 2, got " + sale);
            TradeService.Outcome another = TradeService.sell(player, 1, 0);
            helper.assertTrue(another.result() == Result.SOLD && another.paid() / 100 == 2, "the next one at the cap: expected 2, got " + another);
        });
        helper.succeed();
    }

    // ---- Selling several pieces at once ----

    /** The real payment (gold into the inventory, the rest on the ground), for tests that call the batch directly. */
    private static void pay(ServerPlayer player, long amount) {
        TradeService.give(player, amount);
    }

    /** What the tab would send: the piece in each slot and the price the batch pays for it in this order. */
    private static List<TradeSellPayload.Entry> entriesFor(ServerPlayer player, int... slots) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot : slots) {
            stacks.add(player.getInventory().getItem(slot).copy());
        }
        long[] prices = TradeGoods.salePrices(stacks, TradeService.saturation(player), 0, B);
        List<TradeSellPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            entries.add(new TradeSellPayload.Entry(slots[i], stacks.get(i), prices[i]));
        }
        return entries;
    }

    private static String keyOf(java.util.Optional<net.minecraft.network.chat.Component> message) {
        return ((net.minecraft.network.chat.contents.TranslatableContents) message.orElseThrow().getContents()).getKey();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sellAllSellsEveryPieceAndPaysTheTotal(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            for (int slot = 0; slot < 3; slot++) {
                player.getInventory().setItem(slot, epic(Items.IRON_SWORD));
            }
            player.getInventory().setItem(3, epic(Items.IRON_HELMET));
            List<TradeSellPayload.Entry> entries = entriesFor(player, 0, 1, 2, 3);
            long[] prices = entries.stream().mapToLong(TradeSellPayload.Entry::expectedPrice).toArray();
            helper.assertTrue(TradeRules.total(prices) == 7550, "19,60 + 18,62 + 17,68 + 19,60 = 75,50, got " + java.util.Arrays.toString(prices));
            TradeService.Batch batch = TradeService.sellAll(player, entries, 0, TradeTests::pay);
            helper.assertTrue(batch.sold() == 4 && batch.paid() == 7550 && batch.skipped() == 0 && batch.first() == Result.SOLD, "all sold: " + batch);
            helper.assertTrue(gold(player) == 75, "75 gold (75,50 in the purse), got " + gold(player));
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 3, "sword pressure 3");
            helper.assertTrue(pressure(player, new TradeKey(TradeCategory.HELMET, ItemRating.EPIC), 0) == 1, "helmet pressure 1");
            // Every piece is gone: only coins are left (gold and iron share the first slots).
            for (int slot = 0; slot < 4; slot++) {
                ItemStack left = player.getInventory().getItem(slot);
                helper.assertTrue(left.isEmpty() || left.is(ModItems.GOLD_COIN.get()) || left.is(ModItems.IRON_COIN.get()), "slot " + slot + " holds only coins: " + left);
            }
            helper.assertTrue(TradeService.message(batch).isEmpty(), "no message for a full sale");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sellAllMatchesSellingOneByOne(GameTestHelper helper) {
        run(() -> {
            ServerPlayer batch = TestSupport.fakePlayer(helper);
            ServerPlayer single = TestSupport.fakePlayer(helper);
            Item[] items = {Items.IRON_SWORD, Items.IRON_HELMET, Items.IRON_SWORD, Items.IRON_SWORD, Items.IRON_HELMET};
            for (int slot = 0; slot < items.length; slot++) {
                batch.getInventory().setItem(slot, rated(items[slot], ItemRating.EPIC));
                single.getInventory().setItem(slot, rated(items[slot], ItemRating.EPIC));
            }
            TradeService.Batch result = TradeService.sellAll(batch, entriesFor(batch, 0, 1, 2, 3, 4), 0, TradeTests::pay);
            helper.assertTrue(result.sold() == 5, "five sold: " + result);
            for (int slot = 0; slot < items.length; slot++) {
                helper.assertTrue(TradeService.sell(single, slot, 0, TradeTests::pay).result() == Result.SOLD, "single sale " + slot);
            }
            helper.assertTrue(gold(batch) == gold(single),
                    "same gold: batch " + gold(batch) + ", one by one " + gold(single) + ", paid " + result.paid());
            helper.assertTrue(TradeService.saturation(batch).equals(TradeService.saturation(single)), "same saturation");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aStaleEntryInTheMiddleIsSkipped(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(0, epic(Items.IRON_SWORD));
            player.getInventory().setItem(1, epic(Items.IRON_AXE));
            player.getInventory().setItem(2, epic(Items.IRON_HELMET));
            List<TradeSellPayload.Entry> entries = entriesFor(player, 0, 1, 2);
            ItemStack changed = rated(Items.IRON_AXE, ItemRating.RARE);
            player.getInventory().setItem(1, changed.copy());
            TradeService.Batch batch = TradeService.sellAll(player, entries, 0, TradeTests::pay);
            helper.assertTrue(batch.sold() == 2 && batch.skipped() == 1 && batch.first() == Result.STALE && batch.paid() == 3920, "two sold, one skipped: " + batch);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(1), changed), "the changed axe stays");
            helper.assertTrue(pressure(player, new TradeKey(TradeCategory.AXE, ItemRating.EPIC), 0) == 0
                    && pressure(player, new TradeKey(TradeCategory.AXE, ItemRating.RARE), 0) == 0, "no pressure on the axe");
            helper.assertTrue("message.mitofus.trade_skipped".equals(keyOf(TradeService.message(batch))), "the message says some were skipped");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aStaleEntryCascadesOnlyItsKey(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            for (int slot = 0; slot < 3; slot++) {
                player.getInventory().setItem(slot, epic(Items.IRON_SWORD));
            }
            List<TradeSellPayload.Entry> entries = entriesFor(player, 0, 1, 2);
            player.getInventory().setItem(1, rated(Items.IRON_SWORD, ItemRating.RARE));
            TradeService.Batch batch = TradeService.sellAll(player, entries, 0, TradeTests::pay);
            helper.assertTrue(batch.sold() == 1 && batch.skipped() == 2 && batch.paid() / 100 == 19,
                    "the middle one changed, so the third saw a price (17) the server no longer has (18): " + batch);
            helper.assertTrue(player.getInventory().getItem(2).is(Items.IRON_SWORD), "the third sword stays");
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 1, "only the first one counted");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBatchWithRepeatedSlotsTouchesNothing(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            List<TradeSellPayload.Entry> entries = List.of(new TradeSellPayload.Entry(3, sword.copy(), 1960), new TradeSellPayload.Entry(3, sword.copy(), 1862));
            TradeService.Batch batch = TradeService.sellAll(player, entries, 0, TradeTests::pay);
            helper.assertTrue(batch.sold() == 0 && batch.paid() == 0 && batch.skipped() == 2 && batch.first() == Result.INVALID_SLOT, "refused whole: " + batch);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), sword), "the sword stays");
            helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY) && gold(player) == 0, "nothing counted or paid");
            helper.assertTrue("message.mitofus.trade_stale".equals(keyOf(TradeService.message(batch))), "the stale message");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBatchWithAnOutOfRangeSlotTouchesNothing(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(0, sword.copy());
            List<List<TradeSellPayload.Entry>> batches = new ArrayList<>();
            batches.add(List.of(new TradeSellPayload.Entry(0, sword.copy(), 1960), new TradeSellPayload.Entry(36, sword.copy(), 1960)));
            batches.add(List.of(new TradeSellPayload.Entry(-1, sword.copy(), 1960)));
            List<TradeSellPayload.Entry> tooMany = new ArrayList<>();
            for (int i = 0; i < 37; i++) {
                tooMany.add(new TradeSellPayload.Entry(i % 36, sword.copy(), 1960));
            }
            batches.add(tooMany);
            batches.add(List.of());
            for (List<TradeSellPayload.Entry> entries : batches) {
                TradeService.Batch batch = TradeService.sellAll(player, entries, 0, TradeTests::pay);
                helper.assertTrue(batch.sold() == 0 && batch.first() == Result.INVALID_SLOT, entries.size() + " entries: refused, got " + batch);
                helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), sword), "the sword stays");
                helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY) && gold(player) == 0, "nothing counted or paid");
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aLoanedPieceInABatchIsNotSold(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack loaned = epic(Items.IRON_SWORD);
            loaned.set(ModDataComponents.LOANED_FOR, Mitofus.id("some_quest"));
            player.getInventory().setItem(0, loaned.copy());
            player.getInventory().setItem(1, epic(Items.IRON_SWORD));
            List<TradeSellPayload.Entry> entries = List.of(
                    new TradeSellPayload.Entry(0, loaned.copy(), 1960),
                    new TradeSellPayload.Entry(1, epic(Items.IRON_SWORD), 1960));
            TradeService.Batch batch = TradeService.sellAll(player, entries, 0, TradeTests::pay);
            helper.assertTrue(batch.sold() == 1 && batch.skipped() == 1 && batch.first() == Result.REFUSED && batch.paid() / 100 == 19, "only the normal one sells: " + batch);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), loaned), "the loaned sword stays");
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 1, "only one counted");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sellAllOutsideMitofusTouchesNothing(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = epic(Items.IRON_SWORD);
            player.getInventory().setItem(3, sword.copy());
            List<TradeSellPayload.Entry> entries = entriesFor(player, 3);
            VrHelmetTests.outsideMitofus(helper, () -> {
                TradeService.Batch batch = TradeService.sellAll(player, entries, 0, TradeTests::pay);
                helper.assertTrue(batch.sold() == 0 && batch.skipped() == 1 && batch.first() == Result.OUTSIDE_MITOFUS, "outside Mitofus: " + batch);
                helper.assertTrue("message.mitofus.trade_outside".equals(keyOf(TradeService.message(batch))), "the outside message");
            });
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), sword), "the sword stays");
            helper.assertTrue(gold(player) == 0 && TradeService.saturation(player).equals(TradeSaturation.EMPTY), "nothing paid or counted");
        });
        helper.succeed();
    }

    /** Swaps the connection of a fake player for one that keeps the system notifications it is sent. */
    private static List<SystemNotification> recordNotifications(ServerPlayer player) {
        List<SystemNotification> sent = new ArrayList<>();
        new ServerGamePacketListenerImpl(player.server, new Connection(PacketFlow.CLIENTBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof SystemNotificationPayload payload) {
                    sent.add(payload.notification());
                }
            }
        };
        return sent;
    }

    private static String firstLineKey(SystemNotification notification) {
        return ((net.minecraft.network.chat.contents.TranslatableContents) notification.lines().get(0).getContents()).getKey();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void oneNotificationForTheBatch(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            List<SystemNotification> sent = recordNotifications(player);
            for (int slot = 0; slot < 3; slot++) {
                player.getInventory().setItem(slot, epic(Items.IRON_SWORD));
            }
            TradeService.sellAll(player, entriesFor(player, 0, 1, 2), 0, TradeTests::pay);
            helper.assertTrue(sent.size() == 1 && sent.get(0).kind() == SystemNotification.Kind.TRADE
                    && firstLineKey(sent.get(0)).equals("notification.mitofus.trade.sold_many"), "one sold_many notification, got " + sent);
            sent.clear();
            player.getInventory().setItem(5, epic(Items.IRON_HELMET));
            TradeService.sellAll(player, entriesFor(player, 5), 0, TradeTests::pay);
            helper.assertTrue(sent.size() == 1 && firstLineKey(sent.get(0)).equals("notification.mitofus.trade.sold"), "one sold notification, got " + sent);
            sent.clear();
            player.getInventory().setItem(6, rated(Items.IRON_BOOTS, ItemRating.EPIC));
            TradeService.sell(player, 6, 0);
            helper.assertTrue(sent.size() == 1 && firstLineKey(sent.get(0)).equals("notification.mitofus.trade.sold"), "a single sale still notifies once, got " + sent);
        });
        helper.succeed();
    }

    /** The notification writes an amount of 1000 or more with the thousands separator, as every other amount. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theSaleNotificationWritesBigAmountsWithTheSeparator(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            List<SystemNotification> sent = recordNotifications(player);
            // A ancestral iron sword (level 5 + 40 = 45): 1800 x 1,88 x 0,4 = 1353,6 -> 1353. Nothing is delivered (no stack of 1000 fits the test inventory).
            player.getInventory().setItem(0, rated(Items.IRON_SWORD, ItemRating.ANCESTRAL));
            TradeService.Outcome outcome = TradeService.sell(player, 0, 0, (p, amount) -> { });
            helper.assertTrue(outcome.result() == Result.SOLD && outcome.paid() / 100 == 1353, "sold for 1353: " + outcome);
            helper.assertTrue(sent.size() == 1 && firstLineArgs(sent.get(0))[1].equals(com.orijua.mitofus.feature.economy.MoneyText.iron(outcome.paid())), "the single sale says 1.353: " + sent);

            sent.clear();
            player.getInventory().setItem(1, rated(Items.IRON_SWORD, ItemRating.ANCESTRAL));
            player.getInventory().setItem(2, rated(Items.IRON_SWORD, ItemRating.ANCESTRAL));
            TradeService.Batch batch = TradeService.sellAll(player, entriesFor(player, 1, 2), 0, (p, amount) -> { });
            // The first sale left pressure 1 on the key; the second of the batch is priced with pressure 1 and the third with 2.
            long reference = com.orijua.mitofus.feature.economy.TradeGoods.reference(rated(Items.IRON_SWORD, ItemRating.ANCESTRAL), B);
            long expected = TradeRules.sellPriceIron(reference, 1, B) + TradeRules.sellPriceIron(reference, 2, B);
            helper.assertTrue(expected >= 100_000, "the precondition: the total is a big amount: " + expected);
            helper.assertTrue(batch.sold() == 2 && batch.paid() == expected, "two sold for exactly " + expected + ": " + batch);
            helper.assertTrue(sent.size() == 1 && firstLineKey(sent.get(0)).equals("notification.mitofus.trade.sold_many")
                            && firstLineArgs(sent.get(0))[1].equals(com.orijua.mitofus.feature.economy.MoneyText.iron(batch.paid()))
                            && String.valueOf(firstLineArgs(sent.get(0))[1]).contains("."),
                    "the batch says the total with the separator: " + sent);
        });
        helper.succeed();
    }

    private static Object[] firstLineArgs(SystemNotification notification) {
        return ((net.minecraft.network.chat.contents.TranslatableContents) notification.lines().get(0).getContents()).getArgs();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPartialPaymentInABatchCountsWhatWasPaid(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack legendary = rated(ModItems.DURANDARTE.get(), ItemRating.LEGENDARY);
            legendary.set(ModDataComponents.ENHANCEMENT, 5);
            player.getInventory().setItem(5, legendary);
            player.getInventory().setItem(6, epic(Items.IRON_SWORD));
            List<TradeSellPayload.Entry> entries = entriesFor(player, 5, 6);
            int[] calls = {0};
            TradeService.Batch batch = TradeService.sellAll(player, entries, 0, (p, amount) -> {
                if (calls[0]++ == 1) {
                    throw new IllegalStateException("the second pile broke");
                }
                pay(p, amount);
            });
            helper.assertTrue(batch.sold() == 2 && batch.failed() == 1 && batch.skipped() == 0 && batch.paid() / 100 == 83 && batch.first() == Result.FAILED,
                    "64 of the legendary plus 19 of the sword: " + batch);
            helper.assertTrue(player.getInventory().countItem(ModItems.DURANDARTE.get()) == 0, "the legendary does NOT come back");
            helper.assertTrue(gold(player) == 83, "83 gold, got " + gold(player));
            helper.assertTrue(pressure(player, new TradeKey(TradeCategory.SWORD, ItemRating.LEGENDARY), 0) == 1 && pressure(player, SWORD_EPIC, 0) == 1, "both counted");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPartialPaymentAloneStillNotifiesWhatWasPaid(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            List<SystemNotification> sent = recordNotifications(player);
            ItemStack legendary = rated(ModItems.DURANDARTE.get(), ItemRating.LEGENDARY);
            legendary.set(ModDataComponents.ENHANCEMENT, 5);
            player.getInventory().setItem(5, legendary);
            int[] calls = {0};
            TradeService.Batch batch = TradeService.sellAll(player, entriesFor(player, 5), 0, (p, amount) -> {
                if (calls[0]++ == 1) {
                    throw new IllegalStateException("the second pile broke");
                }
                pay(p, amount);
            });
            helper.assertTrue(batch.sold() == 1 && batch.paid() / 100 == 64 && batch.failed() == 1 && batch.skipped() == 0, "partial alone: " + batch);
            helper.assertTrue(sent.size() == 1 && firstLineKey(sent.get(0)).equals("notification.mitofus.trade.sold"), "one sold notification, got " + sent);
            helper.assertTrue(TradeService.message(batch).map(c -> ((net.minecraft.network.chat.contents.TranslatableContents) c.getContents()).getKey())
                    .orElse("").equals("message.mitofus.trade_failed"), "trade_failed message");
            helper.assertTrue(player.getInventory().countItem(ModItems.DURANDARTE.get()) == 0, "the piece does not come back");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aTotalFailureFirstDoesNotStopTheNext(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(0, epic(Items.IRON_SWORD));
            player.getInventory().setItem(1, epic(Items.IRON_HELMET));
            List<TradeSellPayload.Entry> entries = entriesFor(player, 0, 1);
            long helmetPrice = entries.get(1).expectedPrice();
            int[] calls = {0};
            TradeService.Batch batch = TradeService.sellAll(player, entries, 0, (p, amount) -> {
                if (calls[0]++ == 0) {
                    throw new IllegalStateException("first call broke");
                }
                pay(p, amount);
            });
            helper.assertTrue(batch.sold() == 1 && batch.failed() == 1 && batch.skipped() == 0 && batch.paid() == helmetPrice
                    && batch.first() == Result.FAILED, "total failure then a sale: " + batch);
            helper.assertTrue(player.getInventory().getItem(0).is(Items.IRON_SWORD), "the first piece went back to its slot");
            helper.assertTrue(player.getInventory().countItem(Items.IRON_HELMET) == 0, "the second was sold");
            helper.assertTrue(pressure(player, SWORD_EPIC, 0) == 0, "no pressure for the failed sale");
        });
        helper.succeed();
    }


    @GameTest(template = TestSupport.EMPTY)
    public static void aBatchOfExactly36Sells(GameTestHelper helper) {
        run(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            Item[] items = {Items.IRON_SWORD, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_BOOTS};
            ItemRating[] ratings = {ItemRating.RARE, ItemRating.EPIC, ItemRating.UNIQUE};
            int[] slots = new int[36];
            for (int i = 0; i < 36; i++) {
                player.getInventory().setItem(i, rated(items[i % 4], ratings[(i / 4) % 3]));
                slots[i] = i;
            }
            TradeService.Batch batch = TradeService.sellAll(player, entriesFor(player, slots), 0, TradeTests::pay);
            helper.assertTrue(batch.sold() == 36 && batch.skipped() == 0 && batch.failed() == 0 && batch.first() == Result.SOLD, "36 sold: " + batch);
            helper.assertTrue(batch.paid() > 0, "paid something: " + batch);
            for (int i = 0; i < 36; i++) {
                int slot = i;
                helper.assertTrue(!player.getInventory().getItem(slot).is(items[slot % 4]), "no piece left in slot " + slot);
            }
        });
        helper.succeed();
    }
}
