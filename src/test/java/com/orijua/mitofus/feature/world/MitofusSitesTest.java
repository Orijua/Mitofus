package com.orijua.mitofus.feature.world;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fixed sites of the West Continent ({@code mitofus_map/west/sites.json}) and the rules the search gave them. */
class MitofusSitesTest {
    private static final MitofusSites SITES = MitofusSites.west();
    private static final MitofusMap MAP = new MitofusMap(MitofusMapData.west());

    private static ChunkPos site(String id) {
        return SITES.get(id).orElseThrow(() -> new AssertionError("no " + id + " in sites.json"));
    }

    @Test
    void theFourPlacesAreThereAndNothingElse() {
        assertEquals(List.of(WorldSites.COVADONGA, WorldSites.ALTAMIRA, WorldSites.TORRE_DEL_ORO, WorldSites.EIBAR),
                List.copyOf(SITES.all().keySet()));
    }

    @Test
    void covadongaIsAtItsPixel() {
        assertEquals(new ChunkPos(0, 0), site(WorldSites.COVADONGA), "the map's origin is Covadonga's pixel");
    }

    /** The rules the sites were chosen by hold for the fixed sites. */
    @Test
    void theSitesKeepTheRulesOfTheSearch() {
        ChunkPos covadonga = site(WorldSites.COVADONGA);
        ChunkPos cave = site(WorldSites.ALTAMIRA);
        ChunkPos manor = site(WorldSites.TORRE_DEL_ORO);
        ChunkPos eibar = site(WorldSites.EIBAR);
        for (ChunkPos chunk : SITES.all().values()) {
            assertTrue(MAP.isLand(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()), chunk + " is on the map's land");
            // The shore is at relief 2.5, the border of a coast cell; the cave stands on the coast (3), the rest inland.
            assertTrue(MAP.reliefAt(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()) > MitofusMapData.COAST_RELIEF - 0.5,
                    chunk + " is above the shore");
        }
        assertEquals(MitofusMapData.PLAINS, MAP.regionAt(covadonga.getMiddleBlockX(), covadonga.getMiddleBlockZ()), "Covadonga on the plains");
        assertTrue(covadonga.z - cave.z >= Altamira.MIN_DISTANCE, "the cave far north of Covadonga: " + cave);
        assertTrue(MAP.reliefAt(cave.getMiddleBlockX(), cave.getMiddleBlockZ()) < Altamira.MAX_MAP_RELIEF, "the cave not on a mountain");
        ChunkPos glacier = Altamira.mapOffset(MitofusMapData.west());
        assertTrue(cave.getChessboardDistance(glacier) <= 15, "the cave near the cold target: " + cave + " vs " + glacier);
        int toManor = covadonga.getChessboardDistance(manor);
        assertTrue(toManor >= TorreDelOro.MIN_DISTANCE && toManor <= TorreDelOro.MAX_DISTANCE, "the manor " + toManor + " chunks from Covadonga");
        ChunkPos eibarOnTheMap = Eibar.mapOffset(MitofusMapData.west());
        assertTrue(eibar.getChessboardDistance(eibarOnTheMap) <= 1, "Eibar at its place on the map: " + eibar + " vs " + eibarOnTheMap);
        for (ChunkPos other : List.of(covadonga, cave, manor)) {
            assertTrue(eibar.getChessboardDistance(other) >= Eibar.KEEP_AWAY, "Eibar away from " + other);
        }
    }

    private static MitofusSites read(String json) throws IOException {
        return MitofusSites.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aGoodFileIsRead() throws IOException {
        MitofusSites sites = read("{\"version\": 1, \"sites\": [{\"id\": \"a\", \"chunk\": [3, -4]}, {\"id\": \"b_2\", \"chunk\": [-1, 0], \"note\": \"x\"}]}");
        assertEquals(Map.of("a", new ChunkPos(3, -4), "b_2", new ChunkPos(-1, 0)), sites.all());
        assertTrue(sites.get("c").isEmpty());
    }

    @Test
    void badFilesAreRejected() {
        for (String bad : List.of("{\"version\": 2, \"sites\": []}",
                "{\"sites\": []}",
                "{\"version\": 1}",
                "{\"version\": 1, \"sites\": [{\"chunk\": [0, 0]}]}",
                "{\"version\": 1, \"sites\": [{\"id\": \"A\", \"chunk\": [0, 0]}]}",
                "{\"version\": 1, \"sites\": [{\"id\": \"a\", \"chunk\": [0]}]}",
                "{\"version\": 1, \"sites\": [{\"id\": \"a\", \"chunk\": [0.5, 0]}]}",
                "{\"version\": 1, \"sites\": [{\"id\": \"a\", \"chunk\": [\"0\", 0]}]}",
                "{\"version\": 1, \"sites\": [{\"id\": \"a\", \"chunk\": [9999999, 0]}]}",
                "{\"version\": 1, \"sites\": [{\"id\": \"a\", \"chunk\": [0, 0]}, {\"id\": \"a\", \"chunk\": [1, 1]}]}",
                "{\"version\": 1, \"sites\": [")) {
            assertThrows(IOException.class, () -> read(bad), bad);
        }
    }
}
