package com.orijua.mitofus.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.AppraisalBalance;
import com.orijua.mitofus.core.config.EquipLevelBalance;
import com.orijua.mitofus.core.config.TerminalBalance;
import com.orijua.mitofus.core.config.TradeBalance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.character.CharacterSnapshot;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.OfferEntry;
import com.orijua.mitofus.feature.economy.OfferRules;
import com.orijua.mitofus.feature.economy.TerminalShop;
import com.orijua.mitofus.feature.economy.TradeGoods;
import com.orijua.mitofus.feature.economy.TradeRules;
import com.orijua.mitofus.feature.economy.TradeSaturation;
import com.orijua.mitofus.feature.economy.TradeService;
import com.orijua.mitofus.feature.equipment.Appraisal;
import com.orijua.mitofus.feature.equipment.AppraisalRules;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.EquipLevels;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.EquipmentBonuses;
import com.orijua.mitofus.feature.equipment.EquipmentEvents;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.equipment.EquipmentTooltip;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.RatingRoller;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.npc.ApprenticeContractItem;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.sancho.Mesnada;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.smithing.SmithingHammers;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The appraisal of equipment: what comes unappraised, what an unappraised piece is and does, and how the Appraiser Eye reveals it. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class AppraisalTests {
    private static final TradeBalance TB = TradeBalance.DEFAULT;
    private static final EquipLevelBalance EL = EquipLevelBalance.DEFAULT;
    private static final TerminalBalance D = TerminalBalance.DEFAULT;
    private static final BlockPos ANVIL = new BlockPos(1, 1, 1);
    private static final BlockPos CHEST = new BlockPos(0, 1, 1);
    private static final BlockPos BARREL = new BlockPos(2, 1, 1);
    private static final BlockPos STAND = new BlockPos(1, 1, 0);

    private AppraisalTests() {
    }

    /** Runs {@code body} with the factory numbers of the appraisal, the levels and the prices, whatever the world's config says. */
    private static void defaults(Runnable body) {
        AppraisalBalance.withOverride(AppraisalBalance.DEFAULT, () ->
                EquipLevelBalance.withOverride(EL, () -> TradeBalance.withOverride(TB, body)));
    }

    private static ItemStack rated(Item item, ItemRating rating) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.ITEM_RATING, rating);
        return stack;
    }

    private static RolledOption option(String id, float value) {
        return new RolledOption(Mitofus.id(id), value);
    }

    /** A piece with 14 data: rating, 6 options, the level, 5 stats and the value. Not unappraised yet. */
    private static ItemStack bigPiece(ItemRating rating) {
        ItemStack stack = rated(Items.IRON_SWORD, rating);
        stack.set(ModDataComponents.ITEM_OPTIONS, List.of(option("sharpness", 2f), option("guadalete_agility", 1f),
                option("sharpness", 3f), option("guadalete_agility", 2f), option("sharpness", 1f), option("guadalete_agility", 3f)));
        stack.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(1, Map.of(StatType.STRENGTH, 10, StatType.STAMINA, 10,
                StatType.AGILITY, 10, StatType.INTELLIGENCE, 10, StatType.DEXTERITY, 10)));
        EquipmentService.refreshName(stack);
        return stack;
    }

    private static ItemStack unappraised(ItemStack stack, long seed) {
        AppraisalService.markUnappraised(stack, RandomSource.create(seed));
        return stack;
    }

    private static void atLevel(ServerPlayer player, int level) {
        ProgressionService.set(player, ProgressionService.get(player).withProgress(level, 0, 0));
    }

    private static int nameColor(ItemStack stack) {
        return stack.get(DataComponents.ITEM_NAME).getStyle().getColor().getValue();
    }

    private static boolean hasLine(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> hasKey(line, key, null));
    }

    private static boolean hasLine(List<Component> lines, String key, Object arg) {
        return lines.stream().anyMatch(line -> hasKey(line, key, arg));
    }

    /** A line may be a translatable nested inside an indented literal, so the siblings are searched too. */
    private static boolean hasKey(Component line, String key, Object arg) {
        if (line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)
                && (arg == null || (contents.getArgs().length >= 1 && contents.getArgs()[0].equals(arg)))) {
            return true;
        }
        return line.getSiblings().stream().anyMatch(sibling -> hasKey(sibling, key, arg));
    }

    private static boolean hasColor(Component line, ChatFormatting color) {
        return line.getStyle().getColor() != null && line.getStyle().getColor().equals(TextColor.fromLegacyFormat(color));
    }

    private static long coloredLines(List<Component> lines, ChatFormatting color) {
        return lines.stream().filter(line -> hasColor(line, color)).count();
    }

    /** The top-level lines that hold a translatable with {@code key}, however deep it is nested in an indented literal. */
    private static List<Component> linesWith(List<Component> lines, String key) {
        return lines.stream().filter(line -> hasKey(line, key, null)).toList();
    }

    /** The lines of the options of {@code stack}: the ones that start with the name of one of its option definitions. */
    private static List<Component> optionLines(GameTestHelper helper, ItemStack stack, List<Component> lines) {
        List<Component> names = new ArrayList<>();
        for (RolledOption rolled : stack.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of())) {
            Definitions.get(helper.getLevel().registryAccess(), ModDatapackRegistries.ITEM_OPTION, rolled.option()).ifPresent(d -> names.add(d.name()));
        }
        return lines.stream().filter(line -> !line.getSiblings().isEmpty() && names.contains(line.getSiblings().get(0))).toList();
    }

    private static List<Component> tooltip(GameTestHelper helper, ItemStack stack, ServerPlayer player) {
        return EquipmentTooltip.lines(stack, helper.getLevel().registryAccess(), player);
    }

    // ---- What comes unappraised ----

    @GameTest(template = TestSupport.EMPTY)
    public static void lootComesUnappraisedWithItsRatingAndOptionsKept(GameTestHelper helper) {
        defaults(() -> {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            EquipmentService.rateLoot(helper.getLevel(), sword, 0, ItemRating.LEGENDARY, RandomSource.create(5));
            helper.assertTrue(sword.has(ModDataComponents.APPRAISAL), "the loot piece is unappraised");
            // rateLoot spends the first double of the random on the rating, so the same roll gives the expected one.
            ItemRating expected = RatingRoller.rollLoot(0, ItemRating.LEGENDARY, RandomSource.create(5).nextDouble());
            helper.assertTrue(sword.get(ModDataComponents.ITEM_RATING) == expected, "it keeps its real rating " + expected + ": " + sword.get(ModDataComponents.ITEM_RATING));
            helper.assertTrue(expected.ordinal() >= ItemRating.LEGENDARY.ordinal(), "and the minimum holds");
            helper.assertTrue(sword.has(ModDataComponents.ITEM_OPTIONS), "and its options");
            helper.assertTrue(nameColor(sword) == ItemRating.NORMAL.color(), "its name shows no rating color");
        });
        helper.succeed();
    }

    /** Selling asks first (with the hidden-power warning) only for a legendary or better piece that is not appraised in full. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aHiddenLegendaryAsksBeforeItIsSold(GameTestHelper helper) {
        defaults(() -> {
            helper.assertTrue(AppraisalService.hidesPower(unappraised(rated(Items.IRON_SWORD, ItemRating.LEGENDARY), 1)), "legendary");
            helper.assertTrue(AppraisalService.hidesPower(unappraised(rated(Items.IRON_SWORD, ItemRating.ANCESTRAL), 2)), "ancestral");
            helper.assertFalse(AppraisalService.hidesPower(unappraised(rated(Items.IRON_SWORD, ItemRating.UNIQUE), 3)), "unique");
            helper.assertFalse(AppraisalService.hidesPower(rated(Items.IRON_SWORD, ItemRating.LEGENDARY)), "an appraised legendary");
            ItemStack done = unappraised(rated(Items.IRON_SWORD, ItemRating.LEGENDARY), 4);
            AppraisalService.appraiseFully(done);
            helper.assertFalse(AppraisalService.hidesPower(done), "nor one appraised in full");
        });
        helper.succeed();
    }
    @GameTest(template = TestSupport.EMPTY)
    public static void whatIsForgedByHandIsAppraised(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            EquipmentService.stampForged(smith, sword, Optional.empty(), 0, 0);
            helper.assertTrue(sword.has(ModDataComponents.ITEM_RATING), "it is rated");
            helper.assertFalse(sword.has(ModDataComponents.APPRAISAL), "a piece forged by hand is appraised");
            ItemStack auto = new ItemStack(Items.IRON_SWORD);
            EquipmentService.stampForged(smith, auto, Optional.empty());
            helper.assertFalse(auto.has(ModDataComponents.APPRAISAL), "so is the automatic forge of the anvil");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anApprenticesPieceComesUnappraised(GameTestHelper helper) {
        defaults(() -> {
            helper.setBlock(ANVIL, ModBlocks.SMITHING_ANVIL.get());
            helper.setBlock(CHEST, Blocks.CHEST);
            helper.setBlock(BARREL, Blocks.BARREL);
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ProgressionService.set(player, PlayerStats.DEFAULT.withSmithingXp(50 * 50 * 10));
            ApprenticeSmith apprentice = ApprenticeContractItem.hire(player, helper.absolutePos(STAND)).orElseThrow();
            helper.assertTrue(apprentice.takeOrder(player, ModItems.WOLFRAM_SWORD.get()), "he can forge the Wolfram sword");
            Container chest = helper.getBlockEntity(CHEST);
            Container barrel = helper.getBlockEntity(BARREL);
            chest.setItem(0, new ItemStack(ModItems.WOLFRAM_INGOT.get(), 4));
            chest.setItem(1, new ItemStack(Items.STICK, 2));
            // A roll of 1.0 is never under his fail chance (at most 30%), so the piece is never botched.
            helper.assertTrue(apprentice.forgeOne(1.0) == ApprenticeSmith.Status.WORKING, "a piece");
            ItemStack sword = barrel.getItem(0);
            helper.assertTrue(sword.has(ModDataComponents.ITEM_RATING), "rated");
            helper.assertTrue(sword.has(ModDataComponents.APPRAISAL), "and unappraised");
            apprentice.discard();
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theTerminalOffersTheSameUnappraisedPieceWithItsRealPrice(GameTestHelper helper) {
        defaults(() -> {
            int size = D.offerPerRank() * (D.offerMaxRating().ordinal() + 1);
            for (int position = 0; position < size; position++) {
                OfferEntry first = TerminalShop.offerEntry(helper.getLevel(), 7L, position, D, TB).orElseThrow();
                OfferEntry second = TerminalShop.offerEntry(helper.getLevel(), 7L, position, D, TB).orElseThrow();
                helper.assertTrue(ItemStack.matches(first.stack(), second.stack()), "the same piece twice at " + position);
                helper.assertTrue(first.stack().has(ModDataComponents.APPRAISAL), "unappraised at " + position);
                long real = OfferRules.price(TradeGoods.trueReferenceValue(first.stack(), TB), D.buyMultiplier(), com.orijua.mitofus.feature.economy.Money.IRON_PER_GOLD);
                helper.assertTrue(first.price() == real && second.price() == real, "the price is the real one at " + position);
            }
        });
        helper.succeed();
    }

    // ---- What an unappraised piece is ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theTooltipHidesWhatIsNotRevealed(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = rated(Items.IRON_SWORD, ItemRating.EPIC);
            sword.set(ModDataComponents.ITEM_OPTIONS, List.of(option("sharpness", 2f), option("guadalete_agility", 1f)));
            sword.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(1, Map.of(StatType.AGILITY, 10)));
            sword.set(ModDataComponents.ENHANCEMENT, 2);
            unappraised(sword, 3L);
            int normal = EquipLevels.normalLevel(Items.IRON_SWORD, EL);

            List<Component> hidden = tooltip(helper, sword, player);
            helper.assertTrue(hasLine(hidden, "tooltip.mitofus.unappraised"), "it says it is unappraised");
            helper.assertFalse(hasLine(hidden, "tooltip.mitofus.rating") || hasLine(hidden, "tooltip.mitofus.rating_bonus"), "no rating line");
            helper.assertTrue(optionLines(helper, sword, hidden).isEmpty(), "no option lines");
            helper.assertTrue(coloredLines(hidden, ChatFormatting.GREEN) == 0, "nothing green");
            helper.assertTrue(hasLine(hidden, "tooltip.mitofus.enhancement"), "the enhancement shows");
            helper.assertTrue(hasLine(hidden, "tooltip.mitofus.requires_level_unappraised", normal), "the level of a Normal piece: " + normal);
            helper.assertFalse(hasLine(hidden, "tooltip.mitofus.requires_level"), "not the real level");
            helper.assertFalse(hasLine(hidden, "tooltip.mitofus.value"), "no value");
            helper.assertTrue(sword.get(DataComponents.ATTRIBUTE_MODIFIERS).equals(new ItemStack(Items.IRON_SWORD).get(DataComponents.ATTRIBUTE_MODIFIERS)),
                    "the vanilla attributes are the item's own");

            AppraisalService.appraiseFully(sword);
            List<Component> shown = tooltip(helper, sword, player);
            helper.assertFalse(hasLine(shown, "tooltip.mitofus.unappraised"), "no unappraised line");
            helper.assertTrue(hasLine(shown, "tooltip.mitofus.rating_bonus") || hasLine(shown, "tooltip.mitofus.rating"), "the rating shows");
            List<Component> shownOptions = optionLines(helper, sword, shown);
            helper.assertTrue(shownOptions.size() == 2, "both options show: " + shownOptions.size());
            helper.assertTrue(shownOptions.stream().allMatch(line -> hasColor(line, ChatFormatting.GREEN)), "in green");
            helper.assertTrue(coloredLines(shown, ChatFormatting.GREEN) == 2, "and nothing else is green");
            helper.assertTrue(hasLine(shown, "tooltip.mitofus.requires_level", EquipLevels.trueRequirements(sword, EL).orElseThrow().level()), "the real level");
            long value = TradeRules.sellPriceIron(TradeGoods.trueReference(sword, TB), 0, TB);
            helper.assertTrue(hasLine(shown, "tooltip.mitofus.value", MoneyText.iron(value)), "the exact value " + value);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPartlyAppraisedPieceShowsWhatIsRevealedAsPending(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            atLevel(player, 100);
            ItemStack sword = bigPiece(ItemRating.EPIC);
            sword.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(30, Map.of(StatType.STRENGTH, 1000)));
            unappraised(sword, 21L);
            long seed = sword.get(ModDataComponents.APPRAISAL).seed();
            // Revealed: the rating, one option, the level, the strength and the value. Hidden: the other five options.
            List<String> revealed = List.of(AppraisalRules.RATING, AppraisalRules.optionKey(0), AppraisalRules.LEVEL,
                    AppraisalRules.statKey(StatType.STRENGTH.getSerializedName()), AppraisalRules.VALUE);
            sword.set(ModDataComponents.APPRAISAL, new Appraisal(seed, revealed));
            for (String key : revealed) {
                helper.assertTrue(AppraisalService.keys(sword).contains(key), "the piece has the datum " + key);
            }
            helper.assertTrue(AppraisalService.isUnappraised(sword), "still unappraised");

            List<Component> partial = tooltip(helper, sword, player);
            helper.assertTrue(hasLine(partial, "tooltip.mitofus.partly_appraised"), "it says it is partly appraised");
            List<Component> rating = linesWith(partial, "tooltip.mitofus.rating");
            helper.assertTrue(rating.size() == 1 && hasColor(rating.get(0), ChatFormatting.DARK_GRAY), "the rating, dimmed: " + rating);
            helper.assertFalse(hasLine(partial, "tooltip.mitofus.rating_bonus"), "without the damage that is not applied");
            helper.assertTrue(coloredLines(partial, ChatFormatting.GREEN) == 0, "no green line");
            List<Component> options = optionLines(helper, sword, partial);
            helper.assertTrue(options.size() == 1 && hasColor(options.get(0), ChatFormatting.DARK_GRAY), "the revealed option, dimmed: " + options);
            List<Component> level = linesWith(partial, "tooltip.mitofus.requires_level_appraised");
            List<Component> stat = linesWith(partial, "tooltip.mitofus.requires_stat_appraised");
            helper.assertTrue(level.size() == 1 && hasColor(level.get(0), ChatFormatting.DARK_GRAY), "the future level, dimmed: " + level);
            helper.assertTrue(stat.size() == 1 && hasColor(stat.get(0), ChatFormatting.DARK_GRAY), "the future strength, dimmed: " + stat);
            helper.assertTrue(coloredLines(partial, ChatFormatting.RED) == 0, "nothing red: the strength 1000 only counts once appraised");
            long value = TradeRules.sellPriceIron(TradeGoods.trueReference(sword, TB), 0, TB);
            helper.assertTrue(hasLine(partial, "tooltip.mitofus.value_unappraised", MoneyText.iron(value)), "the real value " + value);
            helper.assertFalse(hasLine(partial, "tooltip.mitofus.value"), "and not as the value of an appraised piece");

            AppraisalService.appraiseFully(sword);
            List<Component> full = tooltip(helper, sword, player);
            helper.assertTrue(hasLine(full, "tooltip.mitofus.value", MoneyText.iron(value)), "appraised: the same value");
            helper.assertFalse(hasLine(full, "tooltip.mitofus.value_unappraised"), "no pending value");
            helper.assertTrue(hasLine(full, "tooltip.mitofus.rating_bonus") || hasLine(full, "tooltip.mitofus.rating"), "the rating");
            List<Component> fullOptions = optionLines(helper, sword, full);
            helper.assertTrue(fullOptions.size() == 6 && fullOptions.stream().allMatch(line -> hasColor(line, ChatFormatting.GREEN)),
                    "the options in green: " + fullOptions.size());
            helper.assertTrue(coloredLines(full, ChatFormatting.DARK_GRAY) == 0, "nothing dimmed");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anUnappraisedPieceGivesOnlyItsBaseStats(GameTestHelper helper) {
        defaults(() -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setData(ModAttachments.PLAYER_STATS, player.getData(ModAttachments.PLAYER_STATS).withProgress(15, 0, 0));
            ItemStack sword = rated(Items.IRON_SWORD, ItemRating.UNIQUE);
            sword.set(ModDataComponents.ITEM_OPTIONS, List.of(option("sharpness", 2f)));
            sword.set(ModDataComponents.ENHANCEMENT, 3);
            unappraised(sword, 1L);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            EquipmentBonuses.apply(player);
            AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(damage.getModifier(Mitofus.id("equip_mainhand_rating")) == null, "no rating bonus");
            helper.assertTrue(damage.getModifier(Mitofus.id("equip_mainhand_option_0")) == null, "no option bonus");
            helper.assertTrue(damage.getModifier(Mitofus.id("equip_mainhand_enhancement")) != null, "the enhancement is active");

            AppraisalService.appraiseFully(sword);
            EquipmentBonuses.apply(player);
            helper.assertTrue(damage.getModifier(Mitofus.id("equip_mainhand_rating")) != null, "the rating bonus appears once appraised");
            helper.assertTrue(damage.getModifier(Mitofus.id("equip_mainhand_option_0")) != null, "and so does the option");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anUnappraisedPieceAsksForTheLevelOfANormalOne(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            int normal = EquipLevels.normalLevel(Items.IRON_SWORD, EL);
            ItemStack legendary = rated(Items.IRON_SWORD, ItemRating.LEGENDARY);
            legendary.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(1, Map.of(StatType.STRENGTH, 1000)));
            unappraised(legendary, 2L);
            atLevel(player, normal);
            helper.assertTrue(EquipmentEvents.meetsRequirements(player, legendary), "a player at the Normal level can use it unappraised");
            AppraisalService.appraiseFully(legendary);
            helper.assertFalse(EquipmentEvents.meetsRequirements(player, legendary), "not once it is appraised");
            ItemStack another = unappraised(rated(Items.IRON_SWORD, ItemRating.LEGENDARY), 3L);
            atLevel(player, normal - 1);
            helper.assertFalse(EquipmentEvents.meetsRequirements(player, another), "below the Normal level it cannot be used");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPartlyAppraisedPieceStillCountsAsNormal(GameTestHelper helper) {
        defaults(() -> {
            ItemStack sword = bigPiece(ItemRating.EPIC);
            unappraised(sword, 4L);
            ItemStack untouched = sword.copy();
            AppraisalRules.Step step = AppraisalService.revealNext(sword, 1);
            helper.assertTrue(step.newlyRevealed().equals(List.of("rating")) && !step.complete(), "only the rating: " + step);
            helper.assertTrue(AppraisalService.isUnappraised(sword), "still unappraised");
            helper.assertTrue(AppraisalService.activeOptions(sword).isEmpty(), "no active options");
            helper.assertTrue(AppraisalService.activeRating(sword) == ItemRating.NORMAL, "it counts as Normal");
            helper.assertTrue(AppraisalService.visibleRating(sword).orElseThrow() == ItemRating.EPIC, "but its rating is visible");
            helper.assertTrue(nameColor(sword) == ItemRating.EPIC.color(), "and its name has the rating color");
            helper.assertTrue(TradeGoods.key(sword).rating() == ItemRating.NORMAL, "it is traded as Normal");
            helper.assertTrue(TradeGoods.reference(sword, TB) == TradeGoods.reference(untouched, TB), "at the price of the unappraised one");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aPieceWithItsLevelAndValueRevealedStillCountsAsNormal(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = bigPiece(ItemRating.EPIC);
            sword.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(30, Map.of(StatType.STRENGTH, 1000)));
            unappraised(sword, 15L);
            ItemStack untouched = sword.copy();
            long seed = sword.get(ModDataComponents.APPRAISAL).seed();
            sword.set(ModDataComponents.APPRAISAL, new Appraisal(seed,
                    List.of(AppraisalRules.RATING, AppraisalRules.LEVEL, AppraisalRules.VALUE)));
            helper.assertTrue(AppraisalService.isRevealed(sword, AppraisalRules.LEVEL) && AppraisalService.isRevealed(sword, AppraisalRules.VALUE),
                    "the level and the value are revealed");
            helper.assertTrue(AppraisalService.isUnappraised(sword), "still unappraised");

            int normal = EquipLevels.normalLevel(Items.IRON_SWORD, EL);
            atLevel(player, normal);
            helper.assertTrue(EquipmentEvents.meetsRequirements(player, sword), "the requirement in force is the Normal level, not the revealed 30 and 1000");
            atLevel(player, normal - 1);
            helper.assertFalse(EquipmentEvents.meetsRequirements(player, sword), "and one level below it is not met");
            helper.assertTrue(TradeGoods.reference(sword, TB) == TradeGoods.reference(untouched, TB), "it is still sold as the unappraised one");
            helper.assertTrue(TradeGoods.trueReference(sword, TB) > TradeGoods.reference(sword, TB), "its true reference is higher");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theLevelOfANormalPieceFollowsTheItem(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            int iron = EquipLevels.normalLevel(Items.IRON_SWORD, EL);
            int diamond = EquipLevels.normalLevel(Items.DIAMOND_SWORD, EL);
            helper.assertTrue(iron != diamond, "iron and diamond have a different Normal level: " + iron + " and " + diamond);
            ItemStack sword = rated(Items.DIAMOND_SWORD, ItemRating.LEGENDARY);
            sword.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(1, Map.of(StatType.STRENGTH, 1000)));
            unappraised(sword, 16L);
            atLevel(player, diamond);
            helper.assertTrue(EquipmentEvents.meetsRequirements(player, sword), "met at the Normal level of a diamond piece");
            atLevel(player, diamond - 1);
            helper.assertFalse(EquipmentEvents.meetsRequirements(player, sword), "not one level below it");
            atLevel(player, iron);
            helper.assertTrue(EquipmentEvents.meetsRequirements(player, sword) == (iron >= diamond), "the iron level is not the one asked: " + iron);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anUnappraisedPieceCanBeEnhanced(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack sword = unappraised(rated(Items.IRON_SWORD, ItemRating.EPIC), 5L);
            helper.assertTrue(EquipmentService.enhance(smith, sword, 0.0) == EquipmentService.EnhanceResult.SUCCESS, "it is enhanced");
            helper.assertTrue(AppraisalService.isUnappraised(sword), "still unappraised");
            helper.assertTrue(sword.get(DataComponents.ITEM_NAME).getString().endsWith(" +1"), "its name says +1");
            helper.assertTrue(nameColor(sword) == ItemRating.NORMAL.color(), "in white");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anUnappraisedWeaponGivesNoSkillUntilAppraised(GameTestHelper helper) {
        defaults(() -> {
            ItemStack guadalete = rated(ModItems.GUADALETE_SWORD.get(), ItemRating.LEGENDARY);
            guadalete.set(ModDataComponents.ITEM_OPTIONS, List.of(option("guadalete_bisection", 1f)));
            unappraised(guadalete, 6L);
            helper.assertTrue(SkillService.weaponSkill(guadalete).isEmpty(), "no skill while unappraised");
            AppraisalService.appraiseFully(guadalete);
            helper.assertTrue(SkillService.weaponSkill(guadalete).isPresent(), "the skill once appraised");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anUnappraisedHammerIsAPlainOne(GameTestHelper helper) {
        defaults(() -> {
            var registries = helper.getLevel().registryAccess();
            ItemStack hammer = rated(ModItems.WOLFRAM_SMITHING_HAMMER.get(), ItemRating.RARE);
            hammer.set(ModDataComponents.ITEM_OPTIONS, List.of(option("hammer_precision", 3f)));
            unappraised(hammer, 7L);
            helper.assertTrue(hammer.has(ModDataComponents.APPRAISAL), "the hammer can be unappraised");
            double plain = SmithingHammers.skillBonus(registries, rated(ModItems.WOLFRAM_SMITHING_HAMMER.get(), ItemRating.NORMAL));
            helper.assertTrue(SmithingHammers.skillBonus(registries, hammer) == plain, "as a Normal hammer with no options: " + plain);
            AppraisalService.appraiseFully(hammer);
            helper.assertTrue(SmithingHammers.skillBonus(registries, hammer) > plain, "more once appraised");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anUnappraisedPieceIsSoldAsANormalOne(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = rated(Items.DIAMOND_SWORD, ItemRating.LEGENDARY);
            player.getInventory().clearContent();
            sword.set(ModDataComponents.ITEM_OPTIONS, List.of(option("sharpness", 2f), option("guadalete_agility", 1f)));
            sword.set(ModDataComponents.ENHANCEMENT, 2);
            sword.setDamageValue(100);
            unappraised(sword, 8L);
            long expected = TradeRules.reference(ItemRating.NORMAL, TradeGoods.material(sword, TB), EquipLevels.normalLevel(Items.DIAMOND_SWORD, EL), 0, 2,
                    false, TradeGoods.durability(sword, TB), TB);
            long reference = TradeGoods.reference(sword, TB);
            helper.assertTrue(reference == expected, "the reference of a Normal one (enhancement and durability count): " + reference + " vs " + expected);
            helper.assertTrue(reference < TradeGoods.trueReference(sword, TB), "less than its true reference");

            player.getInventory().setItem(3, sword.copy());
            long price = TradeGoods.price(sword, TradeSaturation.EMPTY, 0, TB);
            TradeService.Outcome outcome = TradeService.sell(player, 3, 0);
            helper.assertTrue(outcome.result() == TradeService.Result.SOLD && outcome.paid() == price, "sold for exactly " + price + ": " + outcome);
            helper.assertTrue(player.getInventory().getItem(3).isEmpty(), "the piece left its slot");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_SWORD) == 0, "and it is nowhere in the inventory");
            helper.assertTrue(com.orijua.mitofus.feature.economy.Wallet.balance(player) == price, "the purse holds exactly " + price + " iron");

            AppraisalService.appraiseFully(sword);
            helper.assertTrue(TradeGoods.reference(sword, TB) == TradeGoods.trueReference(sword, TB), "appraised: the true reference");
        });
        helper.succeed();
    }

    // ---- The Eye ----

    private static ItemStack eye(ServerPlayer player, ItemStack other) {
        ItemStack eye = new ItemStack(ModItems.APPRAISER_EYE.get(), 5);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, eye);
        player.setItemInHand(InteractionHand.OFF_HAND, other);
        return eye;
    }

    private static InteractionResultHolder<ItemStack> useEye(GameTestHelper helper, ServerPlayer player) {
        return ModItems.APPRAISER_EYE.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeRevealsSixToTenDataPerUseAndCompletesTheAppraisal(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack piece = unappraised(bigPiece(ItemRating.LEGENDARY), 11L);
            long seed = piece.get(ModDataComponents.APPRAISAL).seed();
            helper.assertTrue(AppraisalService.keys(piece).size() == 14, "14 data: " + AppraisalService.keys(piece));
            ItemStack eye = eye(player, piece);

            helper.assertTrue(useEye(helper, player).getResult().consumesAction(), "the first use works");
            helper.assertTrue(eye.getCount() == 4, "it spends one Eye");
            Appraisal first = piece.get(ModDataComponents.APPRAISAL);
            helper.assertTrue(first.revealed().size() == AppraisalRules.amount(seed, 0, 6, 10), "the amount fixed by the seed: " + first.revealed());
            helper.assertTrue(first.revealed().contains("rating"), "the rating goes first");
            helper.assertTrue(player.getCooldowns().isOnCooldown(eye.getItem()), "the Eye is on cooldown");

            int uses = 1;
            while (piece.has(ModDataComponents.APPRAISAL)) {
                player.getCooldowns().removeCooldown(eye.getItem());
                helper.assertTrue(useEye(helper, player).getResult().consumesAction(), "use " + (uses + 1));
                helper.assertTrue(++uses <= 3, "no more than 3 uses");
            }
            helper.assertTrue(eye.getCount() == 5 - uses, "one Eye per use");
            helper.assertTrue(nameColor(piece) == ItemRating.LEGENDARY.color(), "the name takes the rating color");
            helper.assertFalse(AppraisalService.isUnappraised(piece), "appraised");
            helper.assertTrue(AppraisalService.activeOptions(piece).size() == 6, "and its options count");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeFailsWithNothingToAppraise(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack eye = eye(player, ItemStack.EMPTY);
            helper.assertTrue(useEye(helper, player).getResult() == InteractionResult.FAIL, "nothing to appraise");
            helper.assertTrue(eye.getCount() == 5 && !player.getCooldowns().isOnCooldown(eye.getItem()), "the Eye is intact");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeFailsWithAnAppraisedPieceAndNoBook(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack appraised = rated(Items.IRON_SWORD, ItemRating.EPIC);
            ItemStack before = appraised.copy();
            ItemStack eye = eye(player, appraised);
            helper.assertTrue(useEye(helper, player).getResult() == InteractionResult.FAIL, "an appraised piece is nothing to appraise");
            helper.assertTrue(eye.getCount() == 5 && !player.getCooldowns().isOnCooldown(eye.getItem()), "the Eye is intact");
            helper.assertTrue(ItemStack.matches(appraised, before), "and so is the piece");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeAppraisesTheBookWhenThePieceIsAppraised(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack eye = eye(player, rated(Items.IRON_SWORD, ItemRating.EPIC));
            player.getInventory().add(new ItemStack(ModItems.UNAPPRAISED_BOOK.get()));
            helper.assertTrue(useEye(helper, player).getResult().consumesAction(), "it appraises the book");
            helper.assertTrue(player.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 1, "the book is now Sancho's rare book");
            helper.assertTrue(player.getInventory().countItem(ModItems.UNAPPRAISED_BOOK.get()) == 0, "and the unappraised one is gone");
            helper.assertTrue(eye.getCount() == 4, "it spends one Eye: " + eye.getCount());
            retinue(helper, player).forEach(Mesnada::discard);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeAppraisesThePieceBeforeTheBook(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack piece = unappraised(bigPiece(ItemRating.EPIC), 12L);
            long seed = piece.get(ModDataComponents.APPRAISAL).seed();
            ItemStack eye = eye(player, piece);
            player.getInventory().add(new ItemStack(ModItems.UNAPPRAISED_BOOK.get()));
            helper.assertTrue(useEye(helper, player).getResult().consumesAction(), "it appraises the piece");
            helper.assertTrue(piece.get(ModDataComponents.APPRAISAL).revealed().size() == AppraisalRules.amount(seed, 0, 6, 10),
                    "the amount fixed by the seed: " + piece.get(ModDataComponents.APPRAISAL).revealed());
            helper.assertTrue(eye.getCount() == 4, "it spends one Eye: " + eye.getCount());
            helper.assertTrue(player.getInventory().countItem(ModItems.UNAPPRAISED_BOOK.get()) == 1, "the book is still unappraised");
            helper.assertTrue(player.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 0, "and not Sancho's book");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeIgnoresAnUnappraisedPieceInTheInventory(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack eye = eye(player, ItemStack.EMPTY);
            ItemStack piece = unappraised(bigPiece(ItemRating.EPIC), 17L);
            player.getInventory().add(piece);
            ItemStack before = piece.copy();
            helper.assertTrue(useEye(helper, player).getResult() == InteractionResult.FAIL, "only the other hand and the books count");
            helper.assertTrue(ItemStack.matches(piece, before), "the piece in the inventory is intact");
            helper.assertTrue(eye.getCount() == 5 && !player.getCooldowns().isOnCooldown(eye.getItem()), "and so is the Eye");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeWorksFromTheOffHand(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack mainPiece = unappraised(bigPiece(ItemRating.EPIC), 13L);
            long seed = mainPiece.get(ModDataComponents.APPRAISAL).seed();
            player.getInventory().clearContent();
            ItemStack offEye = new ItemStack(ModItems.APPRAISER_EYE.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, mainPiece);
            player.setItemInHand(InteractionHand.OFF_HAND, offEye);
            helper.assertTrue(ModItems.APPRAISER_EYE.get().use(helper.getLevel(), player, InteractionHand.OFF_HAND).getResult().consumesAction(),
                    "the Eye in the off hand");
            helper.assertTrue(offEye.getCount() == 1, "it spent one Eye: " + offEye.getCount());
            helper.assertTrue(mainPiece.get(ModDataComponents.APPRAISAL).revealed().size() == AppraisalRules.amount(seed, 0, 6, 10),
                    "it appraised the main hand piece with the amount of the seed");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeCooldownLastsTheConfiguredTicks(GameTestHelper helper) {
        defaults(() -> AppraisalBalance.withOverride(new AppraisalBalance(6, 10, 40), () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack eye = eye(player, unappraised(bigPiece(ItemRating.EPIC), 18L));
            helper.assertTrue(useEye(helper, player).getResult().consumesAction(), "the use works");
            helper.assertTrue(player.getCooldowns().isOnCooldown(eye.getItem()), "the Eye is on cooldown");
            helper.assertTrue(player.getCooldowns().getCooldownPercent(eye.getItem(), 0f) > 0f, "with time left");
            for (int tick = 0; tick < 39; tick++) {
                player.getCooldowns().tick();
            }
            helper.assertTrue(player.getCooldowns().isOnCooldown(eye.getItem()), "still on cooldown after 39 ticks");
            player.getCooldowns().tick();
            helper.assertFalse(player.getCooldowns().isOnCooldown(eye.getItem()), "free after 40 ticks");
        }));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theEyeHasNoCooldownWhenItIsConfiguredToZero(GameTestHelper helper) {
        defaults(() -> AppraisalBalance.withOverride(new AppraisalBalance(6, 10, 0), () -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack eye = eye(player, unappraised(bigPiece(ItemRating.EPIC), 19L));
            helper.assertTrue(useEye(helper, player).getResult().consumesAction(), "the use works");
            helper.assertFalse(player.getCooldowns().isOnCooldown(eye.getItem()), "no cooldown at 0 ticks");
        }));
        helper.succeed();
    }

    private static List<Mesnada> retinue(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(Mesnada.class, new AABB(player.blockPosition()).inflate(12), Mesnada::isSummoned);
    }

    // ---- Saving, syncing and keeping ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theAppraisalIsSavedAndSynced(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        var registries = player.registryAccess();
        List<Appraisal> cases = List.of(
                new Appraisal(123456789012345L, List.of("rating", "option:0")),
                new Appraisal(0L, List.of()),
                new Appraisal(-1L, List.of("rating")),
                new Appraisal(Long.MIN_VALUE, List.of("rating", "option:0", "value")));
        for (Appraisal appraisal : cases) {
            ItemStack stack = rated(Items.IRON_SWORD, ItemRating.EPIC);
            stack.set(ModDataComponents.APPRAISAL, appraisal);
            Tag saved = stack.save(registries);
            ItemStack back = ItemStack.parse(registries, saved).orElseThrow();
            helper.assertTrue(ItemStack.matches(stack, back) && appraisal.equals(back.get(ModDataComponents.APPRAISAL)), "saved and read back: " + appraisal);

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            Appraisal.STREAM_CODEC.encode(buf, appraisal);
            helper.assertTrue(Appraisal.STREAM_CODEC.decode(buf).equals(appraisal), "the stream codec round trip: " + appraisal);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anUnappraisedPieceStaysUnappraisedThroughTheVrHelmetSnapshot(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack partial = unappraised(bigPiece(ItemRating.EPIC), 14L);
            AppraisalService.revealNext(partial, 1);
            ItemStack untouched = unappraised(bigPiece(ItemRating.RARE), 15L);
            helper.assertTrue(partial.get(ModDataComponents.APPRAISAL).revealed().size() == 1, "the partial piece has one datum revealed");
            helper.assertTrue(untouched.get(ModDataComponents.APPRAISAL).revealed().isEmpty(), "the other has none");
            Appraisal partialBefore = partial.get(ModDataComponents.APPRAISAL);
            Appraisal untouchedBefore = untouched.get(ModDataComponents.APPRAISAL);
            player.getInventory().clearContent();
            player.getInventory().setItem(4, partial.copy());
            player.getInventory().setItem(5, untouched.copy());

            // The real flow of the VR helmet: capture the character, empty the inventory and apply what was captured.
            CompoundTag snapshot = CharacterSnapshot.capture(player);
            player.getInventory().clearContent();
            helper.assertTrue(player.getInventory().getItem(4).isEmpty(), "the inventory was emptied");
            CharacterSnapshot.apply(player, snapshot);

            ItemStack backPartial = player.getInventory().getItem(4);
            ItemStack backUntouched = player.getInventory().getItem(5);
            helper.assertTrue(ItemStack.matches(backPartial, partial), "the partial piece comes back as it was");
            helper.assertTrue(partialBefore.equals(backPartial.get(ModDataComponents.APPRAISAL)), "with the same seed and revealed data");
            helper.assertTrue(ItemStack.matches(backUntouched, untouched), "the untouched piece comes back as it was");
            helper.assertTrue(untouchedBefore.equals(backUntouched.get(ModDataComponents.APPRAISAL)), "still with nothing revealed");
        });
        helper.succeed();
    }

    // ---- Debug commands ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theDebugCommandsDriveTheAppraisal(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack piece = bigPiece(ItemRating.EPIC);
            player.setItemInHand(InteractionHand.MAIN_HAND, piece);
            run(helper, player, "mitofus appraisal unappraise");
            helper.assertTrue(piece.has(ModDataComponents.APPRAISAL), "unappraise marks the piece");
            run(helper, player, "mitofus appraisal reveal 3");
            helper.assertTrue(piece.get(ModDataComponents.APPRAISAL).revealed().size() == 3, "reveal 3 shows three data");
            run(helper, player, "mitofus appraisal info");
            helper.assertTrue(piece.has(ModDataComponents.APPRAISAL), "info changes nothing");
            run(helper, player, "mitofus appraisal complete");
            helper.assertFalse(piece.has(ModDataComponents.APPRAISAL), "complete appraises it in full");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            try {
                run(helper, player, "mitofus appraisal unappraise");
                helper.fail("a block is not equipment");
            } catch (RuntimeException expected) {
                helper.assertTrue(expected.getCause() instanceof CommandSyntaxException, "refused: " + expected);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDebugUnappraiseAndCompleteRefreshTheEffectsAtOnce(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            atLevel(player, 50);
            ItemStack sword = rated(Items.IRON_SWORD, ItemRating.UNIQUE);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            ProgressionService.refreshEffects(player);
            AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            ResourceLocation rating = Mitofus.id("equip_mainhand_rating");
            helper.assertTrue(damage.getModifier(rating) != null, "an appraised Unique piece gives its rating bonus");

            run(helper, player, "mitofus appraisal unappraise");
            helper.assertTrue(damage.getModifier(rating) == null, "unappraise removes the bonus in the same tick");
            run(helper, player, "mitofus appraisal complete");
            helper.assertTrue(damage.getModifier(rating) != null, "complete brings it back");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theDebugAppraisalCommandsHandleTheirEdgeCases(GameTestHelper helper) {
        defaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack piece = bigPiece(ItemRating.EPIC);
            player.setItemInHand(InteractionHand.MAIN_HAND, piece);

            // Without permission the command does not exist for the player.
            expectSyntaxError(helper, player, 0, "mitofus appraisal unappraise", "no permission");
            helper.assertFalse(piece.has(ModDataComponents.APPRAISAL), "and the piece was not touched");

            // Unappraising a piece that is already unappraised with data revealed starts it over.
            run(helper, player, "mitofus appraisal unappraise");
            run(helper, player, "mitofus appraisal reveal 3");
            helper.assertTrue(piece.get(ModDataComponents.APPRAISAL).revealed().size() == 3, "three data revealed");
            run(helper, player, "mitofus appraisal unappraise");
            helper.assertTrue(piece.has(ModDataComponents.APPRAISAL) && piece.get(ModDataComponents.APPRAISAL).revealed().isEmpty(),
                    "unappraised again with nothing revealed");

            // Completing an appraised piece neither fails nor marks it.
            run(helper, player, "mitofus appraisal complete");
            helper.assertFalse(piece.has(ModDataComponents.APPRAISAL), "appraised");
            run(helper, player, "mitofus appraisal complete");
            helper.assertFalse(piece.has(ModDataComponents.APPRAISAL), "completing again changes nothing");

            // The number of data to reveal goes from 1 to the most a piece can have.
            expectSyntaxError(helper, player, 2, "mitofus appraisal reveal 0", "reveal 0");
            expectSyntaxError(helper, player, 2, "mitofus appraisal reveal " + (AppraisalBalance.MAX_DATA + 1), "reveal above the maximum");
        });
        helper.succeed();
    }

    private static void expectSyntaxError(GameTestHelper helper, ServerPlayer player, int permission, String command, String what) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(permission);
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException expected) {
            return;
        }
        helper.fail(what + " must be refused: " + command);
    }

    private static void run(GameTestHelper helper, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(2);
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(command, e);
        }
    }

}
