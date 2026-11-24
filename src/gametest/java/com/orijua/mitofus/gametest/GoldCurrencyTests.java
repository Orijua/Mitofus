package com.orijua.mitofus.gametest;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.core.config.SmithShopBalance;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.character.CharacterSnapshot;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.OfferEntry;
import com.orijua.mitofus.feature.economy.TerminalRules;
import com.orijua.mitofus.feature.economy.TerminalService;
import com.orijua.mitofus.feature.economy.TerminalShop;
import com.orijua.mitofus.feature.economy.TradeGoods;
import com.orijua.mitofus.feature.economy.TradeService;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.npc.ApprenticeRules;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.npc.SmithBuyPayload;
import com.orijua.mitofus.feature.npc.SmithShop;
import com.orijua.mitofus.feature.npc.SmithShopMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * Mitofus's money is gold: the old id of the money still loads, Smith's old shops are restocked in gold, a price above one stack
 * (or paid in many stacks) works, and the prices of the terminal, the Trade tab and the offer follow the gold scale.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class GoldCurrencyTests {
    private static final TerminalBalance D = TerminalBalance.DEFAULT;
    private static final TradeBalance TRADE = TradeBalance.DEFAULT;

    private GoldCurrencyTests() {
    }

    private static Item gold() {
        return ModItems.GOLD_COIN.get();
    }

    private static int carried(ServerPlayer player) {
        return (int) (com.orijua.mitofus.feature.economy.Wallet.balance(player) / 100);
    }

    private static int indexFor(BlacksmithNpc smith, Item result) {
        MerchantOffers offers = smith.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).getResult().is(result)) {
                return i;
            }
        }
        throw new IllegalStateException("no offer gives " + result);
    }

    private static SmithShop.Result buy(ServerPlayer player, BlacksmithNpc smith, int index) {
        MerchantOffer offer = smith.getOffers().get(index);
        return SmithShop.buy(player, smith, index, offer.getResult().copy(), offer.getItemCostA().count(),
                offer.getItemCostB().map(ItemCost::count).orElse(0));
    }

    /** Adds a deal for gold and returns its index. */
    private static int addDeal(BlacksmithNpc smith, int cost, Item result, int maxUses) {
        smith.getOffers().add(new MerchantOffer(new ItemCost(gold(), cost), new ItemStack(result), maxUses, 0, 0.05f));
        return smith.getOffers().size() - 1;
    }

    private static void giveSplit(ServerPlayer player, int... counts) {
        player.setData(com.orijua.mitofus.core.registry.ModAttachments.PURSE.get(), com.orijua.mitofus.feature.economy.Purse.EMPTY); // the money is the purse plus the coins carried: start from nothing
        for (int slot = 0; slot < counts.length; slot++) {
            player.getInventory().setItem(slot, new ItemStack(gold(), counts[slot]));
        }
    }

    private static IPayloadContext contextOf(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> method.getName().equals("player") ? player : null);
    }

    private static void invokeSmithBuy(SmithBuyPayload payload, ServerPlayer player) {
        try {
            Method handler = Class.forName("com.orijua.mitofus.core.network.ServerPayloadHandler")
                    .getDeclaredMethod("smithBuy", SmithBuyPayload.class, IPayloadContext.class);
            handler.setAccessible(true);
            handler.invoke(null, payload, contextOf(player));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 4 and 5. Payment in many stacks, and prices above a stack ----

    @GameTest(template = TestSupport.EMPTY)
    public static void aPriceIsPaidWithGoldFromManyStacksAndTheOffhand(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        int deal = addDeal(smith, 150, ModItems.ENHANCEMENT_STONE.get(), 5);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(gold(), 64));
        player.getInventory().setItem(5, new ItemStack(gold(), 64));
        player.getInventory().setItem(30, new ItemStack(gold(), 20));
        player.getInventory().setItem(40, new ItemStack(gold(), 2)); // the offhand
        helper.assertTrue(carried(player) == 150, "150 gold in four places");
        helper.assertTrue(buy(player, smith, deal) == SmithShop.Result.OK, "150 gold in stacks of 64, 64, 20 and 2 pay the price");
        helper.assertTrue(carried(player) == 0, "nothing is left: " + carried(player));
        helper.assertTrue(player.getInventory().countItem(ModItems.ENHANCEMENT_STONE.get()) == 1, "the stone arrived");
        helper.assertTrue(smith.getOffers().get(deal).getUses() == 1, "and the deal counted one use");

        player.getInventory().clearContent();
        giveSplit(player, 64, 64, 21);
        helper.assertTrue(carried(player) == 149, "149 gold");
        helper.assertTrue(buy(player, smith, deal) == SmithShop.Result.NOT_ENOUGH, "one gold short");
        helper.assertTrue(carried(player) == 149 && player.getInventory().countItem(ModItems.ENHANCEMENT_STONE.get()) == 0
                && smith.getOffers().get(deal).getUses() == 1, "and nothing moved");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPriceAboveNinetyNineIsPaidAndSurvivesSavingTheSmith(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        int deal = addDeal(smith, 1250, ModItems.RESPEC_SCROLL.get(), 2);
        helper.assertTrue(smith.getOffers().get(deal).getItemCostA().count() == 1250, "the cost is 1250, not capped to a stack");

        CompoundTag tag = new CompoundTag();
        smith.addAdditionalSaveData(tag);
        BlacksmithNpc copy = helper.spawn(ModEntities.BLACKSMITH.get(), 2, 1, 1);
        copy.readAdditionalSaveData(tag);
        boolean found = copy.getOffers().stream().anyMatch(offer -> offer.getResult().is(ModItems.RESPEC_SCROLL.get())
                && offer.getItemCostA().count() == 1250);
        helper.assertTrue(found, "the 1250 offer survives saving and loading the Smith");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        MerchantOffers.STREAM_CODEC.encode(buf, smith.getOffers());
        MerchantOffers decoded = MerchantOffers.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.get(deal).getItemCostA().count() == 1250, "and the packet that carries it to the window");

        player.getInventory().clearContent();
        int[] stacks = new int[20];
        java.util.Arrays.fill(stacks, 64);
        stacks[19] = 34; // 19 x 64 + 34 = 1250
        giveSplit(player, stacks);
        helper.assertTrue(carried(player) == 1250, "1250 gold in 20 stacks");
        helper.assertTrue(buy(player, smith, deal) == SmithShop.Result.OK, "paid");
        helper.assertTrue(carried(player) == 0 && player.getInventory().countItem(ModItems.RESPEC_SCROLL.get()) == 1, "all taken, the scroll given");

        giveSplit(player, 64, 64);
        helper.assertTrue(buy(player, smith, deal) == SmithShop.Result.NOT_ENOUGH && carried(player) == 128, "128 do not pay 1250");
        helper.succeed();
    }

    // ---- 6. The Eye: gold and diamonds ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeNeedsTenGoldAndTwoDiamondsFromSeveralStacks(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        int eye = indexFor(smith, ModItems.APPRAISER_EYE.get());
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(gold(), 6));
        player.getInventory().setItem(7, new ItemStack(gold(), 4));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 1));
        player.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 1));
        helper.assertTrue(buy(player, smith, eye) == SmithShop.Result.OK, "10 gold and 2 diamonds in two stacks each pay the Eye");
        helper.assertTrue(carried(player) == 0 && player.getInventory().countItem(Items.DIAMOND) == 0, "gold and diamonds are taken");
        helper.assertTrue(player.getInventory().countItem(ModItems.APPRAISER_EYE.get()) == 1, "and the Eye given");

        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(gold(), 10));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 1));
        helper.assertTrue(buy(player, smith, eye) == SmithShop.Result.NOT_ENOUGH, "one diamond is not enough");
        helper.assertTrue(carried(player) == 10 && player.getInventory().countItem(Items.DIAMOND) == 1, "the gold is not charged");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeIsRefusedWhenTheSeenSecondCostDiffers(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        int eye = indexFor(smith, ModItems.APPRAISER_EYE.get());
        MerchantOffer offer = smith.getOffers().get(eye);
        ItemStack seen = offer.getResult().copy();
        int costA = offer.getItemCostA().count();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(gold(), 10));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 2));
        for (int wrong : new int[]{0, 1}) {
            helper.assertTrue(SmithShop.buy(player, smith, eye, seen, costA, wrong) == SmithShop.Result.STALE,
                    "seen second cost " + wrong + " is stale");
            helper.assertTrue(carried(player) == 10 && player.getInventory().countItem(Items.DIAMOND) == 2
                    && player.getInventory().countItem(ModItems.APPRAISER_EYE.get()) == 0, "gold and diamonds are intact");
        }
        helper.assertTrue(SmithShop.buy(player, smith, eye, seen, costA, 2) == SmithShop.Result.OK, "seen second cost 2 buys");
        helper.assertTrue(carried(player) == 0 && player.getInventory().countItem(Items.DIAMOND) == 0, "and charges both");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void goldOnlyInArmorSlotsDoesNotPayAndStaysThere(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        int scroll = indexFor(smith, ModItems.RESPEC_SCROLL.get());
        player.getInventory().clearContent();
        player.getInventory().setItem(36, new ItemStack(gold(), 10)); // armor slot
        helper.assertTrue(buy(player, smith, scroll) == SmithShop.Result.NOT_ENOUGH, "gold in an armor slot does not pay");
        helper.assertTrue(player.getInventory().getItem(36).getCount() == 10 && smith.getOffers().get(scroll).getUses() == 0, "and stays there");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aFullInventoryStillBuysAndTheResultFallsOnTheGround(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        int scroll = indexFor(smith, ModItems.RESPEC_SCROLL.get());
        player.getInventory().clearContent();
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        player.setData(ModAttachments.PURSE.get(), new com.orijua.mitofus.feature.economy.Purse(500L)); // the gold is a balance: paying 4 leaves one and frees no slot
        player.getInventory().setItem(40, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(buy(player, smith, scroll) == SmithShop.Result.OK, "bought with no free slot");
        helper.assertTrue(carried(player) == 1, "the gold left is one: " + carried(player));
        java.util.List<net.minecraft.world.entity.item.ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(4.0),
                entity -> entity.getItem().is(ModItems.RESPEC_SCROLL.get()));
        helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 1, "the scroll fell next to the player: " + dropped);
        dropped.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void severalUnitsAreAnnouncedWithTheTimesSignAndOneWithoutIt(GameTestHelper helper) {
        net.minecraft.network.chat.contents.TranslatableContents many = (net.minecraft.network.chat.contents.TranslatableContents)
                SmithShop.message(SmithShop.Result.OK, new ItemStack(gold(), 4)).orElseThrow().getContents();
        helper.assertTrue(many.getKey().equals("message.mitofus.smith_shop.bought_many") && many.getArgs().length == 2
                && many.getArgs()[0].equals("4"), "four units use bought_many with 4 and the name: " + many.getKey() + " " + java.util.Arrays.toString(many.getArgs()));
        net.minecraft.network.chat.contents.TranslatableContents one = (net.minecraft.network.chat.contents.TranslatableContents)
                SmithShop.message(SmithShop.Result.OK, new ItemStack(ModItems.ENHANCEMENT_STONE.get())).orElseThrow().getContents();
        helper.assertTrue(one.getKey().equals("message.mitofus.smith_shop.bought") && one.getArgs().length == 1, "one unit uses bought");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDealWithOneUseIsChargedOnceWhenBoughtTwice(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        int deal = addDeal(smith, 3, ModItems.ENHANCEMENT_STONE.get(), 1);
        player.getInventory().clearContent();
        giveSplit(player, 6);
        helper.assertTrue(buy(player, smith, deal) == SmithShop.Result.OK, "the first clicks buys");
        helper.assertTrue(buy(player, smith, deal) == SmithShop.Result.SOLD_OUT, "the second finds it sold out");
        helper.assertTrue(carried(player) == 3 && player.getInventory().countItem(ModItems.ENHANCEMENT_STONE.get()) == 1, "only one was charged");

        int other = addDeal(smith, 3, ModItems.RESPEC_SCROLL.get(), 5);
        player.getInventory().clearContent();
        giveSplit(player, 3);
        helper.assertTrue(buy(player, smith, other) == SmithShop.Result.OK, "the first buys");
        helper.assertTrue(buy(player, smith, other) == SmithShop.Result.NOT_ENOUGH, "with no money left the second is refused");
        helper.assertTrue(player.getInventory().countItem(ModItems.RESPEC_SCROLL.get()) == 1 && smith.getOffers().get(other).getUses() == 1, "once");
        helper.succeed();
    }

    // ---- 8. Refusals ----

    @GameTest(template = TestSupport.EMPTY)
    public static void aDealIsRefusedWhenWhatWasSeenChangedOrTheSituationIsWrong(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ServerPlayer other = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        int scroll = indexFor(smith, ModItems.RESPEC_SCROLL.get());
        ItemStack seenScroll = smith.getOffers().get(scroll).getResult().copy();
        player.getInventory().clearContent();
        giveSplit(player, 10);

        helper.assertTrue(SmithShop.buy(player, smith, scroll, seenScroll, 12, 0) == SmithShop.Result.STALE, "the old price of 12 is stale");
        helper.assertTrue(SmithShop.buy(player, smith, scroll, seenScroll, 4, 2) == SmithShop.Result.STALE,
                "a second cost the scroll does not have is stale");
        helper.assertTrue(SmithShop.buy(player, smith, scroll, new ItemStack(ModItems.ENHANCEMENT_STONE.get()), 4, 0) == SmithShop.Result.STALE,
                "another result is stale");
        helper.assertTrue(SmithShop.buy(player, smith, 99, seenScroll, 4, 0) == SmithShop.Result.STALE, "an index out of range is stale");
        helper.assertTrue(SmithShop.buy(player, smith, -1, seenScroll, 4, 0) == SmithShop.Result.STALE, "and a negative one");
        helper.assertTrue(SmithShop.buy(other, smith, scroll, seenScroll, 4, 0) == SmithShop.Result.NOT_TRADING, "Smith trades with another player");
        VrHelmetTests.outsideMitofus(helper, () ->
                helper.assertTrue(SmithShop.buy(player, smith, scroll, seenScroll, 4, 0) == SmithShop.Result.OUTSIDE_MITOFUS, "not in the real world"));
        helper.assertTrue(carried(player) == 10 && player.getInventory().countItem(ModItems.RESPEC_SCROLL.get()) == 0
                && smith.getOffers().get(scroll).getUses() == 0, "nothing moved in any refusal");

        // The handler: only the open menu of the sender counts.
        // A fake player cannot open a window, so the menu is mounted by hand, as the terminal tests do.
        player.containerMenu = new SmithShopMenu(7, player.getInventory(), smith);
        invokeSmithBuy(new SmithBuyPayload(8, scroll, seenScroll, 4, 0), player);
        helper.assertTrue(carried(player) == 10, "another container id does nothing");
        invokeSmithBuy(new SmithBuyPayload(7, scroll, seenScroll, 4, 0), player);
        helper.assertTrue(carried(player) == 6 && player.getInventory().countItem(ModItems.RESPEC_SCROLL.get()) == 1, "the open menu buys");
        invokeSmithBuy(new SmithBuyPayload(7, scroll, seenScroll, 4, 0), player);
        helper.assertTrue(carried(player) == 2 && player.getInventory().countItem(ModItems.RESPEC_SCROLL.get()) == 2, "and again, at the same price");
        smith.setTradingPlayer(null);
        invokeSmithBuy(new SmithBuyPayload(7, scroll, seenScroll, 4, 0), player);
        helper.assertTrue(carried(player) == 2, "when Smith no longer trades with the player the window is not valid");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    // ---- 9. Closing ----

    @GameTest(template = TestSupport.EMPTY)
    public static void closingTheShopFreesSmith(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        smith.startTrading(player);
        helper.assertTrue(smith.getTradingPlayer() == player, "trading");
        // A fake player cannot open a window: the menu is mounted by hand.
        player.containerMenu = new SmithShopMenu(7, player.getInventory(), smith);
        player.closeContainer();
        helper.assertTrue(smith.getTradingPlayer() == null && !smith.isTrading(), "closing the window frees Smith");
        smith.startTrading(player);
        helper.assertTrue(smith.getTradingPlayer() == player, "and he can trade again");
        smith.startTrading(other(helper));
        helper.assertTrue(smith.getTradingPlayer() == player, "but not with two at once");
        player.closeContainer();
        helper.succeed();
    }

    private static ServerPlayer other(GameTestHelper helper) {
        return TestSupport.fakePlayerIn(helper.getLevel());
    }

    // ---- 10. Selling ----

    @GameTest(template = TestSupport.EMPTY)
    public static void anEpicDiamondPieceSellsForExactlyFiveGold(GameTestHelper helper) {
        TradeBalance d = TradeBalance.DEFAULT;
        TradeBalance noBonuses = new TradeBalance(d.basePrice(), d.materialMultiplier(), d.otherMaterial(), d.enhancementBonus(), 0.0, 0.0,
                d.impossibleMargin(), d.impossibleFactor(), d.sellShare(), d.saturationStep(), d.saturationFloor(), d.recoveryPerDay(),
                d.pressureCap(), d.maxSellGold(), d.minDurabilityFactor(), d.itemMultiplier());
        TradeBalance.withOverride(noBonuses, () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().clearContent();
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.set(ModDataComponents.ITEM_RATING, ItemRating.EPIC);
            player.getInventory().setItem(3, sword);
            helper.assertTrue(TradeGoods.reference(sword, noBonuses) == 14, "a new epic diamond piece without bonuses has reference 14: "
                    + TradeGoods.reference(sword, noBonuses));
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0);
            helper.assertTrue(outcome.result() == TradeService.Result.SOLD && outcome.paid() == 560, "14 x 0,4 = 5,6 pays 5,60 gold: " + outcome);
            helper.assertTrue(carried(player) == 5, "five gold coins in the inventory: " + carried(player));
        });
        helper.succeed();
    }

    // ---- 11. The price of the daily offer ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theOfferPricesAreInIronAndNeverBelowFourGold(GameTestHelper helper) {
        int checked = 0;
        for (long day : new long[]{0L, 1L, 7L, 100L, 1234L}) {
            for (OfferEntry entry : TerminalShop.offer(helper.getLevel(), day, D, TRADE)) {
                double v = TradeGoods.trueReferenceValue(entry.stack(), TRADE);
                long expected = Math.max(1L, Money.floor(Math.max(1.0, v) * D.buyMultiplier() * Money.IRON_PER_GOLD));
                helper.assertTrue(entry.price() == expected && entry.price() >= 4L * Money.IRON_PER_GOLD,
                        "day " + day + " position " + entry.position() + ": price " + entry.price() + " vs " + expected + " (value " + v + ")");
                checked++;
            }
        }
        helper.assertTrue(checked > 0, "some pieces were checked");
        helper.succeed();
    }
}
