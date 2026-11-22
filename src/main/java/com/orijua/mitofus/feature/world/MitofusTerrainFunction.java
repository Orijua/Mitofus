package com.orijua.mitofus.feature.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code "type": "mitofus:mitofus_terrain", "channel": "continents|erosion|ridges", "map": "mitofus:west",
 * "detail": true}: the climate parameter the vanilla terrain splines read, taken from a relief map
 * ({@link MitofusTerrains}: map or the test island) instead of noise. With {@code detail} (default false), a
 * small share ({@link MitofusTerrain.Channel#noiseWeight}) of a noise with a fixed seed ({@link MitofusNoise}) is added,
 * so coasts and ranges are not smooth. Either way it only depends on x and z, never on the world's seed: Mitofus has the
 * same shape in every world. The JSON wraps it in {@code flat_cache}.
 * <p>
 * Until 2026-11 the detail was vanilla's own noise ({@code "noise": "minecraft:overworld/continents"}), which depends
 * on the seed; that field is gone (an old JSON that still has it is read without detail).
 */
public record MitofusTerrainFunction(MitofusTerrain.Channel channel, ResourceLocation map, MitofusRelief relief,
                                     boolean detail) implements DensityFunction {
    public static final MapCodec<MitofusTerrainFunction> CODEC = RecordCodecBuilder.<Raw>mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(MitofusTerrain.Channel::values).fieldOf("channel").forGetter(Raw::channel),
            ResourceLocation.CODEC.fieldOf("map").forGetter(Raw::map),
            Codec.BOOL.optionalFieldOf("detail", false).forGetter(Raw::detail)
    ).apply(instance, Raw::new)).flatXmap(MitofusTerrainFunction::create,
            function -> DataResult.success(new Raw(function.channel, function.map, function.detail)));
    public static final KeyDispatchDataCodec<MitofusTerrainFunction> DATA_CODEC = KeyDispatchDataCodec.of(CODEC);

    /** The fields as written in the JSON. */
    private record Raw(MitofusTerrain.Channel channel, ResourceLocation map, boolean detail) {
    }

    public MitofusTerrainFunction(MitofusTerrain.Channel channel, ResourceLocation map, boolean detail) {
        this(channel, map, MitofusTerrains.get(map), detail);
    }

    private static DataResult<MitofusTerrainFunction> create(Raw raw) {
        try {
            return DataResult.success(new MitofusTerrainFunction(raw.channel(), raw.map(), raw.detail()));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "bad mitofus_terrain: " + e.getMessage());
        }
    }

    @Override
    public double compute(FunctionContext context) {
        return MitofusNoise.climate(channel, relief, detail, context.blockX(), context.blockZ());
    }

    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(array, this);
    }

    /** No noise of the world in it: the visitor (the world's random state) has nothing to seed. */
    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(this);
    }

    @Override
    public double minValue() {
        return MitofusNoise.minValue(channel, detail);
    }

    @Override
    public double maxValue() {
        return MitofusNoise.maxValue(channel, detail);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return DATA_CODEC;
    }
}
