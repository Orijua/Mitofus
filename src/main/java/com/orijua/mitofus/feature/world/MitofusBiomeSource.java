package com.orijua.mitofus.feature.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The biomes of the Mitofus dimension: the lore regions of a map ({@link MitofusMap}, read from
 * {@code mitofus_map/<path>/} by {@link MitofusMapData}) on its land, and the {@code outside} source (vanilla multi-noise)
 * in its sea and lakes (region {@link MitofusMapData#SEA}) and beyond it. In the dimension's JSON:
 * <pre>{"type": "mitofus:mitofus_map", "outside": {...}, "map": "mitofus:west",
 *  "legend": {"1": "minecraft:plains", ...}}</pre>
 * The legend gives a biome to every land region of the map (region index to biome). The map is read from the mod's own
 * resources (the classpath), not from datapacks.
 */
public final class MitofusBiomeSource extends BiomeSource {
    private static final Codec<Integer> REGION = Codec.STRING.comapFlatMap(key -> {
        try {
            return DataResult.success(Integer.parseInt(key));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "a legend key must be a region index, not '" + key + "'");
        }
    }, String::valueOf);

    public static final MapCodec<MitofusBiomeSource> CODEC = RecordCodecBuilder.<Raw>mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("outside").forGetter(Raw::outside),
            ResourceLocation.CODEC.fieldOf("map").forGetter(Raw::map),
            Codec.unboundedMap(REGION, Biome.CODEC).fieldOf("legend").forGetter(Raw::legend)
    ).apply(instance, Raw::new)).flatXmap(MitofusBiomeSource::create,
            source -> DataResult.success(new Raw(source.outside, source.mapId, source.legend)));

    /** The fields as written in the JSON. */
    private record Raw(BiomeSource outside, ResourceLocation map, Map<Integer, Holder<Biome>> legend) {
    }

    private final BiomeSource outside;
    private final ResourceLocation mapId;
    private final Map<Integer, Holder<Biome>> legend;
    private final MitofusMap map;
    /** The legend by region index, for the lookup of every biome; {@code null} for the sea. */
    @SuppressWarnings("unchecked")
    private final Holder<Biome>[] byRegion = new Holder[256];

    public MitofusBiomeSource(BiomeSource outside, ResourceLocation mapId, MitofusMapData data, Map<Integer, Holder<Biome>> legend) {
        this.outside = outside;
        this.mapId = mapId;
        this.legend = Map.copyOf(legend);
        this.map = new MitofusMap(data);
        if (this.legend.containsKey(MitofusMapData.SEA)) {
            throw new IllegalArgumentException("region " + MitofusMapData.SEA + " is the sea and lakes: its biomes come from 'outside'");
        }
        Set<Integer> missing = new TreeSet<>(data.legend().keySet());
        missing.remove(MitofusMapData.SEA);
        missing.removeAll(this.legend.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("the map " + mapId + " has regions missing from the legend: " + missing);
        }
        Set<Integer> unknown = new TreeSet<>(this.legend.keySet());
        unknown.removeAll(data.legend().keySet());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("the legend has regions the map " + mapId + " doesn't: " + unknown);
        }
        this.legend.forEach((region, biome) -> byRegion[region] = biome);
    }

    /** The classpath folder of a map: {@code mitofus:west} is {@code /data/mitofus/mitofus_map/west/}. */
    public static String folder(ResourceLocation map) {
        return "/data/" + map.getNamespace() + "/mitofus_map/" + map.getPath() + "/";
    }

    private static DataResult<MitofusBiomeSource> create(Raw raw) {
        try {
            MitofusMapData data = MitofusMapData.cached(folder(raw.map()));
            return DataResult.success(new MitofusBiomeSource(raw.outside(), raw.map(), data, raw.legend()));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "bad mitofus_map biome source: " + e.getMessage());
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(legend.values().stream(), outside.possibleBiomes().stream()).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        Holder<Biome> lore = byRegion[map.regionAt((quartX << 2) + 2, (quartZ << 2) + 2)];
        return lore != null ? lore : outside.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }

    /** The lore map (tests and the site search use it). */
    public MitofusMap map() {
        return map;
    }

    /** Where the map has no land: the sea, the lakes and beyond the map. */
    public BiomeSource outside() {
        return outside;
    }

    /**
     * Whether block (x, z) is in the given region of the lore map (after the warp). Always true for any other biome
     * source (the real world, tests on vanilla terrain).
     */
    public static boolean inMapRegion(BiomeSource biomes, int blockX, int blockZ, int region) {
        return !(biomes instanceof MitofusBiomeSource mitofus) || mitofus.map.regionAt(blockX, blockZ) == region;
    }

    /**
     * Whether block (x, z) may hold a one-per-world place as far as the lore map goes: on its land, not in its sea or
     * lakes. Always true for any other biome source (the real world, tests on vanilla terrain).
     */
    public static boolean onMapLand(BiomeSource biomes, int blockX, int blockZ) {
        return !(biomes instanceof MitofusBiomeSource mitofus) || mitofus.map.isLand(blockX, blockZ);
    }

    /**
     * Whether the map's relief at block (x, z) is below {@code limit} (0-9; 8 or more is mountain). Always true for any
     * other biome source (the real world, tests on vanilla terrain).
     */
    public static boolean reliefBelow(BiomeSource biomes, int blockX, int blockZ, double limit) {
        return !(biomes instanceof MitofusBiomeSource mitofus) || mitofus.map.reliefAt(blockX, blockZ) < limit;
    }
}
