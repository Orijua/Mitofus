package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.EquipLevelBalance;
import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.character.CharacterSnapshot;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.economy.OfferEntry;
import com.orijua.mitofus.feature.economy.OfferRules;
import com.orijua.mitofus.feature.economy.TerminalShop;
import com.orijua.mitofus.feature.economy.TradeGoods;
import com.orijua.mitofus.feature.economy.TradeRules;
import com.orijua.mitofus.feature.economy.TradeSaturation;
import com.orijua.mitofus.feature.economy.TradeSellPayload;
import com.orijua.mitofus.feature.economy.TradeService;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.economy.TradeService.Result;
import com.orijua.mitofus.feature.equipment.EquipLevelRules;
import com.orijua.mitofus.feature.equipment.EquipLevels;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What a piece is worth beyond its rating: the durability left and the multiplier of a single object. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class TradeValueTests {
    private static final TradeBalance B = TradeBalance.DEFAULT;

    private TradeValueTests() {
    }

    private static ItemStack rated(Item item, ItemRating rating) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.ITEM_RATING, rating);
        return stack;
    }

    private static ItemStack damaged(Item item, ItemRating rating, int damage) {
        ItemStack stack = rated(item, rating);
        stack.setDamageValue(damage);
        return stack;
    }

    /** What the player carries in coins, as iron. */
    private static int gold(ServerPlayer player) {
        return (int) Wallet.balance(player);
    }

    private static void pay(ServerPlayer player, long amount) {
        TradeService.give(player, amount);
    }

    // ---- The durability left

    @GameTest(template = TestSupport.EMPTY)
    public static void theDurabilityLeftLowersTheReference(GameTestHelper helper) {
        TradeBalance.withOverride(B, () -> {
            long fresh = TradeGoods.reference(damaged(Items.IRON_SWORD, ItemRating.RARE, 0), B);
            long half = TradeGoods.reference(damaged(Items.IRON_SWORD, ItemRating.RARE, 125), B);
            long nearlyBroken = TradeGoods.reference(damaged(Items.IRON_SWORD, ItemRating.RARE, 249), B);
            helper.assertTrue(fresh == 3, "a new rare iron sword (level 7) is worth 3 (3 x 1,12 = 3,36), got " + fresh);
            helper.assertTrue(half == 2, "at half durability it is worth 2 (3,36 x 0,625 = 2,1), got " + half);
            helper.assertTrue(nearlyBroken == 1, "nearly broken it is worth 1 (3,36 x 0,253 = 0,85, never below 1), got " + nearlyBroken);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSaleWithTheSeenPricePaysWhatTheBatchRuleSays(GameTestHelper helper) {
        TradeBalance.withOverride(B, () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack worn = damaged(Items.IRON_SWORD, ItemRating.EPIC, 125);
            player.getInventory().setItem(3, worn.copy());
            long seen = TradeGoods.salePrices(List.of(worn), TradeService.saturation(player), 0, B)[0];
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0, new TradeService.Expected(worn, seen));
            helper.assertTrue(outcome.result() == Result.SOLD && outcome.paid() == seen, "paid the seen price " + seen + ": " + outcome);
            helper.assertTrue(gold(player) == seen, "the gold is the seen price, got " + gold(player));
            // Epic iron sword (level 10), 125 of 250 damage: reference 7 x 1,18 x (0,25 + 0,75 x 0,5) = 5,16 -> 5, sold at 40 % = 2.
            helper.assertTrue(seen / 100 == 2, "a half-worn epic sword pays 2 gold and a fraction (5 x 0,4), got " + seen);
            helper.assertTrue(seen < 300, "and less than a new one (8 x 0,4 = 3,2 -> 3)");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPieceSeenNewButWornOnTheServerIsStale(GameTestHelper helper) {
        TradeBalance.withOverride(B, () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack seen = rated(Items.IRON_SWORD, ItemRating.EPIC);
            ItemStack worn = damaged(Items.IRON_SWORD, ItemRating.EPIC, 125);
            player.getInventory().setItem(3, worn.copy());
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0, new TradeService.Expected(seen, 3));
            helper.assertTrue(outcome.result() == Result.STALE, "expected STALE, got " + outcome);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), worn), "the worn sword stays");
            helper.assertTrue(gold(player) == 0, "nothing paid");
            helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY), "the saturation is untouched");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBatchWithAWornPieceTotalsWhatSellAllPays(GameTestHelper helper) {
        TradeBalance.withOverride(B, () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(0, damaged(Items.IRON_SWORD, ItemRating.EPIC, 200));
            player.getInventory().setItem(1, rated(Items.IRON_SWORD, ItemRating.EPIC));
            List<ItemStack> stacks = List.of(player.getInventory().getItem(0).copy(), player.getInventory().getItem(1).copy());
            long[] prices = TradeGoods.salePrices(stacks, TradeService.saturation(player), 0, B);
            // The worn one (200 of 250 damage): reference 8,26 x (0,25 + 0,75 x 0,2) = 3,30 -> 3, sold at 40 % = 1 (1,2). It leaves pressure 1,
            // so the new one of the same key pays 8 x 0,4 x 0,95 = 3 (3,04).
            helper.assertTrue(TradeGoods.reference(stacks.get(0), B) == 3, "the worn sword is worth 3: " + TradeGoods.reference(stacks.get(0), B));
            helper.assertTrue(prices.length == 2 && prices[0] == 120 && prices[1] == 304, "1,20 for the worn one, 3,04 for the new: " + java.util.Arrays.toString(prices));
            List<TradeSellPayload.Entry> entries = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                entries.add(new TradeSellPayload.Entry(i, stacks.get(i), prices[i]));
            }
            long clientTotal = TradeRules.total(prices);
            helper.assertTrue(clientTotal == 424, "the total is 4,24: " + clientTotal);
            TradeService.Batch batch = TradeService.sellAll(player, entries, 0, TradeValueTests::pay);
            helper.assertTrue(batch.sold() == 2 && batch.paid() == clientTotal, "the client total " + clientTotal + " is what is paid: " + batch);
            helper.assertTrue(gold(player) == clientTotal, "gold " + gold(player));
        });
        helper.succeed();
    }

    // ---- Real prices (the default balance): repeated sales and a big payment

    @GameTest(template = TestSupport.EMPTY)
    public static void sellingTheSamePieceOverAndOverNeverPaysMoreAndFollowsThePressure(GameTestHelper helper) {
        TradeBalance.withOverride(B, () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().clearContent();
            long reference = TradeGoods.reference(rated(Items.IRON_SWORD, ItemRating.EPIC), B);
            long previous = Long.MAX_VALUE;
            long total = 0L;
            for (int sale = 0; sale < 6; sale++) {
                player.getInventory().setItem(0, rated(Items.IRON_SWORD, ItemRating.EPIC));
                TradeService.Outcome outcome = TradeService.sell(player, 0, 0, (p, amount) -> { });
                long expected = TradeRules.sellPriceIron(reference, sale, B);
                helper.assertTrue(outcome.result() == Result.SOLD && outcome.paid() == expected,
                        "sale " + sale + " pays what the rule says (" + expected + "): " + outcome);
                helper.assertTrue(outcome.paid() >= 1 && outcome.paid() <= previous, "sale " + sale + " pays at least 1 and no more than the one before: " + outcome.paid());
                previous = outcome.paid();
                total += outcome.paid();
            }
            helper.assertTrue(total >= 6, "six sales paid something: " + total);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPaymentAboveOneStackIsDeliveredExactlyInStacksOfAtMostSixtyFour(GameTestHelper helper) {
        TradeBalance.withOverride(B, () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().clearContent();
            ItemStack piece = rated(ModItems.GUADALETE_SWORD.get(), ItemRating.LEGENDARY);
            long expected = TradeRules.sellPriceIron(TradeGoods.reference(piece, B), 0, B);
            helper.assertTrue(expected > 64 * 100, "the precondition: the price is above one stack of gold: " + expected);
            helper.assertTrue(TradeRules.payableIron(expected, B), "and it can be paid: " + expected);
            player.getInventory().setItem(0, piece);
            TradeService.Outcome outcome = TradeService.sell(player, 0, 0);
            helper.assertTrue(outcome.result() == Result.SOLD && outcome.paid() == expected, "sold for " + expected + ": " + outcome);
            helper.assertTrue(gold(player) == expected, "the inventory holds exactly " + expected + " iron: " + gold(player));
            int stacks = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(ModItems.GOLD_COIN.get())) {
                    stacks++;
                    helper.assertTrue(stack.getCount() <= 64, "no stack above 64: " + stack.getCount());
                }
            }
            helper.assertTrue(stacks == 0, "no coin items: the money is a balance, got " + stacks);
        });
        helper.succeed();
    }

    // ---- The multiplier of a single object

    @GameTest(template = TestSupport.EMPTY)
    public static void theUniqueObjectsHaveTheirOwnMultiplier(GameTestHelper helper) {
        TradeBalance.withOverride(B, () -> {
            // Guadalete has no exception any more (the impossible-level rule replaced it): netherite x3, level 25 + 20 = 45: 150 x 3 x 1,88.
            long guadalete = TradeGoods.reference(rated(ModItems.GUADALETE_SWORD.get(), ItemRating.LEGENDARY), B);
            helper.assertTrue(guadalete == 846, "a legendary Guadalete with no recipe is worth 846 (netherite x3, level 45), got " + guadalete);
            // Wolfram: level 8 + 2 (rare) = 10, x2: 3 x 2 x 1,18 = 7,08.
            long wolfram = TradeGoods.reference(rated(ModItems.WOLFRAM_SWORD.get(), ItemRating.RARE), B);
            helper.assertTrue(wolfram == 7, "a rare Wolfram sword is worth 7 (x2, level 10), got " + wolfram);
            long wolframHammer = TradeGoods.reference(rated(ModItems.WOLFRAM_SMITHING_HAMMER.get(), ItemRating.RARE), B);
            helper.assertTrue(wolframHammer == 7, "a rare Wolfram hammer is worth 7 (x2, level 10), got " + wolframHammer);
            // Mithril hammer: level 20 + 2 = 22, x3: 3 x 3 x 1,42 = 12,78.
            long mithrilHammer = TradeGoods.reference(rated(ModItems.MITHRIL_SMITHING_HAMMER.get(), ItemRating.RARE), B);
            helper.assertTrue(mithrilHammer == 12, "a rare mithril hammer is worth 12 (x3, level 22), got " + mithrilHammer);
        });
        helper.succeed();
    }

    /** The durability left multiplies on top of the multiplier of a single object, and a piece without exception keeps its material. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theDurabilityMultipliesOnTopOfTheExceptionAndAPieceWithoutOneKeepsItsMaterial(GameTestHelper helper) {
        TradeBalance.withOverride(B, () -> {
            ItemStack brokenGuadalete = rated(ModItems.GUADALETE_SWORD.get(), ItemRating.LEGENDARY);
            brokenGuadalete.setDamageValue(brokenGuadalete.getMaxDamage());
            helper.assertTrue(brokenGuadalete.getDamageValue() > 0, "the Guadalete has durability to lose");
            long broken = TradeGoods.reference(brokenGuadalete, B);
            helper.assertTrue(broken == 211, "a broken legendary Guadalete: 150 x 3 x 1,88 x 0,25 = 211,5, got " + broken);
            ItemStack worn = rated(ModItems.GUADALETE_SWORD.get(), ItemRating.LEGENDARY);
            worn.setDamageValue(worn.getMaxDamage() / 2);
            long wornPrice = TradeGoods.reference(worn, B);
            helper.assertTrue(wornPrice > broken && wornPrice < 846, "a half-worn one is between the broken (211) and the new (846): " + wornPrice);

            ItemStack halfWolfram = damaged(ModItems.WOLFRAM_SWORD.get(), ItemRating.RARE, 125);
            helper.assertTrue(halfWolfram.getMaxDamage() == 250, "the Wolfram sword is iron-sized: " + halfWolfram.getMaxDamage());
            long wolfram = TradeGoods.reference(halfWolfram, B);
            helper.assertTrue(wolfram == 4, "a half-worn rare Wolfram sword: 3 x 2 x 1,18 x 0,625 = 4,425, got " + wolfram);
            ItemStack brokenWolfram = rated(ModItems.WOLFRAM_SWORD.get(), ItemRating.RARE);
            brokenWolfram.setDamageValue(brokenWolfram.getMaxDamage());
            helper.assertTrue(TradeGoods.reference(brokenWolfram, B) == 1, "a broken rare Wolfram sword: 3 x 2 x 1,18 x 0,25 = 1,77, got " + TradeGoods.reference(brokenWolfram, B));

            // Cid's legendary sword has no exception: it is still weighed as diamond (x2, level 15 + 20) and the damage multiplies on it.
            ItemStack grid = rated(ModItems.DURANDARTE.get(), ItemRating.LEGENDARY);
            helper.assertFalse(B.itemMultiplier().containsKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(grid.getItem())),
                    "no exception for it");
            helper.assertTrue(TradeGoods.material(grid, B) == 2.0, "it keeps the diamond multiplier: " + TradeGoods.material(grid, B));
            helper.assertTrue(TradeGoods.reference(grid, B) == 504, "new: 150 x 2 x 1,68 (level 35) = 504, got " + TradeGoods.reference(grid, B));
            grid.setDamageValue(grid.getMaxDamage());
            helper.assertTrue(TradeGoods.reference(grid, B) == 126, "broken: 504 x 0,25 = 126, got " + TradeGoods.reference(grid, B));
        });
        helper.succeed();
    }

    private static final Map<ResourceLocation, Double> DOUBLE_IRON_SWORD = Map.of(ResourceLocation.withDefaultNamespace("iron_sword"), 2.0);

    /** The real world's player with a stored Mitofus character (one piece of dirt in its inventory) and a lot of iron. */
    private static ServerPlayer playerWithStoredCharacter(GameTestHelper helper, long won) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIRT));
        player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.REAL, CharacterSnapshot.capture(player)));
        player.getInventory().clearContent();
        TestSupport.setIron(player, won);
        return player;
    }

    /** The position of the first piece of the offer that is an iron sword (the offer is built of nothing else). */
    private static int ironSwordPosition(GameTestHelper helper, List<OfferEntry> offer) {
        helper.assertFalse(offer.isEmpty(), "the offer is not empty");
        for (int i = 0; i < offer.size(); i++) {
            if (offer.get(i).stack().is(Items.IRON_SWORD)) {
                return i;
            }
        }
        helper.fail("no iron sword in the offer");
        return -1;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anExceptionRegeneratesTheOfferAtTheNewPriceAndTheOldPriceIsStale(GameTestHelper helper) {
        TerminalBalance ironSwords = TerminalBalance.DEFAULT.withOffer(6, ItemRating.RARE, List.of(ResourceLocation.withDefaultNamespace("iron_sword")));
        TradeBalance exception = TradeBalances.withItemMultipliers(DOUBLE_IRON_SWORD);
        long startingWon = 10_000_000L;
        ServerPlayer player = playerWithStoredCharacter(helper, startingWon);
        CharacterVault vaultBefore = player.getData(ModAttachments.CHARACTER_VAULT.get());
        VrHelmetTests.outsideMitofus(helper, () -> {
            List<OfferEntry> offerBefore = TerminalShop.offer(helper.getLevel(), 7L, ironSwords, B);
            List<OfferEntry> offerAfter = TerminalShop.offer(helper.getLevel(), 7L, ironSwords, exception);
            int position = ironSwordPosition(helper, offerBefore);
            OfferEntry before = offerBefore.get(position);
            OfferEntry after = offerAfter.get(position);
            helper.assertTrue(ItemStack.matches(before.stack(), after.stack()), "the same piece");
            helper.assertTrue(after.price() > before.price(), "the exception raises the price: " + before.price() + " -> " + after.price());
            // The offered piece is unappraised; the price comes from its true reference.
            helper.assertTrue(after.price() == OfferRules.price(TradeGoods.trueReferenceValue(after.stack(), exception),
                    ironSwords.buyMultiplier(), com.orijua.mitofus.feature.economy.Money.IRON_PER_GOLD), "the price comes from the one reference");

            helper.assertTrue(TerminalShop.buyOffer(player, position, before.stack(), before.price(), 7L, ironSwords, exception).result()
                    == TerminalShop.Result.STALE, "the price seen before the change is stale");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == startingWon, "the stale attempt charged nothing: " + com.orijua.mitofus.feature.economy.Wallet.purse(player).iron());
            helper.assertTrue(player.getData(ModAttachments.CHARACTER_VAULT.get()).equals(vaultBefore), "and the stored character is intact");

            TerminalShop.Outcome bought = TerminalShop.buyOffer(player, position, after.stack(), after.price(), 7L, ironSwords, exception);
            helper.assertTrue(bought.result() == TerminalShop.Result.OK, "the new price buys: " + bought);
            helper.assertTrue(bought.price() == after.price() && com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == startingWon - after.price(),
                    "exactly the new price is charged: " + com.orijua.mitofus.feature.economy.Wallet.purse(player).iron());
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDefaultOfferDoesNotChangeWithTheExceptions(GameTestHelper helper) {
        TradeBalance plain = TradeBalances.withItemMultipliers(Map.of());
        for (long day : new long[]{0L, 3L, 77L}) {
            for (OfferEntry entry : TerminalShop.offer(helper.getLevel(), day, TerminalBalance.DEFAULT, B)) {
                ItemStack stack = entry.stack();
                helper.assertTrue(stack.getDamageValue() == 0, "an offered piece is new: " + stack);
                ItemRating rating = stack.get(ModDataComponents.ITEM_RATING);
                int derived = EquipLevels.derivedLevel(stack, EquipLevelBalance.DEFAULT);
                int stored = EquipLevels.storedLevel(stack);
                double reference = TradeRules.referenceValue(rating, TradeGoods.material(stack, plain), EquipLevelRules.effectiveLevel(stored, derived),
                        stack.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of()).size(),
                        stack.getOrDefault(ModDataComponents.ENHANCEMENT, 0), TradeRules.impossible(stored, derived, plain), 1.0, plain);
                helper.assertTrue(entry.price() == OfferRules.price(reference, TerminalBalance.DEFAULT.buyMultiplier(),
                        com.orijua.mitofus.feature.economy.Money.IRON_PER_GOLD), "price of " + stack + " on day " + day + ": " + entry.price());
            }
        }
        helper.succeed();
    }
}
