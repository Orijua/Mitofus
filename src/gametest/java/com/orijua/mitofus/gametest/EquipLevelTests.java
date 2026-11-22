package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.EquipLevelBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.economy.TradeGoods;
import com.orijua.mitofus.feature.economy.TradeRules;
import com.orijua.mitofus.feature.economy.TradeSaturation;
import com.orijua.mitofus.feature.economy.TradeService;
import com.orijua.mitofus.feature.equipment.EquipLevels;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.EquipmentEvents;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** The level a rated piece needs even if its recipe stored none ("an old piece"), and what that does to the price. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class EquipLevelTests {
    private static final TradeBalance B = TradeBalance.DEFAULT;
    private static final EquipLevelBalance L = EquipLevelBalance.DEFAULT;

    private EquipLevelTests() {
    }

    private static ItemStack rated(Item item, ItemRating rating) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.ITEM_RATING, rating);
        return stack;
    }

    private static void atLevel(ServerPlayer player, int level) {
        ProgressionService.set(player, ProgressionService.get(player).withProgress(level, 0, 0));
    }

    /** Runs {@code body} with the factory numbers of the level and the price, whatever the world's config says. */
    private static void defaults(Runnable body) {
        EquipLevelBalance.withOverride(L, () -> TradeBalance.withOverride(B, body));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anOldRatedPieceNeedsItsDerivedLevelToBeUsed(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = rated(Items.IRON_SWORD, ItemRating.UNIQUE); // iron 5 + unique 10 = 15, no REQUIREMENTS stored
            helper.assertFalse(sword.has(ModDataComponents.REQUIREMENTS), "the piece is an old one: nothing stored");
            atLevel(player, 1);
            helper.assertFalse(EquipmentEvents.meetsRequirements(player, sword), "level 1 cannot use a level 15 piece");
            atLevel(player, 14);
            helper.assertFalse(EquipmentEvents.meetsRequirements(player, sword), "level 14 cannot either");
            atLevel(player, 15);
            helper.assertTrue(EquipmentEvents.meetsRequirements(player, sword), "level 15 can");
            helper.assertTrue(EquipmentEvents.meetsRequirements(ProgressionService.statsWithoutEquipment(player), sword), "the stats form agrees");

            atLevel(player, 1);
            helper.assertTrue(EquipmentEvents.meetsRequirements(player, new ItemStack(Items.IRON_SWORD)), "a piece with no rating asks for nothing");
            helper.assertTrue(EquipmentEvents.meetsRequirements(player, ItemStack.EMPTY), "an empty hand asks for nothing");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEffectiveRequirementsSayTheDerivedLevelAndKeepTheStoredStats(GameTestHelper helper) {
        defaults(() -> {
            ItemStack old = rated(Items.IRON_SWORD, ItemRating.UNIQUE);
            EquipRequirements fromOld = EquipLevels.requirements(old).orElseThrow();
            helper.assertTrue(fromOld.level() == 15 && fromOld.stats().isEmpty(), "level 15 and no stats: " + fromOld);

            ItemStack withStats = rated(Items.IRON_SWORD, ItemRating.UNIQUE);
            withStats.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(1, Map.of(StatType.AGILITY, 10)));
            EquipRequirements raised = EquipLevels.requirements(withStats).orElseThrow();
            helper.assertTrue(raised.level() == 15 && raised.stats().equals(Map.of(StatType.AGILITY, 10)),
                    "a stored level below the derived one is raised and its stats kept: " + raised);
            helper.assertTrue(withStats.get(ModDataComponents.REQUIREMENTS).level() == 1, "nothing is written back to the piece");

            ItemStack higher = rated(Items.IRON_SWORD, ItemRating.UNIQUE);
            higher.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(40, Map.of(StatType.STRENGTH, 3)));
            helper.assertTrue(EquipLevels.requirements(higher).orElseThrow().level() == 40, "a stored level above the derived one is kept");

            helper.assertTrue(EquipLevels.requirements(new ItemStack(Items.IRON_SWORD)).isEmpty(), "no rating and nothing stored: no requirements");
            ItemStack unrated = new ItemStack(Items.IRON_SWORD);
            unrated.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(7, Map.of()));
            helper.assertTrue(EquipLevels.requirements(unrated).orElseThrow().level() == 7, "no rating: what it has stays");

            helper.assertTrue(EquipLevels.derivedLevel(rated(ModItems.WOLFRAM_SWORD.get(), ItemRating.RARE)) == 10, "Wolfram sword: 8 + 2");
            helper.assertTrue(EquipLevels.derivedLevel(rated(ModItems.MITHRIL_SMITHING_HAMMER.get(), ItemRating.NORMAL)) == 20, "mithril hammer: 20");
            helper.assertTrue(EquipLevels.derivedLevel(rated(Items.DIAMOND_CHESTPLATE, ItemRating.EPIC)) == 20, "diamond armor: 15 + 5");
            helper.assertTrue(EquipLevels.derivedLevel(rated(Items.LEATHER_BOOTS, ItemRating.NORMAL)) == 1, "leather armor: 1");
            helper.assertTrue(EquipLevels.derivedLevel(rated(Items.BOW, ItemRating.NORMAL)) == 5, "any other material: 5");
        });
        helper.succeed();
    }

    private static boolean tooltipSaysLevel(List<Component> lines, int level) {
        return lines.stream().anyMatch(line -> isLevelLine(line, level));
    }

    /** The level line is a translatable nested inside an indented literal, so the siblings are searched too. */
    private static boolean isLevelLine(Component line, int level) {
        if (line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("tooltip.mitofus.requires_level") && contents.getArgs().length == 1
                && contents.getArgs()[0].equals(level)) {
            return true;
        }
        return line.getSiblings().stream().anyMatch(sibling -> isLevelLine(sibling, level));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theTooltipOfAnOldPieceShowsTheDerivedLevel(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = rated(Items.IRON_SWORD, ItemRating.UNIQUE);
            List<Component> lines = sword.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), player, TooltipFlag.NORMAL);
            helper.assertTrue(tooltipSaysLevel(lines, 15), "the tooltip asks for level 15: " + lines);
            ItemStack plain = new ItemStack(Items.IRON_SWORD);
            helper.assertFalse(tooltipSaysLevel(plain.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), player, TooltipFlag.NORMAL), 5),
                    "a piece with no rating shows no level");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void sellingAnOldPiecePaysTheNewReference(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            // Iron unique (level 15), no options: 30 x 1 x 1,28 = 38,4 -> 38; sold at 40 % = 15.
            ItemStack sword = rated(Items.IRON_SWORD, ItemRating.UNIQUE);
            helper.assertTrue(TradeGoods.reference(sword, B) == 38, "the reference is 38: " + TradeGoods.reference(sword, B));
            player.getInventory().setItem(3, sword.copy());
            long expected = TradeGoods.price(sword, TradeSaturation.EMPTY, 0, B);
            helper.assertTrue(expected == 1520, "the price is 15,20: " + expected);
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0);
            helper.assertTrue(outcome.result() == TradeService.Result.SOLD && outcome.paid() == expected, "paid exactly " + expected + ": " + outcome);
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.balance(player) == expected, "and the purse holds " + expected);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theOptionsAndTheEnhancementRaiseThePriceOfAnyPiece(GameTestHelper helper) {
        defaults(() -> {
            // Diamond chestplate, epic: level 20, 1 option, +3: 7 x 2 x 1,38 x 1,1 x 1,3 = 27,63 -> 27.
            ItemStack armor = rated(Items.DIAMOND_CHESTPLATE, ItemRating.EPIC);
            armor.set(ModDataComponents.ITEM_OPTIONS, List.of(new RolledOption(Mitofus.id("guadalete_agility"), 1f)));
            armor.set(ModDataComponents.ENHANCEMENT, 3);
            helper.assertTrue(TradeGoods.reference(armor, B) == 27, "got " + TradeGoods.reference(armor, B));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aRecipeLevelFarAboveTheDerivedOneIsKeptAndCutsThePrice(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            // Guadalete as its recipe forges it: unique, level 300 and 5000 STR stored, two options.
            ItemStack guadalete = rated(ModItems.GUADALETE_SWORD.get(), ItemRating.UNIQUE);
            guadalete.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(300, Map.of(StatType.STRENGTH, 5000)));
            guadalete.set(ModDataComponents.ITEM_OPTIONS, List.of(new RolledOption(Mitofus.id("guadalete_agility"), 1f),
                    new RolledOption(Mitofus.id("guadalete_bisection"), 1f)));
            EquipRequirements requirements = EquipLevels.requirements(guadalete).orElseThrow();
            helper.assertTrue(requirements.level() == 300 && requirements.stats().equals(Map.of(StatType.STRENGTH, 5000)),
                    "the requirement stays 300 and 5000 STR: " + requirements);
            int derived = EquipLevels.derivedLevel(guadalete);
            helper.assertTrue(derived == 35, "netherite 25 + unique 10 = 35: " + derived);
            helper.assertTrue(TradeRules.impossible(EquipLevels.storedLevel(guadalete), derived, B), "300 is far above 35 + 50");
            // Netherite (x3): 30 x 3 x (1 + 0,02 x 299) x 1,2 x 0,25 = 188,46 -> 188 (no exception for Guadalete any more).
            helper.assertTrue(TradeGoods.reference(guadalete, B) == 188, "the Guadalete is worth 188: " + TradeGoods.reference(guadalete, B));
            // The same piece with a level just on the margin (35 + 50 = 85) is not impossible: 30 x 3 x 2,68 x 1,2 = 289,44 -> 289.
            ItemStack onTheMargin = guadalete.copy();
            onTheMargin.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(85, Map.of()));
            helper.assertFalse(TradeRules.impossible(85, derived, B), "exactly the margin is not impossible");
            helper.assertTrue(TradeGoods.reference(onTheMargin, B) == 289, "no cut on the margin: " + TradeGoods.reference(onTheMargin, B));
            ItemStack overTheMargin = guadalete.copy();
            overTheMargin.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(86, Map.of()));
            helper.assertTrue(TradeRules.impossible(86, derived, B), "one above the margin is impossible");
            // 30 x 3 x 2,7 x 1,2 x 0,25 = 72,9 -> 72.
            helper.assertTrue(TradeGoods.reference(overTheMargin, B) == 72, "cut above the margin: " + TradeGoods.reference(overTheMargin, B));

            atLevel(player, 100);
            helper.assertFalse(EquipmentEvents.meetsRequirements(player, guadalete), "level 100 does not meet 300");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theGuadaleteTheAnvilForgesAsksForLevel300AndIsImpossible(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack sword = GuadaleteTests.forgedGuadalete(helper, smith, ItemRating.UNIQUE);
            EquipRequirements requirements = EquipLevels.requirements(sword).orElseThrow();
            helper.assertTrue(requirements.level() == 300, "the recipe's level 300 is what counts: " + requirements);
            helper.assertTrue(requirements.stats().get(StatType.STRENGTH) == 5000, "and its 5000 STR");
            helper.assertTrue(EquipLevels.derivedLevel(sword) == 35, "derived 35");
            helper.assertTrue(TradeRules.impossible(EquipLevels.storedLevel(sword), EquipLevels.derivedLevel(sword), B), "so it is impossible");
            int options = sword.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of()).size();
            long expected = (long) Math.floor(30 * 3.0 * (1 + 0.02 * 299) * (1 + 0.1 * options) * 0.25 + 1e-9);
            helper.assertTrue(TradeGoods.reference(sword, B) == expected, "price " + expected + " with " + options + " options, got "
                    + TradeGoods.reference(sword, B));
            // The real numbers for the docs: 4 fixed options on a Unique (5 on a Legendary, with Bisection), netherite x3, recipe 300.
            ItemStack unique = GuadaleteTests.forgedGuadalete(helper, smith, ItemRating.UNIQUE);
            helper.assertTrue(unique.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of()).size() == 4, "a Unique Guadalete has 4 options");
            helper.assertTrue(TradeGoods.reference(unique, B) == 219, "Unique Guadalete: 30 x 3 x 6,98 x 1,4 x 0,25 = 219,87, got " + TradeGoods.reference(unique, B));
            ItemStack legendary = GuadaleteTests.forgedGuadalete(helper, smith, ItemRating.LEGENDARY);
            helper.assertTrue(legendary.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of()).size() == 5, "a Legendary Guadalete has 5 options");
            helper.assertTrue(TradeGoods.reference(legendary, B) == 1177, "Legendary Guadalete: 150 x 3 x 6,98 x 1,5 x 0,25 = 1177,87, got " + TradeGoods.reference(legendary, B));
        });
        helper.succeed();
    }
}
