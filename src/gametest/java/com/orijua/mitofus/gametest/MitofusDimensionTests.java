package com.orijua.mitofus.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.feature.world.MitofusBiomeSource;
import com.orijua.mitofus.feature.world.MitofusMap;
import com.orijua.mitofus.feature.world.MitofusMapData;
import com.orijua.mitofus.feature.world.StartingVillage;
import com.orijua.mitofus.feature.world.WorldSitePlacement;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * The Mitofus dimension: its lore map, the sites a new world chooses in it, and the real world having none. The game-test
 * server drops the datapack's dimensions, so these decode the shipped dimension JSON and work with its generator.
 */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class MitofusDimensionTests {
    static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);
    /**
     * How wide, in blocks, the fringe of a shore may be: the beach climbs gently (about 3 blocks in half a cell), so the 3D
     * noise leaves puddles and sand bars on it. Half a cell.
     */
    private static final int SHORE_FRINGE = 16;

    /**
     * Of the 49 points (8 blocks apart) around Covadonga's place, the most that may be wet or under the sea: vanilla's caves
     * open at the surface here and there. Measured 0, 7 and 1 (seeds 1, 42, -7000123); 7 + 2. The centre and its four
     * neighbours must be dry above the sea at the terrain's own level (seed 42 has a cave mouth at (0, 0) itself: the
     * village search avoids those, the terrain does not). Recalibrate when the terrain changes.
     */
    private static final int MAX_WET_AT_COVADONGA = 9;
    /** How many steps before the map's coast the cuts to the other coasts start (they cross the whole continent otherwise). */
    private static final int COAST_APPROACH = 400;
    /**
     * Cave mouths on the three north cuts, 3 seeds: 29 columns in all (seed 1 x=-8: 6; seed 5 x=120: 12; seed 99 x=-300: 11)
     * and runs of at most 12 in a row; 29 + 3 and 12 + 1. A run of 12 is wide for a cave mouth (a ravine or a flooded
     * hole), but a lake would be wider and this is what the terrain makes. The east, south, west and south-east cuts had none.
     */
    private static final int MAX_CAVE_MOUTH_RUN = 13;
    private static final int MAX_CAVE_MOUTHS = 32;
    /** Steepest step on land of each coast cut. Measured: east 1, south 1, south-east 1, west 4 (it comes down from hills of relief 7). */
    private static final int EAST_LAND_JUMP = 3;
    private static final int SOUTH_LAND_JUMP = 3;
    private static final int WEST_LAND_JUMP = 5;
    private static final int DIAGONAL_LAND_JUMP = 3;
    /**
     * The range on z = -4, x -1700..-2400, in the nine seeds of MitofusFixedWorldTests: steepest step measured 12 in all
     * of them (the detail of the ridges has a fixed seed since 2026-11-07; 7 and 9 in seeds 1 and 99 with their own
     * vanilla noise) + 2; lowest ground 45 blocks over the sea in all (45 and 49 before), - 5.
     */
    private static final int MOUNTAIN_JUMP = 14;
    private static final int MOUNTAIN_MIN_HEIGHT = 40;

    /** Records a failed check to report every seed's failures at once, not only the first. */
    private static void check(List<String> failures, boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private MitofusDimensionTests() {
    }

    /** The generator that data/mitofus/dimension/mitofus.json describes. */
    static ChunkGenerator generator(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        try (Reader reader = server.getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath(Mitofus.MODID, "dimension/mitofus.json"))) {
            JsonElement json = JsonParser.parseReader(reader);
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            return LevelStem.CODEC.parse(ops, json).getOrThrow(IllegalStateException::new).generator();
        } catch (IOException e) {
            throw new AssertionError("the dimension JSON is missing", e);
        }
    }

    /** The map-driven noise settings of the dimension (vanilla's, with the terrain splines fed by map). */
    private static final ResourceKey<NoiseGeneratorSettings> MITOFUS_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, Mitofus.id("mitofus"));

    /** The noise of a world with this seed, with the dimension's own noise settings ({@link #MITOFUS_SETTINGS}). */
    static RandomState random(RegistryAccess registries, long seed) {
        var settings = registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(MITOFUS_SETTINGS);
        return RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
    }

    /** The biome at the middle of a source pixel of map (block (0, 0) is Covadonga's pixel). */
    private static Holder<Biome> biomeAtPixel(BiomeSource biomes, MitofusMapData map, int pixelX, int pixelZ, Climate.Sampler sampler) {
        int x = map.blockXOfPixel(pixelX);
        int z = map.blockZOfPixel(pixelZ);
        return biomes.getNoiseBiome(QuartPos.fromBlock(x), 16, QuartPos.fromBlock(z), sampler);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void dimensionUsesTheLoreMap(GameTestHelper helper) {
        ChunkGenerator generator = generator(helper);
        helper.assertTrue(generator.getBiomeSource() instanceof MitofusBiomeSource, "the lore biome source");
        MitofusBiomeSource biomes = (MitofusBiomeSource) generator.getBiomeSource();
        MitofusMapData map = biomes.map().data();
        var sampler = random(helper.getLevel().registryAccess(), 1L).sampler();
        // Places in the middle of a 3x3 block of cells of one region, out of the warp's reach.
        helper.assertTrue(biomes.getNoiseBiome(0, 16, 0, sampler).is(Biomes.PLAINS), "Covadonga stands on plains");
        helper.assertTrue(biomeAtPixel(biomes, map, 808, 1320, sampler).is(Biomes.DESERT), "the desert");
        helper.assertTrue(biomeAtPixel(biomes, map, 1688, 568, sampler).is(Biomes.SNOWY_TAIGA), "the snowy north of Covadonga");
        helper.assertTrue(biomeAtPixel(biomes, map, 1896, 264, sampler).is(Biomes.SNOWY_PLAINS), "the snowy plains east of Covadonga");
        helper.assertTrue(biomeAtPixel(biomes, map, 1688, 2392, sampler).is(Biomes.BADLANDS), "the orange mesas are badlands");
        helper.assertTrue(biomeAtPixel(biomes, map, 456, 1016, sampler).is(Biomes.STONY_PEAKS), "the stony peaks in the west");
        // The sea (here the cold target and far off the map) is vanilla's, from the outside source.
        for (int[] pixel : new int[][]{{1288, 104}, {-5000, 400}, {9000, 9000}}) {
            int qx = QuartPos.fromBlock(map.blockXOfPixel(pixel[0]));
            int qz = QuartPos.fromBlock(map.blockZOfPixel(pixel[1]));
            helper.assertTrue(biomes.getNoiseBiome(qx, 16, qz, sampler).equals(biomes.outside().getNoiseBiome(qx, 16, qz, sampler)),
                    "the sea at pixel " + pixel[0] + "," + pixel[1] + " comes from the outside source");
        }
        // Away from the edge of any region (the 3x3 cells around it hold one region, and the warp is under half a cell), the
        // biome is the one the map's data gives, read without the warp: not by the formula of the biome source.
        for (long seed : new long[]{1L, 2L, 3L}) {
            var seeded = random(helper.getLevel().registryAccess(), seed).sampler();
            int checked = 0;
            for (int x = -3000; x <= 3000; x += 97) {
                for (int z = -1000; z <= 3000; z += 89) {
                    int col = map.colAtBlock(x);
                    int row = map.rowAtBlock(z);
                    int region = map.regionAtCell(col, row);
                    boolean interior = true;
                    for (int dc = -1; dc <= 1; dc++) {
                        for (int dr = -1; dr <= 1; dr++) {
                            interior &= map.regionAtCell(col + dc, row + dr) == region;
                        }
                    }
                    if (!interior) {
                        continue;
                    }
                    checked++;
                    Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), 16, QuartPos.fromBlock(z), seeded);
                    helper.assertTrue(region == MitofusMapData.SEA
                            ? biome.equals(biomes.outside().getNoiseBiome(QuartPos.fromBlock(x), 16, QuartPos.fromBlock(z), seeded))
                            : biome.is(ResourceLocation.parse(map.legend(region))), "the biome at " + x + "," + z + " follows region " + region + ", seed " + seed);
                }
            }
            helper.assertTrue(checked > 500, "enough interior points were checked, seed " + seed + ": " + checked);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void theRealWorldPlacesNoSites(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ChunkGenerator generator = generator(helper);
        long seed = -5_551_234_567L; // no real world in the test server has it
        RandomState mitofus = random(registries, seed);
        RandomState realWorld = random(registries, seed);
        WorldSites.exclude(realWorld);
        ChunkPos site = new ChunkPos(7, -9);
        for (String name : List.of(WorldSites.COVADONGA, WorldSites.ALTAMIRA, WorldSites.TORRE_DEL_ORO, WorldSites.EIBAR)) {
            StructureSet set = registries.registryOrThrow(Registries.STRUCTURE_SET).get(Mitofus.id(name));
            helper.assertTrue(set != null && set.placement() instanceof WorldSitePlacement, name + " uses the world-site placement");
            WorldSitePlacement placement = (WorldSitePlacement) set.placement();
            WorldSites.remember(seed, name, site);
            try {
                var lookup = registries.lookupOrThrow(Registries.STRUCTURE_SET);
                helper.assertTrue(placement.isStructureChunk(generator.createState(lookup, mitofus, seed), site.x, site.z),
                        name + "'s chunk is a structure chunk in Mitofus");
                helper.assertFalse(placement.isStructureChunk(generator.createState(lookup, realWorld, seed), site.x, site.z),
                        "the same chunk of the real world is not (" + name + ")");
            } finally {
                WorldSites.forget(seed, name);
            }
        }
        helper.succeed();
    }

    /** The real overworld of the test server went through StartingVillage's load event: none of the four sites is placed in it. */
    @GameTest(template = TestSupport.EMPTY)
    public static void theOverworldIsExcludedOnLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var level = helper.getLevel();
        long seed = level.getSeed();
        var state = level.getChunkSource().getGeneratorState();
        var sets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        ChunkPos site = new ChunkPos(7, -9);
        for (String name : List.of(WorldSites.COVADONGA, WorldSites.ALTAMIRA, WorldSites.TORRE_DEL_ORO, WorldSites.EIBAR)) {
            StructureSet set = sets.get(Mitofus.id(name));
            helper.assertTrue(set != null && set.placement() instanceof WorldSitePlacement, name + " uses the world-site placement");
            WorldSitePlacement placement = (WorldSitePlacement) set.placement();
            var previous = WorldSites.siteFor(seed, name);
            WorldSites.remember(seed, name, site);
            try {
                helper.assertFalse(placement.isStructureChunk(state, site.x, site.z), name + " must not be placed in the loaded overworld");
            } finally {
                previous.ifPresentOrElse(chunk -> WorldSites.remember(seed, name, chunk), () -> WorldSites.forget(seed, name));
            }
        }
        helper.succeed();
    }

    /**
     * The ground at a block: the first solid block from the top ({@code height}, the sea floor at sea), whether it is dry
     * (no water over it), and {@code top}, the terrain's own level, where its depth is 0, without the 3D noise, the
     * jaggedness of peaks and the caves (a cave opening at the surface is not a hole in the terrain).
     */
    record Ground(int height, boolean dry, int top) {
        static Ground at(ChunkGenerator generator, RandomState random, int x, int z) {
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, HEIGHT, random);
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random);
            // depth = offset + a gradient that falls by 1 every 128 blocks up, so it is 0 at 128 * depth(y = 0).
            double depth = random.router().depth().compute(new DensityFunction.SinglePointContext(x, 0, z));
            return new Ground(floor, surface == floor, (int) Math.round(128 * depth));
        }
    }

    /**
     * The terrain follows map ({@code mitofus:mitofus} noise settings, relief from {@code relief.png}):
     * a deep sea north of Covadonga and beyond the map, a low coast where the map's land ends, dry lowland, high mountains
     * in the west, and Covadonga's place, (0, 0), on dry plains above the sea. Points from the map's cells (block (0, 0) is
     * Covadonga's pixel; a cell is 32 blocks).
     */
    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 1200)
    public static void mitofusTerrainFollowsTheMap(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ChunkGenerator generator = generator(helper);
        helper.assertTrue(generator instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().is(MITOFUS_SETTINGS),
                "the dimension uses the map-driven noise settings");
        MitofusMap map = ((MitofusBiomeSource) generator.getBiomeSource()).map();
        int sea = generator.getSeaLevel();
        for (long seed : new long[]{1L, 42L, -7_000_123L}) {
            RandomState random = random(registries, seed);
            // The open sea: 7x7 cells of relief 0 north of Covadonga (cell 110, 3), and far beyond the map.
            for (int[] at : new int[][]{{-8, -804}, {-6000, 400}, {0, -3000}, {9000, 9000}}) {
                Ground ground = Ground.at(generator, random, at[0], at[1]);
                helper.assertTrue(!ground.dry() && ground.height() < sea - 15, "a deep sea at " + at[0] + "," + at[1] + ", seed " + seed + ": " + ground);
            }
            // Dry lowland (5x5 cells of relief 4 in the plains) and the mountains of the west (5x5 of relief 9).
            Ground lowland = Ground.at(generator, random, 1272, 380);
            helper.assertTrue(lowland.dry() && lowland.top() > sea && lowland.top() <= sea + 15, "dry lowland, seed " + seed + ": " + lowland);
            Ground peak = Ground.at(generator, random, 600, 3036);
            helper.assertTrue(peak.dry() && peak.top() > sea + 100, "a high mountain, seed " + seed + ": " + peak);
            Ground eastPeak = Ground.at(generator, random, -72, 1692);
            helper.assertTrue(eastPeak.top() > sea + 60, "the mountains south of Covadonga, seed " + seed + ": " + eastPeak);
            // Covadonga's place, the origin: dry plains a little above the sea, with no big drops. Vanilla's caves open at the
            // surface here and there, so only most of the actual ground has to be dry and above the sea.
            List<Integer> tops = new ArrayList<>();
            List<String> rejected = new ArrayList<>();
            for (int x = -24; x <= 24; x += 8) {
                for (int z = -24; z <= 24; z += 8) {
                    Ground ground = Ground.at(generator, random, x, z);
                    tops.add(ground.top());
                    if (!ground.dry() || ground.height() <= sea) {
                        rejected.add(x + "," + z + ": " + ground);
                    }
                }
            }
            tops.sort(null);
            helper.assertTrue(tops.getFirst() > sea && tops.getLast() <= sea + 20 && tops.getLast() - tops.getFirst() <= 8,
                    "Covadonga's place is low, gentle plains above the sea, seed " + seed + ": " + tops);
            Mitofus.LOGGER.info("Covadonga wet points, seed {}: {} of 49 {}", seed, rejected.size(), rejected);
            for (int[] spot : new int[][]{{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
                Ground ground = Ground.at(generator, random, spot[0], spot[1]);
                helper.assertTrue(ground.dry() && ground.top() > sea, "Covadonga's centre is dry ground above the sea at " + spot[0] + ","
                        + spot[1] + ", seed " + seed + ": " + ground);
            }
            helper.assertTrue(rejected.size() <= MAX_WET_AT_COVADONGA, "Covadonga's place is mostly dry ground, seed " + seed + "; not: " + rejected);
            // The north coast on the meridian of Covadonga: the first water going north is where the map's land ends, and the
            // last dry ground before it is a low coast.
            int shore = Integer.MIN_VALUE;
            Ground last = null;
            int wet = 0;
            for (int z = -132; z >= -900 && shore == Integer.MIN_VALUE; z--) {
                Ground ground = Ground.at(generator, random, -8, z);
                if (ground.dry()) {
                    wet = 0;
                    last = ground;
                } else if (++wet == 4) {
                    shore = z + 3; // four wet columns in a row: the sea, not a puddle
                }
            }
            int mapShore = Integer.MIN_VALUE;
            for (int z = -132; z >= -900; z--) {
                if (!map.isLand(-8, z)) {
                    mapShore = z;
                    break;
                }
            }
            helper.assertTrue(shore != Integer.MIN_VALUE && Math.abs(shore - mapShore) <= 24,
                    "the sea starts where the map's land ends, seed " + seed + ": z " + shore + " vs " + mapShore);
            helper.assertTrue(last != null && last.top() <= sea + 8, "a low coast, seed " + seed + ": " + last);
        }
        helper.succeed();
    }

    /** What a cut found: how many flooded cave-mouth columns, the longest run of them and the steepest steps. */
    private record CutStats(int caveMouths, int longestMouth, int maxLandJump, int maxWaterJump) {
    }

    /**
     * One cut, block by block, from (startX, startZ) along (dx, dz) (a unit step on each axis it moves on) across the
     * map's coast: it starts {@code back} steps before the coast (all the way from the start if {@code back} is large)
     * and ends 200 steps out to sea. The terrain's own level crosses the sea level once, within {@code margin} blocks of
     * the map's coast, and never jumps between neighbouring blocks by more than {@code landJump} on land (the steepest
     * hillside below the mountains) or {@code waterJump} under water (vanilla's drop-off from the coast to the ocean
     * floor). The actual ground: more than {@code margin} blocks inland it has no water but in the mouth of a vanilla cave
     * (floor well under the terrain's level; caves under the sea level flood, as in vanilla), a run of at most
     * {@code MAX_CAVE_MOUTH_RUN} columns; more than {@code margin} blocks out to sea it is all water, with no stray
     * islands; and the shore is one line, with a fringe of a few blocks at most.
     */
    private static CutStats coastCut(GameTestHelper helper, ChunkGenerator generator, MitofusMap map, RandomState random, String where,
                                     int startX, int startZ, int dx, int dz, int back, int landJump, int waterJump) {
        int sea = generator.getSeaLevel();
        int margin = 24;
        int shoreIndex = Integer.MIN_VALUE;
        for (int i = 0; i <= 6000 && shoreIndex == Integer.MIN_VALUE; i++) {
            if (!map.isLand(startX + i * dx, startZ + i * dz)) {
                shoreIndex = i;
            }
        }
        helper.assertTrue(shoreIndex != Integer.MIN_VALUE, where + ": the map has a coast on this cut (map data)");
        int from = Math.max(0, shoreIndex - back);
        helper.assertTrue(map.isLand(startX + from * dx, startZ + from * dz), where + ": the cut starts on the map's land (map data)");
        int caveMouths = 0;
        int run = 0;
        int longest = 0;
        int maxLand = 0;
        int maxWater = 0;
        List<String> wrong = new ArrayList<>();
        List<Integer> shapeShores = new ArrayList<>();
        int lastDry = Integer.MIN_VALUE;
        int firstWet = Integer.MIN_VALUE;
        Ground previous = Ground.at(generator, random, startX + from * dx, startZ + from * dz);
        for (int i = from; i <= shoreIndex + 200; i++) {
            Ground ground = Ground.at(generator, random, startX + i * dx, startZ + i * dz);
            if ((ground.top() > sea) != (previous.top() > sea)) {
                shapeShores.add(i);
            }
            int jump = Math.abs(ground.top() - previous.top());
            boolean onLand = Math.min(ground.top(), previous.top()) >= sea;
            if (onLand) {
                maxLand = Math.max(maxLand, jump);
            } else {
                maxWater = Math.max(maxWater, jump);
            }
            if (jump > (onLand ? landJump : waterJump)) {
                wrong.add("a jump at step " + i + ": " + previous.top() + " -> " + ground.top());
            }
            boolean caveMouth = !ground.dry() && ground.height() < ground.top() - 4 && ground.top() > sea;
            if (caveMouth) {
                caveMouths++;
                longest = Math.max(longest, ++run);
            } else {
                run = 0;
                if (ground.dry()) {
                    lastDry = i;
                } else if (firstWet == Integer.MIN_VALUE) {
                    firstWet = i;
                }
            }
            if (i < shoreIndex - margin && !ground.dry() && !caveMouth) {
                wrong.add("water on land at step " + i + " " + ground);
            }
            if (i > shoreIndex + margin && (ground.dry() || ground.height() >= sea)) {
                wrong.add("land in the sea at step " + i + " " + ground);
            }
            previous = ground;
        }
        helper.assertTrue(wrong.isEmpty(), where + ": " + wrong);
        helper.assertTrue(shapeShores.size() == 1 && Math.abs(shapeShores.getFirst() - shoreIndex) <= margin,
                "the terrain meets the sea once, at the map's coast, " + where + ": " + shapeShores + " vs " + shoreIndex);
        helper.assertTrue(firstWet != Integer.MIN_VALUE && firstWet - lastDry <= SHORE_FRINGE,
                "one clean shore, " + where + ": first water at step " + firstWet + ", last dry ground at step " + lastDry);
        helper.assertTrue(longest <= MAX_CAVE_MOUTH_RUN, "a cave mouth is narrow, not a lake, " + where + ": " + longest + " columns in a row");
        Mitofus.LOGGER.info("Cut {}: cave mouths {}, longest run {}, max land jump {}, max water jump {}", where, caveMouths, longest, maxLand, maxWater);
        return new CutStats(caveMouths, longest, maxLand, maxWater);
    }

    /**
     * Cuts from Covadonga's latitude north to the open sea on three meridians (Covadonga's, 128 blocks east and 300 west); see
     * {@link #coastCut}. In all, the flooded cave-mouth columns stay under {@link #MAX_CAVE_MOUTHS}.
     */
    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 2400)
    public static void cutsFromCovadongaToTheCoastHaveNoHoles(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ChunkGenerator generator = generator(helper);
        MitofusMap map = ((MitofusBiomeSource) generator.getBiomeSource()).map();
        int caveMouths = 0;
        for (long seed : new long[]{1L, 5L, 99L}) {
            RandomState random = random(registries, seed);
            for (int x : new int[]{-8, 200, -300}) {
                caveMouths += coastCut(helper, generator, map, random, "seed " + seed + ", x=" + x + " north", x, 0, 0, -1, Integer.MAX_VALUE, 3, 6).caveMouths();
            }
        }
        Mitofus.LOGGER.info("Cuts from Covadonga to the coast: {} columns of flooded cave mouths on land", caveMouths);
        helper.assertTrue(caveMouths <= MAX_CAVE_MOUTHS, "flooded cave mouths on land: " + caveMouths + " (at most " + MAX_CAVE_MOUTHS + ")");
        helper.succeed();
    }

    /** The other orientations of the coast: a cut to the east, to the south, to the west and a diagonal one to the south-east. */
    private static void otherCoast(GameTestHelper helper, String name, int startX, int startZ, int dx, int dz, int landJump, int waterJump) {
        var registries = helper.getLevel().registryAccess();
        ChunkGenerator generator = generator(helper);
        MitofusMap map = ((MitofusBiomeSource) generator.getBiomeSource()).map();
        for (long seed : new long[]{1L, 99L}) {
            coastCut(helper, generator, map, random(registries, seed), "seed " + seed + " " + name, startX, startZ, dx, dz, COAST_APPROACH,
                    landJump, waterJump);
        }
        helper.succeed();
    }

    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 2400)
    public static void cutToTheEastCoastHasNoHoles(GameTestHelper helper) {
        otherCoast(helper, "east", 0, -176, 1, 0, EAST_LAND_JUMP, 6);
    }

    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 2400)
    public static void cutToTheSouthCoastHasNoHoles(GameTestHelper helper) {
        otherCoast(helper, "south", -1136, 0, 0, 1, SOUTH_LAND_JUMP, 6);
    }

    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 2400)
    public static void cutToTheWestCoastHasNoHoles(GameTestHelper helper) {
        otherCoast(helper, "west", 0, 496, -1, 0, WEST_LAND_JUMP, 6);
    }

    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 2400)
    public static void cutToTheSouthEastCoastHasNoHoles(GameTestHelper helper) {
        otherCoast(helper, "south-east", -608, 0, 1, 1, DIAGONAL_LAND_JUMP, 6);
    }

    /**
     * A cut through the western range (relief 8-9 between x = -1700 and -2400, on z = -4): always high above the sea, with
     * no jump over {@link #MOUNTAIN_JUMP} between neighbouring blocks (the land rule of the coast cuts, 3, does not
     * hold up here).
     */
    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 2400)
    public static void aCutThroughTheMountainsHasNoCliffsOrHoles(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ChunkGenerator generator = generator(helper);
        MitofusMap map = ((MitofusBiomeSource) generator.getBiomeSource()).map();
        int sea = generator.getSeaLevel();
        for (long seed : MitofusFixedWorldTests.FEW_SEEDS) {
            RandomState random = random(registries, seed);
            Ground previous = Ground.at(generator, random, -1700, -4);
            int maxJump = 0;
            int lowest = Integer.MAX_VALUE;
            for (int x = -1701; x >= -2400; x--) {
                helper.assertTrue(map.reliefAt(x, -4) >= 6, "the cut stays on the range (map data) at x=" + x + ": " + map.reliefAt(x, -4));
                Ground ground = Ground.at(generator, random, x, -4);
                maxJump = Math.max(maxJump, Math.abs(ground.top() - previous.top()));
                lowest = Math.min(lowest, ground.top());
                previous = ground;
            }
            Mitofus.LOGGER.info("Mountain cut, seed {}: max jump {}, lowest top {} (sea {})", seed, maxJump, lowest, sea);
            helper.assertTrue(maxJump <= MOUNTAIN_JUMP, "no cliffs in the range, seed " + seed + ": a jump of " + maxJump + " blocks");
            helper.assertTrue(lowest > sea + MOUNTAIN_MIN_HEIGHT, "high above the sea all along, seed " + seed + ": lowest " + (lowest - sea));
        }
        helper.succeed();
    }
}
