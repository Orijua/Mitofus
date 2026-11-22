package com.orijua.mitofus.feature.world;

import com.mojang.serialization.MapCodec;
import com.orijua.mitofus.core.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * "type": "mitofus:mitofus_only": lets a placed feature generate only in the Mitofus dimension (the mod's ores:
 * the biome modifiers add them to every overworld biome, and Mitofus uses vanilla biomes, so the dimension is the filter).
 * Reads {@link MitofusWorld#is} on the ServerLevel behind the WorldGenLevel, so the game-test stand-in is honored too.
 */
public final class MitofusOnlyPlacement extends PlacementFilter {
    public static final MitofusOnlyPlacement INSTANCE = new MitofusOnlyPlacement();
    public static final MapCodec<MitofusOnlyPlacement> CODEC = MapCodec.unit(INSTANCE);

    private MitofusOnlyPlacement() {
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return MitofusWorld.is(context.getLevel().getLevel());
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModWorldgen.MITOFUS_ONLY.get();
    }
}
