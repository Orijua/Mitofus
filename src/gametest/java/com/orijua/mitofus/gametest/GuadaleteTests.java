package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModEntities;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.core.registry.ModRecipes;
import com.orijua.mitofus.feature.equipment.EquipmentEvents;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.mob.BoneLord;
import com.orijua.mitofus.feature.mob.MobLevel;
import com.orijua.mitofus.feature.mob.MobLevelService;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.smithing.ForgingRecipe;
import com.orijua.mitofus.feature.smithing.ForgingRecipeInput;
import com.orijua.mitofus.feature.smithing.ForgingRules;
import com.orijua.mitofus.feature.smithing.SmithingAnvilMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Guadalete, Cid's first legendary work: its blueprint, who can forge it, its rating range and its fixed effects. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class GuadaleteTests {
    static final ResourceLocation SUCCESSOR = Mitofus.id("legendary_blacksmith");
    static final ResourceLocation BISECTION = Mitofus.id("bisection");
    private static final Set<ResourceLocation> FIXED_EFFECTS = Set.of(Mitofus.id("guadalete_agility"),
            Mitofus.id("guadalete_block"), Mitofus.id("guadalete_darkness"), Mitofus.id("guadalete_fear"));

    private GuadaleteTests() {
    }

    private static ForgingRecipe guadaleteRecipe(GameTestHelper helper) {
        ForgingRecipeInput input = new ForgingRecipeInput(new ItemStack(ModItems.AZURITE_INGOT.get(), 15),
                new ItemStack(ModItems.GUADALETE_BLUEPRINT.get()));
        return helper.getLevel().getRecipeManager().getRecipeFor(ModRecipes.FORGING.get(), input, helper.getLevel())
                .orElseThrow(() -> new AssertionError("15 blue azurite + the blueprint should be a recipe")).value();
    }

    static SmithingAnvilMenu anvilWithGuadaleteInputs(GameTestHelper helper, ServerPlayer smith) {
        SmithingAnvilMenu menu = new SmithingAnvilMenu(1, smith.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
        menu.getSlot(SmithingAnvilMenu.TOP_SLOT).set(new ItemStack(ModItems.AZURITE_INGOT.get(), 15));
        menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).set(new ItemStack(ModItems.GUADALETE_BLUEPRINT.get()));
        return menu;
    }

    /** A Guadalete forged by a successor with the recipe's own rules, rerolled until it comes out {@code rating}. */
    static ItemStack forgedGuadalete(GameTestHelper helper, ServerPlayer smith, ItemRating rating) {
        ForgingRecipe recipe = guadaleteRecipe(helper);
        for (int i = 0; i < 1000; i++) {
            ItemStack sword = recipe.result().copy();
            EquipmentService.stampForged(smith, sword, recipe.requirements(), recipe.extras(), 0, 0);
            if (sword.get(ModDataComponents.ITEM_RATING) == rating) {
                return sword;
            }
        }
        throw new AssertionError("no " + rating + " Guadalete in 1000 forges");
    }

    /** Gives the player what Guadalete asks for: level 300 and 5000 STR. */
    static void meetRequirements(ServerPlayer player) {
        ProgressionService.set(player, ProgressionService.get(player).withProgress(300, 0, 0).with(StatType.STRENGTH, 5000));
    }

    static void hold(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ProgressionService.refreshEffects(player);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void onlyTheSuccessorCanUseTheBlueprint(GameTestHelper helper) {
        ServerPlayer smith = TestSupport.fakePlayer(helper);
        ClassService.chooseJob(smith, Mitofus.id("blacksmith"));
        helper.assertFalse(anvilWithGuadaleteInputs(helper, smith).canForge(), "a plain blacksmith can't forge Guadalete");

        ClassService.grantJob(smith, SUCCESSOR);
        helper.assertTrue(anvilWithGuadaleteInputs(helper, smith).canForge(), "the Legendary Blacksmith can");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY, timeoutTicks = ForgingRules.MAX_AUTO_TICKS + 40)
    public static void forgingGuadaleteUsesUpTheBlueprint(GameTestHelper helper) {
        ServerPlayer smith = TestSupport.fakePlayer(helper);
        ClassService.grantJob(smith, SUCCESSOR);
        SmithingAnvilMenu menu = anvilWithGuadaleteInputs(helper, smith);
        helper.assertTrue(menu.clickMenuButton(smith, SmithingAnvilMenu.AUTO_FORGE_BUTTON), "auto forging starts");
        helper.runAtTickTime(ForgingRules.MAX_AUTO_TICKS + 5, () -> {
            menu.broadcastChanges();
            helper.assertTrue(menu.getSlot(SmithingAnvilMenu.TOP_SLOT).getItem().isEmpty(), "the 15 ingots are spent");
            helper.assertTrue(menu.getSlot(SmithingAnvilMenu.BOTTOM_SLOT).getItem().isEmpty(), "the blueprint is used up");
            helper.assertTrue(smith.getInventory().items.stream().anyMatch(stack -> stack.is(ModItems.GUADALETE_SWORD.get())),
                    "Guadalete goes to the inventory");
            helper.succeed();
        });
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteIsUniqueOrLegendaryWithTheFixedEffects(GameTestHelper helper) {
        ServerPlayer smith = TestSupport.fakePlayer(helper);
        ClassService.grantJob(smith, SUCCESSOR);
        ForgingRecipe recipe = guadaleteRecipe(helper);
        // Even a sure extra option changes nothing: Guadalete only carries its fixed options.
        double surelyAnExtraOption = 1.0;
        int uniques = 0;
        int legendaries = 0;
        for (int i = 0; i < 100; i++) {
            ItemStack sword = recipe.result().copy();
            EquipmentService.stampForged(smith, sword, recipe.requirements(), recipe.extras(), 0, surelyAnExtraOption);
            ItemRating rating = sword.get(ModDataComponents.ITEM_RATING);
            if (rating == ItemRating.UNIQUE) {
                uniques++;
            } else if (rating == ItemRating.LEGENDARY) {
                legendaries++;
            }
            helper.assertTrue(rating == ItemRating.UNIQUE || rating == ItemRating.LEGENDARY, "got " + rating);
            List<ResourceLocation> options = sword.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of())
                    .stream().map(RolledOption::option).toList();
            helper.assertTrue(options.containsAll(FIXED_EFFECTS), "always the fixed effects, got " + options);
            helper.assertTrue(options.contains(Mitofus.id("guadalete_bisection")) == (rating == ItemRating.LEGENDARY),
                    "Bisection only on a Legendary, got " + options + " on " + rating);
            helper.assertTrue(options.size() == FIXED_EFFECTS.size() + (rating == ItemRating.LEGENDARY ? 1 : 0),
                    "fixed options only, no random extra: " + options);
        }
        helper.assertTrue(uniques > 0 && legendaries > 0, "both ratings showed up in 100 swords: " + uniques + " Unique, " + legendaries + " Legendary");
        helper.assertTrue(guadaleteRecipe(helper).requirements().filter(r -> r.level() == 300
                && r.stats().get(StatType.STRENGTH) == 5000).isPresent(), "requirements: level 300, 5000 STR");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteOptionsNeverRollOnOtherItems(GameTestHelper helper) {
        ServerPlayer smith = TestSupport.fakePlayer(helper);
        ProgressionService.set(smith, ProgressionService.get(smith).with(StatType.DEXTERITY, 200)); // high ratings, more options
        int rolled = 0;
        for (int i = 0; i < 200; i++) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            EquipmentService.stampForged(smith, sword, Optional.empty());
            for (RolledOption option : sword.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of())) {
                rolled++;
                helper.assertFalse(option.option().getPath().startsWith("guadalete_"), "rolled a fixed-only option: " + option.option());
            }
        }
        helper.assertTrue(rolled > 0, "the forge rolled no option at all in 200 swords, so this test proves nothing");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteGivesAgilityOnlyToWhoMeetsItsRequirements(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.grantJob(player, SUCCESSOR);
        ItemStack guadalete = forgedGuadalete(helper, player, ItemRating.UNIQUE);
        int baseAgility = ProgressionService.effectiveStats(player).agility();

        hold(player, guadalete);
        helper.assertTrue(ProgressionService.effectiveStats(player).agility() == baseAgility, "nobody meets Guadalete's requirements");
        meetRequirements(player);
        int agility = ProgressionService.effectiveStats(player).agility();
        helper.assertTrue(agility == baseAgility + 30, "expected +30 AGI, got " + (agility - baseAgility));

        hold(player, forgedGuadalete(helper, player, ItemRating.LEGENDARY));
        agility = ProgressionService.effectiveStats(player).agility();
        helper.assertTrue(agility == baseAgility + 50, "a Legendary Guadalete gives +50 AGI, got " + (agility - baseAgility));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void bisectionIsTheLegendaryGuadaletesWeaponSkill(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.grantJob(player, SUCCESSOR);
        ItemStack unique = forgedGuadalete(helper, player, ItemRating.UNIQUE);
        ItemStack legendary = forgedGuadalete(helper, player, ItemRating.LEGENDARY);
        helper.assertTrue(SkillService.weaponSkill(unique).isEmpty(), "a Unique Guadalete has no skill");
        helper.assertTrue(SkillService.weaponSkill(legendary).filter(BISECTION::equals).isPresent(), "a Legendary one has Bisection");

        player.setData(ModAttachments.MANA, 1000f);
        hold(player, legendary);
        helper.assertTrue(SkillService.use(player, SkillService.WEAPON_SLOT) == SkillService.Result.REQUIREMENTS_UNMET,
                "it can't be cast without meeting the requirements");
        helper.assertTrue(legendary.getDamageValue() == 0, "an unmet-requirements refusal wears nothing");
        meetRequirements(player);
        helper.assertTrue(SkillService.use(player, SkillService.WEAPON_SLOT) == SkillService.Result.SUCCESS, "then it casts");
        int wear = SkillService.definition(player, BISECTION).orElseThrow().weaponWear();
        helper.assertTrue(legendary.getDamageValue() == wear, "casting wears the Guadalete by " + wear + ", got " + legendary.getDamageValue());
        helper.assertFalse(SkillService.get(player).knows(BISECTION), "without being learned");
        helper.assertTrue(SkillService.use(player, SkillService.WEAPON_SLOT) == SkillService.Result.ON_COOLDOWN, "and has a cooldown");
        helper.assertTrue(legendary.getDamageValue() == wear, "a refused cast wears nothing");
        ItemStack iron = new ItemStack(Items.IRON_SWORD);
        hold(player, iron);
        helper.assertTrue(SkillService.use(player, SkillService.WEAPON_SLOT) == SkillService.Result.EMPTY_SLOT, "only while held");
        helper.assertTrue(iron.getDamageValue() == 0, "an empty-slot refusal wears nothing");
        helper.succeed();
    }

    private static boolean frightened(LivingEntity zombie) {
        return zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && zombie.hasEffect(MobEffects.WEAKNESS);
    }

    private static boolean untouched(LivingEntity zombie) {
        return !zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !zombie.hasEffect(MobEffects.WEAKNESS);
    }

    private static Zombie zombieOfLevel(GameTestHelper helper, int level, int x, int z) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, x, 1, z);
        MobLevelService.assign(zombie, new MobLevel(level, false), Balance.DEFAULT);
        return zombie;
    }

    private static ServerPlayer guadaleteWielder(GameTestHelper helper, boolean meetRequirements) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ClassService.grantJob(player, SUCCESSOR);
        hold(player, forgedGuadalete(helper, player, ItemRating.UNIQUE));
        if (meetRequirements) {
            meetRequirements(player);
        }
        return player;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteFrightensMuchWeakerMonsters(GameTestHelper helper) {
        ServerPlayer player = guadaleteWielder(helper, true);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        EquipmentEvents.forEachActiveOption(player, (effect, value) -> effect.tickWorn(player, value));
        helper.assertTrue(frightened(zombie), "a level-1 zombie fears a level-300 wielder");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteFearNeedsAGapOfTwentyLevels(GameTestHelper helper) {
        ServerPlayer player = guadaleteWielder(helper, true);
        int gap = Math.round(fearGap(helper));
        int wielderLevel = ProgressionService.effectiveStats(player).level();
        Zombie exact = zombieOfLevel(helper, wielderLevel - gap, 2, 2); // exactly the threshold
        Zombie short1 = zombieOfLevel(helper, wielderLevel - gap + 1, 2, 0); // one level short
        EquipmentEvents.forEachActiveOption(player, (effect, value) -> effect.tickWorn(player, value));
        helper.assertTrue(gap == 20, "Guadalete's fear needs a gap of 20 levels, recipe says " + gap);
        helper.assertTrue(frightened(exact), "a gap of exactly " + gap + " levels frightens");
        helper.assertTrue(untouched(short1), "a gap of " + (gap - 1) + " levels doesn't");
        helper.succeed();
    }

    /** The fear level gap the Guadalete recipe gives, read from the recipe so the test doesn't repeat the literal. */
    private static float fearGap(GameTestHelper helper) {
        return guadaleteRecipe(helper).extras().fixedOptions().stream()
                .filter(option -> option.option().equals(Mitofus.id("guadalete_fear")))
                .findFirst().orElseThrow().value();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteFearSparesBosses(GameTestHelper helper) {
        ServerPlayer player = guadaleteWielder(helper, true);
        BoneLord boss = ModEntities.BONE_LORD.get().spawn(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), MobSpawnType.SPAWN_EGG);
        helper.assertTrue(boss != null, "the boss should spawn");
        Zombie control = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 0);
        EquipmentEvents.forEachActiveOption(player, (effect, value) -> effect.tickWorn(player, value));
        helper.assertTrue(frightened(control), "control: a level-1 zombie fears the wielder");
        helper.assertTrue(untouched(boss), "a boss never feels fear");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteFearReachesItsRadius(GameTestHelper helper) {
        ServerPlayer player = guadaleteWielder(helper, true);
        // The player stands at (0.5, 0.5) and the search box is the player's box inflated by the radius (8): a zombie in
        // block x=8 overlaps it, one in block x=10 doesn't.
        Zombie inside = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 8, 1, 0);
        Zombie outside = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 10, 1, 0);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5)));
        EquipmentEvents.forEachActiveOption(player, (effect, value) -> effect.tickWorn(player, value));
        helper.assertTrue(frightened(inside), "a monster at the edge of the radius is frightened");
        helper.assertTrue(untouched(outside), "a monster past the radius isn't");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteFearDoesNotReachFarMonsters(GameTestHelper helper) {
        ServerPlayer player = guadaleteWielder(helper, true);
        Zombie far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 12, 1, 0); // the radius is 8 blocks
        EquipmentEvents.forEachActiveOption(player, (effect, value) -> effect.tickWorn(player, value));
        helper.assertTrue(untouched(far), "a monster outside the radius isn't frightened");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteFearNeedsItsRequirements(GameTestHelper helper) {
        ServerPlayer player = guadaleteWielder(helper, false);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        EquipmentEvents.forEachActiveOption(player, (effect, value) -> effect.tickWorn(player, value));
        helper.assertTrue(untouched(zombie), "without meeting the requirements nothing frightens");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void guadaleteFearWorksThroughThePlayerTickOncePerSecond(GameTestHelper helper) {
        ServerPlayer player = guadaleteWielder(helper, true);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 1, 2);
        player.tickCount = 41;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        helper.assertTrue(untouched(zombie), "off the second mark nothing happens");
        player.tickCount = 40;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        helper.assertTrue(frightened(zombie), "the player tick event frightens on the second mark");
        helper.succeed();
    }
}
