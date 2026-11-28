package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.character.CharacterSnapshot;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.economy.OfferBought;
import com.orijua.mitofus.feature.economy.OfferEntry;
import com.orijua.mitofus.feature.economy.OfferRules;
import com.orijua.mitofus.feature.economy.TerminalBuyOfferPayload;
import com.orijua.mitofus.feature.economy.TerminalMenu;
import com.orijua.mitofus.feature.economy.TerminalRules;
import com.orijua.mitofus.feature.economy.TerminalService;
import com.orijua.mitofus.feature.economy.TerminalShop;
import com.orijua.mitofus.feature.economy.TerminalState;
import com.orijua.mitofus.feature.economy.TradeGoods;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.equipment.EquipmentRules;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Buying at the terminal and the phone (economy, step 4): the daily offer of rated equipment,
 * both written into the stored Mitofus character. The services are called directly with the balance of the test.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class TerminalShopTests {
    private static final BlockPos LANDING = new BlockPos(1, 1, 1);
    private static final TerminalBalance D = TerminalBalance.DEFAULT;
    private static final TradeBalance TRADE = TradeBalance.DEFAULT;
    /** Pieces of the daily offer: perRank for each rating from normal up to the highest. */
    private static final int OFFER_SIZE = D.offerPerRank() * (D.offerMaxRating().ordinal() + 1);
    /** Position of the first piece of the second rating (rare): the offer has perRank pieces of each rating, Normal first. */
    private static final int FIRST_RARE = D.offerPerRank();

    private TerminalShopTests() {
    }

    private static Item gold() {
        return ModItems.GOLD_COIN.get();
    }

    private static void real(GameTestHelper helper, Runnable body) {
        TestSupport.withAllDefaults(() -> realWorld(helper, body));
    }

    private static void realWorld(GameTestHelper helper, Runnable body) {
        VrHelmetTests.outsideMitofus(helper, body);
    }

    private static void mitofus(GameTestHelper helper, Runnable body) {
        TestSupport.withAllDefaults(() -> mitofusWorld(helper, body));
    }

    private static void mitofusWorld(GameTestHelper helper, Runnable body) {
        VrHelmetTests.inMitofus(helper, helper.absolutePos(LANDING), body);
    }

    /** The real world's player with a stored Mitofus character holding what {@code fill} puts in the inventory. */
    private static void withStored(ServerPlayer player, Consumer<Inventory> fill) {
        player.getInventory().clearContent();
        fill.accept(player.getInventory());
        CompoundTag snapshot = CharacterSnapshot.capture(player);
        player.getInventory().clearContent();
        player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.REAL, snapshot));
    }

    private static void fillStone(Inventory inventory, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
    }

    private static CharacterVault vault(ServerPlayer player) {
        return player.getData(ModAttachments.CHARACTER_VAULT.get());
    }

    private static ListTag stored(ServerPlayer player) {
        return CharacterSnapshot.inventory(vault(player).stored().orElseThrow());
    }

    /** The entry of a stored slot, or null. */
    private static CompoundTag entryAt(ServerPlayer player, int slot) {
        ListTag list = stored(player);
        for (int i = 0; i < list.size(); i++) {
            if ((list.getCompound(i).getByte("Slot") & 255) == slot) {
                return list.getCompound(i);
            }
        }
        return null;
    }

    private static int countAt(ServerPlayer player, int slot) {
        CompoundTag entry = entryAt(player, slot);
        return entry == null ? 0 : entry.getInt("count");
    }

    private static List<OfferEntry> offer(GameTestHelper helper, long day) {
        return TerminalShop.offer(helper.getLevel(), day, D, TRADE);
    }

    private static OfferBought bought(ServerPlayer player) {
        return player.getData(ModAttachments.OFFER_BOUGHT.get());
    }

    /** A rejected attempt must leave the coins, the whole vault and the bought-today marks exactly as they were. */
    private static void assertUnchanged(GameTestHelper helper, ServerPlayer player, long won, CharacterVault vault, OfferBought bought,
                                        String what) {
        helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == won, what + ": the iron changed to " + com.orijua.mitofus.feature.economy.Wallet.purse(player).iron());
        helper.assertTrue(vault(player).equals(vault), what + ": the vault changed");
        helper.assertTrue(bought(player).equals(bought), what + ": the bought marks changed");
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void shoppingNeedsTheRealWorldAndAReadableCharacter(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> inv.setItem(0, new ItemStack(gold(), 5)));
        TestSupport.setIron(player, 100_000L);
        CharacterVault before = vault(player);
        OfferBought marks = bought(player);
        mitofus(helper, () -> {
            OfferEntry entry = offer(helper, 3L).get(0);
            helper.assertTrue(TerminalShop.buyOffer(player, 0, entry.stack(), entry.price(), 3L, D, TRADE).result() == TerminalShop.Result.IN_MITOFUS,
                    "a piece in Mitofus");
            assertUnchanged(helper, player, 100_000L, before, marks, "in Mitofus");
        });
        real(helper, () -> {
            CharacterVault carryingMitofus = CharacterVault.of(WorldSide.MITOFUS, before.stored().orElseThrow());
            player.setData(ModAttachments.CHARACTER_VAULT.get(), carryingMitofus);
            OfferEntry first = offer(helper, 3L).get(0);
            helper.assertTrue(TerminalShop.buyOffer(player, 0, first.stack(), first.price(), 3L, D, TRADE).result() == TerminalShop.Result.IN_MITOFUS,
                    "carrying Mitofus's things in the real world: no signal");
            CharacterVault nothing = new CharacterVault(Optional.of(WorldSide.REAL), Optional.empty());
            player.setData(ModAttachments.CHARACTER_VAULT.get(), nothing);
            OfferEntry entry = offer(helper, 3L).get(0);
            helper.assertTrue(TerminalShop.buyOffer(player, 0, entry.stack(), entry.price(), 3L, D, TRADE).result() == TerminalShop.Result.NO_CHARACTER,
                    "nothing stored, a piece");
            assertUnchanged(helper, player, 100_000L, nothing, marks, "nothing stored");
            helper.assertTrue(TerminalShop.message(TerminalShop.buyOffer(player, 0, entry.stack(), entry.price(), 3L, D, TRADE)).isPresent(),
                    "and the message of the purchase");

            CharacterVault unreadable = CharacterVault.of(WorldSide.REAL, new CompoundTag());
            player.setData(ModAttachments.CHARACTER_VAULT.get(), unreadable);
            helper.assertTrue(TerminalShop.buyOffer(player, 0, entry.stack(), entry.price(), 3L, D, TRADE).result() == TerminalShop.Result.NO_CHARACTER, "unreadable");
            helper.assertTrue(vault(player).equals(unreadable), "and left as it was");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 100_000L, "nothing was charged");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPurchasePacketOnlyActsOnTheOpenWindowOfTheSender(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> inv.setItem(0, new ItemStack(gold(), 5)));
        player.getInventory().setItem(0, new ItemStack(ModItems.PHONE.get()));
        TestSupport.setIron(player, 100_000L);
        CharacterVault before = vault(player);
        OfferBought marks = bought(player);
        mitofus(helper, () -> {
            player.containerMenu = new TerminalMenu(7, player.getInventory(), Optional.empty());
            try {
                OfferEntry entry = offer(helper, TerminalService.today(player)).get(0);
                helper.assertTrue(TerminalShop.buyOffer(player, new TerminalBuyOfferPayload(7, 0, entry.stack(), entry.price())).result()
                        == TerminalShop.Result.IN_MITOFUS, "a piece with the window open in Mitofus");
                assertUnchanged(helper, player, 100_000L, before, marks, "in Mitofus with a window");
            } finally {
                player.containerMenu = player.inventoryMenu;
            }
        });
        real(helper, () -> {
            assertUnchanged(helper, player, 100_000L, before, marks, "no window, gold");
            OfferEntry entry = offer(helper, TerminalService.today(player)).get(0);
            helper.assertTrue(TerminalShop.buyOffer(player, new TerminalBuyOfferPayload(7, 0, entry.stack(), entry.price())).result()
                    == TerminalShop.Result.NO_TERMINAL, "nor for a piece");
            assertUnchanged(helper, player, 100_000L, before, marks, "no window, piece");

            player.containerMenu = new TerminalMenu(7, player.getInventory(), Optional.empty());
            try {
                assertUnchanged(helper, player, 100_000L, before, marks, "another id");
                TerminalShop.Outcome piece = TerminalShop.buyOffer(player, new TerminalBuyOfferPayload(7, 0, entry.stack(), entry.price()));
                helper.assertTrue(piece.result() == TerminalShop.Result.OK, "a piece by payload: " + piece.result());
            } finally {
                player.containerMenu = player.inventoryMenu;
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void malformedStoredInventoriesAreUnavailableAndCannotBePurchasedInto(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> inv.setItem(0, new ItemStack(gold(), 5)));
        CompoundTag valid = vault(player).stored().orElseThrow();

        CompoundTag wrongList = valid.copy();
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("unreadable entry"));
        wrongList.put("inventory", strings);

        CompoundTag brokenStack = valid.copy();
        ListTag mixed = CharacterSnapshot.inventory(brokenStack);
        CompoundTag unknown = new CompoundTag();
        unknown.putByte("Slot", (byte) 1);
        unknown.putString("id", "mitofus:missing_item_for_test");
        mixed.add(unknown);

        CompoundTag brokenChest = valid.copy();
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) 26);
        stack.putString("id", "minecraft:iron_sword");
        CompoundTag enchantments = new CompoundTag();
        CompoundTag levels = new CompoundTag();
        levels.putInt("mitofus:missing_enchantment_for_test", 1);
        enchantments.put("levels", levels);
        CompoundTag components = new CompoundTag();
        components.put("minecraft:enchantments", enchantments);
        stack.put("components", components);
        ListTag chest = new ListTag();
        chest.add(stack);
        brokenChest.put("ender_chest", chest);

        player.getInventory().setItem(0, new ItemStack(ModItems.PHONE.get()));
        player.getInventory().setItem(1, new ItemStack(Items.EMERALD, 11));
        player.getEnderChestInventory().setItem(4, new ItemStack(Items.DIAMOND, 3));
        TestSupport.setIron(player, 100_000L);
        var purse = Wallet.purse(player);
        CompoundTag live = CharacterSnapshot.capture(player);
        real(helper, () -> {
            long today = TerminalService.today(player);
            OfferEntry entry = offer(helper, today).get(0);
            OfferBought marks = OfferBought.EMPTY.with(1, today);
            player.setData(ModAttachments.OFFER_BOUGHT.get(), marks);
            player.containerMenu = new TerminalMenu(7, player.getInventory(), Optional.empty());
            try {
                TerminalBuyOfferPayload payload = new TerminalBuyOfferPayload(7, entry.position(), entry.stack(), entry.price());
                for (CompoundTag malformed : List.of(wrongList, brokenStack, brokenChest)) {
                    CharacterVault before = CharacterVault.of(WorldSide.REAL, malformed);
                    player.setData(ModAttachments.CHARACTER_VAULT.get(), before);
                    TerminalState state = TerminalService.state(player, Optional.empty());
                    helper.assertFalse(state.characterAvailable(), "unreadable character was offered as a destination");
                    helper.assertTrue(state.freeSlots() == 0 && state.storedGold() == 0, "the terminal exposed partial storage data");
                    assertUnchanged(helper, player, purse.iron(), before, marks, "reading the terminal");

                    TerminalShop.Outcome outcome = TerminalShop.buyOffer(player, payload);
                    helper.assertTrue(outcome.result() == TerminalShop.Result.NO_CHARACTER && TerminalShop.message(outcome).isPresent(),
                            "the purchase should be refused with feedback: " + outcome.result());
                    assertUnchanged(helper, player, purse.iron(), before, marks, "buying from the terminal");
                    helper.assertTrue(Wallet.purse(player).equals(purse), "the entire purse is unchanged");
                    helper.assertTrue(CharacterSnapshot.capture(player).equals(live), "live inventory, Ender Chest and vitals are unchanged");
                }
            } finally {
                player.containerMenu = player.inventoryMenu;
            }
        });
        helper.succeed();
    }

    // ---- The offer ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theOfferIsTheSameForEveryoneAndChangesWithTheDay(GameTestHelper helper) {
        List<OfferEntry> a = offer(helper, 42L);
        List<OfferEntry> b = offer(helper, 42L);
        helper.assertTrue(a.size() == OFFER_SIZE && b.size() == OFFER_SIZE, "pieces: " + a.size());
        int differing = 0;
        List<OfferEntry> other = offer(helper, 43L);
        for (int i = 0; i < OFFER_SIZE; i++) {
            helper.assertTrue(ItemStack.matches(a.get(i).stack(), b.get(i).stack()) && a.get(i).price() == b.get(i).price(), "same day, same piece " + i);
            if (!ItemStack.matches(a.get(i).stack(), other.get(i).stack())) {
                differing++;
            }
        }
        helper.assertTrue(differing > 6, "another day changes most of the offer: " + differing + " of " + OFFER_SIZE);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theOfferHasSixNormalAndSixRarePiecesOfTheConfiguredTypes(GameTestHelper helper) {
        for (long day : new long[]{0L, 1L, 77L, 1234L}) {
            List<OfferEntry> offer = offer(helper, day);
            for (OfferEntry entry : offer) {
                ItemStack stack = entry.stack();
                ItemRating expected = entry.position() < FIRST_RARE ? ItemRating.NORMAL : ItemRating.RARE;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                helper.assertTrue(stack.get(ModDataComponents.ITEM_RATING) == expected, "rating of " + entry.position() + " on day " + day);
                helper.assertTrue(D.offerItems().contains(id), "an offered type: " + id);
                helper.assertFalse(id.getPath().contains("diamond") || id.getPath().contains("netherite"), "nothing OP: " + id);
                List<RolledOption> options = stack.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.of());
                helper.assertTrue(options.size() <= EquipmentRules.optionCount(expected), "options within the rating: " + options.size());
                helper.assertTrue(stack.getCount() == 1, "one unit");
                // The offered piece is unappraised, but the terminal charges its real value: the true reference.
                helper.assertTrue(entry.price() == OfferRules.price(TradeGoods.trueReferenceValue(stack, TRADE), D.buyMultiplier(), com.orijua.mitofus.feature.economy.Money.IRON_PER_GOLD)
                        && entry.price() >= 1L, "price: " + entry.price());
            }
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void buyingAPieceChargesPutsItInTheFirstFreeSlotAndMarksIt(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> {
            fillStone(inv, 0, 2);
            inv.setItem(3, new ItemStack(Items.DIRT, 5));
        });
        TestSupport.setIron(player, 1_000_000L);
        real(helper, () -> {
            OfferEntry entry = offer(helper, 7L).get(3);
            TerminalShop.Outcome outcome = TerminalShop.buyOffer(player, 3, entry.stack(), entry.price(), 7L, D, TRADE);
            helper.assertTrue(outcome.result() == TerminalShop.Result.OK, "bought: " + outcome.result());
            helper.assertTrue(outcome.price() == entry.price() && com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 1_000_000L - entry.price(), "charged the price");
            CompoundTag saved = entryAt(player, 2);
            helper.assertTrue(saved != null, "in the first free slot (2)");
            ItemStack back = ItemStack.parse(player.registryAccess(), saved).orElseThrow();
            helper.assertTrue(ItemStack.matches(back, entry.stack()), "the piece is the one offered");
            helper.assertTrue(player.getData(ModAttachments.OFFER_BOUGHT.get()).has(3, 7L), "marked as bought");
            helper.assertTrue(TerminalShop.message(outcome).isPresent(), "and says so");

            CharacterVault afterFirst = vault(player);
            long wonAfter = com.orijua.mitofus.feature.economy.Wallet.purse(player).iron();
            OfferBought marksAfter = bought(player);
            TerminalShop.Outcome again = TerminalShop.buyOffer(player, 3, entry.stack(), entry.price(), 7L, D, TRADE);
            helper.assertTrue(again.result() == TerminalShop.Result.SOLD_OUT, "one unit per day: " + again.result());
            assertUnchanged(helper, player, wonAfter, afterFirst, marksAfter, "the second one");

            OfferEntry other = offer(helper, 7L).get(4);
            helper.assertTrue(TerminalShop.buyOffer(player, 4, other.stack(), other.price(), 7L, D, TRADE).result() == TerminalShop.Result.OK,
                    "another piece is fine");
            helper.assertTrue(entryAt(player, 4) != null && entryAt(player, 3).getString("id").equals("minecraft:dirt"), "in the next free slot");

            OfferEntry tomorrow = offer(helper, 8L).get(3);
            helper.assertTrue(TerminalShop.buyOffer(player, 3, tomorrow.stack(), tomorrow.price(), 8L, D, TRADE).result() == TerminalShop.Result.OK,
                    "the next day it can be bought again");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPieceOrPriceThatChangedIsStaleAndChangesNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> inv.setItem(0, new ItemStack(Items.DIRT)));
        TestSupport.setIron(player, 1_000_000L);
        CharacterVault before = vault(player);
        OfferBought marks = bought(player);
        real(helper, () -> {
            OfferEntry entry = offer(helper, 7L).get(0);
            ItemStack two = entry.stack().copyWithCount(2);
            ItemStack reRated = entry.stack().copy();
            reRated.set(ModDataComponents.ITEM_RATING, ItemRating.EPIC);
            record Attempt(String what, int position, ItemStack seen, long price) {
            }
            for (Attempt a : List.of(
                    new Attempt("another piece", 0, new ItemStack(Items.STONE), entry.price()),
                    new Attempt("an empty piece", 0, ItemStack.EMPTY, entry.price()),
                    new Attempt("another price", 0, entry.stack(), entry.price() + 1),
                    new Attempt("two units", 0, two, entry.price()),
                    new Attempt("another rating", 0, reRated, entry.price()),
                    new Attempt("position 12", 12, entry.stack(), entry.price()),
                    new Attempt("position -1", -1, entry.stack(), entry.price()))) {
                helper.assertTrue(TerminalShop.buyOffer(player, a.position(), a.seen(), a.price(), 7L, D, TRADE).result() == TerminalShop.Result.STALE,
                        a.what());
                assertUnchanged(helper, player, 1_000_000L, before, marks, a.what());
            }
            helper.assertFalse(bought(player).has(0, 7L), "nor marked anything");

            // The order of the checks: a stale view comes before a sold-out piece, and no character before everything.
            TerminalShop.buyOffer(player, 0, entry.stack(), entry.price(), 7L, D, TRADE);
            CharacterVault afterBuying = vault(player);
            OfferBought marksAfter = bought(player);
            long wonAfter = com.orijua.mitofus.feature.economy.Wallet.purse(player).iron();
            helper.assertTrue(TerminalShop.buyOffer(player, 0, entry.stack(), entry.price() + 1, 7L, D, TRADE).result() == TerminalShop.Result.STALE,
                    "stale and sold out: stale comes first");
            helper.assertTrue(TerminalShop.buyOffer(player, 0, entry.stack(), entry.price(), 7L, D, TRADE).result() == TerminalShop.Result.SOLD_OUT,
                    "sold out");
            assertUnchanged(helper, player, wonAfter, afterBuying, marksAfter, "after the purchase");
            player.setData(ModAttachments.CHARACTER_VAULT.get(), new CharacterVault(Optional.of(WorldSide.REAL), Optional.empty()));
            helper.assertTrue(TerminalShop.buyOffer(player, 0, entry.stack(), entry.price() + 1, 7L, D, TRADE).result() == TerminalShop.Result.NO_CHARACTER,
                    "no character and stale: no character comes first");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSinglePieceIsTheSameAsInTheWholeOffer(GameTestHelper helper) {
        for (long day : new long[]{0L, 1L, 42L, 1234L, -5L}) {
            List<OfferEntry> whole = offer(helper, day);
            for (int position = 0; position < whole.size(); position++) {
                Optional<OfferEntry> one = TerminalShop.offerEntry(helper.getLevel(), day, position, D, TRADE);
                helper.assertTrue(one.isPresent() && ItemStack.matches(one.get().stack(), whole.get(position).stack())
                        && one.get().price() == whole.get(position).price() && one.get().position() == position,
                        "day " + day + " position " + position);
            }
            helper.assertTrue(TerminalShop.offerEntry(helper.getLevel(), day, -1, D, TRADE).isEmpty(), "position -1");
            helper.assertTrue(TerminalShop.offerEntry(helper.getLevel(), day, whole.size(), D, TRADE).isEmpty(), "position size");
        }
        helper.assertTrue(TerminalShop.offerEntry(helper.getLevel(), 5L, 0, D.withOffer(0, ItemRating.RARE, D.offerItems()), TRADE).isEmpty(), "offer off");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPieceWithoutRoomOrWonChangesNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> fillStone(inv, 0, 36));
        TestSupport.setIron(player, 1_000_000L);
        CharacterVault full = vault(player);
        real(helper, () -> {
            OfferEntry entry = offer(helper, 7L).get(5);
            helper.assertTrue(TerminalShop.buyOffer(player, 5, entry.stack(), entry.price(), 7L, D, TRADE).result() == TerminalShop.Result.NO_ROOM, "36 slots of stone");
            assertUnchanged(helper, player, 1_000_000L, full, OfferBought.EMPTY, "no room");
            helper.assertFalse(player.getData(ModAttachments.OFFER_BOUGHT.get()).has(5, 7L), "and it can still be bought later");

            withStored(player, inv -> inv.setItem(0, new ItemStack(Items.DIRT)));
            CharacterVault roomy = vault(player);
            TestSupport.setIron(player, entry.price() - 1);
            helper.assertTrue(TerminalShop.buyOffer(player, 5, entry.stack(), entry.price(), 7L, D, TRADE).result() == TerminalShop.Result.NOT_ENOUGH,
                    "one iron short");
            assertUnchanged(helper, player, entry.price() - 1, roomy, OfferBought.EMPTY, "one iron short");
            helper.assertFalse(player.getData(ModAttachments.OFFER_BOUGHT.get()).has(5, 7L), "not marked");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theBuyPayloadsRoundTrip(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        OfferEntry entry = offer(helper, 5L).get(7);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        TerminalBuyOfferPayload.STREAM_CODEC.encode(buf, new TerminalBuyOfferPayload(9, 7, entry.stack(), entry.price()));
        TerminalBuyOfferPayload decoded = TerminalBuyOfferPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.containerId() == 9 && decoded.position() == 7 && decoded.seenPrice() == entry.price()
                && ItemStack.matches(decoded.seen(), entry.stack()), "the purchase payload");

        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aDecodedOfferPayloadBuysThePiece(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> inv.setItem(0, new ItemStack(gold(), 5)));
        player.getInventory().setItem(0, new ItemStack(ModItems.PHONE.get()));
        TestSupport.setIron(player, 1_000_000L);
        real(helper, () -> {
            long today = TerminalService.today(player);
            OfferEntry entry = offer(helper, today).get(7);
            RegistryFriendlyByteBuf buf =new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            TerminalBuyOfferPayload.STREAM_CODEC.encode(buf, new TerminalBuyOfferPayload(7, 7, entry.stack(), entry.price()));
            TerminalBuyOfferPayload decoded = TerminalBuyOfferPayload.STREAM_CODEC.decode(buf);
            player.containerMenu = new TerminalMenu(7, player.getInventory(), Optional.empty());
            try {
                TerminalShop.Outcome outcome = TerminalShop.buyOffer(player, decoded);
                helper.assertTrue(outcome.result() == TerminalShop.Result.OK, "bought by the decoded payload: " + outcome.result());
                helper.assertTrue(outcome.price() == entry.price() && com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 1_000_000L - entry.price(), "charged the price");
                CompoundTag saved = entryAt(player, 1);
                helper.assertTrue(saved != null, "in the first free slot (1)");
                helper.assertTrue(ItemStack.matches(ItemStack.parse(player.registryAccess(), saved).orElseThrow(), entry.stack()), "the piece is the offered one");
            } finally {
                player.containerMenu = player.inventoryMenu;
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theStateCarriesTheOfferAndWhatWasBought(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> inv.setItem(0, new ItemStack(gold(), 5)));
        TestSupport.setIron(player, 1_000_000L);
        real(helper, () -> {
            long today = TerminalService.today(player);
            OfferEntry entry = offer(helper, today).get(7);
            TerminalShop.Outcome bought = TerminalShop.buyOffer(player, 7, entry.stack(), entry.price(), today, D, TRADE);
            helper.assertTrue(bought.result() == TerminalShop.Result.OK, "bought: " + bought.result());
            helper.assertTrue(OfferBought.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, bought(player)).result().isPresent(), "the mark persists");

            player.setData(ModAttachments.VR_HELMET_TIER.get(), com.orijua.mitofus.feature.vrhelmet.VrHelmetTier.ADVANCED);
            TerminalState state = TerminalService.state(player, Optional.empty());
            RegistryFriendlyByteBuf buf3 = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            TerminalState.STREAM_CODEC.encode(buf3, state);
            TerminalState back = TerminalState.STREAM_CODEC.decode(buf3);
            helper.assertTrue(back.offer().size() == OFFER_SIZE, "the state: offer");
            for (int i = 0; i < OFFER_SIZE; i++) {
                helper.assertTrue(ItemStack.matches(back.offer().get(i).stack(), state.offer().get(i).stack())
                        && back.offer().get(i).price() == state.offer().get(i).price() && back.offer().get(i).position() == i, "piece " + i);
            }
            helper.assertTrue(back.boughtToday().equals(java.util.Set.of(7)), "what was bought today: " + back.boughtToday());
            helper.assertTrue(back.freeSlots() == state.freeSlots() && back.freeSlots() > 0, "room and slots");
            helper.assertTrue(back.sessionTier() == com.orijua.mitofus.feature.vrhelmet.VrHelmetTier.ADVANCED, "the tier of the session");
        });
        helper.succeed();
    }

    private static RegistryFriendlyByteBuf buffer(ServerPlayer player) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theStateWithTheDiamondTierRoundTripsAndKeepsItWithAMessage(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> inv.setItem(0, new ItemStack(gold(), 5)));
        real(helper, () -> {
            player.setData(ModAttachments.VR_HELMET_TIER.get(), VrHelmetTier.DIAMOND);
            TerminalState state = TerminalService.state(player, Optional.empty());
            helper.assertTrue(state.sessionTier() == VrHelmetTier.DIAMOND, "the tier of the session: " + state.sessionTier());

            RegistryFriendlyByteBuf buf = buffer(player);
            TerminalState.STREAM_CODEC.encode(buf, state);
            TerminalState back = TerminalState.STREAM_CODEC.decode(buf);
            helper.assertTrue(back.sessionTier() == VrHelmetTier.DIAMOND, "the tier survives the codec");

            TerminalState told = state.withMessage(Optional.of(Component.literal("hello")));
            helper.assertTrue(told.sessionTier() == VrHelmetTier.DIAMOND && told.message().isPresent(), "withMessage keeps the tier");
            RegistryFriendlyByteBuf buf2 = buffer(player);
            TerminalState.STREAM_CODEC.encode(buf2, told);
            TerminalState backTold = TerminalState.STREAM_CODEC.decode(buf2);
            helper.assertTrue(backTold.message().map(Component::getString).orElse("").equals("hello"), "and so does the codec with a message");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void withoutACharacterTheStateStillCarriesTheOffer(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.CHARACTER_VAULT.get(), new CharacterVault(Optional.of(WorldSide.REAL), Optional.empty()));
        real(helper, () -> {
            TerminalState state = TerminalService.state(player, Optional.empty());
            helper.assertFalse(state.characterAvailable(), "no character");
            helper.assertTrue(state.freeSlots() == 0, "no room");
            helper.assertTrue(state.offer().size() == OFFER_SIZE, "the offer is still shown");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anOfferTurnedOffOrWithoutTypesIsEmptyAndNothingIsBought(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> inv.setItem(0, new ItemStack(Items.DIRT)));
        TestSupport.setIron(player, 1_000_000L);
        CharacterVault before = vault(player);
        TerminalBalance off = D.withOffer(0, ItemRating.RARE, D.offerItems());
        TerminalBalance noTypes = D.withOffer(6, ItemRating.RARE, List.of());
        TerminalBalance junk = D.withOffer(6, ItemRating.RARE, List.of(ResourceLocation.withDefaultNamespace("stone"),
                ResourceLocation.withDefaultNamespace("does_not_exist"), ResourceLocation.withDefaultNamespace("iron_sword")));
        real(helper, () -> {
            helper.assertTrue(TerminalShop.offer(helper.getLevel(), 5L, off, TRADE).isEmpty(), "perRank 0");
            helper.assertTrue(TerminalShop.offer(helper.getLevel(), 5L, noTypes, TRADE).isEmpty(), "no types");
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            helper.assertTrue(TerminalShop.buyOffer(player, 0, sword, 400L, 5L, off, TRADE).result() == TerminalShop.Result.STALE, "stale with the offer off");
            helper.assertTrue(TerminalShop.buyOffer(player, 0, sword, 400L, 5L, noTypes, TRADE).result() == TerminalShop.Result.STALE, "stale without types");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 1_000_000L && vault(player).equals(before), "nothing changed");

            List<OfferEntry> offer = TerminalShop.offer(helper.getLevel(), 5L, junk, TRADE);
            helper.assertTrue(offer.size() == OFFER_SIZE, "unknown ids and non-equipment are left out, the rest remains");
            for (OfferEntry entry : offer) {
                helper.assertTrue(entry.stack().is(Items.IRON_SWORD), "only the iron sword is left: " + entry.stack());
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theOfferPricesFollowTheBalance(GameTestHelper helper) {
        TerminalBalance dear = new TerminalBalance(2.0, 6, ItemRating.RARE, D.offerItems());
        List<OfferEntry> base = TerminalShop.offer(helper.getLevel(), 9L, D, TRADE);
        List<OfferEntry> cheap = TerminalShop.offer(helper.getLevel(), 9L, dear, TRADE);
        for (int i = 0; i < OFFER_SIZE; i++) {
            // The offered pieces are unappraised; the price is made from the true reference.
            helper.assertTrue(cheap.get(i).price() == OfferRules.price(TradeGoods.trueReferenceValue(base.get(i).stack(), TRADE), 2.0, com.orijua.mitofus.feature.economy.Money.IRON_PER_GOLD),
                    "x2 instead of x4 at " + i);
        }
        helper.succeed();
    }

    /** The offered pieces are unappraised, but buying one pays the real price and the piece arrives unappraised, as offered. */
    @GameTest(template = TestSupport.EMPTY)
    public static void buyingAnUnappraisedPieceChargesItsRealPrice(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStored(player, inv -> { });
        TestSupport.setIron(player, 1_000_000L);
        CharacterVault emptyVault = vault(player);
        OfferBought noMarks = bought(player);
        real(helper, () -> {
            OfferEntry entry = offer(helper, 7L).get(FIRST_RARE);
            helper.assertTrue(entry.position() == FIRST_RARE && entry.stack().get(ModDataComponents.ITEM_RATING) == ItemRating.RARE,
                    "the first rare piece of the offer");
            helper.assertTrue(entry.stack().has(ModDataComponents.APPRAISAL), "the offered piece is unappraised");
            long realPrice = OfferRules.price(TradeGoods.trueReferenceValue(entry.stack(), TRADE), D.buyMultiplier(), com.orijua.mitofus.feature.economy.Money.IRON_PER_GOLD);
            long apparentPrice = OfferRules.price(TradeGoods.reference(entry.stack(), TRADE), D.buyMultiplier(), com.orijua.mitofus.feature.economy.Money.IRON_PER_GOLD);
            helper.assertTrue(entry.price() == realPrice, "the offer shows the real price");
            helper.assertTrue(entry.price() > apparentPrice, "the real price is above what the piece would cost as a Normal one: "
                    + realPrice + " vs " + apparentPrice);

            // The price of a piece sold as Normal is stale, and nothing changes.
            helper.assertTrue(TerminalShop.buyOffer(player, FIRST_RARE, entry.stack(), apparentPrice, 7L, D, TRADE).result() == TerminalShop.Result.STALE,
                    "the apparent price is stale");
            assertUnchanged(helper, player, 1_000_000L, emptyVault, noMarks, "the apparent price");
            // One iron short of the real price: refused and nothing is delivered.
            TestSupport.setIron(player, realPrice - 1);
            helper.assertTrue(TerminalShop.buyOffer(player, FIRST_RARE, entry.stack(), entry.price(), 7L, D, TRADE).result()
                    == TerminalShop.Result.NOT_ENOUGH, "one iron short of the real price");
            assertUnchanged(helper, player, realPrice - 1, emptyVault, noMarks, "one iron short");
            TestSupport.setIron(player, 1_000_000L);

            TerminalShop.Outcome outcome = TerminalShop.buyOffer(player, FIRST_RARE, entry.stack(), entry.price(), 7L, D, TRADE);
            helper.assertTrue(outcome.result() == TerminalShop.Result.OK, "bought, not stale: " + outcome.result());
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 1_000_000L - realPrice, "charged the real price");
            ItemStack back = ItemStack.parse(player.registryAccess(), entryAt(player, 0)).orElseThrow();
            helper.assertTrue(back.has(ModDataComponents.APPRAISAL) && ItemStack.matches(back, entry.stack()), "it arrives unappraised, as offered");
        });
        helper.succeed();
    }
}
