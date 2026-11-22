package com.orijua.mitofus.compat.jei;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.feature.smithing.ForgingRecipe;
import com.orijua.mitofus.feature.smithing.client.ForgingRecipeText;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The smithing anvil's page: material (with its count) and handle give the piece, with the same requirements the anvil shows. */
final class ForgingCategory implements IRecipeCategory<RecipeHolder<ForgingRecipe>> {
    static final RecipeType<RecipeHolder<ForgingRecipe>> TYPE = RecipeType.createRecipeHolderType(Mitofus.id("forging"));

    private static final int WIDTH = 150;
    private static final int TEXT_TOP = 22;
    private static final int LINE = 10;
    /** Class, level, stats and smithing XP. */
    private static final int LINES = 4;

    private final IDrawable icon;

    ForgingCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemLike(ModBlocks.SMITHING_ANVIL.get());
    }

    @Override
    public RecipeType<RecipeHolder<ForgingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return ModBlocks.SMITHING_ANVIL.get().getName();
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
        return TEXT_TOP + LINES * LINE;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ForgingRecipe> holder, IFocusGroup focuses) {
        ForgingRecipe recipe = holder.value();
        List<ItemStack> materials = Arrays.stream(recipe.material().getItems())
                .map(stack -> stack.copyWithCount(recipe.materialCount()))
                .toList();
        builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStacks(materials);
        if (!recipe.handle().isEmpty()) {
            builder.addInputSlot(21, 1).setStandardSlotBackground().addIngredients(recipe.handle());
        }
        builder.addOutputSlot(75, 1).setOutputSlotBackground().addItemStack(recipe.result());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ForgingRecipe> holder, IFocusGroup focuses) {
        ForgingRecipe recipe = holder.value();
        builder.addRecipeArrowWidget().setPosition(45, 1);
        List<FormattedText> lines = new ArrayList<>();
        ForgingRecipeText.requiredJob(Minecraft.getInstance().level, recipe).ifPresent(lines::add);
        lines.add(ForgingRecipeText.levelText(ForgingRecipeText.levelRange(recipe)));
        List<Component> stats = ForgingRecipeText.statTexts(recipe);
        if (!stats.isEmpty()) {
            lines.add(ComponentUtils.formatList(stats, Component.literal(" · ")));
        }
        lines.add(Component.translatable("jei.mitofus.smithing_xp", recipe.smithingXp()));
        builder.addText(lines, WIDTH, LINES * LINE).setPosition(0, TEXT_TOP).setColor(JeiStyle.TEXT).setShadow(false);
    }
}
