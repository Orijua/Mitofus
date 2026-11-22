package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.EquipmentRules;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.equipment.EquipmentEvents;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.core.debug.DebugGear;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.feature.equipment.option.ItemOptionDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class EquipmentTests {
    private EquipmentTests() {
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void ratingAddsAttackDamage(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.UNIQUE);
        TestSupport.assertClose(helper, ItemRating.UNIQUE.attackDamageBonus(), TestSupport.attackBonusOver(helper, sword, 15), "unique bonus");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void attributeOptionAddsAttackDamage(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.ITEM_OPTIONS, List.of(new RolledOption(Mitofus.id("sharpness"), 2.0f)));
        TestSupport.assertClose(helper, 2.0, TestSupport.attackBonusOver(helper, sword), "sharpness option");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void enhancementAddsAttackDamage(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.set(ModDataComponents.ENHANCEMENT, 3);
            // Iron sword base attack 5: 5 * (1.05^3 - 1).
            TestSupport.assertClose(helper, 0.788125, TestSupport.attackBonusOver(helper, sword), "+3 enhancement");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void enhancementBonusIsAPercentageOfTheBase(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            // Literals, not recomputed: base 5 (sword) and 6 (chestplate) times (factor - 1). +6 is the first level at 7 %.
            double[] swordBonus = {0.25, 1.3814, 1.8281064, 2.3060738, 3.3647, 3.9503};
            double[] chestBonus = {0.3, 1.6577, 2.1937276, 2.7672886, 4.0377, 4.7403};
            int[] levels = {1, 5, 6, 7, 9, 10};
            for (int i = 0; i < levels.length; i++) {
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                sword.set(ModDataComponents.ENHANCEMENT, levels[i]);
                TestSupport.assertClose(helper, swordBonus[i], TestSupport.attackBonusOver(helper, sword), "sword +" + levels[i]);
                ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
                chest.set(ModDataComponents.ENHANCEMENT, levels[i]);
                TestSupport.assertClose(helper, chestBonus[i], TestSupport.armorBonusOver(helper, chest), "chestplate +" + levels[i]);
            }
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void nameShowsEnhancementInRatingColor(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.EPIC);
        sword.set(ModDataComponents.ENHANCEMENT, 3);
        EquipmentService.refreshName(sword);
        helper.assertTrue(sword.getHoverName().getString().endsWith(" +3"), "name should end in +3: " + sword.getHoverName().getString());
        helper.assertTrue(ItemRating.EPIC.color() == sword.getHoverName().getStyle().getColor().getValue(),
                "name should use the epic color");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void unmetRequirementsDisableBonuses(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.UNIQUE);
        sword.set(ModDataComponents.ENHANCEMENT, 2);
        sword.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(5, Map.of()));
        TestSupport.assertClose(helper, 0.0, TestSupport.attackBonusOver(helper, sword), "bonus with unmet requirements");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void requirementsCountStatsFromOtherEquipment(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
        armor.set(ModDataComponents.ITEM_OPTIONS, List.of(new RolledOption(Mitofus.id("guadalete_agility"), 10f)));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(1, Map.of(StatType.AGILITY, 10)));
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        ProgressionService.refreshEffects(player);

        helper.assertTrue(ProgressionService.effectiveStats(player).agility() >= 10, "the armor does give AGI");
        helper.assertTrue(EquipmentEvents.meetsRequirements(player, sword), "AGI from other equipment counts as the character's own");

        ProgressionService.set(player, ProgressionService.get(player).with(StatType.AGILITY, 10));
        helper.assertTrue(EquipmentEvents.meetsRequirements(player, sword), "assigned AGI does count");
        helper.succeed();
    }

    /** An item never meets its own requirements with the stats it gives, not even together with another such item. */
    @GameTest(template = TestSupport.EMPTY)
    public static void anItemCannotLiftItselfOverItsOwnRequirements(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
        armor.set(ModDataComponents.ITEM_OPTIONS, List.of(new RolledOption(Mitofus.id("guadalete_agility"), 10f)));
        armor.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(1, Map.of(StatType.AGILITY, 10)));
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helmet.set(ModDataComponents.ITEM_OPTIONS, List.of(new RolledOption(Mitofus.id("guadalete_agility"), 10f)));
        helmet.set(ModDataComponents.REQUIREMENTS, new EquipRequirements(1, Map.of(StatType.AGILITY, 10)));
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        ProgressionService.refreshEffects(player);

        helper.assertTrue(ProgressionService.effectiveStats(player).agility() == com.orijua.mitofus.feature.progression.ProgressionRules.baseStat(StatType.AGILITY), "neither piece gives its AGI: " + ProgressionService.effectiveStats(player).agility());
        helper.assertFalse(EquipmentEvents.meetsRequirements(player, armor), "the chestplate doesn't meet its own requirement");
        helper.assertFalse(EquipmentEvents.meetsRequirements(player, helmet), "nor do two such pieces lift each other");
        helper.succeed();
    }

    /** BUG-028 (rule changed on 2026-11-17): the stats the armor gives count towards the requirements of the sword. */
    @GameTest(template = TestSupport.EMPTY)
    public static void debugGearRequirementCountsTheArmorBonus(GameTestHelper helper) {
        for (StatType stat : StatType.values()) {
            String who = stat.getSerializedName() + ": ";
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack armor = DebugGear.armor(stat, 10);
            ItemStack sword = DebugGear.weapon(stat, 10);
            atTheirLevel(player, sword);
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            ProgressionService.refreshEffects(player);
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel()); // never added to the world: only the target of the attack

            helper.assertFalse(EquipmentEvents.meetsRequirements(player, sword), who + "without the armor the requirement is unmet");
            helper.assertTrue(ratingModifier(player) == null, who + "no rating bonus without the armor");
            helper.assertTrue(NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, zombie)).isCanceled(), who + "the attack is cancelled");

            player.setItemSlot(EquipmentSlot.CHEST, armor);
            ProgressionService.refreshEffects(player);
            helper.assertTrue(ProgressionService.effectiveStats(player).get(stat) == com.orijua.mitofus.feature.progression.ProgressionRules.baseStat(stat) + 10, who + "the armor gives the stat");
            helper.assertTrue(EquipmentEvents.meetsRequirements(player, sword), who + "the stat from the armor counts");
            AttributeModifier rating = ratingModifier(player);
            helper.assertTrue(rating != null && Math.abs(rating.amount() - ItemRating.UNIQUE.attackDamageBonus()) < 1e-4,
                    who + "the unique bonus is on: " + rating);
            helper.assertFalse(NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, zombie)).isCanceled(), who + "and the attack goes through");

            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            ProgressionService.refreshEffects(player);
            helper.assertFalse(EquipmentEvents.meetsRequirements(player, sword), who + "without the armor it is unmet again");
            helper.assertTrue(ratingModifier(player) == null, who + "and the rating bonus is gone");
        }
        helper.succeed();
    }

    /** The player at the level the debug piece asks for (a rated piece asks for one), so only the stat rule is tested. */
    private static void atTheirLevel(ServerPlayer player, ItemStack piece) {
        int level = com.orijua.mitofus.feature.equipment.EquipLevels.effectiveLevel(piece,
                com.orijua.mitofus.core.config.EquipLevelBalance.current());
        ProgressionService.set(player, ProgressionService.get(player).withProgress(level, 0, 0));
    }

    private static AttributeModifier ratingModifier(ServerPlayer player) {
        return player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Mitofus.id("equip_mainhand_rating"));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void debugGearTooltipMatchesTheRule(GameTestHelper helper) {
        TextColor red = TextColor.fromLegacyFormat(ChatFormatting.RED);
        TextColor gray = TextColor.fromLegacyFormat(ChatFormatting.GRAY);
        for (StatType stat : StatType.values()) {
            String who = stat.getSerializedName() + ": ";
            String statName = stat.displayName().getString();
            ServerPlayer player = TestSupport.fakePlayer(helper);
            ItemStack sword = DebugGear.weapon(stat, 10);
            ItemStack armor = DebugGear.armor(stat, 10);
            atTheirLevel(player, sword);
            Item.TooltipContext context = Item.TooltipContext.of(helper.getLevel());
            String inactive = Component.translatable("tooltip.mitofus.bonuses_inactive").getString();

            for (boolean assigned : new boolean[]{false, true}) {
                if (assigned) {
                    ProgressionService.set(player, ProgressionService.get(player).with(stat, 10));
                }
                boolean meets = EquipmentEvents.meetsRequirements(player, sword);
                helper.assertTrue(meets == assigned, who + "meets the requirement only once assigned");
                List<Component> lines = sword.getTooltipLines(context, player, TooltipFlag.Default.NORMAL);
                Component requirement = lines.stream().filter(line -> line.getString().contains(statName)).findFirst()
                        .orElseThrow(() -> new AssertionError(who + "a requirement line with the stat: " + lines));
                helper.assertTrue(requirement.getStyle().getColor() != null && requirement.getStyle().getColor().equals(assigned ? gray : red),
                        who + "requirement line color (assigned=" + assigned + "): " + requirement.getStyle().getColor());
                helper.assertTrue(lines.stream().anyMatch(line -> line.getString().equals(inactive)) == !meets,
                        who + "the inactive-bonuses line shows exactly when unmet: " + lines);
            }

            List<Component> armorLines = armor.getTooltipLines(context, player, TooltipFlag.Default.NORMAL);
            helper.assertTrue(armorLines.stream().anyMatch(line -> line.getString().contains(statName) && line.getString().contains("+10")),
                    who + "the armor tooltip names the stat with +10: " + armorLines);
        }
        helper.succeed();
    }

    /** The debug_<stat> options (weight 0) never roll: not from a pool where they would be the only way to fill the count, nor from the real pool. */
    @GameTest(template = TestSupport.EMPTY)
    public static void debugOptionsNeverRoll(GameTestHelper helper) {
        List<Definitions.Entry<ItemOptionDefinition>> real = EquipmentService.itemOptions(helper.getLevel());
        List<Definitions.Entry<ItemOptionDefinition>> debug = real.stream().filter(e -> isDebugOption(e.id())).toList();
        helper.assertTrue(debug.size() == StatType.values().length, "the pool has the five debug options: " + debug.size());
        helper.assertTrue(debug.stream().allMatch(e -> e.value().weight() == 0), "and they have weight 0");

        ItemStack probe = new ItemStack(Items.IRON_CHESTPLATE);
        Definitions.Entry<ItemOptionDefinition> normal = real.stream()
                .filter(e -> !isDebugOption(e.id()) && e.value().weight() > 0 && e.value().canRollOn(probe)).findFirst()
                .orElseThrow(() -> new AssertionError("a normal option that rolls on a chestplate"));
        List<Definitions.Entry<ItemOptionDefinition>> small = new java.util.ArrayList<>(debug);
        small.add(normal);
        for (int seed = 0; seed < 50; seed++) {
            ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
            EquipmentService.rateAs(chestplate, ItemRating.UNIQUE, RandomSource.create(seed), small);
            List<RolledOption> rolled = chestplate.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.of());
            helper.assertTrue(rolled.stream().noneMatch(o -> isDebugOption(o.option())), "small pool, seed " + seed + ": " + rolled);
        }

        for (int seed = 0; seed < 50; seed++) {
            for (ItemStack piece : List.of(new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_SWORD))) {
                EquipmentService.rateAs(piece, ItemRating.ANCESTRAL, RandomSource.create(seed), real);
                List<RolledOption> rolled = piece.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.of());
                helper.assertTrue(rolled.stream().noneMatch(o -> isDebugOption(o.option())), "real pool, seed " + seed + ": " + rolled);
            }
        }
        helper.succeed();
    }

    private static boolean isDebugOption(ResourceLocation id) {
        return id.getPath().startsWith("debug_");
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void enhancingAddsOneOnSuccessAndANormalStoneLosesThreeOnFailure(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            helper.assertTrue(EquipmentService.enhance(smith, sword, 0.0) == EquipmentService.EnhanceResult.SUCCESS, "a low roll succeeds");
            helper.assertTrue(sword.getOrDefault(ModDataComponents.ENHANCEMENT, 0) == 1, "the sword is +1");
            helper.assertTrue(sword.getHoverName().getString().contains("+1"), "and its name says so: " + sword.getHoverName().getString());
            helper.assertTrue(EquipmentService.enhance(smith, sword, 0.999) == EquipmentService.EnhanceResult.FAILURE, "a high roll fails");
            helper.assertTrue(sword.getOrDefault(ModDataComponents.ENHANCEMENT, 0) == 0, "and it falls back to +0, never below");
            sword.set(ModDataComponents.ENHANCEMENT, 5);
            helper.assertTrue(EquipmentService.enhance(smith, sword, 0.999) == EquipmentService.EnhanceResult.FAILURE, "a failure at +5");
            helper.assertTrue(sword.getOrDefault(ModDataComponents.ENHANCEMENT, 0) == 2, "loses three levels, to +2");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void enhancingStopsAtTen(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.set(ModDataComponents.ENHANCEMENT, EquipmentRules.MAX_ENHANCEMENT);
            helper.assertTrue(EquipmentService.enhance(smith, sword, 0.0) == EquipmentService.EnhanceResult.MAXED, "no more than the maximum");
            helper.assertTrue(sword.getOrDefault(ModDataComponents.ENHANCEMENT, 0) == EquipmentRules.MAX_ENHANCEMENT, "the level doesn't change");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void weaponsAndArmorCanBeEnhancedButNotOtherGear(GameTestHelper helper) {
        TestSupport.withAllDefaults(() -> {
            ServerPlayer smith = TestSupport.fakePlayer(helper);
            helper.assertTrue(EquipmentService.canEnhance(new ItemStack(Items.IRON_SWORD)), "a sword can");
            helper.assertTrue(EquipmentService.canEnhance(new ItemStack(Items.IRON_CHESTPLATE)), "armor can");
            helper.assertFalse(EquipmentService.canEnhance(new ItemStack(Items.WOLF_ARMOR)), "a wolf's armor can't");
            helper.assertFalse(EquipmentService.canEnhance(new ItemStack(Items.FISHING_ROD)), "a fishing rod can't");
            helper.assertFalse(EquipmentService.canEnhance(new ItemStack(Items.SHEARS)), "shears can't");
            helper.assertFalse(EquipmentService.canEnhance(new ItemStack(Items.IRON_SWORD, 2)), "a stack of two can't");
            helper.assertFalse(EquipmentService.canEnhance(ItemStack.EMPTY), "nothing can't");
            ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
            helper.assertTrue(EquipmentService.enhance(smith, chestplate, 0.0) == EquipmentService.EnhanceResult.SUCCESS, "enhancing armor works");
            helper.assertTrue(chestplate.getOrDefault(ModDataComponents.ENHANCEMENT, 0) == 1, "and it is +1");
            ItemStack hammer = new ItemStack(com.orijua.mitofus.core.registry.ModItems.IRON_SMITHING_HAMMER.get());
            helper.assertTrue(EquipmentService.canEnhance(hammer), "a smithing hammer can");
            helper.assertTrue(EquipmentService.enhance(smith, hammer, 0.0) == EquipmentService.EnhanceResult.SUCCESS, "enhancing a hammer works");
            helper.assertTrue(hammer.getOrDefault(ModDataComponents.ENHANCEMENT, 0) == 1, "and it is +1");
            ItemStack rod = new ItemStack(Items.FISHING_ROD);
            helper.assertTrue(EquipmentService.enhance(smith, rod, 0.0) == EquipmentService.EnhanceResult.NOT_EQUIPMENT, "enhancing a rod is refused");
            helper.assertFalse(rod.has(ModDataComponents.ENHANCEMENT), "and changes nothing");
        });
        helper.succeed();
    }
}
