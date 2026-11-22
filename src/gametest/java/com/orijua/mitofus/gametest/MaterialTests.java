package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.core.registry.ModRecipes;
import com.orijua.mitofus.feature.smithing.ForgingRecipeInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class MaterialTests {
    private MaterialTests() {
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void mithrilOreDropsRawMithril(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        List<ItemStack> drops = Block.getDrops(ModBlocks.MITHRIL_ORE.get().defaultBlockState(), helper.getLevel(), pos,
                null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(ModItems.RAW_MITHRIL.get())), "ore should drop raw mithril: " + drops);
        helper.assertTrue(ModBlocks.MITHRIL_ORE.get().defaultBlockState().requiresCorrectToolForDrops(), "needs a pickaxe");
        helper.succeed();
    }

    /** Mithril is an advanced metal: it melts into an ingot in the smelter only (SmelterTests checks the furnace refuses it). */
    @GameTest(template = TestSupport.EMPTY)
    public static void rawMithrilMeltsIntoIngotInTheSmelter(GameTestHelper helper) {
        ItemStack result = helper.getLevel().getRecipeManager()
                .getRecipeFor(ModRecipes.MELTING.get(), new SingleRecipeInput(new ItemStack(ModItems.RAW_MITHRIL.get())), helper.getLevel())
                .map(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(ModItems.MITHRIL_INGOT.get()), "raw mithril should melt into an ingot, got " + result);
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void mithrilOreGenerates(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.registryOrThrow(Registries.PLACED_FEATURE).containsKey(Mitofus.id("ore_mithril")),
                "the placed feature should load from the datapack");
        helper.assertTrue(registries.registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).containsKey(Mitofus.id("add_ore_mithril")),
                "and be added to the overworld's biomes");
        helper.succeed();
    }

    /** Wolfram is iron tier: a stone pickaxe is enough, and both ores drop raw wolfram. */
    @GameTest(template = TestSupport.EMPTY)
    public static void wolframOreDropsRawWolframWithAStonePickaxe(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (Block ore : List.of(ModBlocks.WOLFRAM_ORE.get(), ModBlocks.DEEPSLATE_WOLFRAM_ORE.get())) {
            List<ItemStack> drops = Block.getDrops(ore.defaultBlockState(), helper.getLevel(), pos, null, null,
                    new ItemStack(Items.STONE_PICKAXE));
            helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(ModItems.RAW_WOLFRAM.get())), ore + " should drop raw wolfram: " + drops);
            helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(ore.defaultBlockState()), "stone pickaxe tier");
            helper.assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(ore.defaultBlockState()), "not wood");
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void wolframSmeltsIntoIngotAndForgesTheWolframSword(GameTestHelper helper) {
        for (Item source : List.of(ModItems.RAW_WOLFRAM.get(), ModItems.WOLFRAM_ORE.get(), ModItems.DEEPSLATE_WOLFRAM_ORE.get())) {
            ItemStack result = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(source)), helper.getLevel())
                    .map(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()))
                    .orElse(ItemStack.EMPTY);
            helper.assertTrue(result.is(ModItems.WOLFRAM_INGOT.get()), source + " should smelt into a wolfram ingot, got " + result);
        }
        ItemStack sword = helper.getLevel().getRecipeManager().getRecipeFor(ModRecipes.FORGING.get(),
                        new ForgingRecipeInput(new ItemStack(ModItems.WOLFRAM_INGOT.get(), 2), new ItemStack(Items.STICK)), helper.getLevel())
                .map(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(sword.is(ModItems.WOLFRAM_SWORD.get()), "2 wolfram ingots + a stick forge the Wolfram sword, got " + sword);
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(ModRecipes.FORGING.get(),
                        new ForgingRecipeInput(new ItemStack(ModItems.MITHRIL_INGOT.get(), 2), new ItemStack(Items.STICK)), helper.getLevel())
                .isEmpty(), "mithril no longer makes it");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void wolframOreGenerates(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.registryOrThrow(Registries.PLACED_FEATURE).containsKey(Mitofus.id("ore_wolfram")),
                "the placed feature should load from the datapack");
        helper.assertTrue(registries.registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS).containsKey(Mitofus.id("add_ore_wolfram")),
                "and be added to the overworld's biomes");
        helper.succeed();
    }

    /** The hardest mineral: only a diamond pickaxe breaks it for its raw azurite. */
    @GameTest(template = TestSupport.EMPTY)
    public static void blueAzuriteNeedsADiamondPickaxe(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (Block ore : List.of(ModBlocks.AZURITE_ORE.get(), ModBlocks.DEEPSLATE_AZURITE_ORE.get())) {
            List<ItemStack> drops = Block.getDrops(ore.defaultBlockState(), helper.getLevel(), pos, null, null,
                    new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(ModItems.RAW_AZURITE.get())), ore + " drops raw azurite: " + drops);
            helper.assertFalse(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(ore.defaultBlockState()), "iron isn't enough");
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void blueAzuriteGeneratesUnderForests(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.registryOrThrow(Registries.PLACED_FEATURE).containsKey(Mitofus.id("ore_azurite")),
                "the placed feature should load");
        helper.assertTrue(registries.registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .containsKey(Mitofus.id("add_ore_azurite")), "and its biome modifier");
        TagKey<Biome> forests = TagKey.create(Registries.BIOME, Mitofus.id("has_ore/azurite"));
        helper.assertTrue(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DARK_FOREST).is(forests), "under forests");
        helper.assertFalse(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS).is(forests), "not under plains");
        helper.succeed();
    }
}
