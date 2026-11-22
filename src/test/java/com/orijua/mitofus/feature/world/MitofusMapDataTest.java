package com.orijua.mitofus.feature.world;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MitofusMapDataTest {
    private static final Map<Integer, String> LEGEND = Map.of(0, "sea", 1, "minecraft:plains", 7, "minecraft:windswept_hills");

    // 4x3 cells of 10 px (blocks_per_pixel 2: 20 blocks per cell). Covadonga is pixel (15, 15): the middle of cell (1, 1).
    //   sea  sea   sea   sea         relief 0 1 1 0
    //   sea  P     P     sea                0 3 4 0
    //   sea  sea   M     sea                0 2 9 0
    private static final byte[] RELIEF = {0, 1, 1, 0, 0, 3, 4, 0, 0, 2, 9, 0};
    private static final byte[] REGION = {0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 7, 0};

    private static MitofusMapData small() {
        return new MitofusMapData(4, 3, 2, 10, 15, 15, RELIEF, REGION, LEGEND);
    }

    @Test
    void cellsAreRowMajorNorthUp() {
        MitofusMapData data = small();
        assertEquals(4, data.width());
        assertEquals(3, data.height());
        assertEquals(20, data.cellBlocks());
        assertEquals(1, data.originCol());
        assertEquals(1, data.originRow());
        assertEquals(3, data.reliefAtCell(1, 1));
        assertEquals(9, data.reliefAtCell(2, 2));
        assertEquals(1, data.reliefAtCell(2, 0), "the north row is first");
        assertEquals(7, data.regionAtCell(2, 2));
        assertEquals(1, data.regionAtCell(2, 1));
        assertEquals("minecraft:windswept_hills", data.legend(7));
        assertNull(data.legend(5));
    }

    @Test
    void beyondTheGridIsSea() {
        MitofusMapData data = small();
        assertEquals(0, data.reliefAtCell(-1, 0));
        assertEquals(0, data.reliefAtCell(4, 0));
        assertEquals(0, data.reliefAtCell(0, 3));
        assertEquals(MitofusMapData.SEA, data.regionAtCell(100, -100));
        assertEquals("sea", data.biomeAtBlock(1_000_000, 0));
        assertEquals(0.0, data.relief(-100_000, 0), 1e-9);
    }

    @Test
    void landIsReliefThreeOrMore() {
        MitofusMapData data = small();
        assertTrue(data.isLandCell(1, 1));
        assertFalse(data.isLandCell(1, 2), "relief 2 is shallow water");
        assertFalse(data.isLandCell(0, 0));
    }

    @Test
    void blockZeroIsOnTheOriginPixel() {
        MitofusMapData data = small();
        // Origin pixel (15, 15) = block 30 of the grid: the middle of cell (1, 1), which spans blocks 20..39.
        assertEquals(1, data.colAtBlock(0));
        assertEquals(1, data.rowAtBlock(0));
        assertEquals(1, data.colAtBlock(9), "block 9 is still cell 1 (grid block 39)");
        assertEquals(2, data.colAtBlock(10), "block 10 is the next cell east");
        assertEquals(1, data.colAtBlock(-10), "block -10 is grid block 20: the first block of cell 1");
        assertEquals(0, data.colAtBlock(-11));
        assertEquals(0, data.rowAtBlock(-11), "north is negative z");
        assertEquals(2, data.rowAtBlock(10), "south is positive z");
        assertEquals(1, data.regionAtBlock(0, 0));
        assertEquals("minecraft:windswept_hills", data.biomeAtBlock(20, 20));
    }

    @Test
    void reliefIsBilinearBetweenCellCentres() {
        // Cell (1, 1) has relief 3 and its centre is grid block 30, i.e. block 0 here.
        MitofusMapData data = small();
        assertEquals(3.0, data.relief(0, 0), 1e-9);
        assertEquals(4.0, data.relief(20, 0), 1e-9, "centre of cell (2, 1)");
        assertEquals(3.5, data.relief(10, 0), 1e-9);
        assertEquals(9.0, data.relief(20, 20), 1e-9);
        // Halfway between the centres of the four cells (1,1)=3, (2,1)=4, (1,2)=2, (2,2)=9.
        assertEquals((3 + 4 + 2 + 9) / 4.0, data.relief(10, 10), 1e-9);
    }

    @Test
    void finerCellsKeepTheSameBlockMapping() {
        // The same 20-block-wide area as one cell of 10 px, or as 2x2 cells of 5 px: the origin stays on the same pixel.
        byte[] fine = new byte[8 * 6];
        byte[] fineRegion = new byte[8 * 6];
        fine[3 * 8 + 3] = 5;
        fineRegion[3 * 8 + 3] = 1;
        MitofusMapData data = new MitofusMapData(8, 6, 2, 5, 15, 15, fine, fineRegion, LEGEND);
        assertEquals(10, data.cellBlocks());
        assertEquals(3, data.originCol());
        assertEquals(3, data.colAtBlock(0));
        assertEquals(5, data.reliefAtCell(data.colAtBlock(0), data.rowAtBlock(0)));
    }

    @Test
    void rejectsBadData() {
        assertThrows(IllegalArgumentException.class, () -> new MitofusMapData(4, 3, 2, 10, 15, 15, new byte[5], REGION, LEGEND));
        assertThrows(IllegalArgumentException.class, () -> new MitofusMapData(0, 3, 2, 10, 15, 15, RELIEF, REGION, LEGEND));
        assertThrows(IllegalArgumentException.class, () -> new MitofusMapData(4, 3, 2, 0, 15, 15, RELIEF, REGION, LEGEND));
        assertThrows(IllegalArgumentException.class, () -> new MitofusMapData(4, 3, 2, 10, 400, 15, RELIEF, REGION, LEGEND), "origin off the map");
        byte[] tall = RELIEF.clone();
        tall[0] = 10;
        assertThrows(IllegalArgumentException.class, () -> new MitofusMapData(4, 3, 2, 10, 15, 15, tall, REGION, LEGEND), "relief above 9");
        byte[] unknown = REGION.clone();
        unknown[0] = 3;
        assertThrows(IllegalArgumentException.class, () -> new MitofusMapData(4, 3, 2, 10, 15, 15, RELIEF, unknown, LEGEND), "region not in the legend");
    }

    @Test
    void seaAndLandMustAgreeWithTheRelief() {
        byte[] seaOnLand = RELIEF.clone();
        seaOnLand[0] = 4; // region 0 (sea) with relief 4
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new MitofusMapData(4, 3, 2, 10, 15, 15, seaOnLand, REGION, LEGEND));
        assertTrue(e.getMessage().contains("sea"), e.getMessage());
        byte[] landInTheSea = RELIEF.clone();
        landInTheSea[5] = 1; // plains with relief 1
        assertThrows(IllegalArgumentException.class, () -> new MitofusMapData(4, 3, 2, 10, 15, 15, landInTheSea, REGION, LEGEND));
    }

    // --- Parsing the files --------------------------------------------------------------------------------------

    private static final String META = """
            {"version": 1, "blocks_per_pixel": 2, "cell_px": 10, "width": 4, "height": 3,
             "origin": {"px": [15, 15], "col": 1, "row": 1, "name": "Covadonga"},
             "region_legend": {"0": "sea", "1": "minecraft:plains", "7": "minecraft:windswept_hills"}}
            """;

    private static InputStream text(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static BufferedImage image(int type, byte[] values, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, type);
        for (int i = 0; i < values.length; i++) {
            image.getRaster().setSample(i % width, i / width, 0, values[i]);
        }
        return image;
    }

    @Test
    void readsIndexedRegionsAndGrayRelief() throws IOException {
        MitofusMapData data = MitofusMapData.read(text(META),
                png(image(BufferedImage.TYPE_BYTE_INDEXED, REGION, 4, 3)),
                png(image(BufferedImage.TYPE_BYTE_GRAY, RELIEF, 4, 3)));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                assertEquals(RELIEF[row * 4 + col], data.reliefAtCell(col, row), "relief at " + col + "," + row);
                assertEquals(REGION[row * 4 + col], data.regionAtCell(col, row), "region at " + col + "," + row);
            }
        }
        assertEquals(1, data.originCol());
        assertEquals("minecraft:plains", data.legend(1));
    }

    @Test
    void rejectsAnImageOfTheWrongSize() throws IOException {
        InputStream wrong = png(image(BufferedImage.TYPE_BYTE_INDEXED, new byte[6], 3, 2));
        IOException e = assertThrows(IOException.class, () -> MitofusMapData.read(text(META), wrong,
                png(image(BufferedImage.TYPE_BYTE_GRAY, RELIEF, 4, 3))));
        assertTrue(e.getMessage().contains("region.png"), e.getMessage());
    }

    @Test
    void rejectsAReliefImageOfTheWrongSize() throws IOException {
        InputStream wrong = png(image(BufferedImage.TYPE_BYTE_GRAY, new byte[6], 3, 2));
        IOException e = assertThrows(IOException.class, () -> MitofusMapData.read(text(META),
                png(image(BufferedImage.TYPE_BYTE_INDEXED, REGION, 4, 3)), wrong));
        assertTrue(e.getMessage().contains("relief.png"), e.getMessage());
    }

    @Test
    void rejectsNegativeRelief() throws IOException {
        byte[] negative = RELIEF.clone();
        negative[5] = (byte) 200; // a gray level above 127 reads as a negative byte
        IOException e = assertThrows(IOException.class, () -> MitofusMapData.read(text(META),
                png(image(BufferedImage.TYPE_BYTE_INDEXED, REGION, 4, 3)), png(image(BufferedImage.TYPE_BYTE_GRAY, negative, 4, 3))));
        assertTrue(e.getMessage().contains("relief"), e.getMessage());
    }

    @Test
    void rejectsSeaWithReliefFromTheFiles() throws IOException {
        byte[] seaOnLand = RELIEF.clone();
        seaOnLand[0] = 4;
        IOException e = assertThrows(IOException.class, () -> MitofusMapData.read(text(META),
                png(image(BufferedImage.TYPE_BYTE_INDEXED, REGION, 4, 3)), png(image(BufferedImage.TYPE_BYTE_GRAY, seaOnLand, 4, 3))));
        assertTrue(e.getMessage().contains("sea"), e.getMessage());
    }

    @Test
    void rejectsAWrongVersionAndBrokenMeta() throws IOException {
        String v2 = META.replace("\"version\": 1", "\"version\": 2");
        assertThrows(IOException.class, () -> MitofusMapData.read(text(v2),
                png(image(BufferedImage.TYPE_BYTE_INDEXED, REGION, 4, 3)), png(image(BufferedImage.TYPE_BYTE_GRAY, RELIEF, 4, 3))));
        assertThrows(IOException.class, () -> MitofusMapData.read(text("{\"version\": 1}"),
                png(image(BufferedImage.TYPE_BYTE_INDEXED, REGION, 4, 3)), png(image(BufferedImage.TYPE_BYTE_GRAY, RELIEF, 4, 3))));
        assertThrows(IOException.class, () -> MitofusMapData.read(text("not json"),
                png(image(BufferedImage.TYPE_BYTE_INDEXED, REGION, 4, 3)), png(image(BufferedImage.TYPE_BYTE_GRAY, RELIEF, 4, 3))));
    }

    @Test
    void rejectsAMissingFolder() {
        assertThrows(IOException.class, () -> MitofusMapData.load("/data/mitofus/mitofus_map/nowhere/"));
    }

    // --- The real data (converted from map) ---------------------------------------------------------------

    @Test
    void theWestMapLoadsAndCovadongaIsOnLand() throws IOException {
        MitofusMapData west = MitofusMapData.load(MitofusMapData.WEST);
        assertEquals(4096, west.width() * west.cellPx());
        assertEquals(3072, west.height() * west.cellPx());
        assertEquals(2, west.blocksPerPixel());
        assertEquals("sea", west.legend(MitofusMapData.SEA));
        assertEquals("minecraft:plains", west.biomeAtBlock(0, 0), "Covadonga is in the plains");
        double relief = west.relief(0, 0);
        assertTrue(relief >= 4 && relief <= 5, "Covadonga is lowland: " + relief);
        assertTrue(west.isLandCell(west.colAtBlock(0), west.rowAtBlock(0)));
    }

    @Test
    void theWestMapIsSeaAroundAndMountainsAreInTheRightPlace() throws IOException {
        MitofusMapData west = MitofusMapData.load(MitofusMapData.WEST);
        for (int col = 0; col < west.width(); col++) {
            assertEquals(0, west.reliefAtCell(col, 0), "north edge");
            assertEquals(0, west.reliefAtCell(col, west.height() - 1), "south edge");
        }
        for (int row = 0; row < west.height(); row++) {
            assertEquals(0, west.regionAtCell(0, row), "west edge");
            assertEquals(0, west.regionAtCell(west.width() - 1, row), "east edge");
        }
        assertEquals(0.0, west.relief(-1_000_000, 0), 1e-9);
        // Block offsets from Covadonga (pixel 1772, 458) to a spot in the western mountains.
        int dx = (WestMapReference.MOUNTAIN_X - WestMapReference.COVADONGA_X) * west.blocksPerPixel();
        int dz = (WestMapReference.MOUNTAIN_Z - WestMapReference.COVADONGA_Z) * west.blocksPerPixel();
        assertTrue(west.relief(dx, dz) >= 7, "the western mountains are mountain: " + west.relief(dx, dz));
        // The sea east of the continent, is deep sea.
        assertEquals(0.0, west.relief((WestMapReference.SEA_X - WestMapReference.COVADONGA_X) * west.blocksPerPixel(),
                (WestMapReference.SEA_Z - WestMapReference.COVADONGA_Z) * west.blocksPerPixel()), 1e-9);
        // Every region used is in the legend, and region 0 is exactly the water.
        for (int row = 0; row < west.height(); row++) {
            for (int col = 0; col < west.width(); col++) {
                assertEquals(west.regionAtCell(col, row) == MitofusMapData.SEA, west.reliefAtCell(col, row) < MitofusMapData.COAST_RELIEF,
                        "region 0 <=> water at " + col + "," + row);
            }
        }
    }

    @Test
    void cachedIgnoresTheTrailingSlashOfTheFolder() {
        String folder = MitofusMapData.WEST;
        assertTrue(folder.endsWith("/"));
        assertSame(MitofusMapData.cached(folder), MitofusMapData.cached(folder.substring(0, folder.length() - 1)));
    }

    /**
     * The map points the terrain game tests (MitofusDimensionTests) take on trust: they say what is there (relief or region)
     * in the real data, so that a regenerated relief.png/region.png fails here as a data change, not as a terrain bug.
     */
    @Test
    void theGameTestsReferencePointsAreWhatTheyClaim() throws IOException {
        MitofusMapData west = MitofusMapData.load(MitofusMapData.WEST);
        // Block (x, z) -> cells within a radius: relief {min, max}.
        int[][] reliefPoints = {
                {-8, -804, 3, 0, 0},      // the open sea north of Covadonga
                {1272, 380, 2, 4, 4},      // dry lowland
                {600, 3036, 2, 9, 9},     // a high mountain in the south-east
                {-72, 1692, 2, 7, 9},      // the mountains south of Covadonga
                {0, 0, 1, 5, 5}};         // Covadonga's place
        for (int[] p : reliefPoints) {
            int col = west.colAtBlock(p[0]);
            int row = west.rowAtBlock(p[1]);
            for (int dc = -p[2]; dc <= p[2]; dc++) {
                for (int dr = -p[2]; dr <= p[2]; dr++) {
                    int relief = west.reliefAtCell(col + dc, row + dr);
                    assertTrue(relief >= p[3] && relief <= p[4], "relief at block " + p[0] + "," + p[1] + " cell offset " + dc + "," + dr + ": " + relief);
                }
            }
        }
        // Source pixel -> region of the 3x3 cells around it.
        int[][] regionPoints = {
                {0, 0, MitofusMapData.PLAINS},
                {WestMapReference.DESERT_X, WestMapReference.DESERT_Z, 3},
                {1688, 568, 6}, {1896, 264, 5}, {1688, 2392, 9}, {456, 1016, 10}};
        for (int i = 0; i < regionPoints.length; i++) {
            int[] p = regionPoints[i];
            int col = i == 0 ? west.colAtBlock(0) : Math.floorDiv(p[0], west.cellPx());
            int row = i == 0 ? west.rowAtBlock(0) : Math.floorDiv(p[1], west.cellPx());
            for (int dc = -1; dc <= 1; dc++) {
                for (int dr = -1; dr <= 1; dr++) {
                    assertEquals(p[2], west.regionAtCell(col + dc, row + dr), "region around " + p[0] + "," + p[1] + " offset " + dc + "," + dr);
                }
            }
        }
    }
}
