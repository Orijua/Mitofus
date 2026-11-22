package com.orijua.mitofus.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.LootEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class LootTests {
    private LootTests() {
    }

    /** The global loot modifier: every weapon and armor piece in a vanilla chest comes rated. */
    @GameTest(template = TestSupport.EMPTY)
    public static void chestEquipmentComesRated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable weaponsmith = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.VILLAGE_WEAPONSMITH);
        int equipmentSeen = 0;
        for (int i = 0; i < 40; i++) {
            for (ItemStack stack : weaponsmith.getRandomItems(chestParams(helper), i)) {
                if (EquipmentService.isEquipment(stack)) {
                    equipmentSeen++;
                    helper.assertTrue(stack.has(ModDataComponents.ITEM_RATING), "chest equipment should be rated: " + stack);
                } else {
                    helper.assertFalse(stack.has(ModDataComponents.ITEM_RATING), "only equipment gets a rating: " + stack);
                }
            }
        }
        helper.assertTrue(equipmentSeen > 0, "40 weaponsmith chests should hold some equipment");
        helper.succeed();
    }

    /** The loot function, read from JSON as a loot table would: honors min_rating. */
    @GameTest(template = TestSupport.EMPTY)
    public static void rateEquipmentFunctionRespectsMinRating(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootItemFunction function = LootItemFunctions.ROOT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()),
                JsonParser.parseString("{\"function\": \"mitofus:rate_equipment\", \"luck\": 10, \"min_rating\": \"epic\"}")).getOrThrow();
        LootContext context = new LootContext.Builder(chestParams(helper)).create(Optional.empty());
        for (int i = 0; i < 20; i++) {
            ItemStack sword = function.apply(new ItemStack(Items.IRON_SWORD), context);
            ItemRating rating = sword.get(ModDataComponents.ITEM_RATING);
            helper.assertTrue(rating != null && rating.ordinal() >= ItemRating.EPIC.ordinal(), "expected Epic or better, got " + rating);
        }
        ItemStack bread = function.apply(new ItemStack(Items.BREAD), context);
        helper.assertFalse(bread.has(ModDataComponents.ITEM_RATING), "food is not equipment");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void alreadyRatedLootKeepsItsRating(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModDataComponents.ITEM_RATING, ItemRating.ANCESTRAL);
        EquipmentService.rateLoot(helper.getLevel(), sword, 0, ItemRating.NORMAL, helper.getLevel().getRandom());
        helper.assertTrue(sword.get(ModDataComponents.ITEM_RATING) == ItemRating.ANCESTRAL, "a forged Ancestral stays Ancestral");
        helper.succeed();
    }

    /** A low-level elite drops something from the tag, never netherite (its weight there is 0), and may be Normal. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aLowLevelEliteDropsNoNetherite(GameTestHelper helper) {
        for (int i = 0; i < 60; i++) {
            ItemStack drop = LootEvents.eliteDrop(helper.getLevel(), helper.getLevel().getRandom(), 5).orElseThrow();
            helper.assertTrue(drop.is(LootEvents.ELITE_DROPS), "elite drops come from the tag: " + drop);
            helper.assertFalse(drop.getItem().toString().contains("netherite"), "no netherite at level 5: " + drop);
            helper.assertTrue(drop.get(ModDataComponents.ITEM_RATING) != null, "the piece is rated");
        }
        helper.succeed();
    }

    /** The higher the elite's level, the better the floor: Rare from 35, Epic from 50. */
    @GameTest(template = TestSupport.EMPTY)
    public static void aHighLevelEliteDropsRatedAtTheLevelsMinimum(GameTestHelper helper) {
        for (int i = 0; i < 40; i++) {
            ItemRating rare = LootEvents.eliteDrop(helper.getLevel(), helper.getLevel().getRandom(), 40).orElseThrow().get(ModDataComponents.ITEM_RATING);
            helper.assertTrue(rare != null && rare.ordinal() >= ItemRating.RARE.ordinal(), "level 40: Rare or better, got " + rare);
            ItemRating epic = LootEvents.eliteDrop(helper.getLevel(), helper.getLevel().getRandom(), 60).orElseThrow().get(ModDataComponents.ITEM_RATING);
            helper.assertTrue(epic != null && epic.ordinal() >= ItemRating.EPIC.ordinal(), "level 60: Epic or better, got " + epic);
        }
        helper.succeed();
    }

    private static LootParams chestParams(GameTestHelper helper) {
        return new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(0.5, 1, 0.5)))
                .create(LootContextParamSets.CHEST);
    }
}
