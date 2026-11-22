package com.orijua.mitofus.compat.jei;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModRecipes;
import com.orijua.mitofus.feature.world.MitofusShapedRecipe;
import com.orijua.mitofus.feature.world.MitofusShapelessRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * JEI integration (optional): the smithing anvil's and the smelter's recipes get their own pages, and crafting
 * recipes that only work in Mitofus say so. JEI finds this class by its annotation, so nothing loads it without JEI.
 * EMI shows these pages too when JEI is installed next to it.
 */
@JeiPlugin
public final class MitofusJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = Mitofus.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ForgingCategory(gui), new MeltingCategory(gui));
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(MitofusShapedRecipe.class, new MitofusCraftingExtension<>(false));
        registration.getCraftingCategory().addExtension(MitofusShapelessRecipe.class, new MitofusCraftingExtension<>(true));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // JEI registers recipes once the client has the server's recipes, so the level is there.
        if (Minecraft.getInstance().level == null) {
            return;
        }
        RecipeManager recipes = Minecraft.getInstance().level.getRecipeManager();
        registration.addRecipes(ForgingCategory.TYPE, recipes.getAllRecipesFor(ModRecipes.FORGING.get()));
        registration.addRecipes(MeltingCategory.TYPE, recipes.getAllRecipesFor(ModRecipes.MELTING.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.SMITHING_ANVIL.get(), ForgingCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.SMELTER.get(), MeltingCategory.TYPE);
    }
}
