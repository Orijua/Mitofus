package com.orijua.mitofus.feature.world;

import com.mojang.serialization.MapCodec;
import com.orijua.mitofus.core.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * A shaped crafting recipe that only works in Mitofus: in the real world the grid simply does not match, so crafting
 * fails in silence. Same JSON as {@code minecraft:crafting_shaped}, with {@code "type": "mitofus:mitofus_shaped"}.
 */
public final class MitofusShapedRecipe extends ShapedRecipe {
    public MitofusShapedRecipe(ShapedRecipe base) {
        // The result does not depend on the registries (ShapedRecipe ignores the argument), hence the null.
        super(base.getGroup(), base.category(), base.pattern, base.getResultItem(null), base.showNotification());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return MitofusWorld.is(level) && super.matches(input, level);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MITOFUS_SHAPED_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<MitofusShapedRecipe> {
        // Reuses vanilla's codecs, so the JSON and the packet are exactly the shaped recipe's.
        private static final MapCodec<MitofusShapedRecipe> CODEC = RecipeSerializer.SHAPED_RECIPE.codec()
                .xmap(MitofusShapedRecipe::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, MitofusShapedRecipe> STREAM_CODEC =
                RecipeSerializer.SHAPED_RECIPE.streamCodec().map(MitofusShapedRecipe::new, recipe -> recipe);

        @Override
        public MapCodec<MitofusShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MitofusShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
