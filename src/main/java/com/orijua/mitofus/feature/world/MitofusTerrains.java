package com.orijua.mitofus.feature.world;

import com.orijua.mitofus.Mitofus;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The relief maps a {@code mitofus:mitofus_terrain} density function can read, by id: the hand-drawn
 * {@link #TEST_ISLAND}, or a map converted by {@code tools/mitofus_map} ({@code mitofus:west} is
 * {@code data/mitofus/mitofus_map/west/}, read with {@link MitofusMapData} and warped like the biomes by
 * {@link MitofusMap}).
 */
public final class MitofusTerrains {
    /** A hand-drawn island with a mountain in the middle: the technical test of the terrain. */
    public static final ResourceLocation TEST_ISLAND = Mitofus.id("test_island");
    /** The West Continent of map, the terrain of the Mitofus dimension. */
    public static final ResourceLocation WEST = Mitofus.id("west");

    private static final MitofusTerrain ISLAND = new MitofusTerrain(List.of("0000011100000",
            "0001233321000",
            "0023445443200",
            "0134556554310",
            "0245668665420",
            "1345689865431",
            "1356899986531",
            "1345689865431",
            "0245668665420",
            "0134556554310",
            "0023445443200",
            "0001233321000",
            "0000011100000"), 96, 6, 6);

    /** Maps already read, by id: the data never changes while the game runs. */
    private static final Map<ResourceLocation, MitofusMap> MAPS = new ConcurrentHashMap<>();

    private MitofusTerrains() {
    }

    /** The hand-drawn test island. */
    public static MitofusTerrain testIsland() {
        return ISLAND;
    }

    /**
     * The relief map with this id: the test island, or map in {@code mitofus_map/<path>/}. Fails with an
     * {@link IllegalArgumentException} if there is no such map.
     */
    public static MitofusRelief get(ResourceLocation id) {
        if (id.equals(TEST_ISLAND)) {
            return ISLAND;
        }
        return MAPS.computeIfAbsent(id, key -> {
            try {
                return new MitofusMap(MitofusMapData.cached(MitofusBiomeSource.folder(key)));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("unknown Mitofus terrain " + key + " (" + e.getMessage() + ")", e);
            }
        });
    }
}
