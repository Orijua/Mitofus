package com.orijua.mitofus.feature.world;

import com.orijua.mitofus.Mitofus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Covadonga, the starting village of the Mitofus dimension. The dimension's (0, 0) is Covadonga's pixel on map
 * ({@link MitofusMapData}), in its plains. Mitofus is a world with a known map, so it is the same in every
 * new world: when the dimension first loads in a new world, it gets the fixed sites of {@code mitofus_map/west/sites.json}
 * ({@link MitofusSites}): Covadonga at (0, 0), the Altamira Cave on the snowy north coast ({@link Altamira}), Earl
 * Nuno's manor near Covadonga ({@link TorreDelOro}) and Eibar at its place on the map, west-northwest of Covadonga
 * ({@link Eibar}), whatever the seed. They are saved with the landing spot on Covadonga's square, by the well, where a
 * player entering Mitofus starts, as Cid starts in Covadonga. They then generate there with the terrain, like any village
 * ({@link WorldSitePlacement}).
 * <p>
 * The fixed sites were chosen once with {@link #search}, the search over the map that every world ran before
 * 2026-11-07 (Covadonga nearest (0, 0) on dry plains, the rest from it); the search stays for that and for the tests on
 * terrain that doesn't follow the map. They are chosen only once per world (it is remembered); worlds created before
 * keep the sites their search gave them, and worlds created before the dimension have none.
 */
public final class StartingVillage {
    /**
     * How far around Covadonga's place on the map, (0, 0), to look, in chunks: the terrain follows the map, so Covadonga's
     * plains are there and the village lands at (0, 0) or a chunk or two away.
     */
    public static final int SEARCH_RADIUS = 4;
    private static final ResourceKey<Structure> VILLAGE = ResourceKey.create(Registries.STRUCTURE, Covadonga.STRUCTURE);

    private StartingVillage() {
    }

    /** Where the village is and where the player lands. */
    public record Site(ChunkPos chunk, BlockPos spawn) {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(StartingVillage::onLevelLoad);
        gameBus.addListener(StartingVillage::onServerStopped);
    }

    /**
     * The real world's dimensions place no site. The Mitofus dimension: its saved sites have to be known before its
     * chunks generate, and a new world gets them now.
     */
    private static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!MitofusWorld.is(level)) {
            WorldSites.exclude(level.getChunkSource().randomState());
            return;
        }
        WorldSitesData data = WorldSitesData.get(level.getServer().overworld());
        long seed = level.getSeed();
        if (!data.chosen()) {
            ServerChunkCache chunks = level.getChunkSource();
            Chosen chosen = choose(level.registryAccess(), chunks.getGenerator(), chunks.randomState(), level.getStructureManager(), seed, level);
            chosen.sites().forEach(data::setSite);
            chosen.landing().ifPresent(data::setLanding);
            chosen.sites().forEach((name, site) -> Mitofus.LOGGER.info("Mitofus site {} at chunk {}", name, site));
            data.markChosen();
        }
        data.sites().forEach((name, site) -> WorldSites.remember(seed, name, site));
        data.landing().ifPresent(landing -> WorldSites.rememberLanding(seed, landing));
    }

    /** The sites a new Mitofus world gets, and where a player lands in Covadonga. */
    public record Chosen(Map<String, ChunkPos> sites, Optional<BlockPos> landing) {
    }

    /**
     * The sites of every new world: the fixed ones of {@link MitofusSites#west}, the same in every seed, and the landing
     * on Covadonga's square (the village's layout, and so the exact landing, still follows the seed; if the village does not
     * assemble, the surface in the middle of its chunk). Only reads the terrain noise: nothing is placed.
     */
    public static Chosen choose(RegistryAccess registries, ChunkGenerator generator, RandomState random, StructureTemplateManager templates,
                                long seed, LevelHeightAccessor height) {
        return fixed(MitofusSites.west(), registries, generator, random, templates, seed, height);
    }

    /** {@link #choose} with these sites. Without Covadonga, no sites at all. */
    public static Chosen fixed(MitofusSites fixed, RegistryAccess registries, ChunkGenerator generator, RandomState random,
                               StructureTemplateManager templates, long seed, LevelHeightAccessor height) {
        Optional<ChunkPos> village = fixed.get(WorldSites.COVADONGA);
        if (village.isEmpty()) {
            Mitofus.LOGGER.warn("The fixed Mitofus sites have no Covadonga; this world has no starting village");
            return new Chosen(Map.of(), Optional.empty());
        }
        ChunkPos chunk = village.get();
        BlockPos landing = villageAt(registries, generator, random, templates, seed, height, chunk).map(Site::spawn).orElseGet(() -> {
            Mitofus.LOGGER.warn("Covadonga does not assemble at its fixed chunk {}; landing in the middle of it", chunk);
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();
            return new BlockPos(x, generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random), z);
        });
        return new Chosen(new LinkedHashMap<>(fixed.all()), Optional.of(landing));
    }

    /**
     * The search every world ran before its sites were fixed, and how {@code sites.json} was chosen: Covadonga first,
     * nearest (0, 0) ({@link #findSite}), and the rest from it and the map: the cave on the snowy north coast, the manor
     * near Covadonga and Eibar at its place on the map, away from the other two. Its result follows the seed a little (the
     * 3D noise and the layout of the structures). Empty if Covadonga has no room. Only reads the terrain noise and the map.
     */
    public static Chosen search(RegistryAccess registries, ChunkGenerator generator, RandomState random, StructureTemplateManager templates,
                                long seed, LevelHeightAccessor height) {
        Optional<Site> covadonga = findSite(registries, generator, random, templates, seed, height, new ChunkPos(0, 0));
        if (covadonga.isEmpty()) {
            Mitofus.LOGGER.warn("No room for Covadonga within {} chunks of its place on the map", SEARCH_RADIUS);
            return new Chosen(Map.of(), Optional.empty());
        }
        ChunkPos village = covadonga.get().chunk();
        Map<String, ChunkPos> sites = new LinkedHashMap<>();
        sites.put(WorldSites.COVADONGA, village);
        Altamira.findSite(registries, generator, random, templates, seed, height, village)
                .ifPresent(cave -> sites.put(WorldSites.ALTAMIRA, cave));
        TorreDelOro.findSite(registries, generator, random, templates, seed, height, village, TorreDelOro.MIN_DISTANCE, TorreDelOro.MAX_DISTANCE)
                .ifPresent(manor -> sites.put(WorldSites.TORRE_DEL_ORO, manor));
        // Eibar keeps away from the cave and the manor.
        List<ChunkPos> taken = Stream.of(WorldSites.ALTAMIRA, WorldSites.TORRE_DEL_ORO).map(sites::get).filter(Objects::nonNull).toList();
        Eibar.findSite(registries, generator, random, templates, seed, height, village, taken)
                .ifPresent(eibar -> sites.put(WorldSites.EIBAR, eibar));
        return new Chosen(sites, Optional.of(covadonga.get().spawn()));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        WorldSites.clear();
    }

    /**
     * The chunk nearest {@code near}, up to {@link #SEARCH_RADIUS} chunks away, where the village assembles (its biome
     * check included) in the map's plains (in Mitofus, as on map) with every piece on dry land, and the spawn
     * on its square. Only reads the terrain noise: nothing is generated or placed.
     */
    public static Optional<Site> findSite(RegistryAccess registries, ChunkGenerator generator, RandomState random,
                                          StructureTemplateManager templates, long seed, LevelHeightAccessor height, ChunkPos near) {
        return findSite(registries, generator, random, templates, seed, height, near, SEARCH_RADIUS);
    }

    /**
     * The same search up to {@code radius} chunks away: wider on terrain that doesn't follow the map, such as the
     * vanilla overworld of the tests.
     */
    public static Optional<Site> findSite(RegistryAccess registries, ChunkGenerator generator, RandomState random,
                                          StructureTemplateManager templates, long seed, LevelHeightAccessor height, ChunkPos near,
                                          int radius) {
        Structure village = registries.registryOrThrow(Registries.STRUCTURE).get(VILLAGE);
        if (village == null) {
            return Optional.empty();
        }
        for (ChunkPos chunk : ChunkSpiral.around(near, radius)) {
            if (!MitofusBiomeSource.inMapRegion(generator.getBiomeSource(), chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), MitofusMapData.PLAINS)) {
                continue;
            }
            Optional<Site> site = villageAt(village, registries, generator, random, templates, seed, height, chunk);
            if (site.isPresent()) {
                return site;
            }
        }
        return Optional.empty();
    }

    /**
     * The village at this chunk, if it assembles there (its biome check included) with every piece on dry land, and the
     * spawn on its square. Empty if the generator doesn't have it. Only reads the terrain noise.
     */
    public static Optional<Site> villageAt(RegistryAccess registries, ChunkGenerator generator, RandomState random,
                                           StructureTemplateManager templates, long seed, LevelHeightAccessor height, ChunkPos chunk) {
        Structure village = registries.registryOrThrow(Registries.STRUCTURE).get(VILLAGE);
        return village == null ? Optional.empty() : villageAt(village, registries, generator, random, templates, seed, height, chunk);
    }

    private static Optional<Site> villageAt(Structure village, RegistryAccess registries, ChunkGenerator generator, RandomState random,
                                            StructureTemplateManager templates, long seed, LevelHeightAccessor height, ChunkPos chunk) {
        StructureStart start = village.generate(registries, generator, generator.getBiomeSource(), random, templates, seed, chunk, 0,
                height, village.biomes()::contains);
        if (start.isValid() && start.getPieces().stream().allMatch(piece -> SiteTerrain.onDryLand(piece, generator, random, height))) {
            return Optional.of(new Site(chunk, spawnOnSquare(start.getPieces().getFirst().getBoundingBox())));
        }
        return Optional.empty();
    }

    /** Three blocks east of the well in the middle of the square: open floor however the square is turned. */
    private static BlockPos spawnOnSquare(BoundingBox square) {
        BlockPos middle = square.getCenter();
        return new BlockPos(middle.getX() + 3, square.minY() + 1, middle.getZ());
    }
}
