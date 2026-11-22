package com.orijua.mitofus.gametest;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.ModBlocks;
import com.orijua.mitofus.core.registry.ModItems;
import com.orijua.mitofus.feature.sancho.GoldenTableBlock;
import com.orijua.mitofus.feature.world.TorreDelOro;
import com.orijua.mitofus.feature.world.Altamira;
import com.orijua.mitofus.feature.world.AltamiraProtection;
import com.orijua.mitofus.feature.world.PoolPieces;
import com.orijua.mitofus.feature.world.StartingVillage;
import com.orijua.mitofus.feature.world.Eibar;
import com.orijua.mitofus.feature.world.WorldSitePlacement;
import com.orijua.mitofus.feature.world.WorldSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** The Altamira Cave: where it goes in each world, how it assembles, the hall that can't be looted and the golden table. */
@GameTestHolder(Mitofus.MODID)
@PrefixGameTestTemplate(false)
public final class AltamiraTests {
    private AltamiraTests() {
    }

    /** A test's template is loaded one block above its structure block, which is what the helper's positions count from. */
    private static BlockPos inPiece(int x, int y, int z) {
        return new BlockPos(x, y + 1, z);
    }

    @GameTest(template = TestSupport.EMPTY)
    public static void caveGeneratesOnlyAtItsWorldsSite(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.registryOrThrow(Registries.STRUCTURE).containsKey(Altamira.STRUCTURE), "structure");
        StructureSet set = registries.registryOrThrow(Registries.STRUCTURE_SET).get(Altamira.STRUCTURE);
        helper.assertTrue(set != null && set.placement() instanceof WorldSitePlacement site && site.site().equals(WorldSites.ALTAMIRA),
                "placed at the cave's site");
        WorldSitePlacement placement = (WorldSitePlacement) set.placement();
        long seed = -7_123_456_790L;
        helper.assertFalse(placement.applyAdditionalChunkRestrictions(0, 0, seed), "a world without a site has no cave");
        WorldSites.remember(seed, WorldSites.ALTAMIRA, new ChunkPos(3, -60));
        try {
            helper.assertTrue(new ChunkPos(3, -60).equals(placement.getPotentialStructureChunk(seed, 7, 7)), "/locate goes to the site");
            helper.assertTrue(WorldSites.siteFor(seed, WorldSites.COVADONGA).isEmpty(), "the sites don't mix");
        } finally {
            WorldSites.forget(seed, WorldSites.ALTAMIRA);
        }
        TagKey<Biome> cold = TagKey.create(Registries.BIOME, Mitofus.id("has_structure/altamira"));
        helper.assertTrue(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS).is(cold), "snowy plains are cold");
        helper.assertFalse(registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS).is(cold), "plains are not");
        helper.succeed();
    }

    /**
     * The real jigsaw assembly on real terrain (in the flat test world the surface is too close to the bottom of the
     * world for the descent): entrance, descent, hall and sanctum, in that order and each one deeper or level.
     */
    @GameTest(template = TestSupport.EMPTY)
    public static void caveAssemblesGoingDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess registries = level.registryAccess();
        Structure cave = registries.registryOrThrow(Registries.STRUCTURE).get(Altamira.STRUCTURE);
        Holder<NoiseGeneratorSettings> settings = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        ChunkGenerator generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(registries
                .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                settings);
        RandomState random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), 12345L);
        StructureStart start = cave.generate(registries, generator, generator.getBiomeSource(), random, level.getStructureManager(),
                12345L, new ChunkPos(400, 400), 0, LevelHeightAccessor.create(-64, 384), biome -> true);
        helper.assertTrue(start.isValid(), "the cave should assemble");
        List<StructurePiece> pieces = start.getPieces();
        List<Optional<ResourceLocation>> templates = pieces.stream().map(PoolPieces::templateOf).toList();
        helper.assertTrue(pieces.size() == 4, "four pieces, got " + templates);
        List<String> order = List.of(Altamira.ENTRANCE, Altamira.DESCENT, Altamira.HALL, Altamira.SANCTUM);
        for (int i = 0; i < order.size(); i++) {
            helper.assertTrue(templates.get(i).filter(Mitofus.id(order.get(i))::equals).isPresent(), "piece " + i + " is " + order.get(i) + ": " + templates);
        }
        int entranceFloor = pieces.get(0).getBoundingBox().minY() + 2;
        int hallFloor = pieces.get(2).getBoundingBox().minY() + 1;
        helper.assertTrue(entranceFloor - hallFloor == Altamira.DROP, "the hall lies " + Altamira.DROP
                + " below the mouth: " + entranceFloor + " -> " + hallFloor);
        helper.assertTrue(pieces.get(3).getBoundingBox().minY() == pieces.get(2).getBoundingBox().minY(), "the sanctum is level with the hall");
        helper.succeed();
    }

    /**
     * Seeds out of 20 whose cave must be in the cold. Measured: 14/20 (seeds 1-20, after BUG-042's capped search) with a
     * margin of 2. Recalibrate if the search, the cold tag or the terrain change.
     */
    private static final int MIN_COLD_SEEDS = 12;

    /**
     * On real overworld terrain (no lore map), every one of 20 seeds gets its cave far to the north of Covadonga, most of them
     * in the cold, Nuno's manor near Covadonga and Eibar off, away from both, at the map's offsets from Covadonga.
     * The search reads only the noise: nothing generates.
     */
    @GameTest(template = TestSupport.EMPTY, timeoutTicks = 4000)
    public static void oneOfAKindPlacesFindSitesInRealWorlds(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Holder<NoiseGeneratorSettings> settings = registries.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        BiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        TagKey<Biome> coldTag = TagKey.create(Registries.BIOME, Mitofus.id("has_structure/altamira"));
        int cold = 0;
        int nearest = Integer.MAX_VALUE;
        int farthest = 0;
        for (long seed = 1; seed <= 20; seed++) {
            RandomState random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
            ChunkPos usualSpawn = new ChunkPos(random.sampler().findSpawnPosition());
            ChunkPos covadonga = StartingVillage.findSite(registries, generator, random, helper.getLevel().getStructureManager(), seed, height,
                    usualSpawn, TestSupport.VANILLA_TERRAIN_SEARCH_RADIUS).orElseThrow().chunk();
            Optional<ChunkPos> cave = Altamira.findSite(registries, generator, random, helper.getLevel().getStructureManager(), seed,
                    height, covadonga);
            helper.assertTrue(cave.isPresent(), "no cave for seed " + seed);
            Optional<ChunkPos> manor = TorreDelOro.findSite(registries, generator, random, helper.getLevel().getStructureManager(), seed, height,
                    covadonga, TorreDelOro.MIN_DISTANCE, TorreDelOro.MAX_DISTANCE);
            helper.assertTrue(manor.isPresent(), "no room for Nuno's manor near Covadonga, seed " + seed);
            Optional<ChunkPos> eibar = Eibar.findSite(registries, generator, random, helper.getLevel().getStructureManager(), seed, height,
                    covadonga, List.of(cave.get(), manor.get()), TestSupport.VANILLA_TERRAIN_SEARCH_RADIUS);
            helper.assertTrue(eibar.isPresent(), "no room for Eibar, seed " + seed);
            int fromCovadonga = covadonga.getChessboardDistance(eibar.get());
            helper.assertTrue(fromCovadonga >= Eibar.KEEP_AWAY, "Eibar only " + fromCovadonga + " chunks off");
            helper.assertTrue(eibar.get().getChessboardDistance(cave.get()) >= Eibar.KEEP_AWAY, "Eibar away from the cave, seed " + seed);
            int north = covadonga.z - cave.get().z;
            helper.assertTrue(north >= Altamira.MIN_DISTANCE, "seed " + seed + ": only " + north + " chunks north of Covadonga");
            nearest = Math.min(nearest, north);
            farthest = Math.max(farthest, north);
            int y = generator.getBaseHeight(cave.get().getMiddleBlockX(), cave.get().getMiddleBlockZ(),
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, height, random);
            if (biomes.getNoiseBiome(cave.get().getMiddleBlockX() >> 2, y >> 2, cave.get().getMiddleBlockZ() >> 2, random.sampler()).is(coldTag)) {
                cold++;
            } else {
                // Not in the cold: it is still on dry land (the fallback looks for dry ground).
                int floor = generator.getBaseHeight(cave.get().getMiddleBlockX(), cave.get().getMiddleBlockZ(),
                        net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, height, random);
                helper.assertTrue(floor == y, "the cave of seed " + seed + " is not in the cold and not on dry land either: " + cave.get());
            }
        }
        Mitofus.LOGGER.info("Altamira Cave: {}/20 seeds in the cold, {} to {} blocks north of Covadonga", cold, nearest * 16, farthest * 16);
        helper.assertTrue(cold >= MIN_COLD_SEEDS, "most caves should be in the cold: " + cold + "/20 (at least " + MIN_COLD_SEEDS + ")");
        helper.succeed();
    }

    /** The table gives the book once, with the system's notice, and is empty after. It can't be broken. */
    @GameTest(template = Altamira.SANCTUM)
    public static void theGoldenTableGivesTheBookOnce(GameTestHelper helper) {
        BlockPos table = inPiece(6, 2, 6);
        helper.assertBlockPresent(ModBlocks.GOLDEN_TABLE.get(), table);
        helper.assertBlockProperty(table, GoldenTableBlock.BOOK, true);
        helper.assertTrue(helper.getBlockState(table).getDestroySpeed(helper.getLevel(), helper.absolutePos(table)) < 0, "unbreakable");
        ServerPlayer player = TestSupport.fakePlayer(helper);
        helper.useBlock(table, player);
        helper.assertBlockProperty(table, GoldenTableBlock.BOOK, false);
        helper.assertTrue(player.getInventory().countItem(ModItems.UNAPPRAISED_BOOK.get()) == 1, "the book");
        helper.useBlock(table, player);
        helper.assertTrue(player.getInventory().countItem(ModItems.UNAPPRAISED_BOOK.get()) == 1, "only one");
        helper.succeed();
    }

    /** The stands in the hall: all relics, locked and invulnerable, and a survival player's blow doesn't touch them. */
    @GameTest(template = Altamira.HALL)
    public static void theHallsGearCantBeTaken(GameTestHelper helper) {
        List<ArmorStand> stands = helper.getEntities(EntityType.ARMOR_STAND);
        helper.assertTrue(stands.size() == 10, "ten armored stands, got " + stands.size());
        ServerPlayer player = TestSupport.fakePlayer(helper);
        for (ArmorStand stand : stands) {
            helper.assertTrue(AltamiraProtection.isRelic(stand), "a relic");
            helper.assertTrue(stand.isInvulnerable(), "invulnerable");
            helper.assertFalse(stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "wearing its armor");
        }
        ArmorStand first = stands.getFirst();
        player.attack(first);
        helper.assertTrue(first.isAlive() && !first.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "still there, still armored");
        helper.succeed();
    }
}
