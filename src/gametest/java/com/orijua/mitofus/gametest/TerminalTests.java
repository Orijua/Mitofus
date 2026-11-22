package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.vrhelmet.StarterVrHelmet;
import com.orijua.mitofus.feature.character.CharacterSnapshot;
import com.orijua.mitofus.feature.character.CharacterVault;
import com.orijua.mitofus.feature.character.Characters;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.economy.TerminalMenu;
import com.orijua.mitofus.feature.economy.TerminalRules;
import com.orijua.mitofus.feature.economy.TerminalService;
import com.orijua.mitofus.feature.economy.TerminalShop;
import com.orijua.mitofus.feature.world.MitofusOnly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * The account terminal and the phone (economy, step 3): the window, when the window stays open, and who gets the phone and when. The services are called directly with the
 * balance of the test; the window is built by hand because a fake player has no real connection to open one.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class TerminalTests {
    private static final BlockPos LANDING = new BlockPos(1, 1, 1);

    private TerminalTests() {
    }

    private static Item gold() {
        return ModItems.GOLD_COIN.get();
    }

    private static void real(GameTestHelper helper, Runnable body) {
        VrHelmetTests.outsideMitofus(helper, body);
    }

    private static void mitofus(GameTestHelper helper, Runnable body) {
        VrHelmetTests.inMitofus(helper, helper.absolutePos(LANDING), body);
    }

    /** Takes the inventory as a snapshot (the stored Mitofus character) and leaves the player empty-handed. */
    private static CompoundTag storeInventory(ServerPlayer player) {
        CompoundTag snapshot = CharacterSnapshot.capture(player);
        player.getInventory().clearContent();
        return snapshot;
    }

    /** The real world's player with a stored Mitofus character holding 10 + 5 gold coins and a sword. Returns the snapshot. */
    private static CompoundTag withStoredCharacter(ServerPlayer player) {
        player.getInventory().setItem(0, new ItemStack(gold(), 10));
        player.getInventory().setItem(1, new ItemStack(gold(), 5));
        player.getInventory().setItem(2, new ItemStack(Items.DIAMOND_SWORD));
        CompoundTag snapshot = storeInventory(player);
        player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.REAL, snapshot));
        return snapshot;
    }

    /** The player carries the real world's character (live REAL), with nothing stored. */
    private static void realCharacter(ServerPlayer player) {
        player.setData(ModAttachments.CHARACTER_VAULT.get(), new CharacterVault(Optional.of(WorldSide.REAL), Optional.empty()));
    }

    private static int storedGold(ServerPlayer player) {
        CharacterVault vault = player.getData(ModAttachments.CHARACTER_VAULT.get());
        return TerminalRules.countGold(CharacterSnapshot.inventory(vault.stored().orElseThrow()));
    }

    private static String key(Optional<Component> message) {
        return message.map(component -> ((TranslatableContents) component.getContents()).getKey()).orElse("");
    }

    /** BUG-065: the server pushes the new state when the game day changes while the window is open. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theOpenWindowGetsANewStateWhenTheDayChanges(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        realCharacter(player);
        real(helper, () -> {
            TerminalMenu menu = new TerminalMenu(5, player.getInventory(), Optional.empty());
            long today = TerminalService.today(player);
            menu.markSent(today);
            menu.broadcastChanges();
            helper.assertTrue(menu.sentDay() == today, "same day: nothing to push");
            menu.markSent(today - 1);
            menu.broadcastChanges();
            helper.assertTrue(menu.sentDay() == today, "the day changed: a new state was sent for day " + today + ", sent " + menu.sentDay());
        });
        helper.succeed();
    }

    // ---- In Mitofus ----

    @GameTest(template = TestSupport.EMPTY)
    public static void inMitofusEverythingSaysNoSignal(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        withStoredCharacter(player);
        mitofus(helper, () -> {
            helper.assertTrue(TerminalService.open(player, Optional.empty()) == TerminalService.Result.IN_MITOFUS, "open");
            helper.assertTrue(key(TerminalShop.message(new TerminalShop.Outcome(TerminalShop.Result.IN_MITOFUS, 0L, ItemStack.EMPTY))).equals("message.mitofus.terminal.no_signal"), "the message is No signal");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 0L, "nothing was paid");
        });
        helper.succeed();
    }

    /** BUG-062: in the real world but still carrying Mitofus's things (a failed settle), the terminal does nothing. */
    @GameTest(template = TestSupport.EMPTY)
    public static void carryingMitofussThingsInTheRealWorldTheTerminalDoesNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        CompoundTag snapshot = CharacterSnapshot.capture(player);
        player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.MITOFUS, snapshot));
        real(helper, () -> {
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.purse(player).iron() == 0L, "nothing was paid");
            helper.assertTrue(TerminalService.open(player, Optional.empty()) == TerminalService.Result.IN_MITOFUS, "open");
            helper.assertFalse(player.containerMenu instanceof TerminalMenu, "no window opened");
        });
        helper.succeed();
    }

    // ---- Work ----

    // ---- The window stays open ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theWindowStaysOpenNearTheTerminalOrWithAPhone(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlockPos relative = new BlockPos(0, 1, 3);
        BlockPos pos = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.ACCOUNT_TERMINAL.get());
        TerminalMenu menu = new TerminalMenu(1, player.getInventory(), Optional.of(pos));
        real(helper, () -> {
            Vec3 c = Vec3.atCenterOf(pos);
            helper.assertTrue(menu.stillValid(player), "valid at 3 blocks");
            player.moveTo(c.x, c.y, c.z - 8.0);
            helper.assertTrue(menu.stillValid(player), "valid at exactly 8 blocks: " + player.distanceToSqr(c));
            player.moveTo(c.x, c.y, c.z - 8.1);
            helper.assertFalse(menu.stillValid(player), "not valid at 8.1 blocks: " + player.distanceToSqr(c));
            player.getInventory().setItem(0, new ItemStack(ModItems.PHONE.get()));
            helper.assertTrue(menu.stillValid(player), "valid at 8.1 blocks with a phone");
            player.getInventory().clearContent();
            helper.assertFalse(menu.stillValid(player), "not valid once the phone is gone");
            player.moveTo(c.x, c.y, c.z - 3.0);
            helper.assertTrue(menu.stillValid(player), "near again");
            player.setHealth(0.0f);
            helper.assertFalse(menu.stillValid(player), "not valid for a dead player");
            player.setHealth(player.getMaxHealth());
            helper.setBlock(relative, Blocks.AIR);
            helper.assertFalse(menu.stillValid(player), "not valid if the terminal is gone");
            helper.assertTrue(new TerminalMenu(2, player.getInventory(), Optional.empty()).stillValid(player) == false, "a phone window without the phone");
        });
        mitofus(helper, () -> {
            helper.setBlock(relative, ModBlocks.ACCOUNT_TERMINAL.get());
            player.getInventory().setItem(0, new ItemStack(ModItems.PHONE.get()));
            helper.assertFalse(menu.stillValid(player), "never valid in Mitofus");
            player.getInventory().clearContent();
            helper.setBlock(relative, Blocks.AIR);
        });
        helper.succeed();
    }

    // ---- The phone ----
    /** BUG-068 T1: in Mitofus the phone is never given, even carrying the real character. */
    @GameTest(template = TestSupport.EMPTY)
    public static void thePhoneIsNotGivenInMitofusEvenCarryingTheRealCharacter(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.STARTER_VR_HELMET_GIVEN.get(), true);
        CompoundTag snapshot = storeInventory(player);
        player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.REAL, snapshot));
        mitofus(helper, () -> {
            helper.assertFalse(TerminalService.giveIfMissing(player), "not in Mitofus");
            helper.assertFalse(player.getData(ModAttachments.PHONE_GIVEN.get()), "and not marked");
            helper.assertTrue(player.getInventory().countItem(ModItems.PHONE.get()) == 0, "no phone in the inventory");
        });
        helper.succeed();
    }

    // BUG-068 T6 (N4, not automated): the login and change-of-dimension listeners skip fake players, and the mock server player of
    // GameTestHelper cannot receive attachment sync packets (the server crashes ticking it). Check by hand: join with the VR helmet
    // given and the real character, and come back from Mitofus: one phone each time.


    @GameTest(template = TestSupport.EMPTY)
    public static void aNewPlayerGetsThePhoneOnlyAfterTheVrHelmetAndOnlyOnce(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.CHARACTER_VAULT.get(), new CharacterVault(Optional.of(WorldSide.REAL), Optional.empty()));
        real(helper, () -> {
            helper.assertFalse(TerminalService.giveIfMissing(player), "no vrhelmet given yet: no phone");
            helper.assertTrue(player.getInventory().countItem(ModItems.PHONE.get()) == 0, "none in the inventory");
            helper.assertTrue(StarterVrHelmet.giveIfFirstTime(player), "the starter vrhelmet is given");
            helper.assertTrue(TerminalService.giveIfMissing(player), "now the phone");
            helper.assertTrue(player.getInventory().countItem(ModItems.PHONE.get()) == 1, "one phone");
            helper.assertTrue(player.getData(ModAttachments.PHONE_GIVEN.get()), "marked");
            helper.assertFalse(TerminalService.giveIfMissing(player), "not a second time");
            helper.assertTrue(player.getInventory().countItem(ModItems.PHONE.get()) == 1, "still one");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anExistingPlayerGetsThePhoneWhenTheyAreInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.STARTER_VR_HELMET_GIVEN.get(), true);
        player.getInventory().setItem(0, new ItemStack(gold(), 3));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        CompoundTag mitofusCharacter = storeInventory(player);
        ListTag mitofusBefore = CharacterSnapshot.inventory(mitofusCharacter).copy();
        player.setData(ModAttachments.CHARACTER_VAULT.get(), new CharacterVault(Optional.of(WorldSide.MITOFUS), Optional.empty()));
        mitofus(helper, () -> {
            helper.assertFalse(TerminalService.giveIfMissing(player), "not in Mitofus");
            helper.assertFalse(player.getData(ModAttachments.PHONE_GIVEN.get()), "and not marked");
        });
        real(helper, () -> {
            helper.assertFalse(TerminalService.giveIfMissing(player), "in the real world but carrying Mitofus's things (live MITOFUS)");
            player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.EMPTY);
            helper.assertFalse(TerminalService.giveIfMissing(player), "an empty vault says nothing about the world");
            helper.assertFalse(player.getData(ModAttachments.PHONE_GIVEN.get()), "still not marked");
            player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.REAL, mitofusCharacter));
            helper.assertTrue(TerminalService.giveIfMissing(player), "carrying the real world's things: given");
            helper.assertTrue(CharacterSnapshot.inventory(player.getData(ModAttachments.CHARACTER_VAULT.get()).stored().orElseThrow()).equals(mitofusBefore),
                    "the stored Mitofus character is untouched");
            helper.assertTrue(player.getInventory().countItem(ModItems.PHONE.get()) == 1, "one phone");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void thePhoneStaysWithTheRealCharacter(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.STARTER_VR_HELMET_GIVEN.get(), true);
        player.getInventory().setItem(0, new ItemStack(gold(), 3));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        CompoundTag mitofusCharacter = storeInventory(player);
        ListTag mitofusBefore = CharacterSnapshot.inventory(mitofusCharacter).copy();
        player.setData(ModAttachments.CHARACTER_VAULT.get(), CharacterVault.of(WorldSide.REAL, mitofusCharacter));
        real(helper, () -> {
            helper.assertTrue(TerminalService.giveIfMissing(player), "given in the real world");
            helper.assertTrue(CharacterSnapshot.inventory(player.getData(ModAttachments.CHARACTER_VAULT.get()).stored().orElseThrow()).equals(mitofusBefore),
                    "the stored Mitofus character is the same as before");
            helper.assertFalse(inventoryHas(CharacterSnapshot.inventory(player.getData(ModAttachments.CHARACTER_VAULT.get()).stored().orElseThrow()), "mitofus:phone"),
                    "not in the stored Mitofus character");
        });
        mitofus(helper, () -> {
            helper.assertTrue(Characters.settle(player) == Characters.Result.SWAPPED, "entering Mitofus swaps");
            helper.assertTrue(player.getInventory().countItem(ModItems.PHONE.get()) == 0, "the Mitofus character has no phone");
            helper.assertTrue(inventoryHas(CharacterSnapshot.inventory(player.getData(ModAttachments.CHARACTER_VAULT.get()).stored().orElseThrow()), "mitofus:phone"),
                    "it waits with the real character");
        });
        helper.succeed();
    }

    private static boolean inventoryHas(ListTag inventory, String id) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getCompound(i).getString("id").equals(id)) {
                return true;
            }
        }
        return false;
    }

    // ---- Attachments and registry ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theTerminalAttachmentsAreClearedByResetAll(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setData(ModAttachments.PHONE_GIVEN.get(), true);
        player.setData(ModAttachments.OFFER_BOUGHT.get(), com.orijua.mitofus.feature.economy.OfferBought.EMPTY.with(2, 5L));
        ModAttachments.resetAll(player);
        helper.assertTrue(player.getData(ModAttachments.OFFER_BOUGHT.get()).equals(com.orijua.mitofus.feature.economy.OfferBought.EMPTY), "reset all clears the bought offer");
        helper.assertFalse(player.getData(ModAttachments.PHONE_GIVEN.get()), "and the phone flag");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theGoldIdOfTheRulesIsTheRegisteredOne(GameTestHelper helper) {
        helper.assertTrue(TerminalRules.GOLD_ID.equals(BuiltInRegistries.ITEM.getKey(ModItems.GOLD_COIN.get()).toString()),
                "the id used to count gold in a snapshot is the item's");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theTerminalAndThePhoneAreNotInert(GameTestHelper helper) {
        helper.assertFalse(MitofusOnly.isMitofusItem(BuiltInRegistries.ITEM.getKey(ModItems.PHONE.get())), "the phone works in the real world");
        helper.assertFalse(MitofusOnly.isMitofusItem(BuiltInRegistries.ITEM.getKey(ModItems.ACCOUNT_TERMINAL.get())), "the terminal item too");
        helper.assertFalse(MitofusOnly.blocksPlacement(BuiltInRegistries.BLOCK.getKey(ModBlocks.ACCOUNT_TERMINAL.get())), "and it can be placed");
        helper.succeed();
    }
}
