package com.orijua.mitofus.core.registry;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.smelting.MeltingRecipe;
import com.orijua.mitofus.feature.smithing.ForgingRecipe;
import com.orijua.mitofus.feature.world.MitofusShapedRecipe;
import com.orijua.mitofus.feature.world.MitofusShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A recipe needs two registrations: its type (to look recipes up) and its serializer (to read the JSON). */
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Mitofus.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mitofus.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ForgingRecipe>> FORGING =
            RECIPE_TYPES.register("forging", () -> RecipeType.simple(Mitofus.id("forging")));

    public static final DeferredHolder<RecipeType<?>, RecipeType<MeltingRecipe>> MELTING =
            RECIPE_TYPES.register("melting", () -> RecipeType.simple(Mitofus.id("melting")));

    public static final DeferredHolder<RecipeSerializer<?>, ForgingRecipe.Serializer> FORGING_SERIALIZER =
            RECIPE_SERIALIZERS.register("forging", ForgingRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, MeltingRecipe.Serializer> MELTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("melting", MeltingRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, MitofusShapedRecipe.Serializer> MITOFUS_SHAPED_SERIALIZER =
            RECIPE_SERIALIZERS.register("mitofus_shaped", MitofusShapedRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, MitofusShapelessRecipe.Serializer> MITOFUS_SHAPELESS_SERIALIZER =
            RECIPE_SERIALIZERS.register("mitofus_shapeless", MitofusShapelessRecipe.Serializer::new);

    private ModRecipes() {
    }
}
