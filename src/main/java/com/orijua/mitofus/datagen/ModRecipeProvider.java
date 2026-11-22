package com.orijua.mitofus.datagen;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.equipment.option.FixedOption;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.smelting.MeltingRecipe;
import com.orijua.mitofus.feature.smelting.SmelterRules;
import com.orijua.mitofus.feature.smithing.ForgingExtras;
import com.orijua.mitofus.feature.smithing.ForgingRecipe;
import com.orijua.mitofus.feature.world.MitofusShapedRecipe;
import com.orijua.mitofus.feature.world.MitofusShapelessRecipe;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Crafting, furnace, smelter (melting) and smithing-anvil (forging) recipes. */
final class ModRecipeProvider extends RecipeProvider {
    ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        crafting(output);
        smelting(output);
        melting(output);
        forging(output);
    }

    /**
     * Wraps the output so the crafting recipes saved through it become Mitofus-only: the builders still make the
     * recipe and its unlock advancement, and the recipe is swapped for its Mitofus-only twin on the way out.
     */
    private static RecipeOutput mitofusOnly(RecipeOutput output) {
        return new RecipeOutput() {
            @Override
            public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
                Recipe<?> swapped = recipe instanceof ShapedRecipe shaped ? new MitofusShapedRecipe(shaped)
                        : recipe instanceof ShapelessRecipe shapeless ? new MitofusShapelessRecipe(shapeless) : recipe;
                output.accept(id, swapped, advancement, conditions);
            }

            @Override
            public Advancement.Builder advancement() {
                return output.advancement();
            }
        };
    }

    private void crafting(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SMELTER.get())
                .pattern("BBB").pattern("BFB").pattern("iii")
                .define('B', Items.BRICKS).define('F', Items.BLAST_FURNACE).define('i', Items.IRON_INGOT)
                .unlockedBy("has_blast_furnace", has(Items.BLAST_FURNACE))
                .save(mitofusOnly(output), Mitofus.id("smelter"));
        // The basic helmet (own design): glass for the visor, a bed to lie on, iron and redstone for the circuits.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HELMET.get())
                .pattern("GGG").pattern("IBI").pattern("IRI")
                .define('G', Items.GLASS).define('B', ItemTags.BEDS).define('I', Items.IRON_INGOT).define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(output, Mitofus.id("helmet"));
        // The upgrades (own design): the helmet of the tier below and rare materials. Not
        // mitofusOnly (they are made in the real world).
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ADVANCED_HELMET.get())
                .pattern("DLD").pattern("GCG").pattern("D D")
                .define('D', Items.DIAMOND).define('L', Items.ENDER_EYE)
                .define('G', Items.GOLD_BLOCK).define('C', ModItems.HELMET.get())
                .unlockedBy("has_helmet", has(ModItems.HELMET.get()))
                .save(output, Mitofus.id("advanced_helmet"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.DIAMOND_HELMET.get())
                .pattern(" S ").pattern("LCN")
                .define('S', Items.NETHER_STAR).define('L', Items.TOTEM_OF_UNDYING)
                .define('C', ModItems.ADVANCED_HELMET.get()).define('N', Items.NETHERITE_BLOCK)
                .unlockedBy("has_advanced_helmet", has(ModItems.ADVANCED_HELMET.get()))
                .save(output, Mitofus.id("diamond_helmet"));
        // The account terminal and the phone (own design): glass panes for the screen, iron and redstone for the circuits.
        // Not mitofusOnly: they are crafted in the real world, where they work.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ACCOUNT_TERMINAL.get())
                .pattern("GGG").pattern("IRI").pattern("III")
                .define('G', Items.GLASS_PANE).define('I', Items.IRON_INGOT).define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(output, Mitofus.id("account_terminal"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PHONE.get())
                .pattern("G").pattern("R").pattern("I")
                .define('G', Items.GLASS_PANE).define('I', Items.IRON_INGOT).define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(output, Mitofus.id("phone"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SMITHING_ANVIL.get())
                .pattern("III").pattern(" i ").pattern("iii")
                .define('I', Items.IRON_BLOCK).define('i', ModItems.MITHRIL_INGOT.get())
                .unlockedBy("has_mithril_ingot", has(ModItems.MITHRIL_INGOT.get()))
                .save(mitofusOnly(output), Mitofus.id("smithing_anvil"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.WORKSHOP_LEDGER.get())
                .requires(Items.BOOK).requires(Items.IRON_INGOT)
                .unlockedBy("has_apprentice_contract", has(ModItems.APPRENTICE_CONTRACT.get()))
                .save(mitofusOnly(output), Mitofus.id("workshop_ledger"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ENHANCEMENT_STONE.get(), 2)
                .requires(ModItems.MITHRIL_INGOT.get()).requires(Items.AMETHYST_SHARD)
                .unlockedBy("has_mithril_ingot", has(ModItems.MITHRIL_INGOT.get()))
                .save(mitofusOnly(output), Mitofus.id("enhancement_stone"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.RESPEC_SCROLL.get())
                .requires(Items.PAPER).requires(Items.LAPIS_LAZULI).requires(ModItems.MITHRIL_INGOT.get())
                .unlockedBy("has_mithril_ingot", has(ModItems.MITHRIL_INGOT.get()))
                .save(mitofusOnly(output), Mitofus.id("respec_scroll"));
    }

    /**
     * Furnace recipes. Mithril and blue azurite have none: they are advanced metals and only melt in the smelter
     * ({@link #melting}).
     */
    private void smelting(RecipeOutput output) {
        ingot(output, ModItems.WOLFRAM_INGOT.get(), 0.7f, ModItems.RAW_WOLFRAM.get(), ModItems.WOLFRAM_ORE.get(), ModItems.DEEPSLATE_WOLFRAM_ORE.get());
    }

    /** Smelting (10 s) and blasting (5 s) recipes from each source into the ingot, like vanilla's iron. */
    private static void ingot(RecipeOutput output, ItemLike ingot, float xp, ItemLike... sources) {
        String ingotName = getItemName(ingot);
        for (ItemLike source : sources) {
            String name = ingotName + "_from_%s_" + getItemName(source);
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(source), RecipeCategory.MISC, ingot, xp, 200)
                    .group(ingotName).unlockedBy(getHasName(source), has(source))
                    .save(output, Mitofus.id(name.formatted("smelting")));
            SimpleCookingRecipeBuilder.blasting(Ingredient.of(source), RecipeCategory.MISC, ingot, xp, 100)
                    .group(ingotName).unlockedBy(getHasName(source), has(source))
                    .save(output, Mitofus.id(name.formatted("blasting")));
        }
    }

    private void forging(RecipeOutput output) {
        vanillaGear(output);
        forge(output, "iron_sword", Items.IRON_INGOT, 2, Items.STICK, Items.IRON_SWORD, 10, Optional.empty());
        forge(output, "iron_chestplate", Items.IRON_INGOT, 8, null, Items.IRON_CHESTPLATE, 20, Optional.empty());
        forge(output, "iron_smithing_hammer", Items.IRON_INGOT, 4, Items.LEATHER, ModItems.IRON_SMITHING_HAMMER.get(), 15, Optional.empty());
        forge(output, "wolfram_smithing_hammer", ModItems.WOLFRAM_INGOT.get(), 4, Items.LEATHER, ModItems.WOLFRAM_SMITHING_HAMMER.get(), 25, Optional.empty());
        forge(output, "mithril_smithing_hammer", ModItems.MITHRIL_INGOT.get(), 4, Items.LEATHER, ModItems.MITHRIL_SMITHING_HAMMER.get(), 45,
                Optional.empty());
        forge(output, "wolfram_sword", ModItems.WOLFRAM_INGOT.get(), 2, Items.STICK, ModItems.WOLFRAM_SWORD.get(), 15,
                Optional.of(new EquipRequirements(2, Map.of(StatType.STRENGTH, 2))));
        forge(output, "durandarte", Items.DIAMOND, 4, Items.BLAZE_ROD, ModItems.DURANDARTE.get(), 60,
                Optional.of(new EquipRequirements(10, Map.of(StatType.STRENGTH, 8))));
        // Guadalete: 15 blue azurite on its blueprint, only for the Legendary Blacksmith. It comes out
        // Unique or Legendary with the fixed effects, and asks for what nobody has (level 300, 5000 STR).
        forge(output, "guadalete_sword", ModItems.AZURITE_INGOT.get(), 15, ModItems.GUADALETE_BLUEPRINT.get(),
                ModItems.GUADALETE_SWORD.get(), 500, Optional.of(new EquipRequirements(300, Map.of(StatType.STRENGTH, 5000))),
                new ForgingExtras(Optional.of(Mitofus.id("legendary_blacksmith")), ItemRating.UNIQUE, ItemRating.LEGENDARY,
                        List.of(new FixedOption(Mitofus.id("guadalete_agility"), 30, ItemRating.NORMAL, ItemRating.UNIQUE),
                                new FixedOption(Mitofus.id("guadalete_agility"), 50, ItemRating.LEGENDARY),
                                new FixedOption(Mitofus.id("guadalete_block"), 0.15f),
                                new FixedOption(Mitofus.id("guadalete_darkness"), 0.2f),
                                new FixedOption(Mitofus.id("guadalete_fear"), 20),
                                new FixedOption(Mitofus.id("guadalete_bisection"), 1, ItemRating.LEGENDARY))));
    }

    /** Piece of vanilla equipment: name, ingots per piece, whether it takes a stick, XP factor and whether it is armor. */
    private record GearPiece(String name, int count, boolean stick, double xpFactor, boolean armor) {
    }

    private static final List<GearPiece> GEAR_PIECES = List.of(
            new GearPiece("sword", 2, true, 1, false), new GearPiece("pickaxe", 3, true, 1.5, false),
            new GearPiece("axe", 3, true, 1.5, false), new GearPiece("shovel", 1, true, 0.5, false),
            new GearPiece("hoe", 2, true, 1, false), new GearPiece("helmet", 5, false, 2, true),
            new GearPiece("chestplate", 8, false, 3, true), new GearPiece("leggings", 7, false, 2.5, true),
            new GearPiece("boots", 4, false, 1.5, true));

    /**
     * The anvil also forges vanilla equipment (the crafting table still makes it): tools, weapons and armor from wood
     * (planks, no armor), stone (no armor), iron, gold and diamond, plus the shield. Same counts as vanilla. Netherite,
     * the bow, shears and flint and steel are left out on purpose. Diamond asks for level 5 and 4 STR (weapons and
     * tools) or 4 STA (armor): design. The iron sword and chestplate keep their own lines below.
     */
    private void vanillaGear(RecipeOutput output) {
        vanillaTier(output, "wooden", Ingredient.of(ItemTags.PLANKS), 4, null);
        vanillaTier(output, "stone", Ingredient.of(ItemTags.STONE_TOOL_MATERIALS), 6, null);
        vanillaTier(output, "iron", Ingredient.of(Items.IRON_INGOT), 10, null);
        vanillaTier(output, "golden", Ingredient.of(Items.GOLD_INGOT), 12, null);
        vanillaTier(output, "diamond", Ingredient.of(Items.DIAMOND), 25, 5);
        ForgingRecipe shield = new ForgingRecipe(Ingredient.of(ItemTags.PLANKS), 6, Ingredient.of(Items.IRON_INGOT),
                new ItemStack(Items.SHIELD), 15, Optional.empty(), ForgingExtras.NONE);
        output.accept(Mitofus.id("forging/shield"), shield, null);
    }

    private static void vanillaTier(RecipeOutput output, String tier, Ingredient material, int baseXp, Integer requiredLevel) {
        for (GearPiece piece : GEAR_PIECES) {
            if (piece.armor() && (tier.equals("wooden") || tier.equals("stone"))) {
                continue;
            }
            String name = tier + "_" + piece.name();
            if (name.equals("iron_sword") || name.equals("iron_chestplate")) {
                continue;
            }
            Item result = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(name));
            Optional<EquipRequirements> requirements = requiredLevel == null ? Optional.empty()
                    : Optional.of(new EquipRequirements(requiredLevel, Map.of(piece.armor() ? StatType.STAMINA : StatType.STRENGTH, 4)));
            ForgingRecipe recipe = new ForgingRecipe(material, piece.count(), piece.stick() ? Ingredient.of(Items.STICK) : Ingredient.EMPTY,
                    new ItemStack(result), (int) Math.round(baseXp * piece.xpFactor()), requirements, ForgingExtras.NONE);
            output.accept(Mitofus.id("forging/" + name), recipe, null);
        }
    }

    /**
     * Smelter recipes. Iron and wolfram also melt in the furnace (the smelter is better: a chance of an extra ingot);
     * mithril and blue azurite only here. Coal reaches about 1180 degrees, coal blocks 1300, blaze rods 1500 and lava 1600.
     * Iron (1150-1250) and wolfram (950-1000) follow the temperatures; the rest keep the default zone width.
     */
    private void melting(RecipeOutput output) {
        melt(output, "iron_ingot", Items.IRON_INGOT, 1150, 1250, 0, 160, 3, Items.RAW_IRON, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE);
        melt(output, "wolfram_ingot", ModItems.WOLFRAM_INGOT.get(), 950, 1000, 0, 160, 4,
                ModItems.RAW_WOLFRAM.get(), ModItems.WOLFRAM_ORE.get(), ModItems.DEEPSLATE_WOLFRAM_ORE.get());
        melt(output, "mithril_ingot", ModItems.MITHRIL_INGOT.get(), 1200, 3, 240, 8,
                ModItems.RAW_MITHRIL.get(), ModItems.MITHRIL_ORE.get(), ModItems.DEEPSLATE_MITHRIL_ORE.get());
        melt(output, "azurite_ingot", ModItems.AZURITE_INGOT.get(), 1450, 8, 400, 20,
                ModItems.RAW_AZURITE.get(), ModItems.AZURITE_ORE.get(), ModItems.DEEPSLATE_AZURITE_ORE.get());
    }

    /** A smelter recipe per source at data/mitofus/recipe/melting/&lt;result&gt;_from_&lt;source&gt;.json. */
    private static void melt(RecipeOutput output, String result, ItemLike ingot, int minTemperature, int smithingLevel, int time,
                             int smithingXp, ItemLike... sources) {
        melt(output, result, ingot, minTemperature, minTemperature + SmelterRules.ZONE_WIDTH, smithingLevel, time, smithingXp, sources);
    }

    private static void melt(RecipeOutput output, String result, ItemLike ingot, int minTemperature, int maxTemperature,
                             int smithingLevel, int time, int smithingXp, ItemLike... sources) {
        for (ItemLike source : sources) {
            MeltingRecipe recipe = new MeltingRecipe(Ingredient.of(source), new ItemStack(ingot), minTemperature, maxTemperature,
                    smithingLevel, time, smithingXp);
            output.accept(Mitofus.id("melting/" + result + "_from_" + getItemName(source)), recipe, null);
        }
    }

    /** A smithing anvil recipe at data/mitofus/recipe/forging/<name>.json. {@code handle} may be null. */
    private static void forge(RecipeOutput output, String name, ItemLike material, int materialCount, ItemLike handle,
                              ItemLike result, int smithingXp, Optional<EquipRequirements> requirements) {
        forge(output, name, material, materialCount, handle, result, smithingXp, requirements, ForgingExtras.NONE);
    }

    private static void forge(RecipeOutput output, String name, ItemLike material, int materialCount, ItemLike handle,
                              ItemLike result, int smithingXp, Optional<EquipRequirements> requirements, ForgingExtras extras) {
        ForgingRecipe recipe = new ForgingRecipe(Ingredient.of(material), materialCount,
                handle == null ? Ingredient.EMPTY : Ingredient.of(handle), new ItemStack(result), smithingXp, requirements, extras);
        output.accept(Mitofus.id("forging/" + name), recipe, null);
    }
}
