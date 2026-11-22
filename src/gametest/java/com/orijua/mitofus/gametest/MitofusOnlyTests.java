package com.orijua.mitofus.gametest;

import com.mojang.authlib.GameProfile;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.economy.TradeSaturation;
import com.orijua.mitofus.feature.economy.TradeSellPayload;
import com.orijua.mitofus.feature.economy.TradeService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.RateEquipmentModifier;
import com.orijua.mitofus.feature.mob.MobKilledEvent;
import com.orijua.mitofus.feature.npc.ApprenticeContractItem;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.npc.BlacksmithNpc;
import com.orijua.mitofus.feature.sancho.OrbeAurifero;
import com.orijua.mitofus.feature.sancho.SanchoStory;
import com.orijua.mitofus.feature.sancho.ReadOrbePayload;
import com.orijua.mitofus.feature.quest.NpcQuestPayload;
import com.orijua.mitofus.feature.quest.QuestDeadlines;
import com.orijua.mitofus.feature.quest.goal.GoalTracker;
import com.orijua.mitofus.feature.quest.goal.KillGoal;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.progression.AllocateStatPayload;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.progression.TitleService;
import com.orijua.mitofus.feature.skill.SkillService;
import com.orijua.mitofus.feature.smelting.SmelterBlockEntity;
import com.orijua.mitofus.feature.smelting.SmelterMenu;
import com.orijua.mitofus.feature.smelting.SmelterRules;
import com.orijua.mitofus.feature.smithing.SmithingAnvilMenu;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.MitofusOnly;
import com.orijua.mitofus.feature.world.MitofusShapedRecipe;
import com.orijua.mitofus.feature.world.MitofusShapelessRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * "Hide Mitofus in the real world": the real world is any dimension that is not Mitofus. Every case runs in the real
 * world ({@link VrHelmetTests#outsideMitofus}) and in Mitofus (the test server's overworld plays it by default).
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class MitofusOnlyTests {
    private static final ResourceLocation SANCHO_LINK = Mitofus.id("sancho_link");

    private MitofusOnlyTests() {
    }

    static void realWorld(GameTestHelper helper, Runnable body) {
        VrHelmetTests.outsideMitofus(helper, body);
    }

    private static void mitofus(GameTestHelper helper, Runnable body) {
        helper.assertTrue(MitofusWorld.is(helper.getLevel()), "the test level plays Mitofus here");
        body.run();
    }

    // ---- Infrastructure ----

    @GameTest(template = TestSupport.EMPTY)
    public static void theTestOverworldPlaysMitofusByDefaultAndGoesBackAfterwards(GameTestHelper helper) {
        helper.assertTrue(MitofusWorld.is(helper.getLevel()), "by default");
        helper.assertTrue(MitofusWorld.is(helper.getLevel().dimension()), "by key too");
        realWorld(helper, () -> helper.assertFalse(MitofusWorld.is(helper.getLevel().dimension()), "outside: the real world"));
        helper.assertTrue(MitofusWorld.is(helper.getLevel()), "and back to Mitofus after outsideMitofus");
        helper.succeed();
    }

    // ---- Progression ----

    @GameTest(template = TestSupport.EMPTY)
    public static void xpAndSmithingXpAreOnlyEarnedInMitofus(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        realWorld(helper, () -> {
            ProgressionService.awardXp(player, 50);
            ProgressionService.addSmithingXp(player, 50);
            helper.assertTrue(ProgressionService.get(player).equals(PlayerStats.DEFAULT), "nothing earned in the real world: " + ProgressionService.get(player));
        });
        mitofus(helper, () -> {
            ProgressionService.awardXp(player, 50);
            ProgressionService.addSmithingXp(player, 50);
            PlayerStats stats = ProgressionService.get(player);
            PlayerStats expected = ProgressionRules.addXp(Balance.current(), PlayerStats.DEFAULT, 50).stats();
            helper.assertTrue(stats.xp() == expected.xp() && stats.level() == expected.level(), "exact XP earned in Mitofus: " + stats + ", expected " + expected);
            helper.assertTrue(stats.smithingXp() == ProgressionRules.boostedSmithingXp(50, 0.0), "exact smithing XP earned in Mitofus: " + stats);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void whatWasEarnedInMitofusIsKeptInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.awardXp(player, 80);
        ProgressionService.addSmithingXp(player, 80);
        PlayerStats earned = ProgressionService.get(player);
        helper.assertFalse(earned.equals(PlayerStats.DEFAULT), "something was earned");
        realWorld(helper, () -> {
            ProgressionService.refreshEffects(player);
            ProgressionService.awardXp(player, 500);
            helper.assertTrue(ProgressionService.get(player).equals(earned), "the real world neither takes nor adds");
        });
        helper.assertTrue(ProgressionService.get(player).equals(earned), "and it is all there when coming back");
        helper.succeed();
    }

    /** A quest deadline doesn't run while the player is in the real world: the check pushes it back instead of failing the quest. */
    @GameTest(template = TestSupport.EMPTY)
    public static void questDeadlinesWaitInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ResourceLocation quest = SanchoStory.ANGER;
        long now = QuestDeadlines.now(player);
        player.setData(ModAttachments.PLAYER_QUESTS, QuestService.get(player).accept(quest, 0).withDeadline(quest, now - 1));
        realWorld(helper, () -> {
            QuestDeadlines.check(player);
            helper.assertFalse(QuestService.get(player).isFailed(quest), "the quest doesn't fail outside Mitofus");
            long deadline = QuestService.get(player).deadline(quest).orElseThrow();
            helper.assertTrue(deadline > now - 1, "the time outside was given back: " + deadline + " vs " + (now - 1));
        });
        helper.succeed();
    }

    /** Posts the death of a zombie killed by {@code player} and returns how many {@link MobKilledEvent} came out of it. */
    private static int killZombieForReal(GameTestHelper helper, ServerPlayer player) {
        int[] kills = {0};
        Consumer<MobKilledEvent> counter = event -> kills[0]++;
        NeoForge.EVENT_BUS.addListener(counter);
        try {
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(zombie, player.damageSources().playerAttack(player)));
        } finally {
            NeoForge.EVENT_BUS.unregister(counter);
        }
        return kills[0];
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void killingAMobInTheRealWorldGivesNoXpAndNoKill(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        realWorld(helper, () -> {
            helper.assertTrue(killZombieForReal(helper, player) == 0, "no MobKilledEvent (so no KillGoal either)");
            helper.assertTrue(ProgressionService.get(player).equals(PlayerStats.DEFAULT), "and no XP");
        });
        mitofus(helper, () -> {
            helper.assertTrue(killZombieForReal(helper, player) == 1, "in Mitofus the kill counts");
            PlayerStats stats = ProgressionService.get(player);
            Zombie sample = EntityType.ZOMBIE.create(helper.getLevel());
            int killXp = ProgressionRules.killXp(Balance.current(), new ProgressionRules.Victim(
                    sample.getMaxHealth(),
                    sample.getAttribute(Attributes.ATTACK_DAMAGE) != null ? sample.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0,
                    sample.getArmorValue(),
                    sample.getData(ModAttachments.FROM_SPAWNER),
                    sample.getData(ModAttachments.MOB_LEVEL).elite()));
            PlayerStats expected = ProgressionRules.addXp(Balance.current(), PlayerStats.DEFAULT, killXp).stats();
            helper.assertTrue(killXp > 0, "a zombie is worth some XP: " + killXp);
            helper.assertTrue(stats.xp() == expected.xp() && stats.level() == expected.level(), "and gives exactly that XP: " + stats + ", expected " + expected);
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void classesCannotBeChosenOrGrantedInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        realWorld(helper, () -> {
            helper.assertFalse(ClassService.chooseClass(player, 0, Mitofus.id("swordsman")), "choose is refused");
            helper.assertFalse(ClassService.grantClass(player, Mitofus.id("swordsman")), "grant is refused");
            helper.assertTrue(player.getData(ModAttachments.PLAYER_CLASSES).slots().isEmpty(), "no class");
        });
        mitofus(helper, () -> helper.assertTrue(ClassService.chooseClass(player, 0, Mitofus.id("swordsman")), "in Mitofus it works"));
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void realWorldKillsDoNotCountTowardsTitles(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ResourceLocation hunter = Mitofus.id("zombie_hunter");
        String zombieKills = KillGoal.counterKey(Optional.of(EntityType.ZOMBIE));
        realWorld(helper, () -> {
            for (int i = 0; i < 10; i++) {
                helper.assertTrue(killZombieForReal(helper, player) == 0, "kill " + i + " in the real world produces no MobKilledEvent");
            }
            helper.assertTrue(GoalTracker.counters(player).get(zombieKills) == 0, "the zombie counter stays at 0");
            TitleService.checkUnlocks(player);
            helper.assertFalse(player.getData(ModAttachments.PLAYER_IDENTITY).hasTitle(hunter), "no title in the real world");
        });
        mitofus(helper, () -> {
            TitleService.checkUnlocks(player);
            helper.assertFalse(player.getData(ModAttachments.PLAYER_IDENTITY).hasTitle(hunter), "the real-world kills did not count in Mitofus");
            for (int i = 0; i < 10; i++) {
                helper.assertTrue(killZombieForReal(helper, player) == 1, "kill " + i + " in Mitofus counts");
            }
            TitleService.checkUnlocks(player);
            helper.assertTrue(player.getData(ModAttachments.PLAYER_IDENTITY).hasTitle(hunter), "ten kills in Mitofus earn the title");
        });
        helper.succeed();
    }

    // ---- Skills ----

    @GameTest(template = TestSupport.EMPTY)
    public static void skillsReportRealWorldAndSpendNoMana(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        SkillService.learn(player, SANCHO_LINK);
        player.setData(ModAttachments.MANA, 100.0f);
        realWorld(helper, () -> {
            helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.REAL_WORLD, "REAL_WORLD");
            helper.assertTrue(player.getData(ModAttachments.MANA) == 100.0f, "no mana spent");
        });
        mitofus(helper, () -> {
            helper.assertTrue(SkillService.use(player, 0) == SkillService.Result.SUCCESS, "in Mitofus it casts");
            helper.assertTrue(player.getData(ModAttachments.MANA) < 100.0f, "and spends mana");
        });
        helper.succeed();
    }

    // ---- Mobs and loot ----

    private static boolean spawnedWithALevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        EventHooks.finalizeMobSpawn(zombie, level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
        return com.orijua.mitofus.feature.mob.MobLevelService.get((Mob) zombie).isLeveled();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void mobsSpawnedInTheRealWorldHaveNoLevel(GameTestHelper helper) {
        realWorld(helper, () -> helper.assertFalse(spawnedWithALevel(helper), "a vanilla mob in the real world"));
        mitofus(helper, () -> helper.assertTrue(spawnedWithALevel(helper), "in Mitofus it gets a level"));
        helper.succeed();
    }

    private static LootContext lootContext(GameTestHelper helper) {
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(0.5, 1, 0.5)))
                .create(LootContextParamSets.CHEST);
        return new LootContext.Builder(params).withQueriedLootTableId(BuiltInLootTables.VILLAGE_WEAPONSMITH.location()).create(Optional.empty());
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void lootEquipmentIsNotRatedInTheRealWorld(GameTestHelper helper) {
        RateEquipmentModifier modifier = new RateEquipmentModifier(new LootItemCondition[0], "chests/", 0, ItemRating.NORMAL);
        realWorld(helper, () -> {
            ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(List.of(new ItemStack(Items.IRON_SWORD)));
            modifier.apply(loot, lootContext(helper));
            helper.assertFalse(loot.get(0).has(ModDataComponents.ITEM_RATING), "no rating in the real world");
        });
        mitofus(helper, () -> {
            ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(List.of(new ItemStack(Items.IRON_SWORD)));
            modifier.apply(loot, lootContext(helper));
            helper.assertTrue(loot.get(0).has(ModDataComponents.ITEM_RATING), "rated in Mitofus");
        });
        helper.succeed();
    }

    // ---- Equipment and D1 (passive bonuses) ----

    private static List<String> modOnAttribute(ServerPlayer player, Holder<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        List<String> ids = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.id().getNamespace().equals(Mitofus.MODID)) {
                ids.add(modifier.id().getPath());
            }
        }
        return ids;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void ratedWeaponAndStatBonusesAreOffInTheRealWorldAndComeBack(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.UNIQUE);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        ProgressionService.set(player, PlayerStats.DEFAULT.withProgress(15, 0, 0).with(StatType.STRENGTH, 4)); // level 15: what an iron unique piece asks for
        List<String> inMitofus = modOnAttribute(player, Attributes.ATTACK_DAMAGE);
        helper.assertTrue(inMitofus.stream().anyMatch(id -> id.startsWith("equip_")) && inMitofus.contains("stat_strength"),
                "in Mitofus: equipment and strength bonuses: " + inMitofus);
        realWorld(helper, () -> {
            ProgressionService.refreshEffects(player);
            helper.assertTrue(modOnAttribute(player, Attributes.ATTACK_DAMAGE).isEmpty(),
                    "no mod modifiers on the attack damage in the real world: " + modOnAttribute(player, Attributes.ATTACK_DAMAGE));
        });
        ProgressionService.refreshEffects(player);
        helper.assertTrue(modOnAttribute(player, Attributes.ATTACK_DAMAGE).containsAll(inMitofus), "they come back");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void maxHealthDropsInTheRealWorldWithoutKillingAndComesBack(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        double base = player.getMaxHealth();
        ProgressionService.set(player, PlayerStats.DEFAULT.with(StatType.STAMINA, 10));
        double boosted = player.getMaxHealth();
        helper.assertTrue(boosted > base, "stamina raises max health: " + base + " -> " + boosted);
        player.setHealth((float) boosted);
        realWorld(helper, () -> {
            ProgressionService.refreshEffects(player);
            helper.assertTrue(player.getMaxHealth() == base, "max health back to the base: " + player.getMaxHealth());
            helper.assertTrue(player.getHealth() <= base && player.getHealth() > 0 && !player.isDeadOrDying(), "current health limited, not dead: " + player.getHealth());
        });
        ProgressionService.refreshEffects(player);
        helper.assertTrue(player.getMaxHealth() == boosted, "the maximum is back in Mitofus: " + player.getMaxHealth());
        helper.assertTrue(player.isAlive(), "alive");
        helper.succeed();
    }

    // ---- Recipes ----

    private static CraftingInput grid(ItemStack... stacks) {
        return CraftingInput.of(3, 3, List.of(stacks));
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String path) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager().byKey(Mitofus.id(path)).orElseThrow();
        return (CraftingRecipe) holder.value();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theModsCraftingRecipesOnlyMatchInMitofusExceptTheHelmet(GameTestHelper helper) {
        ItemStack e = ItemStack.EMPTY;
        ItemStack block = new ItemStack(Items.IRON_BLOCK);
        ItemStack mithril = new ItemStack(ModItems.MITHRIL_INGOT.get());
        CraftingInput anvilGrid = grid(block, block.copy(), block.copy(), e, mithril, e, mithril.copy(), mithril.copy(), mithril.copy());
        CraftingRecipe anvil = recipe(helper, "smithing_anvil");
        helper.assertTrue(anvil instanceof MitofusShapedRecipe, "the anvil recipe is Mitofus-only");
        helper.assertTrue(recipe(helper, "smelter") instanceof MitofusShapedRecipe, "the smelter too");
        for (String path : new String[]{"workshop_ledger", "enhancement_stone", "respec_scroll"}) {
            helper.assertTrue(recipe(helper, path) instanceof MitofusShapelessRecipe, path + " is Mitofus-only");
        }
        helper.assertFalse(recipe(helper, "helmet") instanceof MitofusShapedRecipe, "the vrhelmet recipe is plain");

        ItemStack glass = new ItemStack(Items.GLASS);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        CraftingInput vrHelmetGrid = grid(glass, glass.copy(), glass.copy(), iron, new ItemStack(Items.WHITE_BED), iron.copy(),
                iron.copy(), new ItemStack(Items.REDSTONE), iron.copy());
        CraftingRecipe vrhelmet = recipe(helper, "helmet");

        CraftingInput ledgerGrid = grid(new ItemStack(Items.BOOK), new ItemStack(Items.IRON_INGOT), e, e, e, e, e, e, e);
        realWorld(helper, () -> {
            helper.assertFalse(anvil.matches(anvilGrid, helper.getLevel()), "the anvil recipe does not match in the real world");
            helper.assertFalse(recipe(helper, "workshop_ledger").matches(ledgerGrid, helper.getLevel()), "nor the ledger");
            helper.assertTrue(vrhelmet.matches(vrHelmetGrid, helper.getLevel()), "the vrhelmet can be crafted in the real world");
        });
        mitofus(helper, () -> {
            helper.assertTrue(anvil.matches(anvilGrid, helper.getLevel()), "the anvil recipe matches in Mitofus");
            helper.assertTrue(recipe(helper, "workshop_ledger").matches(ledgerGrid, helper.getLevel()), "and the ledger");
            helper.assertTrue(vrhelmet.matches(vrHelmetGrid, helper.getLevel()), "and the vrhelmet");
        });
        helper.succeed();
    }

    // ---- Placing and using ----

    private static List<net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block>> oreBlocks() {
        return List.of(ModBlocks.WOLFRAM_ORE, ModBlocks.DEEPSLATE_WOLFRAM_ORE, ModBlocks.MITHRIL_ORE, ModBlocks.DEEPSLATE_MITHRIL_ORE,
                ModBlocks.AZURITE_ORE, ModBlocks.DEEPSLATE_AZURITE_ORE);
    }

    /**
     * The exceptions of {@link MitofusOnly} are strings (helmets, account_terminal, phone); here they are checked against what is really
     * registered. Catches a renamed exception and any mod object that would escape the restriction (ores included).
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void onlyTheRealWorldDevicesWorkThere(GameTestHelper helper) {
        Set<ResourceLocation> expectedItems = new TreeSet<>(List.of(com.orijua.mitofus.core.registry.ModItems.HELMET.getId(), com.orijua.mitofus.core.registry.ModItems.ADVANCED_HELMET.getId(),
                com.orijua.mitofus.core.registry.ModItems.DIAMOND_HELMET.getId(),
                ModBlocks.ACCOUNT_TERMINAL.getId(), com.orijua.mitofus.core.registry.ModItems.PHONE.getId()));
        Set<ResourceLocation> expectedBlocks = new TreeSet<>(List.of(ModBlocks.BODY.getId(), ModBlocks.ADVANCED_BODY.getId(), ModBlocks.DIAMOND_BODY.getId(), ModBlocks.ACCOUNT_TERMINAL.getId()));
        Set<ResourceLocation> actualItems = new TreeSet<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (id.getNamespace().equals(Mitofus.MODID) && !MitofusOnly.isMitofusItem(id)) {
                actualItems.add(id);
            }
        }
        helper.assertTrue(actualItems.equals(expectedItems), "items that work in the real world: " + actualItems + ", expected " + expectedItems);
        Set<ResourceLocation> actualBlocks = new TreeSet<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (id.getNamespace().equals(Mitofus.MODID) && !MitofusOnly.blocksPlacement(id)) {
                actualBlocks.add(id);
            }
        }
        helper.assertTrue(actualBlocks.equals(expectedBlocks), "blocks that can be placed in the real world: " + actualBlocks + ", expected " + expectedBlocks);
        helper.succeed();
    }

    /** The three ores only generate in Mitofus: each loaded placed feature carries the mitofus_only filter. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theOrePlacedFeaturesCarryTheMitofusFilter(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        for (String ore : List.of("ore_wolfram", "ore_mithril", "ore_azurite")) {
            var placed = registry.get(Mitofus.id(ore));
            helper.assertTrue(placed != null, ore + " is loaded");
            helper.assertTrue(placed.placement().stream().anyMatch(modifier -> modifier == com.orijua.mitofus.feature.world.MitofusOnlyPlacement.INSTANCE),
                    ore + " has the mitofus_only filter");
        }
        helper.succeed();
    }

    private static boolean placeCancelled(GameTestHelper helper, ServerPlayer player, net.minecraft.world.level.block.Block block) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block);
        BlockSnapshot snapshot = BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), helper.absolutePos(pos));
        return NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), player)).isCanceled();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theAnvilAndTheSmelterCannotBePlacedInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        realWorld(helper, () -> {
            helper.assertTrue(placeCancelled(helper, player, ModBlocks.SMITHING_ANVIL.get()), "the anvil: cancelled");
            helper.assertTrue(placeCancelled(helper, player, ModBlocks.SMELTER.get()), "the smelter: cancelled");
            for (var ore : oreBlocks()) {
                helper.assertTrue(placeCancelled(helper, player, ore.get()), ore.getId().getPath() + ": cancelled");
            }
            helper.assertFalse(placeCancelled(helper, player, net.minecraft.world.level.block.Blocks.STONE), "a vanilla block: allowed");
        });
        mitofus(helper, () -> {
            helper.assertFalse(placeCancelled(helper, player, ModBlocks.SMITHING_ANVIL.get()), "the anvil is allowed in Mitofus");
            helper.assertFalse(placeCancelled(helper, player, ModBlocks.SMELTER.get()), "and the smelter");
        });
        helper.succeed();
    }

    private static boolean rightClickCancelled(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND)).isCanceled();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theModsItemsDoNothingOnRightClickInTheRealWorld(GameTestHelper helper) {
        MenuSpy player = spy(helper);
        realWorld(helper, () -> {
            helper.assertTrue(rightClickCancelled(player, new ItemStack(ModItems.RESPEC_SCROLL.get())), "the respec scroll: inert");
            helper.assertTrue(player.warned(), "and the player is told (action bar)");
            player.clearMessage();
            helper.assertFalse(rightClickCancelled(player, new ItemStack(Items.IRON_SWORD)), "a vanilla item works");
            helper.assertTrue(player.lastMessage == null, "with no warning");
            helper.assertTrue(rightClickCancelled(player, new ItemStack(ModItems.WORKSHOP_LEDGER.get())), "the ledger: inert");
            helper.assertFalse(rightClickCancelled(player, new ItemStack(ModItems.HELMET.get())), "the helmet works");
            helper.assertTrue(rightClickCancelled(player, new ItemStack(ModBlocks.WOLFRAM_ORE.get())), "an ore block: inert");
            helper.assertFalse(rightClickCancelled(player, new ItemStack(Items.IRON_SWORD)), "a vanilla item works");
            helper.assertFalse(rightClickCancelled(player, ItemStack.EMPTY), "an empty hand works");
        });
        mitofus(helper, () -> {
            player.clearMessage();
            helper.assertFalse(rightClickCancelled(player, new ItemStack(ModItems.RESPEC_SCROLL.get())), "in Mitofus it works");
            helper.assertTrue(player.lastMessage == null, "and nobody is warned");
        });
        helper.succeed();
    }

    /** A fake player that records the menus it is asked to open (a plain fake player never opens any). */
    private static final class MenuSpy extends FakePlayer {
        int opened;
        MenuProvider last;
        Component lastMessage;
        boolean lastOverlay;

        MenuSpy(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "mitofus-menu-spy"));
        }

        @Override
        public void displayClientMessage(Component message, boolean overlay) {
            lastMessage = message;
            lastOverlay = overlay;
        }

        void clearMessage() {
            lastMessage = null;
            lastOverlay = false;
        }

        boolean warned() {
            return lastOverlay && Component.translatable(MitofusOnly.WARNING_KEY).equals(lastMessage);
        }

        @Override
        public OptionalInt openMenu(MenuProvider menu, Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
            opened++;
            last = menu;
            return OptionalInt.of(opened);
        }
    }

    private static MenuSpy spy(GameTestHelper helper) {
        MenuSpy player = new MenuSpy(helper.getLevel());
        Vec3 origin = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.moveTo(origin.x, origin.y, origin.z, 0.0f, 0.0f);
        return player;
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theAnvilOpensNoMenuInTheRealWorld(GameTestHelper helper) {
        MenuSpy player = spy(helper);
        BlockPos anvil = new BlockPos(1, 1, 1);
        helper.setBlock(anvil, ModBlocks.SMITHING_ANVIL.get());
        BlockState state = helper.getBlockState(anvil);
        realWorld(helper, () -> {
            InteractionResult result = state.useWithoutItem(helper.getLevel(), player, hit(helper, anvil));
            helper.assertTrue(result.consumesAction(), "the click is consumed");
            helper.assertTrue(player.opened == 0, "but no menu opens");
        });
        mitofus(helper, () -> {
            state.useWithoutItem(helper.getLevel(), player, hit(helper, anvil));
            helper.assertTrue(player.opened == 1 && player.last.createMenu(1, player.getInventory(), player) instanceof SmithingAnvilMenu, "in Mitofus the menu opens");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theSmelterOpensNoMenuAndDoesNotAdvanceInTheRealWorld(GameTestHelper helper) {
        MenuSpy player = spy(helper);
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.SMELTER.get());
        SmelterBlockEntity smelter = helper.getBlockEntity(pos);
        BlockState state = helper.getBlockState(pos);
        realWorld(helper, () -> {
            state.useWithoutItem(helper.getLevel(), player, hit(helper, pos));
            helper.assertTrue(player.opened == 0, "no menu in the real world");
        });
        mitofus(helper, () -> {
            state.useWithoutItem(helper.getLevel(), player, hit(helper, pos));
            helper.assertTrue(player.opened == 1 && player.last instanceof SmelterBlockEntity, "the menu opens in Mitofus");
        });

        smelter.items().setItem(SmelterBlockEntity.INPUT, new ItemStack(Items.RAW_IRON));
        smelter.items().setItem(SmelterBlockEntity.FUEL, new ItemStack(Items.STICK, 5));
        SmelterMenu menu = new SmelterMenu(1, player.getInventory(), smelter,
                net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
        helper.assertTrue(menu.clickMenuButton(player, SmelterMenu.AUTO_BUTTON), "the fire is lit");
        realWorld(helper, () -> {
            for (int i = 0; i < 40; i++) {
                SmelterBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getLevel().getBlockState(helper.absolutePos(pos)), smelter);
            }
            helper.assertTrue(smelter.items().getItem(SmelterBlockEntity.FUEL).getCount() == 5, "no fuel burns in the real world");
            helper.assertTrue(smelter.temperature() == SmelterRules.AMBIENT, "and it does not heat up: " + smelter.temperature());
        });
        mitofus(helper, () -> {
            for (int i = 0; i < 40; i++) {
                SmelterBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getLevel().getBlockState(helper.absolutePos(pos)), smelter);
            }
            helper.assertTrue(smelter.items().getItem(SmelterBlockEntity.FUEL).getCount() < 5, "in Mitofus the fuel burns");
        });
        helper.succeed();
    }

    // ---- Apprentices ----

    @GameTest(template = TestSupport.EMPTY)
    public static void hiringAnApprenticeFailsInTheRealWorldAndKeepsTheContract(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, PlayerStats.DEFAULT.withSmithingXp(50 * 50 * 10));
        helper.assertTrue(ProgressionService.effectiveSmithingLevel(player) >= 50, "level 50 smith");
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        realWorld(helper, () -> {
            helper.assertTrue(ApprenticeContractItem.hire(player, at).isEmpty(), "hire gives nothing");
            ItemStack contract = new ItemStack(ModItems.APPRENTICE_CONTRACT.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, contract);
            InteractionResult result = contract.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, new BlockPos(1, 0, 1))));
            helper.assertTrue(result == InteractionResult.FAIL, "using it fails");
            helper.assertTrue(contract.getCount() == 1, "the contract is not spent");
            helper.assertTrue(player.getData(ModAttachments.APPRENTICES).isEmpty(), "nobody hired");
        });
        mitofus(helper, () -> {
            Optional<ApprenticeSmith> hired = ApprenticeContractItem.hire(player, at);
            helper.assertTrue(hired.isPresent(), "in Mitofus he is hired");
            hired.get().discard();
        });
        helper.succeed();
    }

    // ---- Dimension change, clicks on blocks and entities ----

    /** The player changes dimension: only the event is posted, so it is the listener (not the test) that refreshes the bonuses. */
    @GameTest(template = TestSupport.EMPTY)
    public static void changingDimensionTurnsTheBonusesOffAndOn(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.UNIQUE);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        ProgressionService.set(player, PlayerStats.DEFAULT.withProgress(15, 0, 0).with(StatType.STRENGTH, 4)); // level 15: what an iron unique piece asks for
        List<String> inMitofus = modOnAttribute(player, Attributes.ATTACK_DAMAGE);
        helper.assertTrue(inMitofus.stream().anyMatch(id -> id.startsWith("equip_")) && inMitofus.contains("stat_strength"),
                "in Mitofus: equipment and strength bonuses: " + inMitofus);
        realWorld(helper, () -> {
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, MitofusWorld.LEVEL, Level.OVERWORLD));
            helper.assertTrue(modOnAttribute(player, Attributes.ATTACK_DAMAGE).isEmpty(),
                    "leaving Mitofus turns the bonuses off: " + modOnAttribute(player, Attributes.ATTACK_DAMAGE));
        });
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, MitofusWorld.LEVEL));
        helper.assertTrue(modOnAttribute(player, Attributes.ATTACK_DAMAGE).containsAll(inMitofus), "entering Mitofus turns them back on");
        helper.succeed();
    }

    private static PlayerInteractEvent.RightClickBlock rightClickBlock(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos relative) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, helper.absolutePos(relative), hit(helper, relative)));
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void aModItemOnABlockDoesNotActButTheBlockStillReacts(GameTestHelper helper) {
        MenuSpy player = spy(helper);
        BlockPos pos = new BlockPos(1, 1, 1);
        realWorld(helper, () -> {
            PlayerInteractEvent.RightClickBlock scroll = rightClickBlock(helper, player, new ItemStack(ModItems.RESPEC_SCROLL.get()), pos);
            helper.assertTrue(scroll.getUseItem() == TriState.FALSE, "the mod item does nothing");
            helper.assertTrue(scroll.getUseBlock() != TriState.FALSE && !scroll.isCanceled(), "but the block still reacts");
            helper.assertTrue(player.warned(), "and the player is told");
            player.clearMessage();
            PlayerInteractEvent.RightClickBlock vrhelmet = rightClickBlock(helper, player, new ItemStack(ModItems.HELMET.get()), pos);
            helper.assertTrue(vrhelmet.getUseItem() == TriState.DEFAULT, "the helmet is used as usual");
            PlayerInteractEvent.RightClickBlock ore = rightClickBlock(helper, player, new ItemStack(ModBlocks.MITHRIL_ORE.get()), pos);
            helper.assertTrue(ore.getUseItem() == TriState.FALSE, "an ore block does nothing either");
            helper.assertTrue(player.warned(), "and the player is told");
            player.clearMessage();
            PlayerInteractEvent.RightClickBlock sword = rightClickBlock(helper, player, new ItemStack(Items.IRON_SWORD), pos);
            helper.assertTrue(sword.getUseItem() == TriState.DEFAULT, "a vanilla item too");
            helper.assertTrue(player.lastMessage == null, "the vrhelmet and vanilla items do not warn");
        });
        mitofus(helper, () -> {
            PlayerInteractEvent.RightClickBlock scroll = rightClickBlock(helper, player, new ItemStack(ModItems.RESPEC_SCROLL.get()), pos);
            helper.assertTrue(scroll.getUseItem() == TriState.DEFAULT, "in Mitofus the mod item is used as usual");
            helper.assertTrue(player.lastMessage == null, "with no warning");
        });
        helper.succeed();
    }

    private static boolean entityInteractCancelled(MenuSpy player, net.minecraft.world.entity.Entity target) {
        PlayerInteractEvent.EntityInteract event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target));
        return event.isCanceled() && event.getCancellationResult() == InteractionResult.FAIL;
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theModsNpcsDoNotAnswerToRightClickInTheRealWorld(GameTestHelper helper) {
        MenuSpy player = spy(helper);
        BlacksmithNpc smith = TestSupport.smith(helper);
        net.minecraft.world.entity.npc.Villager villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        realWorld(helper, () -> {
            helper.assertTrue(entityInteractCancelled(player, smith), "the blacksmith: refused");
            helper.assertTrue(player.warned(), "and the player is told");
            helper.assertTrue(player.interactOn(smith, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "interactOn gives FAIL");
            player.clearMessage();
            helper.assertFalse(entityInteractCancelled(player, villager), "a vanilla villager: untouched");
            helper.assertTrue(player.lastMessage == null, "with no warning");
        });
        mitofus(helper, () -> {
            helper.assertFalse(entityInteractCancelled(player, smith), "in Mitofus the blacksmith answers");
            helper.assertTrue(player.interactOn(smith, InteractionHand.MAIN_HAND) != InteractionResult.FAIL, "interactOn is not refused");
        });
        helper.succeed();
    }

    // ---- Network ----

    private static IPayloadContext contextOf(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> method.getName().equals("player") ? player : null);
    }

    /** Calls a package-private handler of ServerPayloadHandler by reflection, as the network would. */
    private static void invokeHandler(String name, Class<?> payloadType, Object payload, ServerPlayer player) {
        try {
            Method handler = Class.forName("com.orijua.mitofus.core.network.ServerPayloadHandler")
                    .getDeclaredMethod(name, payloadType, IPayloadContext.class);
            handler.setAccessible(true);
            handler.invoke(null, payload, contextOf(player));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void tradeSellOutsideMitofusTouchesNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.EPIC);
        player.getInventory().setItem(3, sword.copy());
        TradeSellPayload payload = new TradeSellPayload(java.util.List.of(new TradeSellPayload.Entry(3, sword.copy(), 16)));
        realWorld(helper, () -> {
            invokeHandler("tradeSell", TradeSellPayload.class, payload, player);
            helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), sword), "the sword stays in the real world");
            helper.assertTrue(player.getInventory().countItem(ModItems.GOLD_COIN.get()) == 0, "nothing paid");
            helper.assertTrue(TradeService.saturation(player).equals(TradeSaturation.EMPTY), "nothing counted");
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void allocatingStatPointsIsIgnoredInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        ProgressionService.set(player, PlayerStats.DEFAULT.withProgress(1, 0, 5));
        AllocateStatPayload payload = new AllocateStatPayload(StatType.STRENGTH, 1);
        realWorld(helper, () -> {
            invokeHandler("allocateStat", AllocateStatPayload.class, payload, player);
            helper.assertTrue(ProgressionService.get(player).freePoints() == 5 && ProgressionService.get(player).strength() == 0,
                    "no points spent in the real world: " + ProgressionService.get(player));
        });
        mitofus(helper, () -> {
            invokeHandler("allocateStat", AllocateStatPayload.class, payload, player);
            helper.assertTrue(ProgressionService.get(player).freePoints() == 4 && ProgressionService.get(player).strength() == 1,
                    "in Mitofus one point is spent: " + ProgressionService.get(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void readingSanchosBookIsRefusedInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        PlayerStats before = PlayerStats.DEFAULT.withProgress(5, 10, 7);
        ProgressionService.set(player, before);
        player.getInventory().add(new ItemStack(ModItems.ORBE_AURIFERO.get()));
        realWorld(helper, () -> {
            invokeHandler("readOrbeAurifero", ReadOrbePayload.class, new ReadOrbePayload(), player);
            helper.assertTrue(player.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 1, "the book is still there");
            helper.assertTrue(ProgressionService.get(player).equals(before), "the stats are untouched: " + ProgressionService.get(player));
            helper.assertFalse(QuestService.get(player).isCompleted(SanchoStory.LEGACY), "the legacy is not completed");
            helper.assertFalse(OrbeAurifero.read(player), "and OrbeAurifero.read refuses by itself");
            helper.assertTrue(player.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 1, "still untouched");
        });
        mitofus(helper, () -> {
            invokeHandler("readOrbeAurifero", ReadOrbePayload.class, new ReadOrbePayload(), player);
            helper.assertTrue(player.getInventory().countItem(ModItems.ORBE_AURIFERO.get()) == 0, "in Mitofus the book is spent");
            helper.assertTrue(ProgressionService.get(player).level() == 1, "and the level is 1: " + ProgressionService.get(player));
        });
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void npcQuestIsIgnoredInTheRealWorld(GameTestHelper helper) {
        ServerPlayer player = TestSupport.fakePlayer(helper);
        BlacksmithNpc smith = TestSupport.smith(helper);
        ResourceLocation firewood = Mitofus.id("smith_firewood");
        NpcQuestPayload payload = new NpcQuestPayload(smith.getId(), firewood, NpcQuestPayload.Action.ACCEPT);
        realWorld(helper, () -> {
            invokeHandler("npcQuest", NpcQuestPayload.class, payload, player);
            helper.assertFalse(QuestService.get(player).isActive(firewood), "the quest is not accepted in the real world");
        });
        mitofus(helper, () -> {
            invokeHandler("npcQuest", NpcQuestPayload.class, payload, player);
            helper.assertTrue(QuestService.get(player).isActive(firewood), "in Mitofus it is");
        });
        helper.succeed();
    }
}
