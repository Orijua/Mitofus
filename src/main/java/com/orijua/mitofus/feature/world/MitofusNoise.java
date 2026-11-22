package com.orijua.mitofus.feature.world;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.EnumMap;
import java.util.Map;

/**
 * The detail of the Mitofus terrain: the noise a {@link MitofusTerrainFunction} adds, in a small share, to each climate
 * parameter it reads from the map, so coasts and ranges are not smooth. Mitofus is a world with a known
 * map, the same in every new world, so this noise has a fixed seed ({@link #SEED}), never the world's: it is the noise
 * vanilla uses for that parameter ({@code minecraft:overworld/continents}, {@code erosion}, {@code ridges}: the same
 * octaves, amplitudes, 1/4 scale and coordinate shift, Minecraft 1.21.1 {@code NoiseData} and {@code NoiseRouterData}),
 * as it is in a world with that seed. Same character and amplitude as before, but the same everywhere and always.
 * Pure and thread-safe, so it is unit-tested.
 */
public final class MitofusNoise {
    /** The fixed seed of the detail (any constant: "Mitofus" in ASCII). Changing it changes the coast of every new world. */
    public static final long SEED = 0x5361_7469_7366_79L;
    /** Vanilla's climate noises are read at a quarter of the block coordinates. */
    private static final double XZ_SCALE = 0.25;
    /** And shifted by up to 4 blocks of that scale by another noise. */
    private static final double SHIFT_SCALE = 4.0;

    /**
     * Only a hash input for the fixed seed, never shown or registered: it keeps the namespace the mod had before the rename
     * to Mitofus so the continent's coast does not change. Changing it reshapes the coast (a world-redesign decision).
     */
    private static final String SEED_NAMESPACE = "mitofus";
    private static final PositionalRandomFactory RANDOM = new XoroshiroRandomSource(SEED).forkPositional();
    private static final NormalNoise SHIFT = create("shift", new NormalNoise.NoiseParameters(-3, 1.0, 1.0, 1.0, 0.0));
    private static final Map<MitofusTerrain.Channel, NormalNoise> NOISES = new EnumMap<>(Map.of(
            MitofusTerrain.Channel.CONTINENTS, create("continentalness", new NormalNoise.NoiseParameters(-9, 1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0)),
            MitofusTerrain.Channel.EROSION, create("erosion", new NormalNoise.NoiseParameters(-9, 1.0, 1.0, 0.0, 1.0, 1.0)),
            MitofusTerrain.Channel.RIDGES, create("ridge", new NormalNoise.NoiseParameters(-7, 1.0, 2.0, 1.0, 0.0, 0.0, 0.0))));

    private MitofusNoise() {
    }

    private static NormalNoise create(String name, NormalNoise.NoiseParameters parameters) {
        return NormalNoise.create(RANDOM.fromHashOf(ResourceLocation.fromNamespaceAndPath(SEED_NAMESPACE, name)), parameters);
    }

    /** The noise of a channel at block (x, z), in [-{@link #sampleBound}, {@link #sampleBound}] (about [-1, 1] most of the time). */
    public static double sample(MitofusTerrain.Channel channel, int blockX, int blockZ) {
        double shiftX = SHIFT.getValue(blockX * XZ_SCALE, 0.0, blockZ * XZ_SCALE) * SHIFT_SCALE;
        double shiftZ = SHIFT.getValue(blockZ * XZ_SCALE, blockX * XZ_SCALE, 0.0) * SHIFT_SCALE;
        return NOISES.get(channel).getValue(blockX * XZ_SCALE + shiftX, 0.0, blockZ * XZ_SCALE + shiftZ);
    }

    /**
     * What a {@code mitofus_terrain} density function gives at block (x, z): the channel's value for the relief there,
     * plus the detail if {@code detail}. The same for every y and every world.
     */
    public static double climate(MitofusTerrain.Channel channel, MitofusRelief relief, boolean detail, int blockX, int blockZ) {
        double value = relief.reliefAt(blockX, blockZ);
        return detail ? channel.of(value, sample(channel, blockX, blockZ)) : channel.of(value);
    }

    /** The lowest {@link #climate} can be. */
    public static double minValue(MitofusTerrain.Channel channel, boolean detail) {
        return detail ? channel.noisyMin() : channel.min();
    }

    /** The highest {@link #climate} can be. */
    public static double maxValue(MitofusTerrain.Channel channel, boolean detail) {
        return detail ? channel.noisyMax() : channel.max();
    }

    /** The bound of {@link #sample} for a channel (the function clamps the sample to [-1, 1] anyway). */
    public static double sampleBound(MitofusTerrain.Channel channel) {
        return NOISES.get(channel).maxValue();
    }
}
