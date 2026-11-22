package com.orijua.mitofus.feature.world;

import com.mojang.serialization.MapCodec;
import com.orijua.mitofus.core.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * A shapeless crafting recipe that only works in Mitofus: in the real world the grid simply does not match. Same JSON as
 * {@code minecraft:crafting_shapeless}, with {@code "type": "mitofus:mitofus_shapeless"}.
 */
public final class MitofusShapelessRecipe extends ShapelessRecipe {
    public MitofusShapelessRecipe(ShapelessRecipe base) {
        // The result does not depend on the registries (ShapelessRecipe ignores the argument), hence the null.
        super(base.getGroup(), base.category(), base.getResultItem(null), base.getIngredients());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return MitofusWorld.is(level) && super.matches(input, level);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MITOFUS_SHAPELESS_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<MitofusShapelessRecipe> {
        private static final MapCodec<MitofusShapelessRecipe> CODEC = RecipeSerializer.SHAPELESS_RECIPE.codec()
                .xmap(MitofusShapelessRecipe::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, MitofusShapelessRecipe> STREAM_CODEC =
                RecipeSerializer.SHAPELESS_RECIPE.streamCodec().map(MitofusShapelessRecipe::new, recipe -> recipe);

        @Override
        public MapCodec<MitofusShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MitofusShapelessRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
