package com.orijua.mitofus.feature.smithing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orijua.mitofus.core.registry.ModRecipes;
import com.orijua.mitofus.feature.equipment.EquipRequirements;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A smithing anvil recipe, loaded from data/<namespace>/recipe/*.json with "type": "mitofus:forging".
 * Example: data/mitofus/recipe/forging/wolfram_sword.json. Special recipes add {@link ForgingExtras}
 * (a class, a rating range, fixed options), e.g. forging/guadalete_sword.json.
 */
public record ForgingRecipe(Ingredient material, int materialCount, Ingredient handle, ItemStack result, int smithingXp,
                            Optional<EquipRequirements> requirements, ForgingExtras extras)
        implements Recipe<ForgingRecipeInput> {

    private static final StreamCodec<ByteBuf, Optional<EquipRequirements>> REQUIREMENTS_STREAM_CODEC =
            ByteBufCodecs.optional(EquipRequirements.STREAM_CODEC);

    public static final MapCodec<ForgingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter(ForgingRecipe::material),
            Codec.intRange(1, 64).optionalFieldOf("material_count", 1).forGetter(ForgingRecipe::materialCount),
            Ingredient.CODEC.optionalFieldOf("handle", Ingredient.EMPTY).forGetter(ForgingRecipe::handle),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ForgingRecipe::result),
            Codec.intRange(0, 10_000).optionalFieldOf("smithing_xp", 10).forGetter(ForgingRecipe::smithingXp),
            EquipRequirements.CODEC.optionalFieldOf("requirements").forGetter(ForgingRecipe::requirements),
            ForgingExtras.MAP_CODEC.forGetter(ForgingRecipe::extras)
    ).apply(instance, ForgingRecipe::new));

    // Written by hand: StreamCodec.composite takes at most six fields.
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.material());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.materialCount());
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.handle());
                ItemStack.STREAM_CODEC.encode(buf, recipe.result());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.smithingXp());
                REQUIREMENTS_STREAM_CODEC.encode(buf, recipe.requirements());
                ForgingExtras.STREAM_CODEC.encode(buf, recipe.extras());
            },
            buf -> new ForgingRecipe(
                    Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
                    ItemStack.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    REQUIREMENTS_STREAM_CODEC.decode(buf),
                    ForgingExtras.STREAM_CODEC.decode(buf)));

    /**
     * The recipes the anvil can forge from these inputs. Several can match at once (armor pieces of one material share the
     * ingot and have no handle: 8 ingots also match the helmet, leggings and boots), so only the ones that ask for the most
     * material count; otherwise the chestplate would never come out. Some tie (pickaxe and axe, sword and hoe take the same
     * items), and then the smith chooses: the list is sorted by recipe id so the order is the same on server and client.
     */
    public static List<RecipeHolder<ForgingRecipe>> candidates(RecipeManager recipes, ForgingRecipeInput input, Level level) {
        List<RecipeHolder<ForgingRecipe>> matching = recipes.getAllRecipesFor(ModRecipes.FORGING.get()).stream()
                .filter(holder -> holder.value().matches(input, level))
                .toList();
        int most = matching.stream().mapToInt(holder -> holder.value().materialCount()).max().orElse(0);
        return matching.stream()
                .filter(holder -> holder.value().materialCount() == most)
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    /** The candidate at {@code choice}, wrapping around, so a stale choice (inputs changed) still lands on a recipe. */
    public static Optional<RecipeHolder<ForgingRecipe>> find(RecipeManager recipes, ForgingRecipeInput input, Level level, int choice) {
        List<RecipeHolder<ForgingRecipe>> candidates = candidates(recipes, input, level);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(Math.floorMod(choice, candidates.size())));
    }

    @Override
    public boolean matches(ForgingRecipeInput input, Level level) {
        // Ingredient.EMPTY only matches an empty slot, so a recipe without handle needs that slot empty.
        return material.test(input.material())
                && input.material().getCount() >= materialCount
                && handle.test(input.handle());
    }

    @Override
    public ItemStack assemble(ForgingRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, material, handle);
    }

    /** Keeps it out of the vanilla recipe book, which has no category for it (avoids log spam). */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FORGING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.FORGING.get();
    }

    public static final class Serializer implements RecipeSerializer<ForgingRecipe> {
        @Override
        public MapCodec<ForgingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ForgingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
