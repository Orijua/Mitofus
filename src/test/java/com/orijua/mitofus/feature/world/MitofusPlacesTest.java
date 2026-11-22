package com.orijua.mitofus.feature.world;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MitofusPlacesTest {
    private static final MitofusMapData MAP = MitofusMapData.west();
    private static final MitofusPlaces PLACES = MitofusPlaces.west();

    @Test
    void theRegistryHasThePlaces() {
        assertEquals(placesInTheFile(), PLACES.all().size(), "every entry of places.json is loaded: " + PLACES.ids());
        for (String id : List.of("covadonga", "eibar", "sierra_nevada", "desierto_de_tabernas", "mar_del_este")) {
            assertTrue(PLACES.get(id).isPresent(), id);
        }
    }

    @Test
    void idsAreUnique() {
        Set<String> seen = new HashSet<>();
        PLACES.all().forEach(place -> assertTrue(seen.add(place.id()), "twice: " + place.id()));
        String twice = """
                {"version": 1, "places": [
                  {"id": "a", "name": "A", "type": "city", "px": [1, 1], "certainty": "mapa", "land": true},
                  {"id": "a", "name": "B", "type": "city", "px": [2, 2], "certainty": "mapa", "land": true}]}""";
        assertThrows(IOException.class, () -> MitofusPlaces.read(new ByteArrayInputStream(twice.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void everyPlaceIsOnTheMap() {
        for (MitofusPlaces.Place place : PLACES.all()) {
            assertTrue(MitofusPlaces.onMap(MAP, place), place.id() + " is off the map");
            int[] block = MitofusPlaces.block(MAP, place);
            assertEquals(place.pixelX() / MAP.cellPx(), MAP.colAtBlock(block[0]), place.id());
            assertEquals(place.pixelZ() / MAP.cellPx(), MAP.rowAtBlock(block[1]), place.id());
        }
    }

    @Test
    void covadongaIsTheOrigin() {
        assertArrayEquals(new int[]{0, 0}, MitofusPlaces.block(MAP, PLACES.get("covadonga").orElseThrow()));
        int[] eibar = MitofusPlaces.block(MAP, PLACES.get("eibar").orElseThrow());
        assertTrue(eibar[0] < 0 && eibar[1] < 0, "Eibar is north-west of Covadonga");
    }

    @Test
    void landPlacesFallOnLand() {
        for (MitofusPlaces.Place place : PLACES.all()) {
            if (!place.land()) {
                continue;
            }
            int col = place.pixelX() / MAP.cellPx();
            int row = place.pixelZ() / MAP.cellPx();
            assertTrue(MAP.isLandCell(col, row), place.id() + " is in the sea: relief " + MAP.reliefAtCell(col, row));
            assertTrue(MAP.regionAtCell(col, row) != MitofusMapData.SEA, place.id() + " has the sea region");
            int[] landing = MitofusPlaces.landing(MAP, place).orElseThrow(() -> new AssertionError(place.id() + ": no landing"));
            int landingCol = MAP.colAtBlock(landing[0]);
            int landingRow = MAP.rowAtBlock(landing[1]);
            assertTrue(MAP.isLandCell(landingCol, landingRow), place.id() + " lands on land");
            assertTrue(Math.abs(landingCol - col) <= MitofusPlaces.SEARCH_CELLS && Math.abs(landingRow - row) <= MitofusPlaces.SEARCH_CELLS,
                    place.id() + " lands near it");
            assertTrue(MAP.relief(landing[0], landing[1]) > 2.5, place.id() + " lands above the shore: " + MAP.relief(landing[0], landing[1]));
        }
    }

    @Test
    void seaPlacesHaveNoLanding() {
        MitofusPlaces.Place sea = PLACES.get("mar_del_este").orElseThrow();
        assertFalse(sea.land());
        assertTrue(MitofusPlaces.landing(MAP, sea).isEmpty());
        // A place said to be land but in open sea, far from any coast, has no landing either.
        MitofusPlaces.Place lost = new MitofusPlaces.Place("lost", "Lost", "island", sea.pixelX(), sea.pixelZ(), "mapa", true);
        assertTrue(MitofusPlaces.landing(MAP, lost).isEmpty());
    }

    private static int placesInTheFile() {
        try (java.io.InputStream in = MitofusPlaces.class.getResourceAsStream(MitofusMapData.WEST + MitofusPlaces.FILE)) {
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("places").size();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String place(String px, String land) {
        return "{\"version\": 1, \"places\": [{\"id\": \"x\", \"name\": \"x\", \"type\": \"city\", \"px\": " + px
                + ", \"certainty\": \"mapa\", \"land\": " + land + "}]}";
    }

    @Test
    void badFilesAreRefused() {
        Map<String, String> bad = new java.util.LinkedHashMap<>();
        bad.put("version", "{\"version\": 2, \"places\": []}");
        bad.put("id", "{\"version\": 1, \"places\": [{\"id\": \"Bad Id\", \"name\": \"x\", \"type\": \"city\", \"px\": [1, 1], \"certainty\": \"mapa\", \"land\": true}]}");
        bad.put("certainty", "{\"version\": 1, \"places\": [{\"id\": \"x\", \"name\": \"x\", \"type\": \"city\", \"px\": [1, 1], \"certainty\": \"seguro\", \"land\": true}]}");
        bad.put("px", place("[1]", "true"));
        bad.put("px 3", place("[1, 2, 3]", "true"));
        bad.put("px -1", place("[-1, 2]", "true"));
        bad.put("px fraction", place("[1.5, 2]", "true"));
        bad.put("px text", place("[\"a\", 2]", "true"));
        bad.put("land", place("[1, 1]", "1"));
        bad.put("land yes", place("[1, 1]", "\"yes\""));
        bad.put("field", "{\"version\": 1, \"places\": [{\"id\": \"x\"}]}");
        bad.put("places", "{\"version\": 1}");
        bad.put("empty", "{}");
        bad.forEach((what, json) -> {
            IOException e = assertThrows(IOException.class,
                    () -> MitofusPlaces.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))), what);
            String key = what.split(" ")[0];
            assertTrue(e.getMessage() != null && (e.getMessage().contains(key) || what.equals("empty") || what.equals("field")),
                    what + ": the message names the field: " + e.getMessage());
        });
        // A valid place with the same shape is accepted.
        assertDoesNotThrow(() -> MitofusPlaces.read(new ByteArrayInputStream(place("[1, 1]", "true").getBytes(StandardCharsets.UTF_8))));
    }

    // --- landing on a synthetic map: 11x11 cells of 10 px, the place in the middle cell (5, 5) ----------------

    private static MitofusMapData grid(int land, int landCol, int landRow) {
        byte[] relief = new byte[121];
        byte[] region = new byte[121];
        if (land >= 0) {
            relief[landRow * 11 + landCol] = (byte) land;
            region[landRow * 11 + landCol] = (byte) (land >= MitofusMapData.COAST_RELIEF ? 1 : 0);
        }
        return new MitofusMapData(11, 11, 2, 10, 55, 55, relief, region, Map.of(0, "sea", 1, "minecraft:plains"));
    }

    private static MitofusPlaces.Place at(int col, int row) {
        return new MitofusPlaces.Place("p", "P", "city", col * 10 + 5, row * 10 + 5, "mapa", true);
    }

    @Test
    void landingStopsAtSearchCells() {
        MitofusPlaces.Place place = at(5, 5);
        assertTrue(MitofusPlaces.landing(grid(5, 5 + MitofusPlaces.SEARCH_CELLS, 5), place).isPresent(), "land at SEARCH_CELLS is found");
        assertTrue(MitofusPlaces.landing(grid(5, 5 + MitofusPlaces.SEARCH_CELLS + 1, 5), place).isEmpty(), "land one cell farther is not");
        assertTrue(MitofusPlaces.landing(grid(-1, 0, 0), place).isEmpty(), "all sea");
    }

    @Test
    void landingUsesACoastCellWhenThereIsNoFirmLand() {
        // An island of one cell with exactly COAST_RELIEF: the coast is all there is.
        MitofusMapData island = grid(MitofusMapData.COAST_RELIEF, 6, 5);
        int[] landing = MitofusPlaces.landing(island, at(5, 5)).orElseThrow();
        assertEquals(6, island.colAtBlock(landing[0]));
        assertEquals(5, island.rowAtBlock(landing[1]));
        // Exactly FIRM_RELIEF counts as firm land.
        MitofusMapData firm = grid(MitofusPlaces.FIRM_RELIEF, 6, 5);
        assertTrue(MitofusPlaces.landing(firm, at(5, 5)).isPresent());
        // Below the coast it is water.
        assertTrue(MitofusPlaces.landing(grid(MitofusMapData.COAST_RELIEF - 1, 6, 5), at(5, 5)).isEmpty());
    }

    @Test
    void landingNextToTheEdgeOfTheGridDoesNotThrow() {
        MitofusMapData data = grid(5, 0, 0);
        MitofusPlaces.Place corner = at(0, 0);
        int[] landing = MitofusPlaces.landing(data, corner).orElseThrow();
        assertEquals(0, data.colAtBlock(landing[0]));
        assertEquals(0, data.rowAtBlock(landing[1]));
        assertTrue(MitofusPlaces.landing(grid(-1, 0, 0), corner).isEmpty());
    }

    @Test
    void aPixelOffTheGridIsNotOnTheMap() {
        MitofusMapData data = grid(5, 5, 5);
        assertFalse(MitofusPlaces.onMap(data, new MitofusPlaces.Place("p", "P", "city", -1, 5, "mapa", true)));
        assertFalse(MitofusPlaces.onMap(data, new MitofusPlaces.Place("p", "P", "city", 5, -1, "mapa", true)));
        assertTrue(MitofusPlaces.onMap(data, new MitofusPlaces.Place("p", "P", "city", 0, 0, "mapa", true)));
        assertFalse(MitofusPlaces.onMap(data, new MitofusPlaces.Place("p", "P", "city", 110, 5, "mapa", true)));
    }

    @Test
    void chunkOffsetIsTheChunkDistanceFromCovadonga() {
        assertEquals(new net.minecraft.world.level.ChunkPos(0, 0), MitofusPlaces.chunkOffset(MAP, PLACES.get("covadonga").orElseThrow()));
        assertEquals(new net.minecraft.world.level.ChunkPos(-13, -6), MitofusPlaces.chunkOffset(MAP, PLACES.get("eibar").orElseThrow()));
    }
}
