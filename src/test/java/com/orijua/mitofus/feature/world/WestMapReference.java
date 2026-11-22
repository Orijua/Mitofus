package com.orijua.mitofus.feature.world;

/**
 * Reference pixels of the West Continent's map (4096x3072) shared by the unit tests. The game tests
 * (src/main/.../gametest) cannot see src/test and repeat the ones they use (the desert 808,1320; the cold spot near
 * Altamira is {@code Altamira.MAP_PIXEL_X/Z}); keep them in step with this class.
 */
final class WestMapReference {
    static final int COVADONGA_X = 1772;
    static final int COVADONGA_Z = 458;
    /** A spot in the mountains of the west: mountain. */
    static final int MOUNTAIN_X = 2072;
    static final int MOUNTAIN_Z = 1976;
    /** A spot in the sea, east of the continent: deep sea. */
    static final int SEA_X = 3960;
    static final int SEA_Z = 968;
    /** The desert, in the south-west. */
    static final int DESERT_X = 808;
    static final int DESERT_Z = 1320;

    private WestMapReference() {
    }
}
