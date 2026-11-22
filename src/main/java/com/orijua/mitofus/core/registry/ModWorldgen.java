package com.orijua.mitofus.core.registry;

import com.mojang.serialization.MapCodec;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.world.MitofusBiomeSource;
import com.orijua.mitofus.feature.world.MitofusOnlyPlacement;
import com.orijua.mitofus.feature.world.MitofusTerrainFunction;
import com.orijua.mitofus.feature.world.WorldSitePlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation types that structure JSON can name, such as a structure_set's "placement". */
public final class ModWorldgen {
    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Mitofus.MODID);

    /** "type": "mitofus:world_site", "site": "...": the one site chosen when the world was created (Covadonga, the cave). */
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<WorldSitePlacement>> WORLD_SITE =
            STRUCTURE_PLACEMENTS.register("world_site", () -> () -> WorldSitePlacement.CODEC);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Mitofus.MODID);

    /** "type": "mitofus:mitofus_map": the lore regions of the Mitofus dimension over vanilla terrain. */
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<MitofusBiomeSource>> MITOFUS_MAP =
            BIOME_SOURCES.register("mitofus_map", () -> MitofusBiomeSource.CODEC);

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Mitofus.MODID);

    /** "type": "mitofus:mitofus_terrain": a climate parameter of the terrain, read from a relief map. */
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<MitofusTerrainFunction>> MITOFUS_TERRAIN =
            DENSITY_FUNCTIONS.register("mitofus_terrain", () -> MitofusTerrainFunction.CODEC);

    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Mitofus.MODID);

    /** "type": "mitofus:mitofus_only": a placed feature that only generates in Mitofus (the mod's ores). */
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<MitofusOnlyPlacement>> MITOFUS_ONLY =
            PLACEMENT_MODIFIERS.register("mitofus_only", () -> () -> MitofusOnlyPlacement.CODEC);

    private ModWorldgen() {
    }
}
