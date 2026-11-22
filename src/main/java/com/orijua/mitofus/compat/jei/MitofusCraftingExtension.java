package com.orijua.mitofus.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.List;

/**
 * A crafting recipe that only works in Mitofus (mitofus_shaped / mitofus_shapeless): JEI lays it out as a normal one
 * and adds "Only in Mitofus" over the arrow, so nobody tries it in the real world and thinks it is broken.
 */
final class MitofusCraftingExtension<R extends CraftingRecipe> implements ICraftingCategoryExtension<R> {
    private final boolean shapeless;

    MitofusCraftingExtension(boolean shapeless) {
        this.shapeless = shapeless;
    }

    @Override
    public void setRecipe(RecipeHolder<R> holder, IRecipeLayoutBuilder builder, ICraftingGridHelper grid, IFocusGroup focuses) {
        R recipe = holder.value();
        int width = 0;
        int height = 0;
        if (shapeless) {
            builder.setShapeless();
        } else if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        }
        grid.createAndSetIngredients(builder, recipe.getIngredients(), width, height);
        ItemStack result = Minecraft.getInstance().level == null ? ItemStack.EMPTY
                : recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
        grid.createAndSetOutputs(builder, List.of(result));
    }

    @Override
    public void createRecipeExtras(RecipeHolder<R> holder, IRecipeExtrasBuilder builder, ICraftingGridHelper grid, IFocusGroup focuses) {
        // The crafting page is 116 wide: the grid takes the left 54, the arrow and the result sit lower on the right.
        builder.addText(List.of(Component.translatable("jei.mitofus.mitofus_only")), 58, 10)
                .setPosition(58, 0)
                .setColor(JeiStyle.HIGHLIGHT)
                .setShadow(false)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setTooltip(Component.translatable("message.mitofus.mitofus_only"));
    }
}
