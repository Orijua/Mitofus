package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.ItemOptionDefinition;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.npc.ApprenticeContractItem;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.smithing.SmithingHammers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Smithing hammers: what they add to a forge, their own options, and apprentices carrying them. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class HammerTests {
    private HammerTests() {
    }

    private static ItemOptionDefinition option(GameTestHelper helper, String id) {
        return Definitions.get(helper.getLevel(), ModDatapackRegistries.ITEM_OPTION, Mitofus.id(id)).orElseThrow();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aHammerAddsItsMaterialRatingEnhancementAndOptions(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ItemStack hammer = new ItemStack(ModItems.WOLFRAM_SMITHING_HAMMER.get());
            var registries = helper.getLevel().registryAccess();
            helper.assertTrue(SmithingHammers.skillBonus(registries, hammer) == 10, "a plain wolfram hammer: its material");
            hammer.set(ModDataComponents.ITEM_RATING, ItemRating.EPIC);
            hammer.set(ModDataComponents.ENHANCEMENT, 2);
            hammer.set(ModDataComponents.ITEM_OPTIONS, List.of(new RolledOption(Mitofus.id("hammer_precision"), 3.0f),
                    new RolledOption(Mitofus.id("hammer_learning"), 0.1f)));
            TestSupport.assertClose(helper, 18.435, SmithingHammers.skillBonus(registries, hammer), "(10 + 4) * 1.05^2 + precision 3");
            helper.assertTrue(Math.abs(SmithingHammers.xpBonus(registries, hammer) - 0.1) < 1e-6, "learning: +10 % experience");
            helper.assertTrue(SmithingHammers.skillBonus(registries, new ItemStack(Items.IRON_SWORD)) == 0, "a sword is no hammer");
        });
        helper.succeed();
    }

    private static ItemStack hammerAt(net.minecraft.world.item.Item item, int level) {
        ItemStack hammer = new ItemStack(item);
        if (level > 0) {
            hammer.set(ModDataComponents.ENHANCEMENT, level);
        }
        return hammer;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aHammerForgeBonusGrowsWithItsLevel(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            var registries = helper.getLevel().registryAccess();
            int[] levels = {0, 5, 9, 10};
            double[] wolfram = {10, 12.7628156, 16.7294478, 17.9005091};
            double previous = -1;
            for (int i = 0; i < levels.length; i++) {
                double bonus = SmithingHammers.skillBonus(registries, hammerAt(ModItems.WOLFRAM_SMITHING_HAMMER.get(), levels[i]));
                if (levels[i] == 0) {
                    helper.assertTrue(bonus == 10, "a plain wolfram hammer is exactly 10, got " + bonus);
                } else {
                    TestSupport.assertClose(helper, wolfram[i], bonus, "wolfram hammer +" + levels[i]);
                }
                helper.assertTrue(bonus > previous, "+" + levels[i] + " beats the previous level");
                previous = bonus;
            }
            helper.assertTrue(SmithingHammers.skillBonus(registries, hammerAt(ModItems.IRON_SMITHING_HAMMER.get(), 0)) == 5, "iron +0 is exactly 5");
            helper.assertTrue(SmithingHammers.skillBonus(registries, hammerAt(ModItems.MITHRIL_SMITHING_HAMMER.get(), 0)) == 20, "mithril +0 is exactly 20");

            // The golden tooltip line follows the bonus.
            ItemStack plain = hammerAt(ModItems.WOLFRAM_SMITHING_HAMMER.get(), 0);
            ItemStack plus5 = hammerAt(ModItems.WOLFRAM_SMITHING_HAMMER.get(), 5);
            helper.assertTrue("+10".equals(forgeArg(helper, plain)), "the +0 tooltip says +10, got " + forgeArg(helper, plain));
            helper.assertTrue("+12.8".equals(forgeArg(helper, plus5)), "the +5 tooltip says +12.8, got " + forgeArg(helper, plus5));
        });
        helper.succeed();
    }

    /** The best hammer is the one with the highest whole bonus, not the best material. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theBestHammerIsChosenByItsWholeBonus(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            var registries = helper.getLevel().registryAccess();
            ItemStack ancestral10 = new ItemStack(ModItems.IRON_SMITHING_HAMMER.get());
            ancestral10.set(ModDataComponents.ITEM_RATING, ItemRating.ANCESTRAL);
            ancestral10.set(ModDataComponents.ENHANCEMENT, 10);
            ItemStack mithril = new ItemStack(ModItems.MITHRIL_SMITHING_HAMMER.get());
            TestSupport.assertClose(helper, 37.5910691, SmithingHammers.skillBonus(registries, ancestral10), "(5 + 16) * 1.79005091");
            helper.assertTrue(SmithingHammers.skillBonus(registries, mithril) == 20, "mithril +0 is 20");

            ServerPlayer player = TestSupport.fakePlayer(helper);
            player.getInventory().setItem(0, mithril);
            player.getInventory().setItem(1, ancestral10);
            helper.assertTrue(SmithingHammers.bestSlot(registries, player.getInventory()) == 1, "the Ancestral +10 iron hammer beats mithril");

            player.setItemSlot(EquipmentSlot.MAINHAND, mithril.copy());
            player.setItemSlot(EquipmentSlot.OFFHAND, ancestral10.copy());
            helper.assertTrue(SmithingHammers.heldHammer(registries, player).equals(Optional.of(EquipmentSlot.OFFHAND)), "the off-hand one counts");
            player.setItemSlot(EquipmentSlot.MAINHAND, ancestral10.copy());
            player.setItemSlot(EquipmentSlot.OFFHAND, mithril.copy());
            helper.assertTrue(SmithingHammers.heldHammer(registries, player).equals(Optional.of(EquipmentSlot.MAINHAND)), "the main-hand one counts");
        });
        helper.succeed();
    }

    /** The argument of the "Forge: %s" tooltip line. */
    private static String forgeArg(GameTestHelper helper, ItemStack hammer) {
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        hammer.getItem().appendHoverText(hammer, net.minecraft.world.item.Item.TooltipContext.of(helper.getLevel()), lines,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        for (net.minecraft.network.chat.Component line : lines) {
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("item.mitofus.smithing_hammer.bonus")) {
                return String.valueOf(t.getArgs()[0]);
            }
        }
        return "<none>";
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void hammersOnlyRollTheirOwnOptions(GameTestHelper helper) {
        ItemStack hammer = new ItemStack(ModItems.IRON_SMITHING_HAMMER.get());
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        helper.assertTrue(option(helper, "hammer_precision").canRollOn(hammer) && !option(helper, "hammer_precision").canRollOn(sword),
                "precision is a hammer's");
        helper.assertTrue(!option(helper, "vitality").canRollOn(hammer) && option(helper, "vitality").canRollOn(sword),
                "a generic option never lands on a hammer");
        helper.succeed();
    }

    /** A hammer counts in either hand; with two, only the better one (they never add up). */
    @GameTest(template = TestSupport.EMPTY)
    public static void aHammerCountsInEitherHandButTwoDoNotAddUp(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(SmithingHammers.heldHammer(registries, player).isEmpty(), "no hammer");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.IRON_SMITHING_HAMMER.get()));
        helper.assertTrue(SmithingHammers.heldHammer(registries, player).equals(Optional.of(EquipmentSlot.MAINHAND)), "in the main hand");
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(ModItems.MITHRIL_SMITHING_HAMMER.get()));
        helper.assertTrue(SmithingHammers.heldHammer(registries, player).equals(Optional.of(EquipmentSlot.OFFHAND)), "the better of two");
        helper.assertTrue(SmithingHammers.heldHammerStack(registries, player).is(ModItems.MITHRIL_SMITHING_HAMMER.get()), "only that one counts");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        helper.assertTrue(SmithingHammers.heldHammer(registries, player).equals(Optional.of(EquipmentSlot.OFFHAND)), "in the off hand alone");
        helper.succeed();
    }

    /** He takes the best hammer, forges with it (wearing it) and hands it back. */
    @GameTest(template = TestSupport.EMPTY)
    public static void anApprenticeCarriesHisMastersBestHammer(GameTestHelper helper) {
        BlockPos anvil = new BlockPos(1, 1, 1);
        helper.setBlock(anvil, ModBlocks.SMITHING_ANVIL.get());
        helper.setBlock(anvil.west(), Blocks.CHEST);
        helper.setBlock(anvil.east(), Blocks.BARREL);
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, PlayerStats.DEFAULT.withSmithingXp(50 * 50 * 10));
        ApprenticeSmith apprentice = ApprenticeContractItem.hire(player, helper.absolutePos(new BlockPos(1, 1, 0))).orElseThrow();

        helper.assertFalse(apprentice.giveHammer(player), "no hammer to give");
        player.getInventory().add(new ItemStack(ModItems.IRON_SMITHING_HAMMER.get()));
        player.getInventory().add(new ItemStack(ModItems.MITHRIL_SMITHING_HAMMER.get()));
        helper.assertTrue(apprentice.giveHammer(player) && apprentice.getMainHandItem().is(ModItems.MITHRIL_SMITHING_HAMMER.get()), "the best one");
        helper.assertTrue(player.getInventory().countItem(ModItems.MITHRIL_SMITHING_HAMMER.get()) == 0, "it left the inventory");

        apprentice.takeOrder(player, ModItems.WOLFRAM_SWORD.get());
        Container chest = helper.getBlockEntity(anvil.west());
        chest.setItem(0, new ItemStack(ModItems.WOLFRAM_INGOT.get(), 2));
        chest.setItem(1, new ItemStack(Items.STICK));
        apprentice.forgeOne(0.99);
        helper.assertTrue(apprentice.getMainHandItem().getDamageValue() == 1, "a piece wears it");

        apprentice.takeHammer(player);
        helper.assertTrue(apprentice.getMainHandItem().isEmpty() && player.getInventory().countItem(ModItems.MITHRIL_SMITHING_HAMMER.get()) == 1,
                "back to his master");
        apprentice.discard();
        helper.succeed();
    }
}
