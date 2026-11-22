package com.orijua.mitofus.feature.world;

/**
 * A relief map over the blocks of the Mitofus dimension, from {@code 0} (deep sea) to {@code 9} (peak), smooth between
 * cells: what a {@link MitofusTerrainFunction} turns into the vanilla terrain parameters. The map
 * ({@link MitofusMap}) and the hand-drawn test island ({@link MitofusTerrain}) are relief maps.
 */
@FunctionalInterface
public interface MitofusRelief {
    /** The relief at block (x, z), from 0 to {@link MitofusTerrain#MAX_RELIEF}. */
    double reliefAt(int blockX, int blockZ);
}
