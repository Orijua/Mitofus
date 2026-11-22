package com.orijua.mitofus.compat.jei;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.feature.smelting.MeltingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/** The smelter's page: what melts into what, at which heat and from which smithing level (the same texts as the smelter). */
final class MeltingCategory implements IRecipeCategory<RecipeHolder<MeltingRecipe>> {
    static final RecipeType<RecipeHolder<MeltingRecipe>> TYPE = RecipeType.createRecipeHolderType(Mitofus.id("melting"));

    private static final int WIDTH = 150;
    private static final int TEXT_TOP = 22;
    private static final int LINE = 10;

    private final IDrawable icon;

    MeltingCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(ModBlocks.SMELTER.get());
    }

    @Override
    public RecipeType<RecipeHolder<MeltingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return ModBlocks.SMELTER.get().getName();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return TEXT_TOP + 4 * LINE;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MeltingRecipe> holder, IFocusGroup focuses) {
        MeltingRecipe recipe = holder.value();
        builder.addInputSlot(1, 1).setStandardSlotBackground().addIngredients(recipe.ingredient());
        builder.addOutputSlot(70, 1).setOutputSlotBackground().addItemStack(recipe.result());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MeltingRecipe> holder, IFocusGroup focuses) {
        MeltingRecipe recipe = holder.value();
        builder.addAnimatedRecipeFlameWidget(recipe.time()).setPosition(22, 2);
        builder.addRecipeArrowWidget().setPosition(40, 1);
        List<FormattedText> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.mitofus.smelter_needs_heat", recipe.minTemperature()));
        if (recipe.smithingLevel() > 0) {
            lines.add(Component.translatable("jei.mitofus.needs_level", recipe.smithingLevel()));
        }
        lines.add(Component.translatable("jei.mitofus.smithing_xp", recipe.smithingXp()));
        builder.addText(lines, WIDTH, 4 * LINE).setPosition(0, TEXT_TOP).setColor(JeiStyle.TEXT).setShadow(false);
    }
}
