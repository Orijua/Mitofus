package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.equipment.option.RolledOption;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.smithing.ForgingRecipe;
import com.orijua.mitofus.feature.smithing.ForgingRecipeInput;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class SmithingTests {
    private SmithingTests() {
    }

    private static Optional<RecipeHolder<ForgingRecipe>> find(GameTestHelper helper, ForgingRecipeInput input) {
        return ForgingRecipe.find(helper.getLevel().getRecipeManager(), input, helper.getLevel());
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void forgingRecipeLoadsFromDatapack(GameTestHelper helper) {
        ForgingRecipeInput input = new ForgingRecipeInput(new ItemStack(ModItems.WOLFRAM_INGOT.get(), 2), new ItemStack(Items.STICK));
        Optional<RecipeHolder<ForgingRecipe>> recipe = find(helper, input);
        helper.assertTrue(recipe.isPresent(), "2 wolfram ingots + stick should match a forging recipe");
        helper.assertTrue(recipe.get().value().result().is(ModItems.WOLFRAM_SWORD.get()), "result should be the Wolfram sword");
        helper.assertTrue(recipe.get().value().requirements().isPresent(), "the Wolfram sword recipe declares requirements");

        ForgingRecipeInput tooFew = new ForgingRecipeInput(new ItemStack(ModItems.WOLFRAM_INGOT.get(), 1), new ItemStack(Items.STICK));
        helper.assertFalse(find(helper, tooFew).isPresent(), "1 ingot must not be enough");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void armorOfOneMaterialPicksThePieceThatTakesTheMostIngots(GameTestHelper helper) {
        // Helmet (5), leggings (7), boots (4) and chestplate (8) share the ingot and have no handle: 8 ingots must give the chestplate.
        int[] counts = {4, 5, 7, 8, 16};
        net.minecraft.world.item.Item[] expected = {Items.IRON_BOOTS, Items.IRON_HELMET, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE, Items.IRON_CHESTPLATE};
        for (int i = 0; i < counts.length; i++) {
            Optional<RecipeHolder<ForgingRecipe>> recipe = find(helper, new ForgingRecipeInput(new ItemStack(Items.IRON_INGOT, counts[i]), ItemStack.EMPTY));
            helper.assertTrue(recipe.isPresent() && recipe.get().value().result().is(expected[i]),
                    counts[i] + " iron ingots should forge " + expected[i]);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void gridSwordIsForgedAndRollsWeaponOptions(GameTestHelper helper) {
        ForgingRecipeInput input = new ForgingRecipeInput(new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.BLAZE_ROD));
        Optional<RecipeHolder<ForgingRecipe>> recipe = find(helper, input);
        helper.assertTrue(recipe.isPresent() && recipe.get().value().result().is(ModItems.DURANDARTE.get()),
                "4 diamonds + blaze rod should forge Cid's sword");
        EquipRequirements requirements = recipe.get().value().requirements().orElseThrow();
        helper.assertTrue(requirements.level() == 10 && requirements.stats().get(StatType.STRENGTH) == 8, "needs level 10 and 8 STR");
        helper.assertTrue(ModItems.DURANDARTE.toStack().is(ItemTags.SWORDS), "it is a sword, so weapon options can roll on it");
        ServerPlayer smith = TestSupport.fakePlayer(helper);
        ProgressionService.set(smith, ProgressionService.get(smith).with(StatType.DEXTERITY, 200)); // high ratings, more options
        Set<ResourceLocation> weaponOptions = Set.of(Mitofus.id("sharpness"), Mitofus.id("swiftness"), Mitofus.id("life_steal"));
        int rolled = 0;
        for (int i = 0; i < 200; i++) {
            ItemStack sword = recipe.get().value().result().copy();
            EquipmentService.stampForged(smith, sword, recipe.get().value().requirements());
            for (RolledOption option : sword.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of())) {
                if (weaponOptions.contains(option.option())) {
                    rolled++;
                }
            }
        }
        helper.assertTrue(rolled > 0, "the forge rolled weapon options on Cid's sword in 200 forges");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void armorNeverRollsWeaponOptions(GameTestHelper helper) {
        ServerPlayer smith = TestSupport.fakePlayer(helper);
        ProgressionService.set(smith, ProgressionService.get(smith).with(StatType.DEXTERITY, 200)); // high ratings, more options
        Set<ResourceLocation> weaponOnly = Set.of(Mitofus.id("sharpness"), Mitofus.id("swiftness"), Mitofus.id("life_steal"));
        int rolled = 0;
        for (int i = 0; i < 50; i++) {
            ItemStack chestplate = new ItemStack(Items.IRON_CHESTPLATE);
            EquipmentService.stampForged(smith, chestplate, Optional.empty());
            for (RolledOption option : chestplate.getOrDefault(ModDataComponents.ITEM_OPTIONS, List.<RolledOption>of())) {
                rolled++;
                helper.assertFalse(weaponOnly.contains(option.option()), "armor rolled a weapon option: " + option.option());
            }
        }
        helper.assertTrue(rolled > 0, "some armor options rolled, so this test proves something");
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void forgedItemGetsRatingAndRequirements(GameTestHelper helper) {
        ServerPlayer smith = TestSupport.fakePlayer(helper);
        ForgingRecipe recipe = find(helper, new ForgingRecipeInput(new ItemStack(ModItems.WOLFRAM_INGOT.get(), 2), new ItemStack(Items.STICK)))
                .orElseThrow().value();
        ItemStack sword = recipe.result().copy();
        EquipmentService.stampForged(smith, sword, recipe.requirements());

        helper.assertTrue(sword.has(ModDataComponents.ITEM_RATING.get()), "forged item must have a rating");
        helper.assertTrue(sword.has(ModDataComponents.REQUIREMENTS.get()), "forged item must carry the recipe's requirements");
        helper.succeed();
    }
}
