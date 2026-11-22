package com.orijua.mitofus.gametest;

import com.orijua.mitofus.core.config.ApprenticeBalance;
import com.orijua.mitofus.core.config.SmithShopBalance;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.EnhanceBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.EnhanceMaterial;
import com.orijua.mitofus.feature.equipment.EquipmentRules;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.equipment.EquipmentService.EnhanceOutcome;
import com.orijua.mitofus.feature.equipment.EquipmentService.EnhanceResult;
import com.orijua.mitofus.feature.equipment.EquipmentTooltip;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.LootEvents;
import com.orijua.mitofus.feature.mob.BoneLord;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.npc.SmithShop;
import com.orijua.mitofus.feature.smithing.SmithingAnvilMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A failed enhancement loses levels (3 with a normal stone, 1 blessed, 0 with the blessed scroll), armor can be enhanced, the
 * ancient scroll never fails, on any piece, and is the only sure way to enhance a Ancestral one, the anvil spends the material only when the attempt happens, Smith sells the new materials and the
 * Bone Lord can drop an ancient scroll.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class EnhanceFailureTests {
    private static final double FAIL_ROLL = 0.999;

    private EnhanceFailureTests() {
    }

    private static ItemStack sword(int level) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        if (level > 0) {
            sword.set(ModDataComponents.ENHANCEMENT, level);
        }
        return sword;
    }

    private static ItemStack ancestralSword(int level) {
        ItemStack sword = sword(level);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.ANCESTRAL);
        return sword;
    }

    private static int level(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ENHANCEMENT, 0);
    }

    private static SmithingAnvilMenu anvil(GameTestHelper helper, ServerPlayer player, ItemStack piece, ItemStack material) {
        SmithingAnvilMenu menu = new SmithingAnvilMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
        menu.getSlot(SmithingAnvilMenu.TOP_SLOT).set(piece);
        menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).set(material);
        return menu;
    }

    // ---- 1. Failure by material ----

    @GameTest(template = TestSupport.EMPTY)
    public static void aFailureLosesLevelsByMaterial(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack a = sword(5);
            EnhanceOutcome out = EquipmentService.enhanceWith(smith, a, EnhanceMaterial.WEAPON_STONE, FAIL_ROLL, 0.0);
            helper.assertTrue(out.result() == EnhanceResult.FAILURE && out.before() == 5 && out.after() == 2 && level(a) == 2, "normal stone: +5 to +2, " + out);
            ItemStack b = sword(5);
            EnhanceOutcome outB = EquipmentService.enhanceWith(smith, b, EnhanceMaterial.BLESSED_WEAPON_STONE, FAIL_ROLL, 0.0);
            helper.assertTrue(outB.result() == EnhanceResult.FAILURE && level(b) == 4, "blessed stone: +5 to +4, got " + level(b) + ", " + outB);
            ItemStack c = sword(5);
            EnhanceOutcome outC = EquipmentService.enhanceWith(smith, c, EnhanceMaterial.BLESSED_SCROLL, FAIL_ROLL, 0.0);
            helper.assertTrue(outC.result() == EnhanceResult.FAILURE && level(c) == 5, "blessed scroll: stays +5, got " + level(c) + ", " + outC);

            // The message names the piece without its "+N", and a custom name wins.
            ItemStack named = sword(3);
            EquipmentService.refreshName(named);
            helper.assertFalse(EquipmentService.baseName(named).getString().contains("+"), "the base name has no +N: " + EquipmentService.baseName(named).getString());
            named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Excalibur"));
            helper.assertTrue(EquipmentService.baseName(named).getString().equals("Excalibur"), "a custom name is kept");

            ItemStack low = sword(2);
            EquipmentService.refreshName(low);
            helper.assertTrue(low.getHoverName().getString().endsWith("+2"), "named +2: " + low.getHoverName().getString());
            EquipmentService.enhanceWith(smith, low, EnhanceMaterial.WEAPON_STONE, FAIL_ROLL, 0.0);
            helper.assertTrue(level(low) == 0 && !low.has(ModDataComponents.ENHANCEMENT), "+2 with a normal stone falls to +0 with no component");
            helper.assertFalse(low.getHoverName().getString().contains("+"), "an unrated sword loses the + in its name: " + low.getHoverName().getString());
        });
        helper.succeed();
    }

    // ---- 2. Armor ----

    @GameTest(template = TestSupport.EMPTY)
    public static void armorIsEnhancedWithItsOwnMaterials(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            helper.assertTrue(EquipmentService.canEnhance(new ItemStack(Items.IRON_CHESTPLATE)), "a chestplate can be enhanced");
            helper.assertFalse(EquipmentService.canEnhance(new ItemStack(Items.WOLF_ARMOR)), "wolf armor can't");
            helper.assertFalse(EquipmentService.canEnhance(new ItemStack(Items.IRON_HORSE_ARMOR)), "horse armor can't");

            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
            EnhanceOutcome wrong = EquipmentService.enhanceWith(smith, chest, EnhanceMaterial.WEAPON_STONE, 0.0, 0.0);
            helper.assertTrue(wrong.result() == EnhanceResult.WRONG_MATERIAL && level(chest) == 0, "a weapon stone on a chestplate: " + wrong);
            ItemStack blade = sword(0);
            EnhanceOutcome wrong2 = EquipmentService.enhanceWith(smith, blade, EnhanceMaterial.ARMOR_STONE, 0.0, 0.0);
            helper.assertTrue(wrong2.result() == EnhanceResult.WRONG_MATERIAL && level(blade) == 0, "an armor stone on a sword: " + wrong2);

            ItemStack plus4 = new ItemStack(Items.IRON_CHESTPLATE);
            plus4.set(ModDataComponents.ENHANCEMENT, 4);
            // Iron chestplate base armor 6: 6 * (1.05^4 - 1).
            TestSupport.assertClose(helper, 1.2930375, TestSupport.armorBonusOver(helper, plus4), "+4 chestplate");
            helper.assertTrue(tooltipHas(EquipmentTooltip.lines(plus4, null, null), "tooltip.mitofus.enhancement_armor"), "the armor tooltip uses the armor line");
            ItemStack plus3 = sword(3);
            TestSupport.assertClose(helper, 0.788125, TestSupport.attackBonusOver(helper, plus3), "+3 sword still gives attack");
            List<Object> args = tooltipArgs(EquipmentTooltip.lines(plus3, null, null), "tooltip.mitofus.enhancement");
            helper.assertTrue(args != null, "the sword tooltip keeps the damage line");
            helper.assertTrue(args.size() == 3 && "3".equals(String.valueOf(args.get(0))) && "0.79".equals(String.valueOf(args.get(1)))
                    // 1.05^3 - 1 = 15.76 %: at 10 % or more it has no decimals, and the "%" sign comes with the text.
                    && "16%".equals(String.valueOf(args.get(2))), "the sword tooltip args are (3, 0.79, 16%): " + args);
        });
        helper.succeed();
    }

    private static List<Object> tooltipArgs(List<Component> lines, String key) {
        return lines.stream().map(Component::getContents)
                .filter(c -> c instanceof TranslatableContents t && t.getKey().equals(key))
                .map(c -> List.of(((TranslatableContents) c).getArgs()))
                .findFirst().orElse(null);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void anEnhancedHammerShowsThePlainLineAndGivesNoAttack(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ItemStack hammer = new ItemStack(ModItems.IRON_SMITHING_HAMMER.get());
            hammer.set(ModDataComponents.ENHANCEMENT, 3);
            List<Component> lines = EquipmentTooltip.lines(hammer, null, null);
            helper.assertTrue(tooltipHas(lines, "tooltip.mitofus.enhancement_plain"), "the hammer uses the plain enhancement line");
            helper.assertFalse(tooltipHas(lines, "tooltip.mitofus.enhancement"), "and not the damage line");
            helper.assertFalse(tooltipHas(lines, "tooltip.mitofus.enhancement_armor"), "nor the armor line");
            // Positive control: the meter does see the enhancement bonus of a weapon.
            TestSupport.assertClose(helper, 0.788125, TestSupport.attackBonusOver(helper, sword(3)), "a +3 sword gives attack");
            // Both are measured against a plain sword, so an enhanced hammer must equal a plain one.
            TestSupport.assertClose(helper, TestSupport.attackBonusOver(helper, new ItemStack(ModItems.IRON_SMITHING_HAMMER.get())),
                    TestSupport.attackBonusOver(helper, hammer), "an enhanced hammer gives no attack");
            ItemStack epic = new ItemStack(ModItems.IRON_SMITHING_HAMMER.get());
            epic.set(ModDataComponents.ITEM_RATING, ItemRating.EPIC);
            epic.set(ModDataComponents.ENHANCEMENT, 3);
            ItemStack plainEpic = new ItemStack(ModItems.IRON_SMITHING_HAMMER.get());
            plainEpic.set(ModDataComponents.ITEM_RATING, ItemRating.EPIC);
            TestSupport.assertClose(helper, TestSupport.attackBonusOver(helper, plainEpic, 50),
                    TestSupport.attackBonusOver(helper, epic, 50), "an Epic +3 hammer gives no attack either");
            // Its rank line speaks of forging, not damage.
            List<Component> epicLines = EquipmentTooltip.lines(epic, null, null);
            helper.assertFalse(tooltipHas(epicLines, "tooltip.mitofus.rating_bonus"), "an Epic hammer shows no damage in its rank");
            List<Object> forge = tooltipArgs(epicLines, "tooltip.mitofus.rating_forge");
            helper.assertTrue(forge != null && forge.size() == 2 && "+4".equals(String.valueOf(forge.get(1))), "the rank line says +4 forging: " + forge);
            // Armor with a rank has no damage bonus either.
            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
            chest.set(ModDataComponents.ITEM_RATING, ItemRating.LEGENDARY);
            List<Component> chestLines = EquipmentTooltip.lines(chest, null, null);
            helper.assertTrue(tooltipHas(chestLines, "tooltip.mitofus.rating"), "a ranked chestplate shows its plain rank");
            helper.assertFalse(tooltipHas(chestLines, "tooltip.mitofus.rating_bonus"), "and no damage bonus");
        });
        helper.succeed();
    }

    private static ItemStack hammer(int level, ItemRating rating) {
        ItemStack hammer = new ItemStack(ModItems.IRON_SMITHING_HAMMER.get());
        if (level > 0) {
            hammer.set(ModDataComponents.ENHANCEMENT, level);
        }
        if (rating != null) {
            hammer.set(ModDataComponents.ITEM_RATING, rating);
        }
        return hammer;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aHammerIsEnhancedLikeAWeapon(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack iron = hammer(0, null);
            helper.assertTrue(EquipmentService.pieceType(iron).orElse(null) == com.orijua.mitofus.feature.equipment.EnhanceRules.PieceType.WEAPON,
                    "a hammer is a weapon for enhancing");
            helper.assertTrue(EquipmentService.canEnhance(iron), "a hammer can be enhanced");

            ItemStack first = hammer(0, null);
            EnhanceOutcome ok = EquipmentService.enhanceWith(smith, first, EnhanceMaterial.WEAPON_STONE, 0.0, 0.0);
            helper.assertTrue(ok.result() == EnhanceResult.SUCCESS && level(first) == 1, "a weapon stone gives +1: " + ok);
            EquipmentService.refreshName(first);
            helper.assertTrue(first.getHoverName().getString().endsWith("+1"), "the name ends in +1: " + first.getHoverName().getString());

            ItemStack a = hammer(5, null);
            EnhanceOutcome failA = EquipmentService.enhanceWith(smith, a, EnhanceMaterial.WEAPON_STONE, FAIL_ROLL, 0.0);
            helper.assertTrue(failA.result() == EnhanceResult.FAILURE && level(a) == 2, "normal stone fail: +5 to +2, got " + level(a) + ", " + failA);
            ItemStack b = hammer(5, null);
            EnhanceOutcome failB = EquipmentService.enhanceWith(smith, b, EnhanceMaterial.BLESSED_WEAPON_STONE, FAIL_ROLL, 0.0);
            helper.assertTrue(failB.result() == EnhanceResult.FAILURE && level(b) == 4, "blessed stone fail: +5 to +4, got " + level(b) + ", " + failB);
            ItemStack c = hammer(5, null);
            EnhanceOutcome failC = EquipmentService.enhanceWith(smith, c, EnhanceMaterial.BLESSED_SCROLL, FAIL_ROLL, 0.0);
            helper.assertTrue(failC.result() == EnhanceResult.FAILURE && level(c) == 5, "blessed scroll fail: stays +5, got " + level(c) + ", " + failC);

            ItemStack one = hammer(1, null);
            EnhanceOutcome failOne = EquipmentService.enhanceWith(smith, one, EnhanceMaterial.WEAPON_STONE, FAIL_ROLL, 0.0);
            helper.assertTrue(failOne.result() == EnhanceResult.FAILURE && level(one) == 0 && !one.has(ModDataComponents.ENHANCEMENT),
                    "+1 fails to +0 with no component: " + failOne);
            ItemStack ten = hammer(10, null);
            EnhanceOutcome maxed = EquipmentService.enhanceWith(smith, ten, EnhanceMaterial.WEAPON_STONE, 0.0, 0.0);
            helper.assertTrue(maxed.result() == EnhanceResult.MAXED && level(ten) == 10, "+10 is maxed and stays: " + maxed);

            ItemStack ancient = hammer(0, null);
            EnhanceOutcome anc = EquipmentService.enhanceWith(smith, ancient, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.9999, 0.0);
            helper.assertTrue(anc.result() == EnhanceResult.SUCCESS && level(ancient) == 1, "the ancient weapon scroll gives +1: " + anc);

            for (EnhanceMaterial armorMaterial : new EnhanceMaterial[]{EnhanceMaterial.ARMOR_STONE, EnhanceMaterial.ANCIENT_ARMOR_SCROLL}) {
                ItemStack wrong = hammer(3, null);
                EnhanceOutcome out = EquipmentService.enhanceWith(smith, wrong, armorMaterial, 0.0, 0.0);
                helper.assertTrue(out.result() == EnhanceResult.WRONG_MATERIAL && level(wrong) == 3, armorMaterial + " on a hammer: " + out);
            }

            // The real rank counts: a Legendary hammer has half the chance.
            double normal = EquipmentRules.enhancementChance(Balance.DEFAULT, 0, ProgressionService.effectiveStats(smith).dexterity());
            helper.assertTrue(EquipmentService.enhanceWith(smith, hammer(0, ItemRating.LEGENDARY), EnhanceMaterial.WEAPON_STONE, normal * 0.49, 0.0).result()
                    == EnhanceResult.SUCCESS, "Legendary hammer just under x0.5 succeeds");
            helper.assertTrue(EquipmentService.enhanceWith(smith, hammer(0, ItemRating.LEGENDARY), EnhanceMaterial.WEAPON_STONE, normal * 0.51, 0.0).result()
                    == EnhanceResult.FAILURE, "Legendary hammer just over x0.5 fails");

            ItemStack hidden = hammer(0, ItemRating.LEGENDARY);
            AppraisalService.markUnappraised(hidden, RandomSource.create(9L));
            helper.assertTrue(AppraisalService.isRatingHidden(hidden), "an unappraised hammer hides its rating");

            // The anvil.
            ServerPlayer player = TestSupport.fakePlayer(helper);
            SmithingAnvilMenu ready = anvil(helper, player, hammer(0, null), new ItemStack(ModItems.ENHANCEMENT_STONE.get()));
            helper.assertTrue(ready.enhanceState() == SmithingAnvilMenu.EnhanceState.READY, "a weapon stone on a hammer is ready");
            SmithingAnvilMenu wrong = anvil(helper, player, hammer(5, null), new ItemStack(ModItems.ARMOR_ENHANCEMENT_STONE.get(), 2));
            helper.assertTrue(wrong.enhanceState() == SmithingAnvilMenu.EnhanceState.WRONG_MATERIAL, "an armor stone on a hammer is the wrong material");
            helper.assertFalse(wrong.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the click is refused");
            helper.assertTrue(wrong.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().getCount() == 2
                    && level(wrong.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 5, "nothing is spent or changed");

            Balance.withOverride(Balance.DEFAULT.withEnhanceChances(1e-12, 1e-12), () -> {
                SmithingAnvilMenu menu = anvil(helper, player, hammer(5, null), new ItemStack(ModItems.ENHANCEMENT_STONE.get(), 1));
                helper.assertTrue(menu.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the click enhances (and fails)");
                helper.assertTrue(level(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 2, "+5 hammer falls to +2");
                helper.assertTrue(menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().isEmpty(), "and the stone is spent");
                helper.assertFalse(menu.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "a second click does nothing");
                helper.assertTrue(level(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 2, "the level is untouched");
            });
        });
        helper.succeed();
    }

    private static boolean tooltipHas(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
    }

    // ---- 3. Ancestral ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theAncientScrollEnhancesAncestralPiecesWithoutFailing(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack low = ancestralSword(0);
            EnhanceOutcome out = EquipmentService.enhanceWith(smith, low, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.9999, 0.0);
            helper.assertTrue(out.result() == EnhanceResult.SUCCESS && level(low) == 1, "gain roll 0 gives +1: " + out);
            ItemStack high = ancestralSword(0);
            EquipmentService.enhanceWith(smith, high, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.9999, 0.99);
            helper.assertTrue(level(high) == 3, "gain roll 0.99 gives +3, got " + level(high));
            ItemStack nine = ancestralSword(9);
            EquipmentService.enhanceWith(smith, nine, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.0, 0.99);
            helper.assertTrue(level(nine) == 10, "+9 reaches +10 and no more, got " + level(nine));
            ItemStack ten = ancestralSword(10);
            helper.assertTrue(EquipmentService.enhanceWith(smith, ten, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.0, 0.0).result() == EnhanceResult.MAXED, "+10 is maxed");

            ServerPlayer player = TestSupport.fakePlayer(helper);
            SmithingAnvilMenu menu = anvil(helper, player, ancestralSword(10), new ItemStack(ModItems.ANCIENT_WEAPON_ENHANCEMENT_SCROLL.get()));
            helper.assertTrue(menu.enhanceState() == SmithingAnvilMenu.EnhanceState.MAXED, "the anvil says maxed");
            helper.assertFalse(menu.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the click does nothing");
            helper.assertTrue(menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().getCount() == 1, "and the scroll is not spent");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theAncientScrollNeverFailsOnAnyPiece(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack plain = sword(0);
            EnhanceOutcome out = EquipmentService.enhanceWith(smith, plain, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.9999, 0.0);
            helper.assertTrue(out.result() == EnhanceResult.SUCCESS && level(plain) == 1, "a plain sword, gain roll 0 gives +1: " + out);
            ItemStack two = sword(2);
            EquipmentService.enhanceWith(smith, two, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.9999, 0.99);
            helper.assertTrue(level(two) == 5, "+2 with gain roll 0.99 gives +5, got " + level(two));
            ItemStack legendary = sword(0);
            legendary.set(ModDataComponents.ITEM_RATING, ItemRating.LEGENDARY);
            EnhanceOutcome rated = EquipmentService.enhanceWith(smith, legendary, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.9999, 0.0);
            helper.assertTrue(rated.result() == EnhanceResult.SUCCESS && level(legendary) == 1, "a Legendary sword succeeds: " + rated);

            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
            EnhanceOutcome wrong = EquipmentService.enhanceWith(smith, chest, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.0, 0.0);
            helper.assertTrue(wrong.result() == EnhanceResult.WRONG_MATERIAL && level(chest) == 0, "a weapon scroll on a chestplate: " + wrong);
            EnhanceOutcome right = EquipmentService.enhanceWith(smith, chest, EnhanceMaterial.ANCIENT_ARMOR_SCROLL, 0.9999, 0.0);
            helper.assertTrue(right.result() == EnhanceResult.SUCCESS && level(chest) == 1, "an armor scroll on a chestplate: " + right);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void onlyAHiddenRatingIsHidden(GameTestHelper helper) {
        ItemStack hidden = ancestralSword(0);
        AppraisalService.markUnappraised(hidden, RandomSource.create(5L));
        helper.assertTrue(AppraisalService.isRatingHidden(hidden), "a rated, unappraised piece hides its rating");
        ItemStack revealed = ancestralSword(0);
        revealed.set(ModDataComponents.APPRAISAL, new com.orijua.mitofus.feature.equipment.Appraisal(5L,
                List.of(com.orijua.mitofus.feature.equipment.AppraisalRules.RATING)));
        helper.assertFalse(AppraisalService.isRatingHidden(revealed), "with only the rating revealed it is not hidden");
        helper.assertFalse(AppraisalService.isRatingHidden(sword(0)), "a sword without a rating hides nothing");
        AppraisalService.appraiseFully(hidden);
        helper.assertFalse(AppraisalService.isRatingHidden(hidden), "an appraised piece hides nothing");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aAncestralPieceWithAStoneHasATenthOfTheChanceAndWithFactorZeroNone(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            // With the default factor the chance is a tenth: a roll that passes a normal piece fails a Ancestral one.
            double normal = EquipmentRules.enhancementChance(Balance.DEFAULT, 0, com.orijua.mitofus.feature.progression.ProgressionService.effectiveStats(smith).dexterity());
            double roll = normal * 0.5;
            helper.assertTrue(EquipmentService.enhanceWith(smith, sword(0), EnhanceMaterial.WEAPON_STONE, roll, 0.0).result() == EnhanceResult.SUCCESS,
                    "a plain piece succeeds with roll " + roll);
            helper.assertTrue(EquipmentService.enhanceWith(smith, ancestralSword(0), EnhanceMaterial.WEAPON_STONE, roll, 0.0).result() == EnhanceResult.FAILURE,
                    "a Ancestral one fails with the same roll");

            EnhanceBalance.withOverride(EnhanceBalance.DEFAULT.withChanceFactor(ItemRating.ANCESTRAL, 0), () -> {
                ItemStack ancestral = ancestralSword(4);
                EnhanceOutcome out = EquipmentService.enhanceWith(smith, ancestral, EnhanceMaterial.WEAPON_STONE, 0.0, 0.0);
                helper.assertTrue(out.result() == EnhanceResult.NO_CHANCE && level(ancestral) == 4, "no chance, nothing changes: " + out);

                ItemStack hidden = ancestralSword(4);
                AppraisalService.markUnappraised(hidden, RandomSource.create(3L));
                helper.assertTrue(EquipmentService.enhanceWith(smith, hidden, EnhanceMaterial.WEAPON_STONE, 0.0, 0.0).result() == EnhanceResult.NO_CHANCE,
                        "an unappraised Ancestral piece gets the factor too");

                ServerPlayer player = TestSupport.fakePlayer(helper);
                SmithingAnvilMenu menu = anvil(helper, player, ancestralSword(4), new ItemStack(ModItems.ENHANCEMENT_STONE.get()));
                // The menu does not evaluate the chance (EquipmentService.enhanceWith decides NO_CHANCE), so it still reads READY.
                helper.assertTrue(menu.enhanceState() == SmithingAnvilMenu.EnhanceState.READY, "the menu still reads ready with chance 0");
                helper.assertFalse(menu.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the anvil refuses");
                helper.assertTrue(menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().getCount() == 1
                        && level(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 4, "the stone is not spent and the piece is untouched");
            });
            ItemStack unappraised = ancestralSword(0);
            AppraisalService.markUnappraised(unappraised, RandomSource.create(4L));
            helper.assertTrue(EquipmentService.enhanceWith(smith, unappraised, EnhanceMaterial.WEAPON_STONE, roll, 0.0).result() == EnhanceResult.FAILURE,
                    "the real Ancestral rank counts even if unappraised");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theChanceDependsOnTheRealRankEvenIfUnappraised(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            double normal = EquipmentRules.enhancementChance(Balance.DEFAULT, 0, com.orijua.mitofus.feature.progression.ProgressionService.effectiveStats(smith).dexterity());
            double low = normal * 0.6;
            helper.assertTrue(enhanceRated(smith, null, false, low) == EnhanceResult.SUCCESS, "no rank at x0.6 succeeds");
            helper.assertTrue(enhanceRated(smith, ItemRating.UNIQUE, false, low) == EnhanceResult.SUCCESS, "Unique (x0.7) at x0.6 succeeds");
            helper.assertTrue(enhanceRated(smith, ItemRating.LEGENDARY, false, low) == EnhanceResult.FAILURE, "Legendary (x0.5) at x0.6 fails");
            helper.assertTrue(enhanceRated(smith, ItemRating.LEGENDARY, true, low) == EnhanceResult.FAILURE, "unappraised Legendary at x0.6 fails too");
            double high = normal * 0.9;
            helper.assertTrue(enhanceRated(smith, ItemRating.RARE, false, high) == EnhanceResult.SUCCESS, "Rare (x0.95) at x0.9 succeeds");
            helper.assertTrue(enhanceRated(smith, ItemRating.EPIC, false, high) == EnhanceResult.FAILURE, "Epic (x0.85) at x0.9 fails");

            EnhanceBalance.withOverride(EnhanceBalance.DEFAULT.withChanceFactor(ItemRating.LEGENDARY, 0), () -> {
                ItemStack legendary = ratedSword(4, ItemRating.LEGENDARY, true);
                EnhanceOutcome out = EquipmentService.enhanceWith(smith, legendary, EnhanceMaterial.WEAPON_STONE, 0.0, 0.0);
                helper.assertTrue(out.result() == EnhanceResult.NO_CHANCE && level(legendary) == 4, "factor 0: no chance, nothing changes: " + out);

                ServerPlayer player = TestSupport.fakePlayer(helper);
                SmithingAnvilMenu menu = anvil(helper, player, ratedSword(4, ItemRating.LEGENDARY, false), new ItemStack(ModItems.ENHANCEMENT_STONE.get()));
                // The menu does not evaluate the chance (EquipmentService.enhanceWith decides NO_CHANCE), so it still reads READY.
                helper.assertTrue(menu.enhanceState() == SmithingAnvilMenu.EnhanceState.READY, "the menu still reads ready with chance 0");
                helper.assertFalse(menu.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the anvil refuses");
                helper.assertTrue(menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().getCount() == 1
                        && level(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 4, "the stone is not spent and the piece is untouched");

                // The blessed stone and the blessed scroll have no chance either, and the +4 stays untouched.
                ItemStack blessedStoneTarget = ratedSword(4, ItemRating.LEGENDARY, false);
                helper.assertTrue(EquipmentService.enhanceWith(smith, blessedStoneTarget, EnhanceMaterial.BLESSED_WEAPON_STONE, 0.0, 0.0).result()
                        == EnhanceResult.NO_CHANCE && level(blessedStoneTarget) == 4, "a blessed stone has no chance with factor 0");
                ItemStack blessedScrollTarget = ratedSword(4, ItemRating.LEGENDARY, false);
                helper.assertTrue(EquipmentService.enhanceWith(smith, blessedScrollTarget, EnhanceMaterial.BLESSED_SCROLL, 0.0, 0.0).result()
                        == EnhanceResult.NO_CHANCE && level(blessedScrollTarget) == 4, "the blessed scroll has no chance with factor 0");
            });
        });
        helper.succeed();
    }

    /** The success roll has to land under {@code normal * factor}: just under succeeds, just over fails (both sides of the factor). */
    private static void assertFactor(GameTestHelper helper, ServerPlayer smith, ItemRating rating, double factor) {
        double normal = EquipmentRules.enhancementChance(Balance.DEFAULT, 0, ProgressionService.effectiveStats(smith).dexterity());
        // With the default balance normal is 0.95: a margin of 0.001 on a factor of 1 keeps the roll under 1 (guard below).
        helper.assertTrue(normal < 1, "the guard: the normal chance is under 1, got " + normal);
        double margin = factor >= 1.0 ? 0.001 : 0.01;
        helper.assertTrue(enhanceRated(smith, rating, false, normal * (factor - margin)) == EnhanceResult.SUCCESS,
                rating + " succeeds just under x" + factor);
        helper.assertTrue(enhanceRated(smith, rating, false, normal * (factor + margin)) == EnhanceResult.FAILURE,
                rating + " fails just over x" + factor);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void everyRankFactorIsBoundedOnBothSides(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            // Literals, not read from EnhanceBalance, so a changed default makes the test fail.
            assertFactor(helper, smith, null, 1.0);
            assertFactor(helper, smith, ItemRating.NORMAL, 1.0);
            assertFactor(helper, smith, ItemRating.RARE, 0.95);
            assertFactor(helper, smith, ItemRating.EPIC, 0.85);
            assertFactor(helper, smith, ItemRating.UNIQUE, 0.7);
            assertFactor(helper, smith, ItemRating.LEGENDARY, 0.5);
            assertFactor(helper, smith, ItemRating.ANCESTRAL, 0.1);

            // The real rank counts while the piece is unappraised.
            double normal = EquipmentRules.enhancementChance(Balance.DEFAULT, 0, ProgressionService.effectiveStats(smith).dexterity());
            helper.assertTrue(enhanceRated(smith, ItemRating.LEGENDARY, true, normal * 0.49) == EnhanceResult.SUCCESS, "unappraised Legendary succeeds at x0.49");
            helper.assertTrue(enhanceRated(smith, ItemRating.LEGENDARY, true, normal * 0.51) == EnhanceResult.FAILURE, "unappraised Legendary fails at x0.51");
            helper.assertTrue(enhanceRated(smith, ItemRating.ANCESTRAL, true, normal * 0.09) == EnhanceResult.SUCCESS, "unappraised Ancestral succeeds at x0.09");
            helper.assertTrue(enhanceRated(smith, ItemRating.ANCESTRAL, true, normal * 0.11) == EnhanceResult.FAILURE, "unappraised Ancestral fails at x0.11");

            // The factor is for armor too: an unappraised Legendary iron chestplate with the armor stone.
            helper.assertTrue(enhanceChest(smith, normal * 0.49) == EnhanceResult.SUCCESS, "Legendary chestplate succeeds at x0.49");
            helper.assertTrue(enhanceChest(smith, normal * 0.51) == EnhanceResult.FAILURE, "Legendary chestplate fails at x0.51");

            // A blessed stone loses one level on failure whatever the rank: a Legendary +5 falls to +4.
            ItemStack plus5 = ratedSword(5, ItemRating.LEGENDARY, false);
            EnhanceOutcome out = EquipmentService.enhanceWith(smith, plus5, EnhanceMaterial.BLESSED_WEAPON_STONE, 0.9999, 0.0);
            helper.assertTrue(out.result() == EnhanceResult.FAILURE && level(plus5) == 4, "Legendary +5 with a blessed stone falls to +4: " + out);
        });
        helper.succeed();
    }

    private static EnhanceResult enhanceChest(ServerPlayer smith, double roll) {
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        chest.set(ModDataComponents.ITEM_RATING, ItemRating.LEGENDARY);
        AppraisalService.markUnappraised(chest, RandomSource.create(9L));
        return EquipmentService.enhanceWith(smith, chest, EnhanceMaterial.ARMOR_STONE, roll, 0.0).result();
    }

    private static ItemStack ratedSword(int level, ItemRating rating, boolean unappraised) {
        ItemStack sword = sword(level);
        if (rating != null) {
            sword.set(ModDataComponents.ITEM_RATING, rating);
        }
        if (unappraised) {
            AppraisalService.markUnappraised(sword, RandomSource.create(9L));
        }
        return sword;
    }

    private static EnhanceResult enhanceRated(ServerPlayer smith, ItemRating rating, boolean unappraised, double roll) {
        return EquipmentService.enhanceWith(smith, ratedSword(0, rating, unappraised), EnhanceMaterial.WEAPON_STONE, roll, 0.0).result();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theAncientScrollKeepsItsHundredPercentWithEveryFactorAtZero(GameTestHelper helper) {
        EnhanceBalance zeroed = EnhanceBalance.DEFAULT;
        for (ItemRating rating : ItemRating.values()) {
            zeroed = zeroed.withChanceFactor(rating, 0);
        }
        // The ancient scroll always gains exactly one level, so the menu case below has an exact result.
        EnhanceBalance none = new EnhanceBalance(zeroed.normalStoneFailLoss(), zeroed.blessedStoneFailLoss(), zeroed.blessedScrollFailLoss(),
                zeroed.bonusPerLevel(), zeroed.bonusPerLevelHigh(), zeroed.bonusHighFromLevel(), zeroed.chanceFactors(), 1, 1,
                zeroed.boneLordAncientScrollChance());
        TestSupport.withAllDefaults(() -> EnhanceBalance.withOverride(none, () -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack legendary = ratedSword(0, ItemRating.LEGENDARY, true);
            helper.assertTrue(EquipmentService.enhanceWith(smith, legendary, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.9999, 0.0).result() == EnhanceResult.SUCCESS
                    && level(legendary) == 1, "an unappraised Legendary piece still takes the ancient scroll");
            ItemStack ancestral = ratedSword(0, ItemRating.ANCESTRAL, false);
            helper.assertTrue(EquipmentService.enhanceWith(smith, ancestral, EnhanceMaterial.ANCIENT_WEAPON_SCROLL, 0.9999, 0.0).result() == EnhanceResult.SUCCESS
                    && level(ancestral) == 1, "and so does a Ancestral one");
            helper.assertTrue(EquipmentService.enhanceWith(smith, ratedSword(0, ItemRating.ANCESTRAL, false), EnhanceMaterial.WEAPON_STONE, 0.0, 0.0).result() == EnhanceResult.NO_CHANCE,
                    "while a stone has no chance");

            // The anvil menu: a Ancestral sword and an ancient weapon scroll read ready, the click works and the scroll is spent.
            ServerPlayer player = TestSupport.fakePlayer(helper);
            SmithingAnvilMenu menu = anvil(helper, player, ratedSword(0, ItemRating.ANCESTRAL, false), new ItemStack(ModItems.ANCIENT_WEAPON_ENHANCEMENT_SCROLL.get()));
            helper.assertTrue(menu.enhanceState() == SmithingAnvilMenu.EnhanceState.READY, "the ancient scroll on a Ancestral sword is ready");
            helper.assertTrue(menu.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the click works");
            helper.assertTrue(level(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 1, "the Ancestral sword is +1");
            helper.assertTrue(menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().isEmpty(), "and the scroll is spent");
        }));
        helper.succeed();
    }

    // ---- 4. The anvil menu ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theAnvilSpendsTheStoneOnlyWhenTheAttemptHappens(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            // A tiny chance (not 0: that would be "no chance") so the random roll always fails.
            Balance.withOverride(Balance.DEFAULT.withEnhanceChances(1e-12, 1e-12), () -> {
                SmithingAnvilMenu menu = anvil(helper, player, sword(5), new ItemStack(ModItems.ENHANCEMENT_STONE.get(), 1));
                helper.assertTrue(menu.enhanceState() == SmithingAnvilMenu.EnhanceState.READY, "ready");
                helper.assertTrue(menu.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the first click enhances (and fails)");
                helper.assertTrue(level(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 2, "+5 falls to +2");
                helper.assertTrue(menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().isEmpty(), "and the only stone is spent");
                helper.assertFalse(menu.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "a second click does nothing");
                helper.assertTrue(level(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 2, "the level is untouched");
            });

            SmithingAnvilMenu wrong = anvil(helper, player, sword(5), new ItemStack(ModItems.ARMOR_ENHANCEMENT_STONE.get(), 2));
            helper.assertTrue(wrong.enhanceState() == SmithingAnvilMenu.EnhanceState.WRONG_MATERIAL, "an armor stone on a sword is the wrong material");
            helper.assertFalse(wrong.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the click is refused");
            helper.assertTrue(wrong.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().getCount() == 2
                    && level(wrong.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 5, "nothing is spent or changed");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theBlessedMaterialsLoseLessInTheAnvilThatFails(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer player = TestSupport.fakePlayer(helper);
            Balance.withOverride(Balance.DEFAULT.withEnhanceChances(1e-12, 1e-12), () -> {
                SmithingAnvilMenu scroll = anvil(helper, player, sword(5), new ItemStack(ModItems.BLESSED_ENHANCEMENT_SCROLL.get()));
                helper.assertTrue(scroll.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the blessed scroll is used");
                helper.assertTrue(level(scroll.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 5, "the piece stays +5");
                helper.assertTrue(scroll.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().isEmpty(), "and the scroll is spent");

                SmithingAnvilMenu stone = anvil(helper, player, sword(5), new ItemStack(ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE.get()));
                helper.assertTrue(stone.clickMenuButton(player, SmithingAnvilMenu.ENHANCE_BUTTON), "the blessed stone is used");
                helper.assertTrue(level(stone.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem()) == 4, "the piece falls to +4");
                helper.assertTrue(stone.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().isEmpty(), "and the stone is spent");
            });
        });
        helper.succeed();
    }

    // ---- 5. Smith's shop ----

    private static int indexFor(BlacksmithNpc smith, Item result) {
        MerchantOffers offers = smith.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).getResult().is(result)) {
                return i;
            }
        }
        throw new IllegalStateException("no offer gives " + result);
    }

    private static void giveGold(ServerPlayer player, int total) {
        player.setData(com.orijua.mitofus.core.registry.ModAttachments.PURSE.get(), com.orijua.mitofus.feature.economy.Purse.EMPTY); // the money is the purse plus the coins carried: start from nothing
        player.getInventory().clearContent();
        int slot = 0;
        for (int left = total; left > 0; slot++) {
            int stack = Math.min(left, 64);
            player.getInventory().setItem(slot, new ItemStack(ModItems.GOLD_COIN.get(), stack));
            left -= stack;
        }
    }

    private static int gold(ServerPlayer player) {
        return (int) (com.orijua.mitofus.feature.economy.Wallet.balance(player) / 100);
    }

    private static SmithShop.Result buy(ServerPlayer player, BlacksmithNpc smith, int index) {
        MerchantOffer offer = smith.getOffers().get(index);
        return SmithShop.buy(player, smith, index, offer.getResult().copy(), offer.getItemCostA().count(), 0);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void smithSellsTheNewMaterialsButNotTheAncientScroll(GameTestHelper helper) {
        BlacksmithNpc smith = TestSupport.smith(helper);
        MerchantOffers offers = smith.getOffers();
        check(helper, offers, ModItems.ARMOR_ENHANCEMENT_STONE.get(), SmithShopBalance.DEFAULT.stonePrice(), SmithShopBalance.DEFAULT.stoneStock());
        check(helper, offers, ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE.get(), SmithShopBalance.DEFAULT.blessedStonePrice(), SmithShopBalance.DEFAULT.blessedStock());
        check(helper, offers, ModItems.BLESSED_ARMOR_ENHANCEMENT_STONE.get(), SmithShopBalance.DEFAULT.blessedStonePrice(), SmithShopBalance.DEFAULT.blessedStock());
        check(helper, offers, ModItems.BLESSED_ENHANCEMENT_SCROLL.get(), SmithShopBalance.DEFAULT.blessedScrollPrice(), SmithShopBalance.DEFAULT.blessedStock());
        check(helper, offers, ModItems.ENHANCEMENT_STONE.get(), SmithShopBalance.DEFAULT.stonePrice(), SmithShopBalance.DEFAULT.stoneStock());
        helper.assertTrue(offers.stream().noneMatch(o -> o.getResult().is(ModItems.ANCIENT_WEAPON_ENHANCEMENT_SCROLL.get())
                || o.getResult().is(ModItems.ANCIENT_ARMOR_ENHANCEMENT_SCROLL.get())), "the ancient scrolls are not sold");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, MerchantOffers offers, Item item, int price, int stock) {
        MerchantOffer offer = offers.stream().filter(o -> o.getResult().is(item)).findFirst().orElse(null);
        helper.assertTrue(offer != null, "an offer for " + item);
        helper.assertTrue(offer.getItemCostA().count() == price && offer.getMaxUses() == stock && offer.getItemCostA().test(new ItemStack(ModItems.GOLD_COIN.get())),
                item + ": " + offer.getItemCostA().count() + " gold, " + offer.getMaxUses() + " uses");
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aBlessedStoneIsBoughtWithGoldInManyStacksAndNotWithOneLess(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        TestSupport.inFatigue(helper, true, () -> {
            smith.startTrading(player);
            int blessed = indexFor(smith, ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE.get());
            giveGold(player, SmithShopBalance.DEFAULT.blessedStonePrice() - 1);
            helper.assertTrue(buy(player, smith, blessed) == SmithShop.Result.NOT_ENOUGH, "1.249 are not enough");
            helper.assertTrue(gold(player) == 1_249 && player.getInventory().countItem(ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE.get()) == 0
                    && smith.getOffers().get(blessed).getUses() == 0, "and nothing moves");
            giveGold(player, SmithShopBalance.DEFAULT.blessedStonePrice());
            helper.assertTrue(buy(player, smith, blessed) == SmithShop.Result.OK, "1.250 buy it");
            helper.assertTrue(gold(player) == 0 && player.getInventory().countItem(ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE.get()) == 1, "leaving no gold");

            int armor = indexFor(smith, ModItems.ARMOR_ENHANCEMENT_STONE.get());
            giveGold(player, 2 * SmithShopBalance.DEFAULT.stonePrice());
            helper.assertTrue(buy(player, smith, armor) == SmithShop.Result.OK && buy(player, smith, armor) == SmithShop.Result.OK, "two armor stones");
            helper.assertTrue(gold(player) == 0, "they take all the gold");
            helper.assertTrue(player.getInventory().countItem(ModItems.ARMOR_ENHANCEMENT_STONE.get()) == 2, "two armor stones in the inventory");
            helper.assertTrue(smith.getOffers().get(armor).getUses() == 2, "and two uses of the offer");

            // Paying with more than the price: only the price is taken.
            int blessedArmor = indexFor(smith, ModItems.BLESSED_ARMOR_ENHANCEMENT_STONE.get());
            giveGold(player, 1_300);
            helper.assertTrue(buy(player, smith, blessedArmor) == SmithShop.Result.OK, "1.300 buy a blessed armor stone");
            helper.assertTrue(gold(player) == 50 && smith.getOffers().get(blessedArmor).getUses() == 1, "leaving 50 gold and one use");

            // The blessed stock runs out: the next order moves nothing.
            int scroll = indexFor(smith, ModItems.BLESSED_ENHANCEMENT_SCROLL.get());
            for (int i = 0; i < SmithShopBalance.DEFAULT.blessedStock(); i++) {
                giveGold(player, SmithShopBalance.DEFAULT.blessedScrollPrice());
                helper.assertTrue(buy(player, smith, scroll) == SmithShop.Result.OK, "blessed scroll number " + (i + 1));
            }
            helper.assertTrue(smith.getOffers().get(scroll).getUses() == SmithShopBalance.DEFAULT.blessedStock(), "the stock is used up");
            giveGold(player, SmithShopBalance.DEFAULT.blessedScrollPrice());
            helper.assertTrue(buy(player, smith, scroll) == SmithShop.Result.SOLD_OUT, "the next one is sold out");
            helper.assertTrue(gold(player) == SmithShopBalance.DEFAULT.blessedScrollPrice() && player.getInventory().countItem(ModItems.BLESSED_ENHANCEMENT_SCROLL.get()) == 0,
                    "and no gold or scroll moves");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSmithSavedWithShopVersionTwoIsRestockedAtTheNewPrice(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc original = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        MerchantOffers offers = original.getOffers();
        int stone = indexFor(original, ModItems.ENHANCEMENT_STONE.get());
        offers.set(stone, new MerchantOffer(new ItemCost(ModItems.GOLD_COIN.get(), 2), new ItemStack(ModItems.ENHANCEMENT_STONE.get()), 16, 0, 0.05f));
        // A spent offer: the restock refills it.
        offers.get(indexFor(original, ModItems.BLESSED_ENHANCEMENT_SCROLL.get())).increaseUses();
        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        saved.putInt("mitofus_shop_version", 2);

        BlacksmithNpc old = helper.spawn(ModEntities.BLACKSMITH.get(), 2, 1, 1);
        old.readAdditionalSaveData(saved);
        helper.assertTrue(old.getOffers().get(indexFor(old, ModItems.ENHANCEMENT_STONE.get())).getItemCostA().count() == 2, "it loads with the old price");
        old.startTrading(player);
        helper.assertTrue(old.getOffers().get(indexFor(old, ModItems.ENHANCEMENT_STONE.get())).getItemCostA().count() == SmithShopBalance.DEFAULT.stonePrice(),
                "opening it restocks the stone at " + SmithShopBalance.DEFAULT.stonePrice());
        helper.assertTrue(old.getOffers().stream().anyMatch(o -> o.getResult().is(ModItems.BLESSED_ENHANCEMENT_SCROLL.get())), "and adds the scroll");
        check(helper, old.getOffers(), ModItems.ARMOR_ENHANCEMENT_STONE.get(), SmithShopBalance.DEFAULT.stonePrice(), SmithShopBalance.DEFAULT.stoneStock());
        check(helper, old.getOffers(), ModItems.BLESSED_WEAPON_ENHANCEMENT_STONE.get(), SmithShopBalance.DEFAULT.blessedStonePrice(), SmithShopBalance.DEFAULT.blessedStock());
        check(helper, old.getOffers(), ModItems.BLESSED_ARMOR_ENHANCEMENT_STONE.get(), SmithShopBalance.DEFAULT.blessedStonePrice(), SmithShopBalance.DEFAULT.blessedStock());
        check(helper, old.getOffers(), ModItems.BLESSED_ENHANCEMENT_SCROLL.get(), SmithShopBalance.DEFAULT.blessedScrollPrice(), SmithShopBalance.DEFAULT.blessedStock());
        helper.assertTrue(old.getOffers().stream().allMatch(o -> o.getUses() == 0), "and the restock refills every spent use");
        CompoundTag again = new CompoundTag();
        old.addAdditionalSaveData(again);
        helper.assertTrue(again.getInt("mitofus_shop_version") == BlacksmithNpc.SHOP_VERSION, "and it is stamped with the current version");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSmithSavedWithTheCurrentShopVersionKeepsItsSpentOffers(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc original = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        MerchantOffer spent = original.getOffers().get(indexFor(original, ModItems.BLESSED_ENHANCEMENT_SCROLL.get()));
        spent.increaseUses();
        spent.increaseUses();
        CompoundTag saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        helper.assertTrue(saved.getInt("mitofus_shop_version") == BlacksmithNpc.SHOP_VERSION, "it is saved with the current version");

        BlacksmithNpc loaded = helper.spawn(ModEntities.BLACKSMITH.get(), 2, 1, 1);
        loaded.readAdditionalSaveData(saved);
        loaded.startTrading(player);
        MerchantOffer after = loaded.getOffers().get(indexFor(loaded, ModItems.BLESSED_ENHANCEMENT_SCROLL.get()));
        helper.assertTrue(after.getUses() == 2, "opening it does not restock: uses " + after.getUses());
        helper.assertTrue(after.getItemCostA().count() == SmithShopBalance.DEFAULT.blessedScrollPrice(), "and the price is the same");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aSmithAlreadyInTheWorldFollowsAChangedConfigKeepingTheUsesSpent(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = helper.spawn(ModEntities.BLACKSMITH.get(), 1, 1, 1);
        MerchantOffer stone = smith.getOffers().get(indexFor(smith, ModItems.ENHANCEMENT_STONE.get()));
        for (int i = 0; i < 5; i++) {
            stone.increaseUses();
        }
        SmithShopBalance cheap = new SmithShopBalance(40, 1_250, 1_250, 3, 3, 4, 2, 5, 10, 4);
        ApprenticeBalance contracts = new ApprenticeBalance(50, 10, 5, 1, 14, 30, 60, 20);
        SmithShopBalance.withOverride(cheap, () -> ApprenticeBalance.withOverride(contracts, () -> smith.startTrading(player)));
        MerchantOffers offers = smith.getOffers();
        MerchantOffer after = offers.get(indexFor(smith, ModItems.ENHANCEMENT_STONE.get()));
        helper.assertTrue(after.getItemCostA().count() == 40 && after.getMaxUses() == 3, "the stone takes the new price and stock: "
                + after.getItemCostA().count() + " gold, " + after.getMaxUses() + " uses");
        helper.assertTrue(after.getUses() == 3, "the 5 uses spent are capped at the new stock of 3: " + after.getUses());
        check(helper, offers, ModItems.ARMOR_ENHANCEMENT_STONE.get(), 40, 3);
        check(helper, offers, ModItems.BLESSED_ENHANCEMENT_SCROLL.get(), 1_250, 3);
        MerchantOffer mithril = offers.stream().filter(o -> o.getItemCostA().item().value() == ModItems.MITHRIL_INGOT.get()).findFirst().orElseThrow();
        helper.assertTrue(mithril.getResult().getCount() == 2, "4 mithril ingots now pay 2 gold");
        helper.assertTrue(offers.get(indexFor(smith, ModItems.APPRENTICE_CONTRACT.get())).getItemCostA().count() == 30, "and the contract costs 30");
        helper.assertTrue(offers.stream().filter(o -> o.getResult().is(ModItems.ENHANCEMENT_STONE.get())).count() == 1, "no offer is duplicated");
        helper.succeed();
    }

    // ---- 6. The Bone Lord's loot ----

    /** Collects what a mob drops, after the other listeners. */
    public static final class Recorder {
        private final LivingEntity victim;
        final List<ItemEntity> drops = new ArrayList<>();

        Recorder(LivingEntity victim) {
            this.victim = victim;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onDrops(LivingDropsEvent event) {
            if (event.getEntity() == victim) {
                drops.addAll(event.getDrops());
            }
        }
    }

    private static boolean isAncient(ItemStack stack) {
        return stack.is(ModItems.ANCIENT_WEAPON_ENHANCEMENT_SCROLL.get()) || stack.is(ModItems.ANCIENT_ARMOR_ENHANCEMENT_SCROLL.get());
    }

    private static int killBoneLord(GameTestHelper helper, double chance, boolean mitofus, Function<BoneLord, DamageSource> source) {
        BoneLord lord = helper.spawn(ModEntities.BONE_LORD.get(), new BlockPos(1, 1, 1));
        Recorder recorder = new Recorder(lord);
        NeoForge.EVENT_BUS.register(recorder);
        try {
            EnhanceBalance.withOverride(EnhanceBalance.DEFAULT.withBoneLordAncientScrollChance(chance),
                    () -> TestSupport.inFatigue(helper, mitofus, () -> lord.hurt(source.apply(lord), 100_000.0f)));
        } finally {
            NeoForge.EVENT_BUS.unregister(recorder);
        }
        helper.assertTrue(lord.isDeadOrDying(), "the Bone Lord should die");
        int scrolls = (int) recorder.drops.stream().map(ItemEntity::getItem).filter(EnhanceFailureTests::isAncient).count();
        recorder.drops.forEach(ItemEntity::discard);
        return scrolls;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theBoneLordDropsAnAncientScrollOnlyForAPlayerAndByChance(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        int sure = killBoneLord(helper, 1.0, true, lord -> player.damageSources().playerAttack(player));
        helper.assertTrue(sure == 1, "with chance 1 exactly one ancient scroll, got " + sure);
        int never = killBoneLord(helper, 0.0, true, lord -> player.damageSources().playerAttack(player));
        helper.assertTrue(never == 0, "with chance 0 none, got " + never);
        int noPlayer = killBoneLord(helper, 1.0, true, lord -> lord.damageSources().generic());
        helper.assertTrue(noPlayer == 0, "without a player none, got " + noPlayer);
        int outside = killBoneLord(helper, 1.0, false, lord -> player.damageSources().playerAttack(player));
        helper.assertTrue(outside == 0, "outside Mitofus none, got " + outside);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theAncientScrollDropIsAWeaponOrArmorScroll(GameTestHelper helper) {
        boolean weapon = false;
        boolean armor = false;
        for (int seed = 0; seed < 40; seed++) {
            ItemStack drop = LootEvents.ancientScrollDrop(RandomSource.create(seed), 1.0).orElseThrow();
            weapon |= drop.is(ModItems.ANCIENT_WEAPON_ENHANCEMENT_SCROLL.get());
            armor |= drop.is(ModItems.ANCIENT_ARMOR_ENHANCEMENT_SCROLL.get());
            helper.assertTrue(isAncient(drop), "only ancient scrolls");
            helper.assertTrue(LootEvents.ancientScrollDrop(RandomSource.create(seed), 0.0).isEmpty(), "chance 0 gives nothing");
        }
        helper.assertTrue(weapon && armor, "both kinds appear");
        helper.succeed();
    }

    // ---- 7. Pieces that were already enhanced ----

    @GameTest(template = TestSupport.EMPTY)
    public static void aPieceAlreadyEnhancedKeepsItsLevelAndBonus(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ItemStack plus7 = sword(7);
            EquipmentService.refreshName(plus7);
            String name = plus7.getHoverName().getString();
            Tag saved = plus7.save(helper.getLevel().registryAccess());
            ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow();
            helper.assertTrue(level(loaded) == 7 && loaded.getHoverName().getString().equals(name), "still +7 with the same name: " + loaded.getHoverName().getString());
            // 5 * (1.05^5 * 1.07^2 - 1)
            TestSupport.assertClose(helper, 2.30607, TestSupport.attackBonusOver(helper, loaded), "+7 attack");

            ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
            chest.set(ModDataComponents.ENHANCEMENT, 3);
            TestSupport.assertClose(helper, 0.94575, TestSupport.armorBonusOver(helper, chest), "+3 chestplate by hand");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void changingTheLevelOfAWornPieceReplacesItsModifier(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setData(ModAttachments.PLAYER_STATS, player.getData(ModAttachments.PLAYER_STATS).withProgress(1, 0, 0));
            ItemStack sword = sword(3);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            player.tick();
            double at3 = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            sword.set(ModDataComponents.ENHANCEMENT, 9);
            ProgressionService.refreshEffects(player);
            double at9 = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            // 5 * (1.6729448 - 1) - 0.788125 = 2.5765989: +9 bonus minus the +3 bonus, so the old modifier is not kept.
            TestSupport.assertClose(helper, 2.5765989, at9 - at3, "the attack follows the new level");
            long modifiers = player.getAttribute(Attributes.ATTACK_DAMAGE).getModifiers().stream()
                    .filter(m -> m.id().equals(Mitofus.id("equip_mainhand_enhancement"))).count();
            helper.assertTrue(modifiers == 1, "exactly one enhancement modifier, got " + modifiers);
        });
        helper.succeed();
    }
}
